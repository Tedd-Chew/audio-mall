-- 发送 MQ 失败时的补偿：把刚才占的名额还回去
--
-- 场景：Lua 已经扣了库存、记了 bought，但消息没发出去。
-- 这时用户还没收到"抢到"的承诺，所以可以反悔。
--
-- KEYS[1] = audio:seckill:stock:{actId}
-- KEYS[2] = audio:seckill:bought:{actId}
-- ARGV[1] = userId
--
-- 为什么先判断 bought > 0：
--   已有的买过的用户（比如 limit=3 已经买了 3 件），补偿一次变 2 件。
--   如果补偿被调用两次，bought 还是 > 0，会变成 1 件并多加一次库存。
--   所以严格来说这个脚本不是幂等的 —— 调用方保证只调一次，
--   补偿本身失败就接受"少卖一件"，绝不重试（重试可能导致库存变多 → 超卖）。

local bought = tonumber(redis.call('HGET', KEYS[2], ARGV[1])) or 0
if bought > 0 then
    redis.call('HINCRBY', KEYS[2], ARGV[1], -1)
    redis.call('INCRBY', KEYS[1], 1)
end
return 1
