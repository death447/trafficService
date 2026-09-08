-- 施救车辆实时位置
ALTER TABLE `rescue_vehicle`
  ADD COLUMN `location_updated_at` DATETIME DEFAULT NULL
    COMMENT '最近一次移动端 GPS 上报时间' AFTER `latitude`;

INSERT INTO `permission` (`id`, `permission_name`, `permission_code`, `permission_type`, `parent_id`, `sort_order`)
SELECT * FROM (
  SELECT 67 AS id, '位置上报' AS permission_name, 'rescuer:location' AS permission_code,
         'BUTTON' AS permission_type, 53 AS parent_id, 10 AS sort_order
) AS tmp
WHERE NOT EXISTS (SELECT 1 FROM `permission` WHERE `id` = 67 OR `permission_code` = 'rescuer:location');

INSERT INTO `role_permission` (`role_id`, `permission_id`)
SELECT 3, 67 FROM DUAL
WHERE NOT EXISTS (
  SELECT 1 FROM `role_permission` WHERE `role_id` = 3 AND `permission_id` = 67
);
