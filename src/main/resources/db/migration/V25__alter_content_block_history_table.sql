ALTER TABLE `content_block_history`
ADD COLUMN `file_ids_json` LONGTEXT COMMENT '파일 ID 목록을 저장하는 JSON 필드' AFTER `content`,
DROP COLUMN `file_id`;