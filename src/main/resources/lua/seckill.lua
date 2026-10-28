-- 秒杀核心脚本（合并版）：令牌桶 → 时间窗 → 库存 → 限购 → 扣减
--
-- 为什么全部塞进一个脚本：
--   这五步原来分散在「限流脚本」「扣减脚本」「HGETALL 读活动信息」三处，
--   每处都是一个完整的网络往返。脚本在 Redis 内部执行，执行期间不会有别的命令插进来，
--   所以合并既省掉往返，又不损失原子性。
--
-- 为什么限流出错不再放行（v1 的 fail-open 已被推翻）：
--   限流和库存在同一个 Redis 里。Redis 整体不可用时，限流放行之后库存那段照样失败，
--   用户一样买不到 —— 放行换不来可用性，只是把故障藏进日志里（v1 的 catch 只 log.error）。
--   唯一能让放行有意义的情况是「限流 key 单独坏了而库存/meta 正常」，那是运维事故，
--   不值得为它设计。现在选择：出错就拒绝，等价于「这次没抢到令牌」。
--
--   因此这里刻意不用 pcall。一个脚本是整体，出错即中断，请求失败 —— 这正是想要的行为。
--
-- KEYS[1] = audio:seckill:rate:{actId}    令牌桶（Hash: tokens / lastRefill）
-- KEYS[2] = audio:seckill:stock:{actId}   库存（String）
-- KEYS[3] = audio:seckill:bought:{actId}  已购（Hash: userId -> 件数）
-- KEYS[4] = audio:seckill:meta:{actId}    活动配置（Hash）
-- ARGV[1] = userId
-- ARGV[2] = 当前 epoch 秒
-- ARGV[3] = 桶容量 capacity（瞬时突发上限）
-- ARGV[4] = 每秒补令牌数 rate（长期速率）
-- ARGV[5] = 当前 epoch 毫秒
--
-- 返回：{'0', productId, seckillPrice} 成功
--       {'code'}                      失败
--   code：0 成功 / 1 售罄 / 2 超限购 / 3 未预热 / 4 未开始 / 5 已结束 / 6 被限流
--
-- 返回值全部是字符串（数字也 tostring 过），Java 侧统一按 List<String> 读，
-- 避免「整数回复 + 批量字符串」混在一个数组里，反序列化行为依赖驱动实现。

local userId = ARGV[1]
local now = tonumber(ARGV[2])
local capacity = tonumber(ARGV[3])
local rate = tonumber(ARGV[4])
local nowMs = tonumber(ARGV[5])

-- ① 令牌桶：先挡流量，挡住的请求不碰后面任何逻辑
--    放在最前面是为了保持和 v1 一致的语义（v1 也是限流先跑），
--    这样压测的拒绝分类可以直接对比，不用重新解释数字。
local bucket = redis.call('HMGET', KEYS[1], 'tokens', 'lastRefill')
local tokens = tonumber(bucket[1])
local lastRefill = tonumber(bucket[2])

-- 首次进来：装满桶 —— 开抢那一瞬间允许 capacity 个请求一起竞争
if tokens == nil then
    tokens = capacity
    lastRefill = nowMs
end

local elapsed = nowMs - lastRefill
if elapsed > 0 then
    local add = math.floor(elapsed * rate / 1000)
    if add > 0 then
        tokens = math.min(capacity, tokens + add)
        -- lastRefill 只前进「被消耗掉的那部分时间」，不足一个令牌的零头留给下次，
        -- 否则每次补不满一个令牌的零头都会被丢掉，长期速率会偏慢
        lastRefill = lastRefill + math.floor(add * 1000 / rate)
    end
end

if tokens < 1 then
    redis.call('HSET', KEYS[1], 'tokens', tokens, 'lastRefill', lastRefill)
    redis.call('PEXPIRE', KEYS[1], 60000)
    return {'6'}
end

redis.call('HSET', KEYS[1], 'tokens', tokens - 1, 'lastRefill', lastRefill)
redis.call('PEXPIRE', KEYS[1], 60000)

-- ② 活动配置：一次 HMGET 读 5 个字段。
--    v1 是 HMGET 读 2 个 + 单独 HGET 读 limitPerUser，再在 Java 里 HGETALL 一次读商品信息。
--    现在一次读完，顺便把 productId / seckillPrice 带回去给 Java 组装 MQ 消息，
--    省掉那次 HGETALL 往返。
local meta = redis.call('HMGET', KEYS[4],
        'startTime', 'endTime', 'limitPerUser', 'productId', 'seckillPrice')

local startTime = tonumber(meta[1])
local endTime = tonumber(meta[2])
if startTime == nil or endTime == nil then
    return {'3'}
end
if now < startTime then
    return {'4'}
end
if now > endTime then
    return {'5'}
end

-- ③ 库存：只有一次 GET，最便宜，先挡掉绝大多数售罄请求
local stock = tonumber(redis.call('GET', KEYS[2]))
if stock == nil then
    return {'3'}
end
if stock <= 0 then
    return {'1'}
end

-- ④ 限购：限购数从 meta 读，读不到按 1 处理（一人一单）
--    tonumber(redis.call(...)) 读不到字段时返回 false，tonumber(false) 是 nil，
--    必须用 or 兜底，否则 nil 参与比较会直接报错，整个接口挂掉
local limit = tonumber(meta[3]) or 1
local bought = tonumber(redis.call('HGET', KEYS[3], userId)) or 0
if bought + 1 > limit then
    return {'2'}
end

-- ⑤ 商品信息缺失时绝不能扣减，否则会生成一张查不到商品的订单。
--    HMGET 读不到的字段在 Lua 里是 false，而把 false 放进返回 table 会被当成 nil、
--    导致数组从那里截断（{'0', false, '199'} 会变成 {'0'}），所以这里必须显式判 false。
if meta[4] == false or meta[5] == false then
    return {'3'}
end

-- ⑥ 扣减 + 记账，与上面所有判断在同一次执行里，不可分割
redis.call('INCRBY', KEYS[2], -1)
redis.call('HINCRBY', KEYS[3], userId, 1)

-- ⑦ 已购记录必须设 TTL，否则活动越开越多、这个 Hash 永远不释放
redis.call('EXPIRE', KEYS[3], 3 * 24 * 3600)

return {'0', meta[4], meta[5]}
