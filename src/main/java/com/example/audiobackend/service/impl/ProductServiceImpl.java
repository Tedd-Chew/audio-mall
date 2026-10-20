package com.example.audiobackend.service.impl;

import com.example.audiobackend.common.PageResult;
import com.example.audiobackend.constant.RedisConstant;
import com.example.audiobackend.dto.ProductDTO;
import com.example.audiobackend.dto.ProductQuery;
import com.example.audiobackend.entity.Product;
import com.example.audiobackend.entity.ProductCategory;
import com.example.audiobackend.exception.BusinessException;
import com.example.audiobackend.mapper.ProductCategoryMapper;
import com.example.audiobackend.mapper.ProductMapper;
import com.example.audiobackend.service.ProductService;
import com.example.audiobackend.vo.ProductVO;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProductServiceImpl implements ProductService {

    /** 详情缓存 12 小时 */
    private static final long CACHE_TTL_SECONDS = 12 * 3600L;

    /** TTL 随机偏移上限（30 分钟）：防止一批 key 同一时刻集中过期（缓存雪崩） */
    private static final int CACHE_TTL_JITTER_SECONDS = 1800;

    /** 空值缓存 1 分钟：挡缓存穿透，又不能留太久 —— 万一是商品刚上架 */
    private static final long NULL_CACHE_TTL_SECONDS = 60L;

    private final ProductMapper productMapper;
    private final ProductCategoryMapper productCategoryMapper;
    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;
    private final ThreadPoolExecutor threadPoolExecutor;

    // ==================== 查询 ====================

    @Override
    public ProductVO getDetail(Long id) {
        if (id == null || id <= 0) {
            throw new BusinessException("商品 id 不合法");
        }

        // 1. 空值缓存命中 —— 之前查过，数据库里确实没有
        if (Boolean.TRUE.equals(stringRedisTemplate.hasKey(RedisConstant.PRODUCT_NULL + id))) {
            throw new BusinessException("商品不存在");
        }

        // 2. 正常缓存命中
        String cached = stringRedisTemplate.opsForValue().get(RedisConstant.PRODUCT_INFO + id);
        if (cached != null) {
            asyncAddViewCount(id);
            return toVO(fromJson(cached), null);
        }

        // ================= 分布式锁（暂时注释，Sprint 8 做多实例时启用） =================
        // 原本的结构长这样，保留在此供参考：
        //
        // String lockKey = RedisConstant.PRODUCT_LOCK + id;
        // Boolean lock = stringRedisTemplate.opsForValue()
        //         .setIfAbsent(lockKey, "1", 3, TimeUnit.SECONDS);
        // if (lock == null || !lock) {
        //     Thread.sleep(50);
        //     return getDetail(id);            // 递归重试
        // }
        // try {
        //     ... 查库 + 写缓存 ...
        // } finally {
        //     stringRedisTemplate.delete(lockKey);   // 不论是谁的锁，直接删
        // }
        //
        // 两个问题：
        //   1. finally 里不校验持有者就删 —— 锁过期后别人拿到了锁，这一删把别人的删了
        //   2. 抢不到锁就递归调用自己 —— 一直抢不到会栈溢出
        //
        // 先不用它：当前量级（商品几百、QPS 几百）缓存击穿不构成问题，
        // 而一把写错的锁比没有锁更危险。要做就用 Lua「比较并删除」重写。
        // ============================================================================

        // 3. 都没命中，回源查库
        Product product = productMapper.selectById(id);
        if (product == null) {
            stringRedisTemplate.opsForValue()
                    .set(RedisConstant.PRODUCT_NULL + id, "1", NULL_CACHE_TTL_SECONDS, TimeUnit.SECONDS);
            throw new BusinessException("商品不存在");
        }

        writeCache(product);
        asyncAddViewCount(id);

        ProductCategory category = productCategoryMapper.selectById(product.getCategoryId());
        return toVO(product, category == null ? null : category.getName());
    }

    @Override
    public PageResult<ProductVO> page(ProductQuery query) {
        long total = productMapper.countByCondition(query);
        if (total == 0) {
            return new PageResult<>(0, Collections.emptyList(), query.getPageNum(), query.getLimit());
        }

        List<Product> products = productMapper.selectPage(query);
        List<ProductVO> list = products.stream().map(p -> toVO(p, null)).toList();
        return new PageResult<>(total, list, query.getPageNum(), query.getLimit());
    }

    // ==================== 增删改 ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long add(ProductDTO dto) {
        Product product = new Product();
        copyFromDTO(product, dto);
        // 销量和浏览量由服务端决定，新增时一律从 0 开始
        product.setSales(0);
        product.setViewCount(0);
        product.setStatus(dto.getStatus() == null ? 1 : dto.getStatus());

        productMapper.insert(product);
        log.info("新增商品成功，id={}，名称={}", product.getId(), product.getName());
        return product.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(Long id, ProductDTO dto) {
        if (productMapper.selectById(id) == null) {
            throw new BusinessException("商品不存在");
        }

        Product product = new Product();
        product.setId(id);
        copyFromDTO(product, dto);
        productMapper.updateById(product);

        evictCache(id);
        log.info("修改商品成功，id={}", id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateStatus(Long id, Integer status) {
        if (status == null || (status != 0 && status != 1)) {
            throw new BusinessException("状态只能是 0（下架）或 1（上架）");
        }
        if (productMapper.updateStatus(id, status) == 0) {
            throw new BusinessException("商品不存在");
        }

        evictCache(id);
        log.info("商品状态变更，id={}，status={}", id, status);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        if (productMapper.deleteById(id) == 0) {
            throw new BusinessException("商品不存在");
        }

        evictCache(id);
        log.info("删除商品成功，id={}", id);
    }

    // ==================== 私有方法 ====================

    /** 只搬 DTO 里允许前端决定的字段 */
    private void copyFromDTO(Product product, ProductDTO dto) {
        product.setName(dto.getName());
        product.setCategoryId(dto.getCategoryId());
        product.setPrice(dto.getPrice());
        product.setStock(dto.getStock());
        product.setCoverImg(dto.getCoverImg());
        product.setDescription(dto.getDescription());
        product.setStatus(dto.getStatus());
    }

    /**
     * 回源查库之后写缓存。
     * TTL 加随机偏移，避免同一批 key 一起过期。
     */
    private void writeCache(Product product) {
        long ttl = CACHE_TTL_SECONDS + ThreadLocalRandom.current().nextInt(CACHE_TTL_JITTER_SECONDS);
        try {
            String json = objectMapper.writeValueAsString(product);
            stringRedisTemplate.opsForValue()
                    .set(RedisConstant.PRODUCT_INFO + product.getId(), json, ttl, TimeUnit.SECONDS);
        } catch (JsonProcessingException e) {
            // 缓存写不进去不该让接口失败，下次请求会再试
            log.error("商品写入缓存失败，id={}", product.getId(), e);
        }
    }

    /** 改了数据就删缓存（Cache Aside），而不是去改缓存 —— 改缓存容易写出不一致 */
    private void evictCache(Long id) {
        stringRedisTemplate.delete(RedisConstant.PRODUCT_INFO + id);
        // 空值缓存也删：「商品不存在」时留下的那个会挡住新数据
        stringRedisTemplate.delete(RedisConstant.PRODUCT_NULL + id);
    }

    private Product fromJson(String json) {
        try {
            return objectMapper.readValue(json, Product.class);
        } catch (JsonProcessingException e) {
            log.error("商品缓存反序列化失败，内容：{}", json, e);
            throw new BusinessException("商品数据异常，请稍后重试");
        }
    }

    /** 浏览量异步累加：详情接口不该为了一个计数多等一次 Redis 往返 */
    private void asyncAddViewCount(Long productId) {
        try {
            threadPoolExecutor.execute(() -> {
                try {
                    stringRedisTemplate.opsForValue()
                            .increment(RedisConstant.PRODUCT_VIEW + productId);
                } catch (Exception e) {
                    log.error("浏览量累加失败，id={}", productId, e);
                }
            });
        } catch (Exception e) {
            // 线程池满了不该影响用户看商品
            log.warn("浏览量任务提交失败，id={}", productId, e);
        }
    }

    private ProductVO toVO(Product product, String categoryName) {
        ProductVO vo = new ProductVO();
        vo.setId(product.getId());
        vo.setName(product.getName());
        vo.setCategoryId(product.getCategoryId());
        vo.setCategoryName(categoryName);
        vo.setPrice(product.getPrice());
        vo.setStock(product.getStock());
        vo.setSales(product.getSales());
        vo.setCoverImg(product.getCoverImg());
        vo.setDescription(product.getDescription());
        vo.setStatus(product.getStatus());
        return vo;
    }
}
