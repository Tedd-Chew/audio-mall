-- 音响商城 —— 第二批建表（订单域 + 活动域）
-- 索引设计说明：商品只有 500 条，全表扫比走索引还快，所以商品域基本不加索引；
-- 订单日增 5 万，才是索引的主战场。

SET NAMES utf8mb4;

-- ---------------------------------------------------------------
-- 订单主表
-- 商品快照在 order_item 里，这张表只管"订单整体"
-- ---------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `orders` (
    `id`           BIGINT         NOT NULL AUTO_INCREMENT,
    `order_no`     VARCHAR(32)    NOT NULL                COMMENT '订单号，对外标识',
    `user_id`      BIGINT         NOT NULL                COMMENT '买家 id',
    `total_amount` DECIMAL(10, 2) NOT NULL                COMMENT '订单总额',
    `sale_type`    TINYINT        NOT NULL DEFAULT 1      COMMENT '1 普通 / 2 秒杀 / 3 预售',
    `status`       TINYINT        NOT NULL DEFAULT 0      COMMENT '0 待支付 / 1 已支付 / 2 已关闭 / 3 待付尾款',
    `expire_time`  DATETIME                DEFAULT NULL   COMMENT '支付截止时间，超时扫描的判据',
    `pay_time`     DATETIME                DEFAULT NULL   COMMENT '支付完成时间',
    `close_time`   DATETIME                DEFAULT NULL   COMMENT '关闭时间',
    `create_time`  DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `update_time`  DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_order_no` (`order_no`),
    -- "我的订单"：WHERE user_id=? ORDER BY create_time DESC
    KEY `idx_user_time` (`user_id`, `create_time`),
    -- 超时扫描：WHERE status=0 AND expire_time<NOW()
    KEY `idx_status_expire` (`status`, `expire_time`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '订单主表';

-- ---------------------------------------------------------------
-- 订单明细（商品快照）
-- 当前一次只买一件，永远一行；留着是为了以后加购物车
-- ---------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `order_item` (
    `id`           BIGINT         NOT NULL AUTO_INCREMENT,
    `order_id`     BIGINT         NOT NULL                COMMENT '订单 id',
    `product_id`   BIGINT         NOT NULL                COMMENT '商品 id',
    `product_name` VARCHAR(100)   NOT NULL                COMMENT '快照：下单时的商品名',
    `product_img`  VARCHAR(255)            DEFAULT NULL   COMMENT '快照：下单时的封面图',
    `unit_price`   DECIMAL(10, 2) NOT NULL                COMMENT '快照：下单时的单价',
    `quantity`     INT            NOT NULL DEFAULT 1      COMMENT '数量',
    `create_time`  DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_order` (`order_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '订单明细（商品快照）';

-- ---------------------------------------------------------------
-- 支付单
-- 一次支付行为一行：普通/秒杀一张（全额），预售两张（定金+尾款）
-- 回调幂等按 pay_no 去重 —— 挂在订单上分不清是定金还是尾款
-- ---------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `payment` (
    `id`          BIGINT         NOT NULL AUTO_INCREMENT,
    `pay_no`      VARCHAR(32)    NOT NULL                COMMENT '支付单号，回调幂等的依据',
    `order_id`    BIGINT         NOT NULL                COMMENT '订单 id',
    `user_id`     BIGINT         NOT NULL                COMMENT '买家 id',
    `pay_type`    TINYINT        NOT NULL DEFAULT 3      COMMENT '1 定金 / 2 尾款 / 3 全额',
    `amount`      DECIMAL(10, 2) NOT NULL                COMMENT '本次支付金额',
    `status`      TINYINT        NOT NULL DEFAULT 0      COMMENT '0 待支付 / 1 已支付 / 2 失败 / 3 已关闭',
    `pay_time`    DATETIME                DEFAULT NULL   COMMENT '支付成功时间',
    `create_time` DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `update_time` DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_pay_no` (`pay_no`),
    KEY `idx_order` (`order_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '支付单';

-- ---------------------------------------------------------------
-- 秒杀活动
-- 库存预热到 Redis，扣减走 Lua；这张表的 stock 是兜底的那份
-- ---------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `seckill_activity` (
    `id`             BIGINT         NOT NULL AUTO_INCREMENT,
    `product_id`     BIGINT         NOT NULL                COMMENT '商品 id',
    `seckill_price`  DECIMAL(10, 2) NOT NULL                COMMENT '秒杀价',
    `stock`          INT            NOT NULL                COMMENT '秒杀库存',
    `limit_per_user` INT            NOT NULL DEFAULT 1      COMMENT '每人限购件数',
    `start_time`     DATETIME       NOT NULL                COMMENT '开始时间',
    `end_time`       DATETIME       NOT NULL                COMMENT '结束时间',
    `status`         TINYINT        NOT NULL DEFAULT 0      COMMENT '0 未开始 / 1 进行中 / 2 已结束',
    `create_time`    DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `update_time`    DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_product` (`product_id`),
    KEY `idx_status_time` (`status`, `start_time`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '秒杀活动';

-- ---------------------------------------------------------------
-- 预售活动
-- 两阶段支付：定金 -> 尾款
-- ---------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `presale_activity` (
    `id`             BIGINT         NOT NULL AUTO_INCREMENT,
    `product_id`     BIGINT         NOT NULL                COMMENT '商品 id',
    `deposit_amount` DECIMAL(10, 2) NOT NULL                COMMENT '定金',
    `final_amount`   DECIMAL(10, 2) NOT NULL                COMMENT '尾款',
    `stock`          INT            NOT NULL                COMMENT '预售名额',
    `deposit_start`  DATETIME       NOT NULL                COMMENT '定金开始时间',
    `deposit_end`    DATETIME       NOT NULL                COMMENT '定金截止时间',
    `final_start`    DATETIME       NOT NULL                COMMENT '尾款开始时间',
    `final_end`      DATETIME       NOT NULL                COMMENT '尾款截止时间',
    `status`         TINYINT        NOT NULL DEFAULT 0      COMMENT '0 未开始 / 1 定金中 / 2 尾款中 / 3 已结束',
    `create_time`    DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `update_time`    DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_product` (`product_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '预售活动';
