-- audio-mall 生产环境种子数据
-- 由 docker-entrypoint-initdb.d 在首次初始化时执行（文件名排在建表脚本之后）
-- 只用于演示环境：真实业务里商品由管理后台录入

SET NAMES utf8mb4;

INSERT INTO `category` (`name`, `sort`) VALUES
  ('Portable_Speaker', 1),
  ('HIFI_Speaker',     2),
  ('Monitor_Speaker',  3),
  ('Party_Speaker',    4),
  ('Retro_Speaker',    5);

INSERT INTO `product` (`name`, `category_id`, `price`, `stock`, `description`, `status`) VALUES
  ('GO 便携蓝牙音箱',        1,  499.00, 1000, 'Bluetooth 6，支持 Auracast，IPX7 防水', 1),
  ('S10 MICRO-M',            1,  299.00, 1000, 'Bluetooth 6.0，2600mAh，IPX7 防水',     1),
  ('OS3M',                   1,  599.00, 1000, '30W RMS，支持 Auracast 无线组网',        1),
  ('SC02A 无线充电音箱',     1,  259.00, 1000, '蓝牙 5.0 + 15W Qi 无线充电',            1),
  ('Eclipse 5 HIFI 音箱',    2, 3999.00,  200, '35W+15W×2 RMS，5.25 寸低音',            1),
  ('Eclipse 6 HIFI 音箱',    2, 5299.00,  120, '55W+35W×2 RMS，6.25 寸低音',            1),
  ('M8 监听音箱',            3, 2699.00,  150, '8 寸玻璃芳纶盆，120W 双功放',           1),
  ('M6 监听音箱',            3, 1999.00,  180, '6.5 寸低音，90W 双功放',                1),
  ('OS5 派对音箱',           4, 1599.00,  300, '100W 输出，支持无线麦克风与吉他输入',   1),
  ('PMS-12 有源低音炮',      4, 2499.00,  100, '200W，12 寸低音单元，30-150Hz',         1),
  ('MR30 复古蓝牙音箱',      5,  899.00,  400, '木纹箱体 + 皮革面板，蓝牙 5.2',         1),
  ('MR65 复古蓝牙音箱',      5, 1399.00,  260, '80W 输出，6.5 寸低音，支持 HDMI ARC',   1);

-- 一个进行中的秒杀活动（演示用，30 天窗口）
-- 和"创建活动"业务流程一致：库存先从商品里划出来，再挂到活动上
UPDATE `product` SET `stock` = `stock` - 100 WHERE `id` = 1;

INSERT INTO `seckill_activity`
  (`product_id`, `seckill_price`, `stock`, `limit_per_user`, `start_time`, `end_time`, `status`)
VALUES
  (1, 99.00, 100, 1, NOW() - INTERVAL 1 MINUTE, NOW() + INTERVAL 30 DAY, 1);
