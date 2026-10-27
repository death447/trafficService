-- 入库登记的扣押部门 / 施救方式改为后台字典配置
CREATE TABLE IF NOT EXISTS `sys_dict_item` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `dict_type` VARCHAR(32) NOT NULL COMMENT '字典类型：DETAIN_DEPT/RESCUE_METHOD',
  `name` VARCHAR(100) NOT NULL COMMENT '显示名称',
  `sort_order` INT NOT NULL DEFAULT 0 COMMENT '排序，越小越靠前',
  `status` VARCHAR(20) NOT NULL DEFAULT 'ENABLED' COMMENT 'ENABLED/DISABLED',
  `remark` VARCHAR(200) DEFAULT NULL,
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
  `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_dict_type_name` (`dict_type`, `name`),
  KEY `idx_dict_type` (`dict_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='系统字典项';

INSERT IGNORE INTO `sys_dict_item` (`dict_type`, `name`, `sort_order`, `status`) VALUES
('DETAIN_DEPT', '福田交警大队', 1, 'ENABLED'),
('DETAIN_DEPT', '南山交警大队', 2, 'ENABLED'),
('DETAIN_DEPT', '罗湖交警大队', 3, 'ENABLED'),
('DETAIN_DEPT', '宝安交警大队', 4, 'ENABLED'),
('DETAIN_DEPT', '城北中队', 5, 'ENABLED'),
('DETAIN_DEPT', '城南中队', 6, 'ENABLED'),
('RESCUE_METHOD', '拖车', 1, 'ENABLED'),
('RESCUE_METHOD', '清障车', 2, 'ENABLED'),
('RESCUE_METHOD', '代驾', 3, 'ENABLED'),
('RESCUE_METHOD', '自开', 4, 'ENABLED');

INSERT IGNORE INTO `permission`
  (`id`, `permission_name`, `permission_code`, `permission_type`, `parent_id`, `sort_order`) VALUES
(72, '字典管理', 'dict:manage', 'MODULE', 0, 15),
(73, '字典查询', 'dict:query', 'BUTTON', 72, 1),
(74, '字典新增', 'dict:add', 'BUTTON', 72, 2),
(75, '字典编辑', 'dict:edit', 'BUTTON', 72, 3),
(76, '字典删除', 'dict:delete', 'BUTTON', 72, 4);

INSERT INTO `role_permission` (`role_id`, `permission_id`)
SELECT 5, p.id FROM `permission` p
WHERE p.id BETWEEN 72 AND 76
  AND NOT EXISTS (
    SELECT 1 FROM `role_permission` rp WHERE rp.role_id = 5 AND rp.permission_id = p.id
  );
