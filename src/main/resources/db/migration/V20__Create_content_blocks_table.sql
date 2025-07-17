CREATE TABLE content_blocks (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    menu_id INT(11) NOT NULL,
    type VARCHAR(20) NOT NULL,
    content TEXT,
    file_id BIGINT(20),
    sort_order INT NOT NULL DEFAULT 0,
    created_by VARCHAR(36) DEFAULT NULL COMMENT '생성자 ID',
    created_ip VARCHAR(45) DEFAULT NULL COMMENT '생성자 IP',
    created_date DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '생성 일시',
    updated_by VARCHAR(36) DEFAULT NULL COMMENT '수정자 ID',
    updated_ip VARCHAR(45) DEFAULT NULL COMMENT '수정자 IP',
    updated_date DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '수정 일시',
    CONSTRAINT fk_content_blocks_menu FOREIGN KEY (menu_id) REFERENCES menu (id) ON DELETE CASCADE,
    CONSTRAINT fk_content_blocks_file FOREIGN KEY (file_id) REFERENCES file (file_id) ON DELETE SET NULL
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_general_ci;