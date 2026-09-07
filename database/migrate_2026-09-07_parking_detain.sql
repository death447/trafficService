-- 增量迁移：停车场、扣留车辆及权限（2026-09-07）
-- 新环境请优先执行完整 database/init.sql，本脚本仅用于已有 vue_springboot_system 库的升级。

USE vue_springboot_system;

CREATE TABLE IF NOT EXISTS `parking_lot` (
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

CREATE TABLE IF NOT EXISTS `detained_vehicle` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `detain_no` VARCHAR(32) NOT NULL COMMENT '扣押编号，唯一',
  `plate_no` VARCHAR(20) NOT NULL COMMENT '车牌',
  `vehicle_type` VARCHAR(50) DEFAULT NULL COMMENT '车辆类型文本',
  `parking_lot_id` BIGINT NOT NULL COMMENT '所属停车场',
  `dispatch_order_id` BIGINT DEFAULT NULL COMMENT '可选关联救援工单',
  `detain_dept` VARCHAR(100) DEFAULT NULL COMMENT '扣留部门',
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
  KEY `idx_plate_no` (`plate_no`),
  KEY `idx_status` (`status`),
  KEY `idx_parking_lot_id` (`parking_lot_id`),
  KEY `idx_dispatch_order_id` (`dispatch_order_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='扣留车辆';

INSERT IGNORE INTO `permission` (`id`, `permission_name`, `permission_code`, `permission_type`, `parent_id`, `sort_order`) VALUES
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
(52, '吊牌打印', 'detain:print', 'BUTTON', 46, 6);

-- ADMIN: 补齐 parking:manage(19) 与 42-52
INSERT INTO `role_permission` (`role_id`, `permission_id`)
SELECT 5, p.id FROM `permission` p
WHERE p.id = 19 OR p.id BETWEEN 42 AND 52
  AND NOT EXISTS (
    SELECT 1 FROM `role_permission` rp
    WHERE rp.role_id = 5 AND rp.permission_id = p.id
  );

-- PARKING_ADMIN: parking:manage(19) 与 42-52
INSERT INTO `role_permission` (`role_id`, `permission_id`)
SELECT 4, p.id FROM `permission` p
WHERE p.id = 19 OR p.id BETWEEN 42 AND 52
  AND NOT EXISTS (
    SELECT 1 FROM `role_permission` rp
    WHERE rp.role_id = 4 AND rp.permission_id = p.id
  );

INSERT IGNORE INTO `parking_lot` (`name`, `code`, `address`, `contact_name`, `contact_phone`, `status`, `remark`) VALUES
('福田扣留场', 'PK-FT-01', '深圳市福田区示例路1号', '张管', '13900000001', 'ENABLED', '主场'),
('南山扣留场', 'PK-NS-01', '深圳市南山区示例路2号', '李管', '13900000002', 'ENABLED', NULL),
('罗湖备用场', 'PK-LH-00', '深圳市罗湖区示例路3号', NULL, NULL, 'DISABLED', '禁用样例');

INSERT IGNORE INTO `detained_vehicle`
(`detain_no`, `plate_no`, `vehicle_type`, `parking_lot_id`, `dispatch_order_id`, `detain_dept`, `status`,
 `in_time`, `out_time`, `cleared_at`, `operator_in_id`, `operator_out_id`, `remark`)
SELECT 'DV202609070001', '粤B·扣留01', '小型车', pl.id, NULL, '福田交警大队', 'IN_YARD',
       NOW(), NULL, NULL, 1, NULL, '在库样例'
FROM `parking_lot` pl WHERE pl.code = 'PK-FT-01' LIMIT 1;

INSERT IGNORE INTO `detained_vehicle`
(`detain_no`, `plate_no`, `vehicle_type`, `parking_lot_id`, `dispatch_order_id`, `detain_dept`, `status`,
 `in_time`, `out_time`, `cleared_at`, `operator_in_id`, `operator_out_id`, `remark`)
SELECT 'DV202609070002', '粤B·扣留02', '货车', pl.id, NULL, '南山交警大队', 'OUT',
       DATE_SUB(NOW(), INTERVAL 2 DAY), DATE_SUB(NOW(), INTERVAL 1 DAY), NULL, 1, 1, '已出库样例'
FROM `parking_lot` pl WHERE pl.code = 'PK-FT-01' LIMIT 1;

-- 停车场管理员演示账号（若不存在）
INSERT INTO `user` (`username`, `email`, `password`, `phone`, `real_name`, `status`)
SELECT 'parkingadmin', 'parking@example.com',
       '$2a$10$tRbGvdiWK.72JRbBlUYmB.3K2h44sbb20U3qKWrAeggv0.lbqUhzW',
       '13800000004', '停车场演示', 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM `user` WHERE `username` = 'parkingadmin');

INSERT IGNORE INTO `user_role` (`user_id`, `role_id`)
SELECT id, 4 FROM `user` WHERE `username` = 'parkingadmin';
