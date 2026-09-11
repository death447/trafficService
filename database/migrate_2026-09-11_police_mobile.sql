CREATE TABLE IF NOT EXISTS `dispatch_order_evaluation` (
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

INSERT INTO `permission` (`id`, `permission_name`, `permission_code`, `permission_type`, `parent_id`, `sort_order`)
SELECT * FROM (
  SELECT 68 AS id, '事故查询' AS permission_name, 'accident:query' AS permission_code,
         'BUTTON' AS permission_type, 17 AS parent_id, 1 AS sort_order
) AS tmp
WHERE NOT EXISTS (SELECT 1 FROM `permission` WHERE `id` = 68 OR `permission_code` = 'accident:query');

INSERT INTO `permission` (`id`, `permission_name`, `permission_code`, `permission_type`, `parent_id`, `sort_order`)
SELECT * FROM (
  SELECT 69 AS id, '事故评价' AS permission_name, 'accident:rate' AS permission_code,
         'BUTTON' AS permission_type, 17 AS parent_id, 2 AS sort_order
) AS tmp
WHERE NOT EXISTS (SELECT 1 FROM `permission` WHERE `id` = 69 OR `permission_code` = 'accident:rate');

INSERT INTO `role_permission` (`role_id`, `permission_id`)
SELECT 1, 68 FROM DUAL WHERE NOT EXISTS (
  SELECT 1 FROM `role_permission` WHERE `role_id` = 1 AND `permission_id` = 68
);
INSERT INTO `role_permission` (`role_id`, `permission_id`)
SELECT 1, 69 FROM DUAL WHERE NOT EXISTS (
  SELECT 1 FROM `role_permission` WHERE `role_id` = 1 AND `permission_id` = 69
);
INSERT INTO `role_permission` (`role_id`, `permission_id`)
SELECT 5, 17 FROM DUAL WHERE NOT EXISTS (
  SELECT 1 FROM `role_permission` WHERE `role_id` = 5 AND `permission_id` = 17
);
INSERT INTO `role_permission` (`role_id`, `permission_id`)
SELECT 5, 68 FROM DUAL WHERE NOT EXISTS (
  SELECT 1 FROM `role_permission` WHERE `role_id` = 5 AND `permission_id` = 68
);
INSERT INTO `role_permission` (`role_id`, `permission_id`)
SELECT 5, 69 FROM DUAL WHERE NOT EXISTS (
  SELECT 1 FROM `role_permission` WHERE `role_id` = 5 AND `permission_id` = 69
);

INSERT INTO `user` (`username`, `email`, `password`, `phone`, `real_name`, `status`)
SELECT 'trafficpolice', 'police@example.com',
       '$2a$10$tRbGvdiWK.72JRbBlUYmB.3K2h44sbb20U3qKWrAeggv0.lbqUhzW',
       '13800000003', '交警演示', 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM `user` WHERE `username` = 'trafficpolice');

INSERT INTO `user_role` (`user_id`, `role_id`)
SELECT u.id, 1 FROM `user` u
WHERE u.username = 'trafficpolice'
  AND NOT EXISTS (SELECT 1 FROM `user_role` ur WHERE ur.user_id = u.id AND ur.role_id = 1);
