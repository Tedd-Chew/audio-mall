# 待办

## 商品域（Sprint 2 处理）

- `ProductServiceImpl` 需要拆分：`getById` 一个方法 200 行，缓存、分布式锁、业务逻辑全混在一起；手写的锁用 `setIfAbsent` + 无校验 `delete`，有误删他人锁的风险；递归重试 `return getById(id)` 可能栈溢出；`price` 用 `Double` 存钱，要改 `BigDecimal`
