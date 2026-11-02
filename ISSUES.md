# 待办

## 压测环境（已处理）

- WSL2 虚拟磁盘的 fsync 比生产 SSD 慢一个数量级，导致单请求耗时虚高（B 实验 116ms）。开发环境把 `innodb_flush_log_at_trx_commit` 改为 2、`sync_binlog` 改为 0，模拟生产磁盘的开销。**生产必须保持 1 / 1**。

## 订单域

- ~~**下单接口没有幂等**~~ —— **已解决**。`orders.request_id` + `UNIQUE (user_id, request_id)` 当判据；普通下单在事务外捕获 `DuplicateKeyException` 返回原单；秒杀在 `seckill.lua` 扣减之前加一道 `SET idem:... NX EX 86400`，重复请求不发 MQ、不扣库存。
  - 幂等门 TTL 是 **1 天**（原 600 秒）：它同时当补偿脚本的幂等守卫，太短会让正常重试走进补偿路径
  - `requestId` 暂时不是必填（方便测试），不传等于没有幂等
  - `limitPerUser = 1` 时重复请求会先被限购挡住，提示语是"已达到限购数量"
  - 设计细节见 `temp/项目流程与迭代规划.md` 的「下单幂等（已定）」

## 消息消费（已处理）

- ~~消费失败静默丢弃~~ —— **已解决**。`acknowledge-mode` 改成 `manual`，`SeckillOrderListener` 显式 ack/nack：
  - 撞 `uk_order_no`（同一条消息重投）→ 直接 ack，订单本来就建好了
  - 其他异常 → 线程内退避重试 4 次（1/2/4/8 秒），消息一直挂着未确认
  - 仍失败 → `basicNack(tag, false, false)` 转入死信队列 `audio.seckill.order.dlq`，消息留着可重放
  - 设计细节见 `temp/项目流程与迭代规划.md` 的「手动 ack 与失败重试（已定）」

- ~~**撞 `uk_user_request` 被当成"重复消息"丢掉，库存永久少卖**~~ —— **已解决**（2026-11-02）。老代码 `catch (DuplicateKeyException)` 一律 ack，分不清撞的是哪个唯一索引。现在消费端按 `order_no` 查一次：
  - 查得到 → 撞的是 `uk_order_no`，重复投递，ack
  - 查不到 → 撞的是 `uk_user_request`，本次扣减永远建不出单，调用 `seckill-compensate.lua` 还库存 + bought −1 + 删幂等门
  - 补偿脚本改成真幂等：守卫是「门里存的单号 == 本次消息的单号」，撤完删门，第二次调用返回 0
  - 撤销失败（Redis 不可用 / 门已过期）→ 抛异常让消息重试；重试用尽进死信，不会静默丢

## 消息消费（待办）

- **死信队列没有监控和自动重放**：目前只能人工从管理 API 取消息重发。生产需要有告警（DLQ 深度 > 0 就报）和定时重放任务，否则消息躺在那里没人知道。
- **用户侧没有「失败」状态**：消息进了死信，用户在页面上看到的始终是「排队中」，订单永远不出现，也没有接口告诉他失败了。
- **幂等门过期后补偿会失效**：门 TTL 1 天，1 天后的重复请求（撞 `uk_user_request`）撤不了扣减，只能少卖。极端情况，靠对账兜。

## 依赖故障（2026-11-02 E2 实测发现，见 `docs/可靠性实验.md`）

- **Redis 不可用时每个请求卡 60 秒，还报成 401**：`JwtInterceptor` 的 `get(user:token:{id})` 走 Lettuce 默认 60 秒命令超时，异常被泛 `catch` 包成 `BusinessException(401, "Token解析失败")`。用户看到"登录失效"，会去重新登录（而此时登录也失败）；200 个并发请求就能占满 Tomcat 线程池。
  - 修法（改配置即可，但要先定超时值）：`spring.data.redis.timeout: 1s`，并把 `RedisConnectionFailureException` 与鉴权失败分开处理，返回 503 而不是 401。
- **MySQL 不可用时一条消息占消费者线程 115 秒**：`hikari.connection-timeout: 20000` 乘以 5 次尝试，加上 1/2/4/8 秒退避。库存 100 的场景下这不会积压，但堆积 100 条消息就要 115×100/并发数。
  - 修法：`connection-timeout` 降到 2 秒；或者消费端先用 `SELECT 1` 探活，不通就直接 nack 进死信，不在线程里耗。
- **MySQL 恢复后不自动补单**：消息在死信里躺着，要靠人手动重放。和上面「死信没有监控和自动重放」是同一条。

## 商品域（Sprint 2 处理）

- `ProductServiceImpl` 需要拆分：`getById` 一个方法 200 行，缓存、分布式锁、业务逻辑全混在一起；手写的锁用 `setIfAbsent` + 无校验 `delete`，有误删他人锁的风险；递归重试 `return getById(id)` 可能栈溢出；`price` 用 `Double` 存钱，要改 `BigDecimal`
