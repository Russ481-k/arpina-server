-- 게시판 마스터
CREATE TABLE bbs_master (
  bbs_id        BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT COMMENT 'PK: 게시판 ID',
  bbs_name      VARCHAR(100) NOT NULL COMMENT '게시판 이름',
  skin_type     ENUM('BASIC','FAQ','QNA','PRESS') NOT NULL COMMENT '게시판 스킨 유형',
  read_auth     VARCHAR(50) DEFAULT 'PUBLIC' COMMENT '읽기 권한 코드',
  write_auth    VARCHAR(50) DEFAULT 'ROLE_STAFF' COMMENT '쓰기 권한 코드',
  admin_auth    VARCHAR(50) DEFAULT 'ROLE_ADMIN' COMMENT '관리 권한 코드',
  display_yn    TINYINT(1) DEFAULT 1 COMMENT '노출 여부(1=YES)',
  sort_order    INT DEFAULT 0 COMMENT '게시판 정렬 순서',
  attachment_limit INT DEFAULT 0 COMMENT '첨부파일 최대 개수',
  attachment_size  INT DEFAULT 0 COMMENT '첨부파일 최대 용량(MB)',
  extra_schema  JSON NULL COMMENT '추가 필드 정의(JSON Schema)',
  created_by VARCHAR(36),
  created_ip VARCHAR(45),
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  updated_by VARCHAR(36),
  updated_ip VARCHAR(45),
  updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='게시판 설정';

-- 카테고리
CREATE TABLE bbs_category (
  category_id BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT COMMENT 'PK: 카테고리 ID',
  bbs_id      BIGINT UNSIGNED NOT NULL COMMENT 'FK: 게시판 ID',
  code        VARCHAR(50) NOT NULL COMMENT '카테고리 코드',
  name        VARCHAR(100) NOT NULL COMMENT '카테고리 명',
  sort_order  INT DEFAULT 0 COMMENT '카테고리 정렬 순서',
  display_yn  TINYINT(1) DEFAULT 1 COMMENT '노출 여부',
  created_by VARCHAR(36),
  created_ip VARCHAR(45),
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  updated_by VARCHAR(36),
  updated_ip VARCHAR(45),
  updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  FOREIGN KEY (bbs_id) REFERENCES bbs_master(bbs_id) ON DELETE CASCADE,
  UNIQUE KEY uk_bbs_category_code (bbs_id, code),
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='게시판별 카테고리';

-- 게시글
CREATE TABLE bbs_article (
  ntt_id            BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT COMMENT 'PK: 게시글 ID',
  bbs_id            BIGINT UNSIGNED NOT NULL COMMENT 'FK: 게시판 ID',
  parent_ntt_id     BIGINT UNSIGNED NULL COMMENT '부모 글 ID(답변형)',
  thread_depth      INT UNSIGNED DEFAULT 0 COMMENT '답변 깊이',
  writer            VARCHAR(50) NOT NULL COMMENT '작성자',
  title             VARCHAR(255) NOT NULL COMMENT '제목',
  content      MEDIUMTEXT COMMENT '내용(HTML)',
  notice_yn         TINYINT(1) DEFAULT 0 COMMENT '공지 여부',
  publish_start_dt  DATETIME NULL COMMENT '게시 시작일',
  publish_end_dt    DATETIME NULL COMMENT '게시 종료일',
  external_link     VARCHAR(255) COMMENT '외부 링크 URL',
  hits              INT DEFAULT 0 COMMENT '조회수',
  deleted_yn        TINYINT(1) DEFAULT 0 COMMENT '삭제 여부(소프트 딜리트)',
  created_by VARCHAR(36),
  created_ip VARCHAR(45),
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  updated_by VARCHAR(36),
  updated_ip VARCHAR(45),
  updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  FOREIGN KEY (bbs_id) REFERENCES bbs_master(bbs_id) ON DELETE CASCADE,
  FOREIGN KEY (parent_ntt_id) REFERENCES bbs_article(ntt_id) ON DELETE SET NULL,
  INDEX idx_article_bbs (bbs_id, created_at),
  INDEX idx_article_parent (parent_ntt_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='게시판 게시글';

-- 첨부파일
CREATE TABLE file (
  file_id        BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT COMMENT 'PK: 첨부파일 ID',
  ntt_id         BIGINT UNSIGNED NOT NULL COMMENT 'FK: 게시글 ID',
  origin_name    VARCHAR(255) NOT NULL COMMENT '원본 파일명',
  stored_name    VARCHAR(255) NOT NULL COMMENT '저장 파일명(중복방지)',
  file_size      BIGINT UNSIGNED NOT NULL COMMENT '파일 크기(Byte)',
  download_count INT UNSIGNED DEFAULT 0 COMMENT '다운로드 횟수',
  created_by VARCHAR(36),
  created_ip VARCHAR(45),
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  updated_by VARCHAR(36),
  updated_ip VARCHAR(45),
  updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  FOREIGN KEY (ntt_id) REFERENCES bbs_article(ntt_id) ON DELETE CASCADE,
  INDEX idx_attach_ntt (ntt_id)

) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='게시글 첨부파일';
