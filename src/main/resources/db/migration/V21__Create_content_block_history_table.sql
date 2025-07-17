CREATE TABLE content_block_history (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    content_block_id BIGINT NOT NULL,
    version INT NOT NULL,
    type VARCHAR(20) NOT NULL,
    content TEXT,
    file_id BIGINT(20),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(36),
    created_ip VARCHAR(45),
    CONSTRAINT fk_history_content_block FOREIGN KEY (content_block_id) REFERENCES content_blocks (id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_general_ci;