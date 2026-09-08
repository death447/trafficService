-- 事故车型字典 + 工单车牌/车型快照
CREATE TABLE IF NOT EXISTS `accident_vehicle_type` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `name` VARCHAR(50) NOT NULL COMMENT '车型名称',
  `sort_order` INT NOT NULL DEFAULT 0,
  `status` VARCHAR(20) NOT NULL DEFAULT 'ENABLED',
  `remark` VARCHAR(200) DEFAULT NULL,
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
  `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_name` (`name`),
  KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='事故车型字典';

-- 幂等：仅当列不存在时由执行者按环境手工确认；推荐开发库直接 ALTER：
ALTER TABLE `dispatch_order`
  ADD COLUMN `plate_no` VARCHAR(20) DEFAULT NULL COMMENT '事故车辆车牌' AFTER `rescue_reason`,
  ADD COLUMN `vehicle_type_id` BIGINT DEFAULT NULL COMMENT '事故车型字典 id' AFTER `plate_no`,
  ADD COLUMN `vehicle_type_name` VARCHAR(50) DEFAULT NULL COMMENT '车型名称快照' AFTER `vehicle_type_id`;

INSERT INTO `accident_vehicle_type` (`name`, `sort_order`, `status`)
SELECT v.name, v.sort_order, 'ENABLED' FROM (
  SELECT '轿车' AS name, 1 AS sort_order UNION ALL
  SELECT '大客车', 2 UNION ALL SELECT '半挂货车', 3 UNION ALL
  SELECT '黄牌大货车', 4 UNION ALL SELECT '蓝牌大货车', 5 UNION ALL
  SELECT '厢式货车', 6 UNION ALL SELECT '面包车', 7 UNION ALL
  SELECT '房车', 8 UNION ALL SELECT '越野车', 9 UNION ALL
  SELECT '三轮机动车', 10 UNION ALL SELECT '三轮电动车', 11 UNION ALL
  SELECT '人力三轮车', 12 UNION ALL SELECT '二轮摩托车', 13 UNION ALL
  SELECT '二轮电动车', 14 UNION ALL SELECT '自行车', 15 UNION ALL
  SELECT '残疾车', 16 UNION ALL SELECT '其他', 17
) v
WHERE NOT EXISTS (SELECT 1 FROM `accident_vehicle_type` t WHERE t.name = v.name);

INSERT INTO `permission` (`id`, `permission_name`, `permission_code`, `permission_type`, `parent_id`, `sort_order`)
SELECT * FROM (
  SELECT 63 AS id, '车型管理' AS permission_name, 'vehicle-type:manage' AS permission_code, 'MODULE' AS permission_type, 0 AS parent_id, 13 AS sort_order
  UNION ALL SELECT 64, '车型查询', 'vehicle-type:query', 'BUTTON', 63, 1
  UNION ALL SELECT 65, '车型新增', 'vehicle-type:add', 'BUTTON', 63, 2
  UNION ALL SELECT 66, '车型编辑', 'vehicle-type:edit', 'BUTTON', 63, 3
) x WHERE NOT EXISTS (SELECT 1 FROM `permission` p WHERE p.id = x.id);

INSERT INTO `role_permission` (`role_id`, `permission_id`)
SELECT 5, id FROM `permission` WHERE id BETWEEN 63 AND 66
AND NOT EXISTS (
  SELECT 1 FROM `role_permission` rp WHERE rp.role_id = 5 AND rp.permission_id = permission.id
);
