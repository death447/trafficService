-- 增量迁移：片区电子围栏、值班排班及权限（2026-09-04）
-- 新环境请优先执行完整 database/init.sql，本脚本仅用于已有 vue_springboot_system 库的升级。

USE vue_springboot_system;

CREATE TABLE IF NOT EXISTS `district` (
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

CREATE TABLE IF NOT EXISTS `duty_schedule` (
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

INSERT IGNORE INTO `permission` (`id`, `permission_name`, `permission_code`, `permission_type`, `parent_id`, `sort_order`) VALUES
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
(41, '排班删除', 'schedule:delete', 'BUTTON', 37, 4);

-- ADMIN: 补齐片区/排班权限 31-41
INSERT INTO `role_permission` (`role_id`, `permission_id`)
SELECT 5, p.id FROM `permission` p
WHERE p.id BETWEEN 31 AND 41
  AND NOT EXISTS (
    SELECT 1 FROM `role_permission` rp
    WHERE rp.role_id = 5 AND rp.permission_id = p.id
  );

-- DISPATCHER: 补齐片区/排班权限 31-41
INSERT INTO `role_permission` (`role_id`, `permission_id`)
SELECT 2, p.id FROM `permission` p
WHERE p.id BETWEEN 31 AND 41
  AND NOT EXISTS (
    SELECT 1 FROM `role_permission` rp
    WHERE rp.role_id = 2 AND rp.permission_id = p.id
  );

-- DISPATCHER: user:query（id=2）供排班值班人下拉；不含 user:manage 菜单
INSERT INTO `role_permission` (`role_id`, `permission_id`)
SELECT 2, 2 FROM DUAL
WHERE NOT EXISTS (
  SELECT 1 FROM `role_permission` rp
  WHERE rp.role_id = 2 AND rp.permission_id = 2
);

INSERT IGNORE INTO `district` (`name`, `code`, `fence_json`, `status`, `remark`) VALUES
('福田中心片区', 'FT-CENTER',
 '[{"lng":114.040,"lat":22.530},{"lng":114.080,"lat":22.530},{"lng":114.080,"lat":22.560},{"lng":114.040,"lat":22.560}]',
 'ENABLED', '市民中心一带'),
('南山前海片区', 'NS-QIANHAI',
 '[{"lng":113.980,"lat":22.500},{"lng":114.020,"lat":22.500},{"lng":114.020,"lat":22.540},{"lng":113.980,"lat":22.540}]',
 'ENABLED', '前海样例');

UPDATE `rescue_vehicle` SET `district_id` = (
  SELECT id FROM `district` WHERE `code` = 'FT-CENTER' LIMIT 1
) WHERE `plate_no` IN ('粤B·救援01', '粤B·救援02');

-- 施救员演示账号（若不存在）
INSERT INTO `user` (`username`, `email`, `password`, `phone`, `real_name`, `status`)
SELECT 'towdriver', 'tow@example.com',
       '$2a$10$tRbGvdiWK.72JRbBlUYmB.3K2h44sbb20U3qKWrAeggv0.lbqUhzW',
       '13800000002', '施救员演示', 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM `user` WHERE `username` = 'towdriver');

INSERT IGNORE INTO `user_role` (`user_id`, `role_id`)
SELECT id, 3 FROM `user` WHERE `username` = 'towdriver';

-- 样例排班：仅当当日尚无该 user 班次时插入
INSERT INTO `duty_schedule`
(`duty_date`, `start_time`, `end_time`, `user_id`, `role_type`, `district_id`, `vehicle_id`, `remark`)
SELECT CURDATE(), CONCAT(CURDATE(), ' 08:00:00'), CONCAT(CURDATE(), ' 18:00:00'),
       u.id, 'DISPATCHER', d.id, NULL, '调度白班样例'
FROM `user` u
CROSS JOIN (SELECT id FROM `district` WHERE `code` = 'FT-CENTER' LIMIT 1) d
WHERE u.username = 'dispatcher'
  AND NOT EXISTS (
    SELECT 1 FROM `duty_schedule` ds
    WHERE ds.user_id = u.id AND ds.duty_date = CURDATE()
  );

INSERT INTO `duty_schedule`
(`duty_date`, `start_time`, `end_time`, `user_id`, `role_type`, `district_id`, `vehicle_id`, `remark`)
SELECT CURDATE(), CONCAT(CURDATE(), ' 08:00:00'), CONCAT(CURDATE(), ' 18:00:00'),
       u.id, 'TOW_DRIVER', d.id, v.id, '施救白班样例'
FROM `user` u
CROSS JOIN (SELECT id FROM `district` WHERE `code` = 'FT-CENTER' LIMIT 1) d
CROSS JOIN (SELECT id FROM `rescue_vehicle` WHERE `plate_no` = '粤B·救援01' LIMIT 1) v
WHERE u.username = 'towdriver'
  AND NOT EXISTS (
    SELECT 1 FROM `duty_schedule` ds
    WHERE ds.user_id = u.id AND ds.duty_date = CURDATE()
  );
