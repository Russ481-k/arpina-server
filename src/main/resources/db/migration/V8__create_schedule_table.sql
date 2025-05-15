-- 일정 테이블 생성
CREATE TABLE schedule (
    schedule_id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT 'PK: 일정 ID',
    title VARCHAR(255) NOT NULL COMMENT '일정 제목',
    content TEXT NULL COMMENT '일정 내용',
    start_date_time DATETIME NOT NULL COMMENT '시작 일시',
    end_date_time DATETIME NOT NULL COMMENT '종료 일시',
    display_yn VARCHAR(1) DEFAULT 'Y' COMMENT '노출 여부 (Y: 노출, N: 숨김)',
    -- 감사(Audit) 필드
    created_by VARCHAR(36) NULL COMMENT '생성자 ID',
    created_ip VARCHAR(45) NULL COMMENT '생성자 IP',
    created_date DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '생성 일시',
    updated_by VARCHAR(36) NULL COMMENT '수정자 ID',
    updated_ip VARCHAR(45) NULL COMMENT '수정자 IP',
    updated_date DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '수정 일시',
    -- 제약조건
    UNIQUE KEY uk_schedule_unique (title, start_date_time) COMMENT '동일한 제목과 시작 시간의 일정 중복 방지'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='일정';

-- 인덱스 생성
CREATE INDEX idx_schedule_display ON schedule(display_yn) COMMENT '노출 여부 인덱스';
CREATE INDEX idx_schedule_time_range ON schedule(start_date_time, end_date_time) COMMENT '시간 범위 검색 인덱스';

-- 트리거 생성 (시간 제약조건 검증)
DELIMITER //
CREATE TRIGGER trg_schedule_time_check
BEFORE INSERT ON schedule
FOR EACH ROW
BEGIN
    IF NEW.start_date_time >= NEW.end_date_time THEN
        SIGNAL SQLSTATE '45000'
        SET MESSAGE_TEXT = '시작 시간은 종료 시간보다 빨라야 합니다.';
    END IF;
END//

CREATE TRIGGER trg_schedule_time_check_update
BEFORE UPDATE ON schedule
FOR EACH ROW
BEGIN
    IF NEW.start_date_time >= NEW.end_date_time THEN
        SIGNAL SQLSTATE '45000'
        SET MESSAGE_TEXT = '시작 시간은 종료 시간보다 빨라야 합니다.';
    END IF;
END//

-- 트리거 생성 (display_yn 값 검증)
CREATE TRIGGER trg_schedule_display_yn_check
BEFORE INSERT ON schedule
FOR EACH ROW
BEGIN
    IF NEW.display_yn NOT IN ('Y', 'N') THEN
        SIGNAL SQLSTATE '45000'
        SET MESSAGE_TEXT = 'display_yn 값은 Y 또는 N이어야 합니다.';
    END IF;
END//

CREATE TRIGGER trg_schedule_display_yn_check_update
BEFORE UPDATE ON schedule
FOR EACH ROW
BEGIN
    IF NEW.display_yn NOT IN ('Y', 'N') THEN
        SIGNAL SQLSTATE '45000'
        SET MESSAGE_TEXT = 'display_yn 값은 Y 또는 N이어야 합니다.';
    END IF;
END//
DELIMITER ; 