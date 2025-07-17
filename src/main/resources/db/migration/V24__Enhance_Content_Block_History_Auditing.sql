ALTER TABLE `content_block_history`
CHANGE COLUMN `created_at` `created_date` DATETIME DEFAULT current_timestamp() COMMENT '해당 버전 레코드가 생성된 일시',
ADD COLUMN `updated_by` VARCHAR(36) DEFAULT NULL COMMENT '해당 버전 레코드를 수정한 사용자의 ID' AFTER `created_ip`,
ADD COLUMN `updated_ip` VARCHAR(45) DEFAULT NULL COMMENT '해당 버전 레코드를 수정한 사용자의 IP 주소' AFTER `updated_by`,
ADD COLUMN `updated_date` DATETIME DEFAULT current_timestamp() ON UPDATE current_timestamp() COMMENT '해당 버전 레코드가 마지막으로 수정된 일시' AFTER `updated_ip`;