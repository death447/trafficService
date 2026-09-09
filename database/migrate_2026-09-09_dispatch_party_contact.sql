ALTER TABLE `dispatch_order`
  ADD COLUMN `party_name` VARCHAR(50) DEFAULT NULL COMMENT '事故当事人' AFTER `vehicle_type_name`,
  ADD COLUMN `party_phone` VARCHAR(20) DEFAULT NULL COMMENT '联系方式/手机号码' AFTER `party_name`;
