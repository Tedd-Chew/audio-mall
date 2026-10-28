# 批量注册压测用户并把 token 落到 tokens.json
#
# 为什么要预生成：秒杀一人一单，压测必须用不同用户。k6 脚本里不方便注册，
# 而且每次压测都重新注册太慢。注册一次，之后复用。
#
# 可重复执行：用户已存在时注册会失败（被 catch 掉），登录仍能拿到 token。

param(
    [int]$Count = 1000,
    [string]$BaseUrl = "http://localhost:8080",
    [string]$OutFile = "tokens.json"
)

$ErrorActionPreference = 'Continue'
$password = 'k6pass123'

Write-Host "开始注册 $Count 个压测用户 -> $OutFile"

$tokens = [System.Collections.Generic.List[string]]::new()
$failed = 0

for ($i = 1; $i -le $Count; $i++) {
    $username = "k6u_$i"
    try {
        Invoke-RestMethod "$BaseUrl/api/user/register" -Method Post -ContentType 'application/json' `
            -Body (@{ username = $username; password = $password; nickname = "k6_$i" } | ConvertTo-Json) `
            -TimeoutSec 10 | Out-Null
    } catch {
        # 已注册过，忽略
    }

    try {
        $resp = Invoke-RestMethod "$BaseUrl/api/user/login" -Method Post -ContentType 'application/json' `
            -Body (@{ username = $username; password = $password } | ConvertTo-Json) -TimeoutSec 10
        $tokens.Add($resp.data.token)
    } catch {
        $failed++
    }

    if ($i % 100 -eq 0) {
        Write-Host "  进度 $i / $Count"
    }
}

if ($failed -gt 0) {
    Write-Host "警告：$failed 个用户登录失败" -ForegroundColor Yellow
}

# k6 的 open() 读 JSON，格式固定为 { "tokens": [...] }
@{ tokens = $tokens } | ConvertTo-Json -Depth 3 | Set-Content -Path $OutFile -Encoding UTF8

Write-Host "完成：$($tokens.Count) 个 token 写入 $OutFile" -ForegroundColor Green
