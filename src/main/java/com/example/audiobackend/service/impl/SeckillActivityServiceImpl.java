package com.example.audiobackend.service.impl;

import com.example.audiobackend.constant.RedisConstant;
import com.example.audiobackend.constant.SeckillMetaField;
import com.example.audiobackend.entity.SeckillActivity;
import com.example.audiobackend.exception.BusinessException;
import com.example.audiobackend.mapper.ProductMapper;
import com.example.audiobackend.mapper.SeckillActivityMapper;
import com.example.audiobackend.service.SeckillActivityService;
import com.example.audiobackend.vo.SeckillActivityVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Duration;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class SeckillActivityServiceImpl implements SeckillActivityService {

    /** meta 的 TTL：活动最长按天算，给 7 天足够，避免活动删了 key 还留着 */
    private static final long META_TTL_SECONDS = 7 * 24 * 3600L;

    /**
     * 库存 key 的兜底 TTL。
     *
     * <p>正常由活动收尾任务删除，这里只防「收尾任务删失败 → 库存 key 永久残留」。
     * 取 30 天而不是「活动结束 + 1 天」：活动被延长时 TTL 不会跟着更新，
     * 算得太紧反而可能在活动进行中把库存 key 过期掉 —— 活动突然卖不了，
     * 比残留一个 key 严重得多。
     */
    private static final Duration STOCK_TTL = Duration.ofDays(30);

    private final SeckillActivityMapper seckillActivityMapper;
    private final ProductMapper productMapper;
    private final StringRedisTemplate stringRedisTemplate;
    private final DefaultRedisScript<Long> seckillRollbackScript;

    @Override
    public void releaseSeckillStock(Long actId, Long userId) {
        // 只在下面判断活动状态用不到，这里查一次只是为了拿 productId
        SeckillActivity activity = seckillActivityMapper.selectById(actId);
        if (activity == null) {
            log.error("关单回滚时活动不存在，actId={}", actId);
            return;
        }

        // ① 用条件 UPDATE 决定还到哪张表 —— 判断和写入必须同原子。
        //    不能先 selectById 看 status 再分支：查状态和改库存是两次数据库交互，
        //    中间活动可能被执行结束任务改成 FINISHED，那 1 件就永远回不到商品库存了。
        if (seckillActivityMapper.increaseStockIfNotFinished(actId) == 1) {
            // 活动还在进行中，已经加到活动库存 → Redis 名额也要放回去
            releaseRedisAfterCommit(actId, userId);
        } else {
            // 活动已结束：剩余库存早已还回商品，这 1 件也直接给商品。
            // Redis 不用动 —— 活动结束时 key 已经被清理了。
            productMapper.increaseStock(activity.getProductId(), 1);
        }
    }

    /**
     * Redis 名额推迟到事务提交之后才放回去。
     *
     * <p>为什么不能直接做：Redis 不受数据库事务管理。如果先动 Redis 而事务最后提交失败，
     * 名额就白放出去了 —— 多给一个名额 = 超卖。先提交再动 Redis，最坏结果是 Redis
     * 没放成功（少卖），方向是安全的。
     */
    private void releaseRedisAfterCommit(Long actId, Long userId) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            log.warn("releaseSeckillStock 不在事务中调用，直接回滚 Redis，actId={}", actId);
            redisRelease(actId, userId);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                redisRelease(actId, userId);
            }
        });
    }

    @Override
    public List<Long> findExpiredRunningIds(int limit) {
        return seckillActivityMapper.selectExpiredRunning(limit)
                .stream()
                .map(SeckillActivity::getId)
                .toList();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean finishOneActivity(Long actId) {
        // 只从「进行中」改成「已结束」：多实例重复扫描时，只有一个能改成功
        if (seckillActivityMapper.finish(actId) == 0) {
            return false;
        }

        SeckillActivity activity = seckillActivityMapper.selectById(actId);
        if (activity == null) {
            log.error("结束活动后查不到活动，actId={}", actId);
            return true;
        }

        // 没卖完的还回商品库存 —— 创建活动时是从这里扣出来的
        int remaining = activity.getStock() == null ? 0 : activity.getStock();
        if (remaining > 0) {
            productMapper.increaseStock(activity.getProductId(), remaining);
        }

        // 清理 Redis。同样推迟到提交之后：万一事务回滚，提前删了 Redis
        // 会让还在进行中的活动瞬间抢不了
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    stringRedisTemplate.delete(List.of(
                            RedisConstant.SECKILL_STOCK + actId,
                            RedisConstant.SECKILL_META + actId,
                            RedisConstant.SECKILL_BOUGHT + actId,
                            RedisConstant.SECKILL_RATE + actId));
                }
            });
        }

        log.info("秒杀活动结束，actId={}，剩余库存 {} 已还回商品 {}", actId, remaining, activity.getProductId());
        return true;
    }

    /**
     * 把名额还回 Redis。
     *
     * <p>失败时记日志并抛出：此时订单已经提交为 CLOSED，下一轮关单扫描不会再捞到它，
     * <b>不会自动重试</b>。抛出来是为了让异常带着堆栈冒到调用方，而不是静默消失；
     * 真正的补偿只能靠对账（部署阶段再做）。
     */
    private void redisRelease(Long actId, Long userId) {
        try {
            stringRedisTemplate.execute(
                    seckillRollbackScript,
                    List.of(RedisConstant.SECKILL_STOCK + actId, RedisConstant.SECKILL_BOUGHT + actId),
                    String.valueOf(userId));
        } catch (Exception e) {
            log.error("关单回滚 Redis 名额失败，订单已提交为 CLOSED 不会重试，只能靠对账补偿，actId={}，userId={}",
                    actId, userId, e);
            throw new IllegalStateException("关单回滚 Redis 名额失败，actId=" + actId, e);
        }
    }

    @Override
    public SeckillActivityVO getById(Long actId) {
        if (actId == null || actId <= 0) {
            throw new BusinessException("活动 id 不合法");
        }
        SeckillActivity activity = seckillActivityMapper.selectById(actId);
        if (activity == null) {
            throw new BusinessException("活动不存在");
        }

        SeckillActivityVO vo = new SeckillActivityVO();
        vo.setActId(activity.getId());
        vo.setProductId(activity.getProductId());
        vo.setSeckillPrice(activity.getSeckillPrice());
        vo.setStock(activity.getStock());
        vo.setLimitPerUser(activity.getLimitPerUser());
        vo.setStartTime(activity.getStartTime());
        vo.setEndTime(activity.getEndTime());
        vo.setStatus(activity.getStatus());
        return vo;
    }

    @Override
    public int warmUp() {
        List<SeckillActivity> activities = seckillActivityMapper.selectNotFinished();
        int created = 0;
        for (SeckillActivity activity : activities) {
            try {
                if (warmUpOne(activity)) {
                    created++;
                }
            } catch (Exception e) {
                // 单个活动预热失败不影响其他活动，也不影响应用启动
                log.warn("秒杀活动预热失败，actId={}", activity.getId(), e);
            }
        }
        log.info("秒杀预热完成：未结束活动 {} 个，新写入库存 key {} 个（其余 key 已存在，未被覆盖）",
                activities.size(), created);
        return created;
    }

    /**
     * 预热单个活动。
     *
     * @return true 表示库存 key 是本次新建的；false 表示 key 已存在，保留了 Redis 里的实时值
     */
    private boolean warmUpOne(SeckillActivity activity) {
        String stockKey = RedisConstant.SECKILL_STOCK + activity.getId();

        // 必须是 setIfAbsent：活动可能已经进行中、Redis 里的库存已经扣过一部分，
        // 直接 SET 会把已放行的名额再放一遍 → 超卖
        Boolean created = stringRedisTemplate.opsForValue()
                .setIfAbsent(stockKey, String.valueOf(activity.getStock()));

        // 给库存 key 一个很长的兜底过期（30 天），防止活动收尾任务删 key 失败时永久残留。
        //
        // 为什么不用「活动结束时间 + 1 天」：活动可以被延长（改 end_time），而 TTL 是写死的、
        // 不会跟着更新，那样可能在活动进行中把库存 key 过期掉 —— 活动突然卖不了，
        // 比残留一个 key 严重得多。30 天远长于任何秒杀活动，风险接近 0。
        stringRedisTemplate.expire(stockKey, STOCK_TTL);

        // 其余字段每次覆盖写：它们不会被扣减，数据库的值永远是对的
        String metaKey = RedisConstant.SECKILL_META + activity.getId();
        ZoneId zone = ZoneId.systemDefault();
        Map<String, String> meta = new HashMap<>(8);
        meta.put(SeckillMetaField.START_TIME, String.valueOf(activity.getStartTime().atZone(zone).toEpochSecond()));
        meta.put(SeckillMetaField.END_TIME, String.valueOf(activity.getEndTime().atZone(zone).toEpochSecond()));
        meta.put(SeckillMetaField.LIMIT_PER_USER, String.valueOf(activity.getLimitPerUser()));
        // 商品 id 和秒杀价也写进来：抢购成功时要拿它们组装 MQ 消息，
        // 放 Redis 里就不用在秒杀路径上查库了
        meta.put(SeckillMetaField.PRODUCT_ID, String.valueOf(activity.getProductId()));
        meta.put(SeckillMetaField.SECKILL_PRICE, activity.getSeckillPrice().toPlainString());
        stringRedisTemplate.opsForHash().putAll(metaKey, meta);
        stringRedisTemplate.expire(metaKey, java.time.Duration.ofSeconds(META_TTL_SECONDS));

        return Boolean.TRUE.equals(created);
    }
}
