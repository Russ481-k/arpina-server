CREATE TABLE `content_block_files` (
    `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '고유 식별자 (PK)',
    `content_block_id` bigint(20) NOT NULL COMMENT 'content_blocks 테이블 ID (FK)',
    `file_id` bigint(20) NOT NULL COMMENT 'files 테이블 ID (FK)',
    `sort_order` int NOT NULL DEFAULT 0 COMMENT '블록 내 파일 표시 순서',
    `created_by` varchar(36) DEFAULT NULL COMMENT '생성자 ID',
    `created_ip` varchar(45) DEFAULT NULL COMMENT '생성자 IP',
    `created_date` datetime DEFAULT current_timestamp() COMMENT '생성 일시',
    `updated_by` varchar(36) DEFAULT NULL COMMENT '수정자 ID',
    `updated_ip` varchar(45) DEFAULT NULL COMMENT '수정자 IP',
    `updated_date` datetime DEFAULT current_timestamp() ON UPDATE current_timestamp() COMMENT '수정 일시',
    PRIMARY KEY (`id`),
    KEY `idx_content_block_id` (`content_block_id`),
    KEY `idx_file_id` (`file_id`),
    CONSTRAINT `fk_content_block_files_to_content_block` FOREIGN KEY (`content_block_id`) REFERENCES `content_blocks` (`id`) ON DELETE CASCADE,
    CONSTRAINT `fk_content_block_files_to_file` FOREIGN KEY (`file_id`) REFERENCES `file` (`file_id`) ON DELETE CASCADE
) COMMENT = '콘텐츠 블록과 파일의 다대다 관계 테이블';