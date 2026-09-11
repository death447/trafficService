INSERT INTO `permission` (`id`, `permission_name`, `permission_code`, `permission_type`, `parent_id`, `sort_order`)
SELECT * FROM (
  SELECT 70 AS id, '报表管理' AS permission_name, 'report:manage' AS permission_code,
         'MODULE' AS permission_type, 0 AS parent_id, 14 AS sort_order
) AS tmp
WHERE NOT EXISTS (SELECT 1 FROM `permission` WHERE `id` = 70 OR `permission_code` = 'report:manage');

INSERT INTO `permission` (`id`, `permission_name`, `permission_code`, `permission_type`, `parent_id`, `sort_order`)
SELECT * FROM (
  SELECT 71 AS id, '报表查询' AS permission_name, 'report:query' AS permission_code,
         'BUTTON' AS permission_type, 70 AS parent_id, 1 AS sort_order
) AS tmp
WHERE NOT EXISTS (SELECT 1 FROM `permission` WHERE `id` = 71 OR `permission_code` = 'report:query');

INSERT INTO `role_permission` (`role_id`, `permission_id`)
SELECT 2, 70 FROM DUAL WHERE NOT EXISTS (
  SELECT 1 FROM `role_permission` WHERE `role_id` = 2 AND `permission_id` = 70
);
INSERT INTO `role_permission` (`role_id`, `permission_id`)
SELECT 2, 71 FROM DUAL WHERE NOT EXISTS (
  SELECT 1 FROM `role_permission` WHERE `role_id` = 2 AND `permission_id` = 71
);
INSERT INTO `role_permission` (`role_id`, `permission_id`)
SELECT 5, 70 FROM DUAL WHERE NOT EXISTS (
  SELECT 1 FROM `role_permission` WHERE `role_id` = 5 AND `permission_id` = 70
);
INSERT INTO `role_permission` (`role_id`, `permission_id`)
SELECT 5, 71 FROM DUAL WHERE NOT EXISTS (
  SELECT 1 FROM `role_permission` WHERE `role_id` = 5 AND `permission_id` = 71
);
