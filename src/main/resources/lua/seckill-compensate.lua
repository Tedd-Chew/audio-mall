-- 撤销一次已经完成但注定建不成单的扣减
--
-- 两个调用方：
--   ① 生产者：Lua 已经扣了库存、占了幂等门，但 MQ 没发出去
--   ② 消费者：消息投出去了，但 insert 撞 uk_user_request —— 这个 requestId
--      已经有一张单了（旧单），这次扣减永远建不出订单
-- 两种情况要做的事完全一样：还库存、bought -1、删掉幂等门。
--
-- KEYS[1] = audio:seckill:stock:{actId}
-- KEYS[2] = audio:seckill:bought:{actId}
-- KEYS[3] = audio:seckill:idem:{actId}:{userId}:{requestId}
-- ARGV[1] = userId
-- ARGV[2] = '1' 表示这次抢购启用了幂等门，'0' 表示客户端没传 requestId
-- ARGV[3] = orderNo（本次要撤销的那个单号）
--
-- 返回：1 = 这次真的撤销了；0 = 没有撤销（已经撤过 / 门不属于这次 / 没买过）
--
-- ── 幂等守卫（这是本次修改的核心）────────────────────────────────
--
-- 老版本只看 bought > 0，那不是幂等，只是「这个人买过东西」。反例：
--   limit=3，用户已买 3 件，第 3 件要撤销。
--     撤销一次：bought 3→2，stock +1        ← 正确
--     撤销两次：bought 2→1，stock 再 +1     ← 名额凭空多出来一个 = 超卖
-- 现在补偿会被重复调用（消息重投、消费者重试），所以必须能挡住第二次。
--
-- 判据用幂等门里存的单号：
--   门里存的 == 本次的单号  → 这次扣减还没被撤销过，可以撤销，撤完删门
--   门不存在 / 门里是别的单号 → 已经撤销过，或者门已经过期/属于别的尝试 → 不动
--
-- DEL 本身就是幂等的，所以「删门」既是收尾也是标记，不需要额外加一个 key。
--
-- 为什么不比 bought、不改用别的标记：
--   门的单号精确对应「这一次尝试」，bought 是用户的累计值，粒度对不上。
--
-- 门过期怎么办（TTL 1 天）：撤不了，接受少卖一件，绝不冒险多还。
-- 方向永远是「宁可少卖，不可超卖」。
--
-- 客户端没传 requestId 时（ARGV[2]='0'）没有门可用，只能退回老逻辑。
-- 这条路径只在生产者同步调用，一次请求最多调一次，不会重复。
-- 消费者侧不存在这种情况：uk_user_request 要求 request_id 非 NULL。

local idemEnabled = (ARGV[2] == '1')

if idemEnabled then
    -- 门里的值不是本次单号 → 说明这次扣减已经被撤销（或被别的尝试顶替），直接退出
    if redis.call('GET', KEYS[3]) ~= ARGV[3] then
        return 0
    end
end

local bought = tonumber(redis.call('HGET', KEYS[2], ARGV[1])) or 0
if bought <= 0 then
    -- 没有已购记录，说明这次扣减本来就不存在，不能凭空加库存
    if idemEnabled then
        redis.call('DEL', KEYS[3])
    end
    return 0
end

redis.call('HINCRBY', KEYS[2], ARGV[1], -1)
redis.call('INCRBY', KEYS[1], 1)

-- 只有真的还了名额才删门：否则会出现「门拆了、名额没还」，
-- 用户重试能过门，但库存是错的
if idemEnabled then
    redis.call('DEL', KEYS[3])
end

return 1
