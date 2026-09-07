-- 增量迁移：施救员移动端 schema 与权限（2026-09-07）
-- 本脚本仅执行一次；重复执行 ALTER ADD COLUMN 会失败。新环境请优先 database/init.sql。

USE vue_springboot_system;

ALTER TABLE `dispatch_order`
  ADD COLUMN `accepted_at` DATETIME DEFAULT NULL COMMENT '接单时间',
  ADD COLUMN `checked_in_at` DATETIME DEFAULT NULL COMMENT '签到时间',
  ADD COLUMN `checkin_lng` DECIMAL(10,7) DEFAULT NULL,
  ADD COLUMN `checkin_lat` DECIMAL(10,7) DEFAULT NULL,
  ADD COLUMN `checkin_mode` VARCHAR(20) DEFAULT NULL COMMENT 'AUTO/MANUAL',
  ADD COLUMN `checkin_remark` VARCHAR(500) DEFAULT NULL,
  ADD COLUMN `reject_reason` VARCHAR(500) DEFAULT NULL COMMENT '最近退单原因';

-- 同步 init 时 status 注释已在 init 改；migrate 不强制 MODIFY COMMENT

CREATE TABLE IF NOT EXISTS `dispatch_field_record` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `dispatch_order_id` BIGINT NOT NULL,
  `plate_no` VARCHAR(20) DEFAULT NULL,
  `vehicle_type` VARCHAR(50) DEFAULT NULL,
  `damage_desc` VARCHAR(1000) DEFAULT NULL,
  `scene_remark` VARCHAR(500) DEFAULT NULL,
  `park_address` VARCHAR(255) DEFAULT NULL,
  `park_remark` VARCHAR(500) DEFAULT NULL,
  `scene_submitted_at` DATETIME DEFAULT NULL,
  `park_submitted_at` DATETIME DEFAULT NULL,
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
  `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_dispatch_order_id` (`dispatch_order_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='工单现场与入库登记';

CREATE TABLE IF NOT EXISTS `dispatch_media` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `dispatch_order_id` BIGINT NOT NULL,
  `biz_type` VARCHAR(20) NOT NULL COMMENT 'DAMAGE/PARK',
  `file_path` VARCHAR(500) NOT NULL,
  `sort_order` INT DEFAULT 0,
  `uploaded_by` BIGINT NOT NULL,
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_order_biz` (`dispatch_order_id`, `biz_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='工单现场媒体';

INSERT INTO `permission` (`id`, `permission_name`, `permission_code`, `permission_type`, `parent_id`, `sort_order`) VALUES
(53, '施救员移动端', 'mobile:rescuer', 'MODULE', 0, 12),
(54, '施救资料', 'rescuer:profile', 'BUTTON', 53, 1),
(55, '扫码绑车', 'rescuer:bind-vehicle', 'BUTTON', 53, 2),
(56, '施救任务', 'rescuer:task', 'BUTTON', 53, 3),
(57, '施救接单', 'rescuer:accept', 'BUTTON', 53, 4),
(58, '施救退单', 'rescuer:reject', 'BUTTON', 53, 5),
(59, '施救签到', 'rescuer:checkin', 'BUTTON', 53, 6),
(60, '现场采集', 'rescuer:scene', 'BUTTON', 53, 7),
(61, '入库登记', 'rescuer:park', 'BUTTON', 53, 8),
(62, '施救完成', 'rescuer:complete', 'BUTTON', 53, 9);

INSERT INTO `role_permission` (`role_id`, `permission_id`)
SELECT 3, id FROM `permission` WHERE id BETWEEN 53 AND 62;
INSERT INTO `role_permission` (`role_id`, `permission_id`)
SELECT 5, id FROM `permission` WHERE id BETWEEN 53 AND 62;
