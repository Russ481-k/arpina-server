-- 파일 관리 테이블 생성
CREATE TABLE file (
    file_id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT 'PK: 파일 ID',
    menu VARCHAR(30) NOT NULL COMMENT '모듈 코드 (BBS, POPUP, CONTENT, PROGRAM)',
    menu_id BIGINT NOT NULL COMMENT '모듈별 리소스 ID',
    origin_name VARCHAR(255) NOT NULL COMMENT '원본 파일명',
    saved_name VARCHAR(255) NOT NULL COMMENT '저장된 파일명',
    mime_type VARCHAR(100) NOT NULL COMMENT 'MIME 타입',
    size BIGINT NOT NULL COMMENT '파일 크기(바이트)',
    ext VARCHAR(20) NOT NULL COMMENT '파일 확장자',
    version INT DEFAULT 1 COMMENT '파일 버전',
    public_yn VARCHAR(1) DEFAULT 'Y' COMMENT '공개 여부 (Y/N)',
    file_order INT DEFAULT 0 COMMENT '파일 순서',
    created_by VARCHAR(36) NULL COMMENT '생성자 ID',
    created_ip VARCHAR(45) NULL COMMENT '생성자 IP',
    created_date DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '생성 일시',
    updated_by VARCHAR(36) NULL COMMENT '수정자 ID',
    updated_ip VARCHAR(45) NULL COMMENT '수정자 IP',
    updated_date DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '수정 일시',
    -- 인덱스
    INDEX idx_file_menu (menu, menu_id),
    INDEX idx_file_public (public_yn),
    INDEX idx_file_order (menu, menu_id, file_order),
    INDEX idx_file_public_menu (menu, menu_id, public_yn),
    -- 제약조건
    CONSTRAINT chk_public_yn CHECK (public_yn IN ('Y', 'N')),
    CONSTRAINT uk_saved_name UNIQUE (saved_name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='CMS 파일';

-- 파일 공개 여부 체크 트리거
DELIMITER //
CREATE TRIGGER trg_file_public_yn_check_insert BEFORE INSERT ON file
FOR EACH ROW
BEGIN
    IF NEW.public_yn NOT IN ('Y', 'N') THEN
        SIGNAL SQLSTATE '45000'
        SET MESSAGE_TEXT = 'public_yn must be Y or N';
    END IF;
END; 

DELIMITER ;

-- 파일 공개 여부 체크 트리거 (업데이트)
DELIMITER //
CREATE TRIGGER trg_file_public_yn_check_update BEFORE UPDATE ON file
FOR EACH ROW
BEGIN
    IF NEW.public_yn NOT IN ('Y', 'N') THEN
        SIGNAL SQLSTATE '45000'
        SET MESSAGE_TEXT = 'public_yn must be Y or N';
    END IF;
END;
DELIMITER ;

-- 파일 순서 체크 트리거
DELIMITER //
CREATE TRIGGER trg_file_order_check_insert BEFORE INSERT ON file
FOR EACH ROW
BEGIN
    IF NEW.file_order < 0 THEN
        SIGNAL SQLSTATE '45000'
        SET MESSAGE_TEXT = 'file_order must be greater than or equal to 0';
    END IF;
END
DELIMITER ;

-- 파일 순서 체크 트리거 (업데이트)
DELIMITER //
CREATE TRIGGER trg_file_order_check_update BEFORE UPDATE ON file
FOR EACH ROW
BEGIN
    IF NEW.file_order < 0 THEN
        SIGNAL SQLSTATE '45000'
        SET MESSAGE_TEXT = 'file_order must be greater than or equal to 0';
    END IF;
END;
DELIMITER ; 
CREATE INDEX idx_file_menu ON file (menu, menu_id);
CREATE INDEX idx_file_order ON file (menu, menu_id, file_order);
CREATE INDEX idx_file_public_menu ON file (menu, menu_id, public_yn); 

-- 파일 더미 데이터 삽입
INSERT INTO file (menu, menu_id, origin_name, saved_name, mime_type, size, ext, version, public_yn, file_order, created_by, created_ip)
VALUES 
-- CONTENT 모듈 파일들
('CONTENT', 1, 'main_banner.jpg', 'content_1_main_banner_20240504.jpg', 'image/jpeg', 1024000, 'jpg', 1, 'Y', 1, 'admin', '127.0.0.1'),
('CONTENT', 1, 'sub_banner.png', 'content_1_sub_banner_20240504.png', 'image/png', 512000, 'png', 1, 'Y', 2, 'admin', '127.0.0.1'),
('CONTENT', 2, 'notice.pdf', 'content_2_notice_20240504.pdf', 'application/pdf', 2048000, 'pdf', 1, 'Y', 1, 'admin', '127.0.0.1'),
('CONTENT', 3, 'guide.docx', 'content_3_guide_20240504.docx', 'application/vnd.openxmlformats-officedocument.wordprocessingml.document', 1536000, 'docx', 1, 'N', 1, 'admin', '127.0.0.1'),
-- BBS 모듈 파일들
('BBS', 1, 'board_image.jpg', 'bbs_1_board_image_20240504.jpg', 'image/jpeg', 768000, 'jpg', 1, 'Y', 1, 'admin', '127.0.0.1'),
('BBS', 1, 'attachment.zip', 'bbs_1_attachment_20240504.zip', 'application/zip', 3072000, 'zip', 1, 'Y', 2, 'admin', '127.0.0.1'),
('BBS', 2, 'notice.pdf', 'bbs_2_notice_20240504.pdf', 'application/pdf', 1024000, 'pdf', 1, 'Y', 1, 'admin', '127.0.0.1'),
-- POPUP 모듈 파일들
('POPUP', 1, 'event_banner.jpg', 'popup_1_event_banner_20240504.jpg', 'image/jpeg', 512000, 'jpg', 1, 'Y', 1, 'admin', '127.0.0.1'),
('POPUP', 2, 'promotion.png', 'popup_2_promotion_20240504.png', 'image/png', 768000, 'png', 1, 'Y', 1, 'admin', '127.0.0.1'),
-- PROGRAM 모듈 파일들
('PROGRAM', 1, 'program_guide.pdf', 'program_1_guide_20240504.pdf', 'application/pdf', 1536000, 'pdf', 1, 'Y', 1, 'admin', '127.0.0.1'),
('PROGRAM', 1, 'sample_code.zip', 'program_1_sample_20240504.zip', 'application/zip', 2048000, 'zip', 1, 'N', 2, 'admin', '127.0.0.1'),
('PROGRAM', 2, 'manual.docx', 'program_2_manual_20240504.docx', 'application/vnd.openxmlformats-officedocument.wordprocessingml.document', 1024000, 'docx', 1, 'Y', 1, 'admin', '127.0.0.1'); 