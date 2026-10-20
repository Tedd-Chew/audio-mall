-- 开发测试数据
-- 必须用文件 + stdin 的方式灌进去：
--   docker exec -i audio-mall-mysql mysql -uroot -p密码 audio_mall < sql/02-test-data.sql
-- 不要用 mysql -e "INSERT ... '中文' ..." —— 命令行参数会被 Windows 转码，中文必乱

SET NAMES utf8mb4;

DELETE FROM product;
DELETE FROM category;

INSERT INTO category (id, name, sort) VALUES
(1, '蓝牙音箱', 1),
(2, '家庭影院', 2),
(3, '耳机', 3);

INSERT INTO product (id, name, category_id, price, stock, sales, view_count, description, status) VALUES
(1, 'JBL Charge 5 黑色', 1, 999.00, 50, 0, 0, '便携防水蓝牙音箱', 1),
(2, 'JBL Charge 5 蓝色', 1, 999.00, 30, 0, 0, '便携防水蓝牙音箱', 1),
(3, 'Bose SoundLink Flex 黑色', 1, 1299.00, 20, 0, 0, '户外便携音箱', 1);
