-- 秒杀活动测试数据（开发用，不进生产）
-- 注意：中文必须用 stdin 灌入，不能用 mysql -e 传参（Windows 会转码导致双重编码）

SET NAMES utf8mb4;

-- 1. 把活动库存从商品库存里扣出来（模拟"创建活动"这一步）
UPDATE product SET stock = stock - 100 WHERE id = 1 AND stock >= 100;

-- 2. 建活动：现在开始，24 小时后结束，进行中
INSERT INTO seckill_activity (product_id, seckill_price, stock, limit_per_user, start_time, end_time, status)
VALUES (1, 499.00, 100, 1, NOW() - INTERVAL 1 MINUTE, NOW() + INTERVAL 1 DAY, 1);

-- 3. 再建一个"还没开始"的，用于验证时间窗拦截
UPDATE product SET stock = stock - 50 WHERE id = 2 AND stock >= 50;
INSERT INTO seckill_activity (product_id, seckill_price, stock, limit_per_user, start_time, end_time, status)
VALUES (2, 699.00, 50, 1, NOW() + INTERVAL 1 HOUR, NOW() + INTERVAL 2 HOUR, 0);

SELECT id, product_id, seckill_price, stock, status, start_time, end_time FROM seckill_activity;
SELECT id, name, stock FROM product WHERE id IN (1, 2);
