# 待办

## 压测环境（已处理）

- WSL2 虚拟磁盘的 fsync 比生产 SSD 慢一个数量级，导致单请求耗时虚高（B 实验 116ms）。开发环境把 `innodb_flush_log_at_trx_commit` 改为 2、`sync_binlog` 改为 0，模拟生产磁盘的开销。**生产必须保持 1 / 1**。

## 商品域（Sprint 2 处理）

- `ProductServiceImpl` 需要拆分：`getById` 一个方法 200 行，缓存、分布式锁、业务逻辑全混在一起；手写的锁用 `setIfAbsent` + 无校验 `delete`，有误删他人锁的风险；递归重试 `return getById(id)` 可能栈溢出；`price` 用 `Double` 存钱，要改 `BigDecimal`
