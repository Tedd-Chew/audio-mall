# 待办

## 压测环境（已处理）

- WSL2 虚拟磁盘的 fsync 比生产 SSD 慢一个数量级，导致单请求耗时虚高（B 实验 116ms）。开发环境把 `innodb_flush_log_at_trx_commit` 改为 2、`sync_binlog` 改为 0，模拟生产磁盘的开销。**生产必须保持 1 / 1**。

## 订单域

- ~~**下单接口没有幂等**~~ —— **已解决**。`orders.request_id` + `UNIQUE (user_id, request_id)` 当判据；普通下单在事务外捕获 `DuplicateKeyException` 返回原单；秒杀在 `seckill.lua` 扣减之前加一道 `SET idem:... NX EX 86400`，重复请求不发 MQ、不扣库存。
  - 幂等门 TTL 是 **1 天**（原 600 秒）：它同时当补偿脚本的幂等守卫，太短会让正常重试走进补偿路径
  - `requestId` 暂时不是必填（方便测试），不传等于没有幂等
  - ~~`limitPerUser = 1` 时重复请求会先被限购挡住，提示语是"已达到限购数量"~~ —— **已修**（2026-11-02）。`seckill.lua` 最前面加了只读的幂等速查：查到门就直接返回『重复提交 + 原单号』，不再被 ④ 的限购挡住。改之前用户重试会看到"你超过限购了"，以为没抢到，其实有单。回归测试见 `docs/集成测试.md`。
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

- ~~**Redis 不可用时每个请求卡 60 秒，还报成 401**~~ —— **已修**。`spring.data.redis.timeout: 1s` + `connect-timeout: 200ms`；`JwtInterceptor` 单独捕获 `RedisConnectionFailureException` / `QueryTimeoutException` 报 503。实测 60,087 ms / 401 → 1,043 ms / 503，Redis 恢复后第一个请求也从 24 秒降到 74 毫秒。
- ~~**MySQL 不可用时一条消息占消费者线程 115 秒**~~ —— **已修**。`hikari.connection-timeout` 20s → 5s，一条消息进死信从 115 秒降到 41 秒。**代价**：能自动扛住的停机时长同比缩短（这两个是同一个数），40 秒覆盖一次常规重启（实测启动 6.2 秒）够用，更长的停机走死信 + 重放。
- **MySQL 恢复后不自动补单**：消息在死信里躺着，要靠人手动重放。和下面「死信没有监控和自动重放」是同一条。

## 消费吞吐天花板（2026-11-02 E3 实测，见 `docs/可靠性实验.md`）

- **加消费线程没有用**：并发 1 / 2 / 4 的吞吐都是 25~29 单/秒，只有单条耗时线性变长（39 / 69 / 151 毫秒）。瓶颈是 `UPDATE seckill_activity SET stock=stock-1 WHERE id=?` —— 所有订单抢同一行的行锁，数据库那边就是一条队。
- **现在不做优化**，理由：一次放 100~1000 件，29 单/秒对应 3.4~34 秒，用户等得起。
- 真要提上去有两条路，代价都写在 `docs/可靠性实验.md` 的 E3 里：
  - 库存拆桶（`seckill_activity` 一行拆成 N 行，按 userId 取模）—— 能线性扩，但要改扣减逻辑和回滚逻辑。
  - Redis 当唯一扣减口径，DB 只落流水 —— 快，但要处理 Redis 丢数据的对账。

## 商品域（Sprint 2 处理）

- **浏览量功能已整体删除**（2026-11-03）。原来的设计是「Redis `INCR` 累加 → 定时任务回写 MySQL」，思路本身是对的（避开 `UPDATE product` 的热点行），但那个同步任务从不干活：key 前缀写成 `product:*`（实际是 `audio:product:view:*`）、`if (keys != null && !keys.isEmpty()) return;` 判断反了、把 String 当 Hash 读。而且 `ProductVO` 里根本没有 `viewCount` 字段 —— 这个数字从来没返回给前端，页面上看不到。所以连带删掉了同步任务、`PlatformThreadPoolConfig`（唯一用户就是它）、`ProductMapper.updateViewCount`（无调用方）和 `RedisConstant.PRODUCT_VIEW`。
  - **不要改成「每次详情页 UPDATE view_count = view_count + 1」**：秒杀期间大量用户涌入详情页，改的是同一行，就是 E3 里那个热点行更新，会把这个页面拖死。要恢复这个功能，还是得「Redis 累加 + 定时批量落库」，而且定时任务要写对。
- `ProductServiceImpl` 需要拆分：`getById` 一个方法 200 行，缓存、分布式锁、业务逻辑全混在一起；手写的锁用 `setIfAbsent` + 无校验 `delete`，有误删他人锁的风险；递归重试 `return getById(id)` 可能栈溢出；`price` 用 `Double` 存钱，要改 `BigDecimal`
