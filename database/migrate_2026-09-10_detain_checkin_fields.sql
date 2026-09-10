-- 增量迁移：停放区域、入库扩展字段、扣车照片（2026-09-10）
-- 新环境请优先执行完整 database/init.sql，本脚本仅用于已有 vue_springboot_system 库的升级。

USE vue_springboot_system;

CREATE TABLE IF NOT EXISTS `parking_area` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `parking_lot_id` BIGINT NOT NULL COMMENT '所属停车场',
  `name` VARCHAR(100) NOT NULL COMMENT '区域名称',
  `status` VARCHAR(20) NOT NULL DEFAULT 'ENABLED' COMMENT 'ENABLED/DISABLED',
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
  `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_lot_name` (`parking_lot_id`, `name`),
  KEY `idx_parking_lot_id` (`parking_lot_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='停车场停放区域';

ALTER TABLE `detained_vehicle`
  MODIFY COLUMN `detain_no` VARCHAR(64) NOT NULL COMMENT '扣押编号（手填凭证编码），唯一';

ALTER TABLE `detained_vehicle`
  ADD COLUMN `entry_no` VARCHAR(32) DEFAULT NULL COMMENT '入场编号，系统生成' AFTER `detain_no`,
  ADD COLUMN `brand_model` VARCHAR(100) DEFAULT NULL COMMENT '厂牌型号' AFTER `vehicle_type`,
  ADD COLUMN `vehicle_color` VARCHAR(30) DEFAULT NULL COMMENT '车辆颜色' AFTER `brand_model`,
  ADD COLUMN `mileage` VARCHAR(50) DEFAULT NULL COMMENT '行驶里程' AFTER `vehicle_color`,
  ADD COLUMN `important_equipment` VARCHAR(200) DEFAULT NULL COMMENT '重要装备' AFTER `mileage`,
  ADD COLUMN `has_key` VARCHAR(8) DEFAULT NULL COMMENT 'YES/NO' AFTER `important_equipment`,
  ADD COLUMN `rescuer_name` VARCHAR(50) DEFAULT NULL COMMENT '施救人员' AFTER `detain_dept`,
  ADD COLUMN `rescue_reason` VARCHAR(20) DEFAULT NULL COMMENT 'ACCIDENT/ILLEGAL/RESCUE' AFTER `rescuer_name`,
  ADD COLUMN `rescue_method` VARCHAR(100) DEFAULT NULL COMMENT '施救方式' AFTER `rescue_reason`,
  ADD COLUMN `rescue_time` DATETIME DEFAULT NULL COMMENT '施救时间' AFTER `rescue_method`,
  ADD COLUMN `rescue_address` VARCHAR(255) DEFAULT NULL COMMENT '施救地点' AFTER `rescue_time`,
  ADD COLUMN `parking_area_id` BIGINT DEFAULT NULL COMMENT '停放区域' AFTER `parking_lot_id`,
  ADD COLUMN `stall_no` VARCHAR(50) DEFAULT NULL COMMENT '车位编号' AFTER `parking_area_id`;

UPDATE `detained_vehicle`
SET `entry_no` = CONCAT(DATE_FORMAT(`in_time`, '%Y%m%d'), LPAD(`id`, 6, '0'))
WHERE `entry_no` IS NULL OR `entry_no` = '';

ALTER TABLE `detained_vehicle`
  ADD UNIQUE KEY `uk_entry_no` (`entry_no`),
  ADD KEY `idx_parking_area_id` (`parking_area_id`);

CREATE TABLE IF NOT EXISTS `detain_media` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `detain_id` BIGINT NOT NULL,
  `biz_type` VARCHAR(20) NOT NULL COMMENT 'SCENE/PARK',
  `file_path` VARCHAR(255) NOT NULL,
  `sort_order` INT NOT NULL DEFAULT 0,
  `uploaded_by` BIGINT DEFAULT NULL,
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_detain_id` (`detain_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='扣车现场/停放照片';

INSERT INTO `parking_area` (`parking_lot_id`, `name`, `status`)
SELECT pl.id, 'A区', 'ENABLED' FROM `parking_lot` pl WHERE pl.code = 'PK-FT-01'
AND NOT EXISTS (SELECT 1 FROM `parking_area` a WHERE a.parking_lot_id = pl.id AND a.name = 'A区');

INSERT INTO `parking_area` (`parking_lot_id`, `name`, `status`)
SELECT pl.id, 'B区', 'ENABLED' FROM `parking_lot` pl WHERE pl.code = 'PK-FT-01'
AND NOT EXISTS (SELECT 1 FROM `parking_area` a WHERE a.parking_lot_id = pl.id AND a.name = 'B区');

INSERT INTO `parking_area` (`parking_lot_id`, `name`, `status`)
SELECT pl.id, 'A区', 'ENABLED' FROM `parking_lot` pl WHERE pl.code = 'PK-NS-01'
AND NOT EXISTS (SELECT 1 FROM `parking_area` a WHERE a.parking_lot_id = pl.id AND a.name = 'A区');
