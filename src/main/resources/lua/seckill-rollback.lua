-- 关单回滚：订单超时未付，把占掉的名额还回去
--
-- KEYS[1] = audio:seckill:stock:{actId}
-- KEYS[2] = audio:seckill:bought:{actId}
-- ARGV[1] = userId
--
-- 两道闸门，缺一不可：
--
-- ① EXISTS —— 关单可能发生在活动结束之后，那时库存 key 已被清理。
--    INCRBY 对不存在的 key 会「创建」它，留下一个没有对应活动的孤立库存。
--    （补偿脚本不需要这个判断，因为它只在抢购请求的同一个线程里跑，活动必然还活着。）
--
-- ② bought > 0 必须在最外层 —— 这是「我没还过」的证据。
--    如果把 INCRBY 写在判断之前，重复调用会：
--      第一次：stock +1，bought 1→0
--      第二次：stock 又 +1（bought 已是 0，跳过了减已购数）
--    结果是库存凭空变多 → 超卖。

if redis.call('EXISTS', KEYS[1]) == 1 then
    local bought = tonumber(redis.call('HGET', KEYS[2], ARGV[1])) or 0
    if bought > 0 then
        redis.call('HINCRBY', KEYS[2], ARGV[1], -1)
        redis.call('INCRBY', KEYS[1], 1)
    end
end
return 1
