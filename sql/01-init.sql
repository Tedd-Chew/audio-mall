-- 音响商城 —— 第一批建表（商品域 + 用户）
-- 订单、支付、活动的表等 Sprint 3 前再补

SET NAMES utf8mb4;

-- ---------------------------------------------------------------
-- 商品分类
-- ---------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `category` (
    `id`          BIGINT       NOT NULL AUTO_INCREMENT,
    `name`        VARCHAR(50)  NOT NULL                COMMENT '分类名',
    `sort`        INT          NOT NULL DEFAULT 0      COMMENT '排序，小的在前',
    `create_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `update_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_name` (`name`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '商品分类';

-- ---------------------------------------------------------------
-- 商品（扁平模型：每个配色 / 版本独立一行）
-- 不做 SPU/SKU 两层，前台也不按款式聚合
-- 想改成聚合展示时，加 parent_id 自关联即可
-- ---------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `product` (
    `id`          BIGINT         NOT NULL AUTO_INCREMENT,
    `name`        VARCHAR(100)   NOT NULL                COMMENT '商品名，如 JBL Charge 5 黑色',
    `category_id` BIGINT         NOT NULL                COMMENT '分类 id',
    `price`       DECIMAL(10, 2) NOT NULL                COMMENT '售价',
    `stock`       INT            NOT NULL DEFAULT 0      COMMENT '库存，秒杀扣的就是它',
    `sales`       INT            NOT NULL DEFAULT 0      COMMENT '销量，支付成功时累加',
    `view_count`  INT            NOT NULL DEFAULT 0      COMMENT '浏览量，Redis 累加后定时回写',
    `cover_img`   VARCHAR(255)            DEFAULT NULL   COMMENT '封面图地址',
    `description` TEXT                                    COMMENT '描述',
    `status`      TINYINT        NOT NULL DEFAULT 1      COMMENT '1 上架 / 0 下架',
    `create_time` DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `update_time` DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_category` (`category_id`),
    KEY `idx_sales` (`sales`),
    KEY `idx_status` (`status`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '商品';

-- ---------------------------------------------------------------
-- 买家账号
-- 原项目的 sys_user 是后台管理员，与这张表分开
-- ---------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `user` (
    `id`          BIGINT       NOT NULL AUTO_INCREMENT,
    `username`    VARCHAR(50)  NOT NULL                COMMENT '用户名',
    `password`    VARCHAR(100) NOT NULL                COMMENT 'BCrypt 加密后的密码',
    `nickname`    VARCHAR(50)           DEFAULT NULL   COMMENT '昵称',
    `phone`       VARCHAR(20)           DEFAULT NULL   COMMENT '手机号',
    `role`        VARCHAR(20)  NOT NULL DEFAULT 'BUYER' COMMENT 'BUYER 买家 / ADMIN 管理员',
    `status`      TINYINT      NOT NULL DEFAULT 1      COMMENT '1 正常 / 0 禁用',
    `create_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `update_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_username` (`username`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '买家账号';
