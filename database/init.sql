CREATE DATABASE IF NOT EXISTS vue_springboot_system DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE vue_springboot_system;

DROP TABLE IF EXISTS `detained_vehicle`;
DROP TABLE IF EXISTS `parking_lot`;
DROP TABLE IF EXISTS `duty_schedule`;
DROP TABLE IF EXISTS `district`;
DROP TABLE IF EXISTS `dispatch_media`;
DROP TABLE IF EXISTS `dispatch_field_record`;
DROP TABLE IF EXISTS `accident_vehicle_type`;
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
  `location_updated_at` DATETIME DEFAULT NULL COMMENT '最近一次移动端 GPS 上报时间',
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
  `plate_no` VARCHAR(20) DEFAULT NULL COMMENT '事故车辆车牌',
  `vehicle_type_id` BIGINT DEFAULT NULL COMMENT '事故车型字典 id',
  `vehicle_type_name` VARCHAR(50) DEFAULT NULL COMMENT '车型名称快照',
  `party_name` VARCHAR(50) DEFAULT NULL COMMENT '事故当事人',
  `party_phone` VARCHAR(20) DEFAULT NULL COMMENT '联系方式/手机号码',
  `status` VARCHAR(20) NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/DISPATCHED/ACCEPTED/COMPLETED/ABORTED',
  `dispatcher_id` BIGINT NOT NULL COMMENT '创建调度员 user.id',
  `vehicle_id` BIGINT DEFAULT NULL,
  `rescuer_id` BIGINT DEFAULT NULL COMMENT '施救员 user.id',
  `abort_reason` VARCHAR(500) DEFAULT NULL,
  `dispatched_at` DATETIME DEFAULT NULL,
  `completed_at` DATETIME DEFAULT NULL,
  `accepted_at` DATETIME DEFAULT NULL COMMENT '接单时间',
  `checked_in_at` DATETIME DEFAULT NULL COMMENT '签到时间',
  `checkin_lng` DECIMAL(10,7) DEFAULT NULL,
  `checkin_lat` DECIMAL(10,7) DEFAULT NULL,
  `checkin_mode` VARCHAR(20) DEFAULT NULL COMMENT 'AUTO/MANUAL',
  `checkin_remark` VARCHAR(500) DEFAULT NULL,
  `reject_reason` VARCHAR(500) DEFAULT NULL COMMENT '最近退单原因',
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
  `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_order_no` (`order_no`),
  KEY `idx_status` (`status`),
  KEY `idx_dispatcher_id` (`dispatcher_id`),
  KEY `idx_vehicle_id` (`vehicle_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='救援工单';

CREATE TABLE `accident_vehicle_type` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `name` VARCHAR(50) NOT NULL COMMENT '车型名称',
  `sort_order` INT NOT NULL DEFAULT 0 COMMENT '排序，越小越靠前',
  `status` VARCHAR(20) NOT NULL DEFAULT 'ENABLED' COMMENT 'ENABLED/DISABLED',
  `remark` VARCHAR(200) DEFAULT NULL,
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
  `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_name` (`name`),
  KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='事故车型字典';

CREATE TABLE `dispatch_field_record` (
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

CREATE TABLE `dispatch_media` (
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

CREATE TABLE `district` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `name` VARCHAR(100) NOT NULL COMMENT '片区名称',
  `code` VARCHAR(50) NOT NULL COMMENT '片区编码',
  `fence_json` TEXT NOT NULL COMMENT '多边形顶点 JSON：[{lng,lat},...]',
  `status` VARCHAR(20) NOT NULL DEFAULT 'ENABLED' COMMENT 'ENABLED/DISABLED',
  `remark` VARCHAR(200) DEFAULT NULL,
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
  `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_code` (`code`),
  KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='片区电子围栏';

CREATE TABLE `duty_schedule` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `duty_date` DATE NOT NULL COMMENT '值班归属日（用于列表筛选）',
  `start_time` DATETIME NOT NULL COMMENT '班次开始',
  `end_time` DATETIME NOT NULL COMMENT '班次结束（可跨日）',
  `user_id` BIGINT NOT NULL COMMENT '值班人 user.id',
  `role_type` VARCHAR(30) NOT NULL COMMENT 'DISPATCHER/TOW_DRIVER',
  `district_id` BIGINT DEFAULT NULL COMMENT '可选片区',
  `vehicle_id` BIGINT DEFAULT NULL COMMENT '施救班次必填；调度班次必须为空',
  `remark` VARCHAR(200) DEFAULT NULL,
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
  `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_duty_date` (`duty_date`),
  KEY `idx_user_id` (`user_id`),
  KEY `idx_vehicle_id` (`vehicle_id`),
  KEY `idx_district_id` (`district_id`),
  KEY `idx_start_end` (`start_time`, `end_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='值班排班';

CREATE TABLE `parking_lot` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `name` VARCHAR(100) NOT NULL COMMENT '停车场名称',
  `code` VARCHAR(50) NOT NULL COMMENT '编码，唯一',
  `address` VARCHAR(255) DEFAULT NULL,
  `contact_name` VARCHAR(50) DEFAULT NULL,
  `contact_phone` VARCHAR(20) DEFAULT NULL,
  `status` VARCHAR(20) NOT NULL DEFAULT 'ENABLED' COMMENT 'ENABLED/DISABLED',
  `remark` VARCHAR(200) DEFAULT NULL,
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
  `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_code` (`code`),
  KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='停车场';

CREATE TABLE `parking_area` (
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

CREATE TABLE `detained_vehicle` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `detain_no` VARCHAR(64) NOT NULL COMMENT '扣押编号（手填凭证编码），唯一',
  `entry_no` VARCHAR(32) DEFAULT NULL COMMENT '入场编号，系统生成',
  `plate_no` VARCHAR(20) NOT NULL COMMENT '车牌',
  `vehicle_type` VARCHAR(50) DEFAULT NULL COMMENT '车辆类型文本',
  `brand_model` VARCHAR(100) DEFAULT NULL COMMENT '厂牌型号',
  `vehicle_color` VARCHAR(30) DEFAULT NULL COMMENT '车辆颜色',
  `mileage` VARCHAR(50) DEFAULT NULL COMMENT '行驶里程',
  `important_equipment` VARCHAR(200) DEFAULT NULL COMMENT '重要装备',
  `has_key` VARCHAR(8) DEFAULT NULL COMMENT 'YES/NO',
  `parking_lot_id` BIGINT NOT NULL COMMENT '所属停车场',
  `parking_area_id` BIGINT DEFAULT NULL COMMENT '停放区域',
  `stall_no` VARCHAR(50) DEFAULT NULL COMMENT '车位编号',
  `dispatch_order_id` BIGINT DEFAULT NULL COMMENT '可选关联救援工单',
  `detain_dept` VARCHAR(100) DEFAULT NULL COMMENT '扣留部门',
  `rescuer_name` VARCHAR(50) DEFAULT NULL COMMENT '施救人员',
  `rescue_reason` VARCHAR(20) DEFAULT NULL COMMENT 'ACCIDENT/ILLEGAL/RESCUE',
  `rescue_method` VARCHAR(100) DEFAULT NULL COMMENT '施救方式',
  `rescue_time` DATETIME DEFAULT NULL COMMENT '施救时间',
  `rescue_address` VARCHAR(255) DEFAULT NULL COMMENT '施救地点',
  `status` VARCHAR(20) NOT NULL DEFAULT 'IN_YARD' COMMENT 'IN_YARD/OUT/CLEARED',
  `in_time` DATETIME NOT NULL COMMENT '入库时间',
  `out_time` DATETIME DEFAULT NULL,
  `cleared_at` DATETIME DEFAULT NULL,
  `operator_in_id` BIGINT DEFAULT NULL COMMENT '入库操作人 user.id',
  `operator_out_id` BIGINT DEFAULT NULL COMMENT '出库操作人 user.id',
  `remark` VARCHAR(200) DEFAULT NULL,
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
  `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_detain_no` (`detain_no`),
  UNIQUE KEY `uk_entry_no` (`entry_no`),
  KEY `idx_plate_no` (`plate_no`),
  KEY `idx_status` (`status`),
  KEY `idx_parking_lot_id` (`parking_lot_id`),
  KEY `idx_parking_area_id` (`parking_area_id`),
  KEY `idx_dispatch_order_id` (`dispatch_order_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='扣留车辆';

CREATE TABLE `detain_media` (
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

CREATE TABLE `dispatch_order_evaluation` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `dispatch_order_id` BIGINT NOT NULL COMMENT '工单 id',
  `rater_user_id` BIGINT NOT NULL COMMENT '评价人',
  `score_punctual` TINYINT NOT NULL COMMENT '到达及时 1-5',
  `score_standard` TINYINT NOT NULL COMMENT '处置规范 1-5',
  `score_safety` TINYINT NOT NULL COMMENT '操作安全 1-5',
  `score_attitude` TINYINT NOT NULL COMMENT '服务态度 1-5',
  `comment` VARCHAR(500) DEFAULT NULL COMMENT '反馈意见',
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_dispatch_order_id` (`dispatch_order_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='交警工单评价';

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
(30, '车辆删除', 'vehicle:delete', 'BUTTON', 26, 4),
(31, '片区管理', 'district:manage', 'MODULE', 0, 9),
(32, '片区查询', 'district:query', 'BUTTON', 31, 1),
(33, '片区新增', 'district:add', 'BUTTON', 31, 2),
(34, '片区编辑', 'district:edit', 'BUTTON', 31, 3),
(35, '片区删除', 'district:delete', 'BUTTON', 31, 4),
(36, '片区解析', 'district:resolve', 'BUTTON', 31, 5),
(37, '排班管理', 'schedule:manage', 'MODULE', 0, 10),
(38, '排班查询', 'schedule:query', 'BUTTON', 37, 1),
(39, '排班新增', 'schedule:add', 'BUTTON', 37, 2),
(40, '排班编辑', 'schedule:edit', 'BUTTON', 37, 3),
(41, '排班删除', 'schedule:delete', 'BUTTON', 37, 4),
(42, '停车场查询', 'parking:query', 'BUTTON', 19, 1),
(43, '停车场新增', 'parking:add', 'BUTTON', 19, 2),
(44, '停车场编辑', 'parking:edit', 'BUTTON', 19, 3),
(45, '停车场删除', 'parking:delete', 'BUTTON', 19, 4),
(46, '扣留车辆', 'detain:manage', 'MODULE', 0, 11),
(47, '扣留查询', 'detain:query', 'BUTTON', 46, 1),
(48, '扣留入库', 'detain:add', 'BUTTON', 46, 2),
(49, '扣留编辑', 'detain:edit', 'BUTTON', 46, 3),
(50, '扣留出库', 'detain:out', 'BUTTON', 46, 4),
(51, '扣留清理', 'detain:clear', 'BUTTON', 46, 5),
(52, '吊牌打印', 'detain:print', 'BUTTON', 46, 6),
(53, '施救员移动端', 'mobile:rescuer', 'MODULE', 0, 12),
(54, '施救资料', 'rescuer:profile', 'BUTTON', 53, 1),
(55, '扫码绑车', 'rescuer:bind-vehicle', 'BUTTON', 53, 2),
(56, '施救任务', 'rescuer:task', 'BUTTON', 53, 3),
(57, '施救接单', 'rescuer:accept', 'BUTTON', 53, 4),
(58, '施救退单', 'rescuer:reject', 'BUTTON', 53, 5),
(59, '施救签到', 'rescuer:checkin', 'BUTTON', 53, 6),
(60, '现场采集', 'rescuer:scene', 'BUTTON', 53, 7),
(61, '入库登记', 'rescuer:park', 'BUTTON', 53, 8),
(62, '施救完成', 'rescuer:complete', 'BUTTON', 53, 9),
(63, '车型管理', 'vehicle-type:manage', 'MODULE', 0, 13),
(64, '车型查询', 'vehicle-type:query', 'BUTTON', 63, 1),
(65, '车型新增', 'vehicle-type:add', 'BUTTON', 63, 2),
(66, '车型编辑', 'vehicle-type:edit', 'BUTTON', 63, 3),
(67, '位置上报', 'rescuer:location', 'BUTTON', 53, 10),
(68, '事故查询', 'accident:query', 'BUTTON', 17, 1),
(69, '事故评价', 'accident:rate', 'BUTTON', 17, 2);

INSERT INTO `accident_vehicle_type` (`name`, `sort_order`, `status`) VALUES
('轿车', 1, 'ENABLED'),
('大客车', 2, 'ENABLED'),
('半挂货车', 3, 'ENABLED'),
('黄牌大货车', 4, 'ENABLED'),
('蓝牌大货车', 5, 'ENABLED'),
('厢式货车', 6, 'ENABLED'),
('面包车', 7, 'ENABLED'),
('房车', 8, 'ENABLED'),
('越野车', 9, 'ENABLED'),
('三轮机动车', 10, 'ENABLED'),
('三轮电动车', 11, 'ENABLED'),
('人力三轮车', 12, 'ENABLED'),
('二轮摩托车', 13, 'ENABLED'),
('二轮电动车', 14, 'ENABLED'),
('自行车', 15, 'ENABLED'),
('残疾车', 16, 'ENABLED'),
('其他', 17, 'ENABLED');

-- ADMIN: 1-15 + 派单 16,19,20-66
INSERT INTO `role_permission` (`role_id`, `permission_id`)
SELECT 5, id FROM `permission` WHERE id BETWEEN 1 AND 15
   OR id IN (16, 17, 19)
   OR id BETWEEN 20 AND 66
   OR id IN (68, 69);

-- DISPATCHER: user:query（排班选人）+ 派单 + 车辆 + 片区 + 排班（无 user:manage 菜单）
INSERT INTO `role_permission` (`role_id`, `permission_id`)
SELECT 2, id FROM `permission` WHERE id = 2 OR id = 16 OR id BETWEEN 20 AND 41;

-- TRAFFIC_POLICE 拥有事故处理
INSERT INTO `role_permission` (`role_id`, `permission_id`) VALUES (1, 17), (1, 68), (1, 69);

-- TOW_DRIVER 拥有救援执行 + 施救员移动端 53-62 + 位置上报 67 (rescuer:location)
INSERT INTO `role_permission` (`role_id`, `permission_id`) VALUES (3, 18);
INSERT INTO `role_permission` (`role_id`, `permission_id`)
SELECT 3, id FROM `permission` WHERE id BETWEEN 53 AND 62 OR id = 67;

-- PARKING_ADMIN 拥有停车场与扣留车辆管理
INSERT INTO `role_permission` (`role_id`, `permission_id`)
SELECT 4, id FROM `permission` WHERE id = 19 OR id BETWEEN 42 AND 52;

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

INSERT INTO `district` (`name`, `code`, `fence_json`, `status`, `remark`) VALUES
('福田中心片区', 'FT-CENTER',
 '[{"lng":114.040,"lat":22.530},{"lng":114.080,"lat":22.530},{"lng":114.080,"lat":22.560},{"lng":114.040,"lat":22.560}]',
 'ENABLED', '市民中心一带'),
('南山前海片区', 'NS-QIANHAI',
 '[{"lng":113.980,"lat":22.500},{"lng":114.020,"lat":22.500},{"lng":114.020,"lat":22.540},{"lng":113.980,"lat":22.540}]',
 'ENABLED', '前海样例');

UPDATE `rescue_vehicle` SET `district_id` = 1 WHERE `plate_no` IN ('粤B·救援01', '粤B·救援02');

INSERT INTO `user` (`username`, `email`, `password`, `phone`, `real_name`, `status`) VALUES
('towdriver', 'tow@example.com',
 '$2a$10$tRbGvdiWK.72JRbBlUYmB.3K2h44sbb20U3qKWrAeggv0.lbqUhzW',
 '13800000002', '施救员演示', 1);
INSERT INTO `user_role` (`user_id`, `role_id`) VALUES (3, 3);

INSERT INTO `duty_schedule`
(`duty_date`, `start_time`, `end_time`, `user_id`, `role_type`, `district_id`, `vehicle_id`, `remark`) VALUES
(CURDATE(), CONCAT(CURDATE(), ' 08:00:00'), CONCAT(CURDATE(), ' 18:00:00'),
 2, 'DISPATCHER', 1, NULL, '调度白班样例'),
(CURDATE(), CONCAT(CURDATE(), ' 08:00:00'), CONCAT(CURDATE(), ' 18:00:00'),
 3, 'TOW_DRIVER', 1, 1, '施救白班样例');

INSERT INTO `user` (`username`, `email`, `password`, `phone`, `real_name`, `status`) VALUES
('parkingadmin', 'parking@example.com',
 '$2a$10$tRbGvdiWK.72JRbBlUYmB.3K2h44sbb20U3qKWrAeggv0.lbqUhzW',
 '13800000004', '停车场演示', 1);
INSERT INTO `user_role` (`user_id`, `role_id`)
SELECT id, 4 FROM `user` WHERE username = 'parkingadmin';

INSERT INTO `user` (`username`, `email`, `password`, `phone`, `real_name`, `status`) VALUES
('trafficpolice', 'police@example.com',
 '$2a$10$tRbGvdiWK.72JRbBlUYmB.3K2h44sbb20U3qKWrAeggv0.lbqUhzW',
 '13800000003', '交警演示', 1);
INSERT INTO `user_role` (`user_id`, `role_id`)
SELECT id, 1 FROM `user` WHERE username = 'trafficpolice';

INSERT INTO `parking_lot` (`name`, `code`, `address`, `contact_name`, `contact_phone`, `status`, `remark`) VALUES
('福田扣留场', 'PK-FT-01', '深圳市福田区示例路1号', '张管', '13900000001', 'ENABLED', '主场'),
('南山扣留场', 'PK-NS-01', '深圳市南山区示例路2号', '李管', '13900000002', 'ENABLED', NULL),
('罗湖备用场', 'PK-LH-00', '深圳市罗湖区示例路3号', NULL, NULL, 'DISABLED', '禁用样例');

INSERT INTO `parking_area` (`parking_lot_id`, `name`, `status`) VALUES
(1, 'A区', 'ENABLED'),
(1, 'B区', 'ENABLED'),
(2, 'A区', 'ENABLED');

INSERT INTO `detained_vehicle`
(`detain_no`, `entry_no`, `plate_no`, `vehicle_type`, `parking_lot_id`, `parking_area_id`, `dispatch_order_id`, `detain_dept`, `status`,
 `in_time`, `out_time`, `cleared_at`, `operator_in_id`, `operator_out_id`, `remark`) VALUES
('DV202609070001', '20260907000001', '粤B·扣留01', '小型车', 1, 1, NULL, '福田交警大队', 'IN_YARD',
 NOW(), NULL, NULL, 1, NULL, '在库样例'),
('DV202609070002', '20260907000002', '粤B·扣留02', '货车', 1, 1, NULL, '南山交警大队', 'OUT',
 DATE_SUB(NOW(), INTERVAL 2 DAY), DATE_SUB(NOW(), INTERVAL 1 DAY), NULL, 1, 1, '已出库样例');
