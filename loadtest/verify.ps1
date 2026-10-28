# 压测后的交叉验证
#
# 四组数据必须对得上，任何一组对不上都说明有请求被静默吞掉了：
#   ① k6 成功数  ==  本次新增订单数
#   ② 库存初始值 - Redis 剩余  ==  k6 成功数
#   ③ 库存初始值 - DB 活动剩余  ==  k6 成功数
#   ④ 成功 + 售罄 + 限购 + 限流 + 其他  ==  总请求数

param(
    [string]$ResultFile = "result-oversell.json"
)

$ErrorActionPreference = 'Stop'
$MysqlArgs = @('-uroot', '-proot123456', 'audio_mall')

$baselinePath = Join-Path $PSScriptRoot 'last-baseline.json'
if (-not (Test-Path $baselinePath)) {
    Write-Host "找不到 last-baseline.json，请先跑 reset.ps1" -ForegroundColor Red
    exit 1
}
$baseline = Get-Content $baselinePath -Raw -Encoding UTF8 | ConvertFrom-Json

$resultPath = Join-Path $PSScriptRoot $ResultFile
if (-not (Test-Path $resultPath)) {
    Write-Host "找不到压测结果 $ResultFile" -ForegroundColor Red
    exit 1
}
$result = Get-Content $resultPath -Raw -Encoding UTF8 | ConvertFrom-Json

# 秒杀下单是异步的：k6 结束时 100 条消息可能还在 MQ 里没消费完，
# 这时候查 DB 会看到"订单数只有 66"，误判成 bug。先等队列排空。
Write-Host "等待 MQ 消费完..." -NoNewline
$deadline = (Get-Date).AddSeconds(90)
$pending = -1
while ((Get-Date) -lt $deadline) {
    $line = docker exec audio-mall-rabbitmq rabbitmqctl list_queues name messages 2>$null |
        Select-String 'audio\.seckill\.order\.queue'
    $pending = 0
    if ($line) {
        $parts = ($line.ToString().Trim() -split "`t")
        $pending = [int]$parts[-1]
    }
    if ($pending -eq 0) { break }
    Start-Sleep -Milliseconds 800
}
Write-Host " 队列剩余 $pending 条"

$actId = $baseline.actId
$initStock = $baseline.stock
$maxOrderId = $baseline.maxOrderId

function Metric([string]$name, [string]$field) {
    $metric = $result.metrics.$name
    if ($null -eq $metric -or $null -eq $metric.values) { return 0 }
    return $metric.values.$field
}

$httpCount = [int](Metric 'http_reqs' 'count')
$success = [int](Metric 'biz_success' 'count')
$soldOut = [int](Metric 'biz_sold_out' 'count')
$limit = [int](Metric 'biz_limit_exceeded' 'count')
$rateLimited = [int](Metric 'biz_rate_limited' 'count')
$other = [int](Metric 'biz_other' 'count')

$redisStock = [int](docker exec audio-mall-redis redis-cli -a redis123456 --no-auth-warning GET "audio:seckill:stock:$actId" 2>$null)
$dbStock = [int](docker exec audio-mall-mysql mysql @MysqlArgs -N -e "SELECT stock FROM seckill_activity WHERE id=$actId;" 2>$null)
$newOrders = [int](docker exec audio-mall-mysql mysql @MysqlArgs -N -e "SELECT COUNT(*) FROM orders WHERE activity_id=$actId AND id > $maxOrderId;" 2>$null)
$boughtUsers = [int](docker exec audio-mall-redis redis-cli -a redis123456 --no-auth-warning HLEN "audio:seckill:bought:$actId" 2>$null)

$bizSum = $success + $soldOut + $limit + $rateLimited + $other

Write-Host ""
Write-Host "========== 交叉验证 ==========" -ForegroundColor Cyan
Write-Host "k6 总请求     : $httpCount"
Write-Host "  成功        : $success"
Write-Host "  售罄        : $soldOut"
Write-Host "  限购拒绝    : $limit"
Write-Host "  限流拒绝    : $rateLimited"
Write-Host "  其他        : $other"
Write-Host "--- 数据源 ---"
Write-Host "初始库存      : $initStock"
Write-Host "Redis 剩余    : $redisStock"
Write-Host "DB 活动剩余   : $dbStock"
Write-Host "新增订单数    : $newOrders"
Write-Host "已购用户数    : $boughtUsers"
Write-Host ""

$problems = @()
if ($bizSum -ne $httpCount) { $problems += "① 业务结果合计($bizSum) != 总请求数($httpCount) —— 有请求静默失败" }
if ($success -ne $newOrders) { $problems += "② k6 成功($success) != 新增订单($newOrders)" }
if (($initStock - $redisStock) -ne $success) { $problems += "③ 库存消耗($($initStock - $redisStock)) != 成功数($success)" }
if (($initStock - $dbStock) -ne $success) { $problems += "④ DB 库存消耗($($initStock - $dbStock)) != 成功数($success)" }
if ($redisStock -lt 0) { $problems += "⑤ Redis 库存为负($redisStock) —— 超卖" }
if ($dbStock -lt 0) { $problems += "⑥ DB 活动库存为负($dbStock) —— 超卖" }

if ($problems.Count -eq 0) {
    Write-Host "全部一致 ✅" -ForegroundColor Green
} else {
    Write-Host "发现问题：" -ForegroundColor Red
    $problems | ForEach-Object { Write-Host "  - $_" -ForegroundColor Red }
}
