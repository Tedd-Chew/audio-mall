-- 压测数据
-- 用法：docker exec -i audio-mall-mysql mysql -uroot -p密码 --default-character-set=utf8mb4 audio_mall < sql/03-loadtest-data.sql

SET NAMES utf8mb4;

-- 清掉旧的压测商品（id > 3 的）
DELETE FROM product WHERE id > 3;

-- 100 款商品，每款库存 1000
-- 用 CTE 生成 1..100 的序列，避免手写 100 行 INSERT
INSERT INTO product (name, category_id, price, stock, sales, view_count, description, status)
WITH RECURSIVE seq AS (
    SELECT 1 AS n
    UNION ALL
    SELECT n + 1 FROM seq WHERE n < 100
)
SELECT CONCAT('压测商品', LPAD(n, 3, '0')), 1, 999.00, 1000, 0, 0, '压测用', 1
FROM seq;

-- 把原有的 3 个商品库存也调大，方便拿 id=1 做热点测试
UPDATE product SET stock = 10000 WHERE id <= 3;

SELECT COUNT(*) AS 商品总数, MIN(id) AS 最小id, MAX(id) AS 最大id FROM product;
