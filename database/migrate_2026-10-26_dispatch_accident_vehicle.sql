-- Multi accident vehicles per dispatch order (child table)
CREATE TABLE IF NOT EXISTS `dispatch_accident_vehicle` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `dispatch_order_id` BIGINT NOT NULL,
  `sort_order` INT NOT NULL DEFAULT 0,
  `plate_no` VARCHAR(20) NOT NULL,
  `vehicle_type_id` BIGINT DEFAULT NULL,
  `vehicle_type_name` VARCHAR(50) DEFAULT NULL,
  `party_name` VARCHAR(50) DEFAULT NULL,
  `party_phone` VARCHAR(20) DEFAULT NULL,
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
  `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_order_id` (`dispatch_order_id`),
  KEY `idx_plate_no` (`plate_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='工单事故车辆';

-- Backfill: only rows with non-empty plate_no
INSERT INTO `dispatch_accident_vehicle` (
  `dispatch_order_id`, `sort_order`, `plate_no`, `vehicle_type_id`, `vehicle_type_name`,
  `party_name`, `party_phone`
)
SELECT o.`id`, 0,
  TRIM(o.`plate_no`),
  o.`vehicle_type_id`, o.`vehicle_type_name`, o.`party_name`, o.`party_phone`
FROM `dispatch_order` o
WHERE o.`plate_no` IS NOT NULL AND TRIM(o.`plate_no`) <> ''
AND NOT EXISTS (
  SELECT 1 FROM `dispatch_accident_vehicle` a WHERE a.`dispatch_order_id` = o.`id`
);
