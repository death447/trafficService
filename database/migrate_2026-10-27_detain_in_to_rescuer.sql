-- 入库改由救援员移动端填写
-- 1) 停车场管理员不再拥有扣留入库权限（48 = detain:add）
DELETE FROM `role_permission` WHERE `role_id` = 4 AND `permission_id` = 48;

-- 2) 救援员保留 rescuer:park（61 = 入库登记），缺失时补上
INSERT INTO `role_permission` (`role_id`, `permission_id`)
SELECT 3, p.id FROM `permission` p
WHERE p.`permission_code` = 'rescuer:park'
  AND NOT EXISTS (
    SELECT 1 FROM `role_permission` rp WHERE rp.`role_id` = 3 AND rp.`permission_id` = p.id
  );
