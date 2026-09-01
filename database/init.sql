CREATE DATABASE IF NOT EXISTS vue_springboot_system DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE vue_springboot_system;

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
(19, '停车场管理', 'parking:manage', 'MODULE', 0, 7);

-- ADMIN 拥有全部管理权限 1-15
INSERT INTO `role_permission` (`role_id`, `permission_id`)
SELECT 5, id FROM `permission` WHERE id BETWEEN 1 AND 15;

-- DISPATCHER 拥有派单管理
INSERT INTO `role_permission` (`role_id`, `permission_id`) VALUES (2, 16);

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
