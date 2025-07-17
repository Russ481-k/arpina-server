ALTER TABLE content_blocks
ADD COLUMN version INT NOT NULL DEFAULT 1 COMMENT '콘텐츠 버전' AFTER sort_order;