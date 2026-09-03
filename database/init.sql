CREATE DATABASE IF NOT EXISTS vue_springboot_system DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE vue_springboot_system;

DROP TABLE IF EXISTS `dispatch_order`;
DROP TABLE IF EXISTS `rescue_vehicle`;
DROP TABLE IF EXISTS `role_permission`;
DROP TABLE IF EXISTS `user_role`;
DROP TABLE IF EXISTS `permission`;
DROP TABLE IF EXISTS `role`;
DROP TABLE IF EXISTS `user`;

CREATE TABLE `user` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `username` VARCHAR(50) NOT NULL COMMENT '用户名',
  `email` VARCHAR(100) NOT NULL COMMENT '邮箱',
  `password` VARCHAR(255) NOT NULL COMMENT '密码',
  `phone` VARCHAR(20) DEFAULT NULL COMMENT '手机号',
  `real_name` VARCHAR(50) DEFAULT NULL COMMENT '真实姓名',
  `status` TINYINT DEFAULT 1 COMMENT '状态：1-启用，0-停用',
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_username` (`username`),
  UNIQUE KEY `uk_email` (`email`),
  UNIQUE KEY `uk_phone` (`phone`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户表';

CREATE TABLE `role` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `role_name` VARCHAR(50) NOT NULL,
  `role_code` VARCHAR(50) NOT NULL,
  `description` VARCHAR(200) DEFAULT NULL,
  `status` TINYINT DEFAULT 1,
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
  `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_role_code` (`role_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='角色表';

CREATE TABLE `permission` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `permission_name` VARCHAR(100) NOT NULL,
  `permission_code` VARCHAR(100) NOT NULL,
  `permission_type` VARCHAR(20) NOT NULL COMMENT 'MODULE/BUTTON/API',
  `parent_id` BIGINT DEFAULT 0,
  `description` VARCHAR(200) DEFAULT NULL,
  `sort_order` INT DEFAULT 0,
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
  `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_permission_code` (`permission_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='权限表';

CREATE TABLE `user_role` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `user_id` BIGINT NOT NULL,
  `role_id` BIGINT NOT NULL,
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_role` (`user_id`, `role_id`),
  KEY `idx_user_id` (`user_id`),
  KEY `idx_role_id` (`role_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户角色关联表';

CREATE TABLE `role_permission` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `role_id` BIGINT NOT NULL,
  `permission_id` BIGINT NOT NULL,
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_role_permission` (`role_id`, `permission_id`),
  KEY `idx_role_id` (`role_id`),
  KEY `idx_permission_id` (`permission_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='角色权限关联表';

CREATE TABLE `rescue_vehicle` (
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

CREATE TABLE `dispatch_order` (
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

INSERT INTO `role` (`role_name`, `role_code`, `description`) VALUES
('交警', 'TRAFFIC_POLICE', '负责事故处理'),
('调度员', 'DISPATCHER', '负责派单管理、资源调度、任务分配'),
('拖车施救员', 'TOW_DRIVER', '负责执行救援任务'),
('停车场管理员', 'PARKING_ADMIN', '负责停车场管理'),
('系统管理员', 'ADMIN', '系统管理，含用户角色权限管理');

INSERT INTO `permission` (`id`, `permission_name`, `permission_code`, `permission_type`, `parent_id`, `sort_order`) VALUES
(1, '用户管理', 'user:manage', 'MODULE', 0, 1),
(2, '用户查询', 'user:query', 'BUTTON', 1, 1),
(3, '用户新增', 'user:add', 'BUTTON', 1, 2),
(4, '用户编辑', 'user:edit', 'BUTTON', 1, 3),
(5, '用户删除', 'user:delete', 'BUTTON', 1, 4),
(6, '角色管理', 'role:manage', 'MODULE', 0, 2),
(7, '角色查询', 'role:query', 'BUTTON', 6, 1),
(8, '角色新增', 'role:add', 'BUTTON', 6, 2),
(9, '角色编辑', 'role:edit', 'BUTTON', 6, 3),
(10, '角色删除', 'role:delete', 'BUTTON', 6, 4),
(11, '权限管理', 'permission:manage', 'MODULE', 0, 3),
(12, '权限查询', 'permission:query', 'BUTTON', 11, 1),
(13, '权限新增', 'permission:add', 'BUTTON', 11, 2),
(14, '权限编辑', 'permission:edit', 'BUTTON', 11, 3),
(15, '权限删除', 'permission:delete', 'BUTTON', 11, 4),
(16, '派单管理', 'dispatch:manage', 'MODULE', 0, 4),
(17, '事故处理', 'accident:manage', 'MODULE', 0, 5),
(18, '救援执行', 'rescue:manage', 'MODULE', 0, 6),
(19, '停车场管理', 'parking:manage', 'MODULE', 0, 7),
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

-- ADMIN: 系统管理 1-15 + 派单模块及按钮 16,20-25 + 车辆 26-30
INSERT INTO `role_permission` (`role_id`, `permission_id`)
SELECT 5, id FROM `permission` WHERE id BETWEEN 1 AND 15
   OR id = 16 OR id BETWEEN 20 AND 30;

-- DISPATCHER: 派单 + 车辆
INSERT INTO `role_permission` (`role_id`, `permission_id`)
SELECT 2, id FROM `permission` WHERE id = 16 OR id BETWEEN 20 AND 30;

-- TRAFFIC_POLICE 拥有事故处理
INSERT INTO `role_permission` (`role_id`, `permission_id`) VALUES (1, 17);

-- TOW_DRIVER 拥有救援执行
INSERT INTO `role_permission` (`role_id`, `permission_id`) VALUES (3, 18);

-- PARKING_ADMIN 拥有停车场管理
INSERT INTO `role_permission` (`role_id`, `permission_id`) VALUES (4, 19);

-- admin 用户密码为 BCrypt(admin123)
INSERT INTO `user` (`username`, `email`, `password`, `phone`, `real_name`, `status`) VALUES
('admin', 'admin@example.com', '$2a$10$tRbGvdiWK.72JRbBlUYmB.3K2h44sbb20U3qKWrAeggv0.lbqUhzW', '13800000000', '系统管理员', 1);

INSERT INTO `user_role` (`user_id`, `role_id`) VALUES (1, 5);

INSERT INTO `user` (`username`, `email`, `password`, `phone`, `real_name`, `status`) VALUES
('dispatcher', 'dispatcher@example.com',
 '$2a$10$tRbGvdiWK.72JRbBlUYmB.3K2h44sbb20U3qKWrAeggv0.lbqUhzW',
 '13800000001', '调度员演示', 1);
INSERT INTO `user_role` (`user_id`, `role_id`) VALUES (2, 2);

INSERT INTO `rescue_vehicle`
(`plate_no`, `vehicle_type`, `color`, `equipment`, `longitude`, `latitude`, `status`, `remark`) VALUES
('粤B·救援01', 'TOW', '黄', '拖车绳', 114.0578680, 22.5430990, 'IDLE', '深圳市民中心附近'),
('粤B·救援02', 'TOW', '白', '液压绞盘', 114.0859470, 22.5470000, 'IDLE', '稍偏东'),
('粤B·救援03', 'CLEARANCE', '蓝', '清障设备', 114.0300000, 22.5400000, 'IDLE', '稍偏西'),
('粤B·救援04', 'TOW', '红', NULL, 114.0578680, 22.5430990, 'OFFLINE', '离线样例');
