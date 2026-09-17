-- =============================================================
-- 秒饭 miao-fan-takeout : 限时特价抢购模块建表脚本
-- 使用方式: mysql -uroot -p < db/seckill.sql
-- 说明: 脚本可重复执行(幂等), 重复执行不会报错也不会重复插入示例数据
-- =============================================================
USE miaofan_takeout;

-- -------------------------------------------------------------
-- 1. 抢购活动表
--    total_stock 与 sold_stock 是数据库侧的真实库存与已售数量,
--    Redis 侧的库存是"预扣"库存, 两者通过定时对账任务保持最终一致
-- -------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `seckill_activity` (
    `id`             bigint        NOT NULL AUTO_INCREMENT COMMENT '主键',
    `name`           varchar(64)   NOT NULL COMMENT '活动名称',
    `dish_id`        bigint        NOT NULL COMMENT '关联菜品id',
    `seckill_price`  decimal(10,2) NOT NULL COMMENT '抢购价',
    `total_stock`    int           NOT NULL COMMENT '活动总库存',
    `sold_stock`     int           NOT NULL DEFAULT 0 COMMENT '已售数量',
    `per_user_limit` int           NOT NULL DEFAULT 1 COMMENT '每人限购数量',
    `start_time`     datetime      NOT NULL COMMENT '开始时间',
    `end_time`       datetime      NOT NULL COMMENT '结束时间',
    `status`         int           NOT NULL DEFAULT 0 COMMENT '0未上架 1已上架 2已结束',
    `create_time`    datetime      DEFAULT NULL,
    `update_time`    datetime      DEFAULT NULL,
    `create_user`    bigint        DEFAULT NULL,
    `update_user`    bigint        DEFAULT NULL,
    PRIMARY KEY (`id`),
    KEY `idx_dish_id` (`dish_id`),
    KEY `idx_time_status` (`start_time`, `end_time`, `status`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='限时抢购活动';

-- -------------------------------------------------------------
-- 2. 抢购流水表
--    uk_activity_user 唯一索引是"一人一单"的最后一道防线,
--    即使 Redis 的预校验被绕过, 重复请求也会在写库时失败
-- -------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `seckill_record` (
    `id`          bigint   NOT NULL AUTO_INCREMENT COMMENT '主键',
    `activity_id` bigint   NOT NULL COMMENT '活动id',
    `user_id`     bigint   NOT NULL COMMENT '用户id',
    `order_id`    bigint   DEFAULT NULL COMMENT '生成的订单id',
    `status`      int      NOT NULL DEFAULT 0 COMMENT '0排队中 1抢购成功 2抢购失败',
    `create_time` datetime DEFAULT NULL,
    `update_time` datetime DEFAULT NULL,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_activity_user` (`activity_id`, `user_id`),
    KEY `idx_order_id` (`order_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='限时抢购流水';

-- -------------------------------------------------------------
-- 3. 订单表增加抢购活动字段
--    MySQL 的 ALTER TABLE 不支持 IF NOT EXISTS, 这里用 information_schema
--    动态拼 SQL 做幂等处理, 重复执行只会打印提示
-- -------------------------------------------------------------
SET @col_exists = (SELECT COUNT(*)
                   FROM information_schema.columns
                   WHERE table_schema = DATABASE()
                     AND table_name = 'orders'
                     AND column_name = 'seckill_activity_id');
SET @sql = IF(@col_exists = 0,
              'ALTER TABLE `orders` ADD COLUMN `seckill_activity_id` bigint DEFAULT NULL COMMENT ''限时抢购活动id, 普通订单为NULL''',
              'SELECT ''orders.seckill_activity_id 已存在, 跳过'' AS notice');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- -------------------------------------------------------------
-- 4. 示例活动数据
--    时间使用 NOW() 计算, 保证任何时间导入都处于"可抢购"状态
--    46·王老吉 47·北冰洋 51·老坛酸菜鱼 52·经典酸菜鮰鱼 (来自基础数据)
-- -------------------------------------------------------------
INSERT INTO `seckill_activity` (`name`, `dish_id`, `seckill_price`, `total_stock`, `sold_stock`,
                                `per_user_limit`, `start_time`, `end_time`, `status`,
                                `create_time`, `update_time`, `create_user`, `update_user`)
SELECT '老坛酸菜鱼 限时抢', 51, 29.90, 100, 0, 1,
       DATE_SUB(NOW(), INTERVAL 1 DAY), DATE_ADD(NOW(), INTERVAL 7 DAY), 1,
       NOW(), NOW(), 1, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM `seckill_activity` WHERE `name` = '老坛酸菜鱼 限时抢');

INSERT INTO `seckill_activity` (`name`, `dish_id`, `seckill_price`, `total_stock`, `sold_stock`,
                                `per_user_limit`, `start_time`, `end_time`, `status`,
                                `create_time`, `update_time`, `create_user`, `update_user`)
SELECT '经典酸菜鮰鱼 特惠场', 52, 33.90, 50, 0, 1,
       DATE_SUB(NOW(), INTERVAL 1 DAY), DATE_ADD(NOW(), INTERVAL 7 DAY), 1,
       NOW(), NOW(), 1, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM `seckill_activity` WHERE `name` = '经典酸菜鮰鱼 特惠场');

INSERT INTO `seckill_activity` (`name`, `dish_id`, `seckill_price`, `total_stock`, `sold_stock`,
                                `per_user_limit`, `start_time`, `end_time`, `status`,
                                `create_time`, `update_time`, `create_user`, `update_user`)
SELECT '北冰洋 冰爽一夏', 47, 0.99, 200, 0, 2,
       DATE_SUB(NOW(), INTERVAL 1 DAY), DATE_ADD(NOW(), INTERVAL 7 DAY), 0,
       NOW(), NOW(), 1, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM `seckill_activity` WHERE `name` = '北冰洋 冰爽一夏');
