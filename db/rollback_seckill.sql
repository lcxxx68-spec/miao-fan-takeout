-- =============================================================
-- 秒饭 miao-fan-takeout : 限时抢购模块回滚脚本
-- 用途: 撤销 db/seckill.sql 的全部改动, 恢复到改造前状态
-- =============================================================
USE miaofan_takeout;

DROP TABLE IF EXISTS `seckill_record`;
DROP TABLE IF EXISTS `seckill_activity`;

SET @col_exists = (SELECT COUNT(*)
                   FROM information_schema.columns
                   WHERE table_schema = DATABASE()
                     AND table_name = 'orders'
                     AND column_name = 'seckill_activity_id');
SET @sql = IF(@col_exists = 1,
              'ALTER TABLE `orders` DROP COLUMN `seckill_activity_id`',
              'SELECT ''orders.seckill_activity_id 不存在, 无需回滚'' AS notice');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
