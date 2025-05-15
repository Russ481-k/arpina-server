-- 메뉴 타입에 PROGRAM 추가
ALTER TABLE menu 
MODIFY COLUMN type ENUM('LINK','FOLDER','BOARD','CONTENT','PROGRAM') NOT NULL COMMENT '메뉴 타입';

-- target_id 컬럼 설명 업데이트
ALTER TABLE menu 
MODIFY COLUMN target_id BIGINT UNSIGNED COMMENT '연결 대상 ID (BOARD/CONTENT/PROGRAM 타입일 때 필수)';

-- 기존 메뉴 데이터에 PROGRAM 타입이 있는지 확인
SELECT COUNT(*) FROM menu WHERE type = 'PROGRAM'; 