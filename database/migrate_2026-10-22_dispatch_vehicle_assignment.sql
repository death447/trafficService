-- 工单-施救车多对多赋值表；历史单车数据迁入；现场/媒体挂 assignment_id
-- 仅执行一次

CREATE TABLE IF NOT EXISTS `dispatch_vehicle_assignment` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `dispatch_order_id` BIGINT NOT NULL,
  `vehicle_id` BIGINT NOT NULL,
  `rescuer_id` BIGINT NOT NULL COMMENT '派单时车辆绑定司机',
  `status` VARCHAR(20) NOT NULL COMMENT 'DISPATCHED/ACCEPTED/COMPLETED/REJECTED/ABORTED',
  `dispatched_at` DATETIME DEFAULT NULL,
  `accepted_at` DATETIME DEFAULT NULL,
  `checked_in_at` DATETIME DEFAULT NULL,
  `checkin_lng` DECIMAL(10,7) DEFAULT NULL,
  `checkin_lat` DECIMAL(10,7) DEFAULT NULL,
  `checkin_mode` VARCHAR(20) DEFAULT NULL,
  `checkin_remark` VARCHAR(500) DEFAULT NULL,
  `reject_reason` VARCHAR(500) DEFAULT NULL,
  `completed_at` DATETIME DEFAULT NULL,
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
  `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_order_vehicle` (`dispatch_order_id`, `vehicle_id`),
  KEY `idx_rescuer_status` (`rescuer_id`, `status`),
  KEY `idx_vehicle_status` (`vehicle_id`, `status`),
  KEY `idx_order_id` (`dispatch_order_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='工单-施救车赋值';

INSERT INTO `dispatch_vehicle_assignment` (
  `dispatch_order_id`, `vehicle_id`, `rescuer_id`, `status`,
  `dispatched_at`, `accepted_at`, `checked_in_at`, `checkin_lng`, `checkin_lat`,
  `checkin_mode`, `checkin_remark`, `reject_reason`, `completed_at`
)
SELECT
  o.`id`,
  o.`vehicle_id`,
  COALESCE(o.`rescuer_id`, v.`driver_user_id`),
  o.`status`,
  o.`dispatched_at`,
  o.`accepted_at`,
  o.`checked_in_at`,
  o.`checkin_lng`,
  o.`checkin_lat`,
  o.`checkin_mode`,
  o.`checkin_remark`,
  o.`reject_reason`,
  o.`completed_at`
FROM `dispatch_order` o
LEFT JOIN `rescue_vehicle` v ON v.`id` = o.`vehicle_id`
WHERE o.`vehicle_id` IS NOT NULL
  AND o.`status` IN ('DISPATCHED', 'ACCEPTED', 'COMPLETED', 'ABORTED')
  AND COALESCE(o.`rescuer_id`, v.`driver_user_id`) IS NOT NULL
  AND NOT EXISTS (
    SELECT 1 FROM `dispatch_vehicle_assignment` a
    WHERE a.`dispatch_order_id` = o.`id` AND a.`vehicle_id` = o.`vehicle_id`
  );

-- 幂等：列已存在则跳过（手工环境可忽略重复列错误后继续）
ALTER TABLE `dispatch_field_record`
  ADD COLUMN `assignment_id` BIGINT DEFAULT NULL COMMENT '所属赋值' AFTER `dispatch_order_id`;

ALTER TABLE `dispatch_media`
  ADD COLUMN `assignment_id` BIGINT DEFAULT NULL COMMENT '所属赋值' AFTER `dispatch_order_id`;

UPDATE `dispatch_field_record` fr
  INNER JOIN `dispatch_vehicle_assignment` a ON a.`dispatch_order_id` = fr.`dispatch_order_id`
SET fr.`assignment_id` = a.`id`
WHERE fr.`assignment_id` IS NULL;

UPDATE `dispatch_media` m
  INNER JOIN `dispatch_vehicle_assignment` a ON a.`dispatch_order_id` = m.`dispatch_order_id`
SET m.`assignment_id` = a.`id`
WHERE m.`assignment_id` IS NULL;

-- 若旧 uk 不存在可忽略报错后手工处理
ALTER TABLE `dispatch_field_record` DROP INDEX `uk_dispatch_order_id`;
ALTER TABLE `dispatch_field_record`
  ADD UNIQUE KEY `uk_assignment_id` (`assignment_id`);
