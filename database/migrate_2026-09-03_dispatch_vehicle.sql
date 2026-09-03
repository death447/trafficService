-- 增量迁移：救援车辆、工单表及调度员权限（2026-09-03）
-- 新环境请优先执行完整 database/init.sql，本脚本仅用于已有 vue_springboot_system 库的升级。

USE vue_springboot_system;

CREATE TABLE IF NOT EXISTS `rescue_vehicle` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `plate_no` VARCHAR(20) NOT NULL COMMENT '车牌',
  `vehicle_type` VARCHAR(50) NOT NULL COMMENT '车辆类型：TOW/CLEARANCE/OTHER 等',
  `color` VARCHAR(30) DEFAULT NULL,
  `equipment` VARCHAR(200) DEFAULT NULL COMMENT '配备装备',
  `longitude` DECIMAL(10,7) DEFAULT NULL,
  `latitude` DECIMAL(10,7) DEFAULT NULL,
  `status` VARCHAR(20) NOT NULL DEFAULT 'IDLE' COMMENT 'IDLE/BUSY/OFFLINE',
  `district_id` BIGINT DEFAULT NULL COMMENT '预留片区',
  `driver_user_id` BIGINT DEFAULT NULL COMMENT '绑定施救员 user.id',
  `remark` VARCHAR(200) DEFAULT NULL,
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
  `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_plate_no` (`plate_no`),
  KEY `idx_status` (`status`),
  KEY `idx_district_id` (`district_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='施救车辆';

CREATE TABLE IF NOT EXISTS `dispatch_order` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `order_no` VARCHAR(32) NOT NULL COMMENT '业务单号',
  `accident_address` VARCHAR(255) NOT NULL,
  `longitude` DECIMAL(10,7) DEFAULT NULL,
  `latitude` DECIMAL(10,7) DEFAULT NULL,
  `rescue_reason` VARCHAR(500) DEFAULT NULL,
  `status` VARCHAR(20) NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/DISPATCHED/COMPLETED/ABORTED',
  `dispatcher_id` BIGINT NOT NULL COMMENT '创建调度员 user.id',
  `vehicle_id` BIGINT DEFAULT NULL,
  `rescuer_id` BIGINT DEFAULT NULL COMMENT '施救员 user.id',
  `abort_reason` VARCHAR(500) DEFAULT NULL,
  `dispatched_at` DATETIME DEFAULT NULL,
  `completed_at` DATETIME DEFAULT NULL,
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
  `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_order_no` (`order_no`),
  KEY `idx_status` (`status`),
  KEY `idx_dispatcher_id` (`dispatcher_id`),
  KEY `idx_vehicle_id` (`vehicle_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='救援工单';

INSERT IGNORE INTO `permission` (`id`, `permission_name`, `permission_code`, `permission_type`, `parent_id`, `sort_order`) VALUES
(20, '工单查询', 'dispatch:query', 'BUTTON', 16, 1),
(21, '工单新增', 'dispatch:add', 'BUTTON', 16, 2),
(22, '工单编辑', 'dispatch:edit', 'BUTTON', 16, 3),
(23, '工单派单', 'dispatch:dispatch', 'BUTTON', 16, 4),
(24, '工单完成', 'dispatch:complete', 'BUTTON', 16, 5),
(25, '工单中止', 'dispatch:abort', 'BUTTON', 16, 6),
(26, '施救车辆', 'vehicle:manage', 'MODULE', 0, 8),
(27, '车辆查询', 'vehicle:query', 'BUTTON', 26, 1),
(28, '车辆新增', 'vehicle:add', 'BUTTON', 26, 2),
(29, '车辆编辑', 'vehicle:edit', 'BUTTON', 26, 3),
(30, '车辆删除', 'vehicle:delete', 'BUTTON', 26, 4);

-- 重建 ADMIN / DISPATCHER 与派单、车辆相关的授权
DELETE FROM `role_permission`
WHERE role_id = 5 AND permission_id IN (16, 20, 21, 22, 23, 24, 25, 26, 27, 28, 29, 30);

DELETE FROM `role_permission`
WHERE role_id = 2 AND permission_id IN (16, 20, 21, 22, 23, 24, 25, 26, 27, 28, 29, 30);

INSERT IGNORE INTO `role_permission` (`role_id`, `permission_id`)
SELECT 5, id FROM `permission` WHERE id = 16 OR id BETWEEN 20 AND 30;

INSERT IGNORE INTO `role_permission` (`role_id`, `permission_id`)
SELECT 2, id FROM `permission` WHERE id = 16 OR id BETWEEN 20 AND 30;

-- 调度员演示账号（若不存在）
INSERT INTO `user` (`username`, `email`, `password`, `phone`, `real_name`, `status`)
SELECT 'dispatcher', 'dispatcher@example.com',
       '$2a$10$tRbGvdiWK.72JRbBlUYmB.3K2h44sbb20U3qKWrAeggv0.lbqUhzW',
       '13800000001', '调度员演示', 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM `user` WHERE `username` = 'dispatcher');

INSERT IGNORE INTO `user_role` (`user_id`, `role_id`)
SELECT id, 2 FROM `user` WHERE `username` = 'dispatcher';

-- 种子车辆
INSERT IGNORE INTO `rescue_vehicle`
(`plate_no`, `vehicle_type`, `color`, `equipment`, `longitude`, `latitude`, `status`, `remark`) VALUES
('粤B·救援01', 'TOW', '黄', '拖车绳', 114.0578680, 22.5430990, 'IDLE', '深圳市民中心附近'),
('粤B·救援02', 'TOW', '白', '液压绞盘', 114.0859470, 22.5470000, 'IDLE', '稍偏东'),
('粤B·救援03', 'CLEARANCE', '蓝', '清障设备', 114.0300000, 22.5400000, 'IDLE', '稍偏西'),
('粤B·救援04', 'TOW', '红', NULL, 114.0578680, 22.5430990, 'OFFLINE', '离线样例');
