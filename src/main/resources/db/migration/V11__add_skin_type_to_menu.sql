-- 메뉴 테이블에 skin_type 컬럼 추가
ALTER TABLE menu 
ADD COLUMN skin_type VARCHAR(50) COMMENT '메뉴 스킨 타입 (CONTENT, BOARD, PROGRAM 등)';

-- 기존 메뉴 데이터의 skin_type 설정
UPDATE menu 
SET skin_type = CASE 
    WHEN type = 'CONTENT' THEN 'DEFAULT'
    WHEN type = 'BOARD' THEN 'DEFAULT'
    WHEN type = 'PROGRAM' THEN 'DEFAULT'
    ELSE NULL 
END; 