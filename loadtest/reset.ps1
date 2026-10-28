# 每组压测前重置环境
#
# 关键决策：直接写 Redis，不重启应用。
#   预热只在应用启动时跑，但每次重启会让 JVM 失去预热状态（JIT、连接池），
#   前几秒 QPS 明显偏低，会污染测量。
#   代价是绕过了预热逻辑本身 —— 预热已单独验证过（setIfAbsent 不覆盖已扣减的值）。
#
# 不删历史订单：用 baseline 里记录的 maxOrderId 做分界，verify.ps1 只数新增的。

param(
    [long]$ActId = 1,
    [int]$Stock = 100,
    [int]$LimitPerUser = 1,
    [int]$ProductId = 1,
    [string]$SeckillPrice = "499.00",
    [int]$DurationMinutes = 1440
)

$ErrorActionPreference = 'Stop'
$MysqlContainer = 'audio-mall-mysql'
$MysqlArgs = @('-uroot', '-proot123456', 'audio_mall')
$RedisContainer = 'audio-mall-redis'

Write-Host "=== 重置活动 $ActId：库存 $Stock，限购 $LimitPerUser ==="

# ① 数据库：刷新时间窗和配置
$sql = @"
UPDATE seckill_activity
   SET start_time = NOW() - INTERVAL 1 HOUR,
       end_time   = NOW() + INTERVAL $DurationMinutes MINUTE,
       status     = 1,
       stock      = $Stock,
       limit_per_user = $LimitPerUser,
       product_id = $ProductId,
       seckill_price = $SeckillPrice
 WHERE id = $ActId;
SELECT id, product_id, stock, limit_per_user, status, start_time, end_time
  FROM seckill_activity WHERE id = $ActId;
"@
docker exec $MysqlContainer mysql @MysqlArgs -e $sql 2>$null

# ② Redis：库存、meta、已购、令牌桶全部重写
$startEpoch = [DateTimeOffset]::UtcNow.ToUnixTimeSeconds() - 3600
$endEpoch = $startEpoch + 3600 + ($DurationMinutes * 60)

docker exec $RedisContainer redis-cli -a redis123456 --no-auth-warning SET "audio:seckill:stock:$ActId" $Stock 2>$null | Out-Null
docker exec $RedisContainer redis-cli -a redis123456 --no-auth-warning DEL "audio:seckill:bought:$ActId" 2>$null | Out-Null
docker exec $RedisContainer redis-cli -a redis123456 --no-auth-warning DEL "audio:seckill:rate:$ActId" 2>$null | Out-Null
docker exec $RedisContainer redis-cli -a redis123456 --no-auth-warning HSET "audio:seckill:meta:$ActId" `
    startTime $startEpoch endTime $endEpoch limitPerUser $LimitPerUser `
    productId $ProductId seckillPrice $SeckillPrice 2>$null | Out-Null

# ③ 记录基线，供 verify.ps1 判断"本次新增了多少订单"
$maxOrderId = (docker exec $MysqlContainer mysql @MysqlArgs -N -e "SELECT IFNULL(MAX(id),0) FROM orders;" 2>$null | Out-String).Trim()
$baseline = @{
    actId       = $ActId
    stock       = $Stock
    maxOrderId  = [long]$maxOrderId
    resetAt     = (Get-Date -Format 'yyyy-MM-dd HH:mm:ss')
}
$baseline | ConvertTo-Json | Set-Content -Path (Join-Path $PSScriptRoot 'last-baseline.json') -Encoding UTF8

Write-Host "Redis 库存 = $(docker exec $RedisContainer redis-cli -a redis123456 --no-auth-warning GET "audio:seckill:stock:$ActId" 2>$null)"
Write-Host "订单基线 maxOrderId = $maxOrderId"
Write-Host "重置完成" -ForegroundColor Green
