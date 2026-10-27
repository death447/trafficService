-- 现场采集支持多辆事故车：逐车现场记录 + 照片归属车辆

CREATE TABLE IF NOT EXISTS `dispatch_scene_vehicle` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `dispatch_order_id` BIGINT NOT NULL,
  `assignment_id` BIGINT DEFAULT NULL COMMENT '所属赋值',
  `accident_vehicle_id` BIGINT DEFAULT NULL COMMENT '对应 dispatch_accident_vehicle.id',
  `plate_no` VARCHAR(20) DEFAULT NULL,
  `vehicle_type` VARCHAR(50) DEFAULT NULL,
  `damage_desc` VARCHAR(1000) DEFAULT NULL,
  `sort_order` INT NOT NULL DEFAULT 0,
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
  `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_order_accident_vehicle` (`dispatch_order_id`, `accident_vehicle_id`),
  KEY `idx_order_id` (`dispatch_order_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='工单现场采集-逐车';

SET @col_exists = (
  SELECT COUNT(*) FROM `information_schema`.`COLUMNS`
  WHERE `TABLE_SCHEMA` = DATABASE()
    AND `TABLE_NAME` = 'dispatch_media'
    AND `COLUMN_NAME` = 'accident_vehicle_id'
);
SET @ddl = IF(@col_exists = 0,
  'ALTER TABLE `dispatch_media` ADD COLUMN `accident_vehicle_id` BIGINT DEFAULT NULL COMMENT ''所属事故车'' AFTER `assignment_id`',
  'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @idx_exists = (
  SELECT COUNT(*) FROM `information_schema`.`STATISTICS`
  WHERE `TABLE_SCHEMA` = DATABASE()
    AND `TABLE_NAME` = 'dispatch_media'
    AND `INDEX_NAME` = 'idx_accident_vehicle_id'
);
SET @ddl = IF(@idx_exists = 0,
  'ALTER TABLE `dispatch_media` ADD KEY `idx_accident_vehicle_id` (`accident_vehicle_id`)',
  'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
