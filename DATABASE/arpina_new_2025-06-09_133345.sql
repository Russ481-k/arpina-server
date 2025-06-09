CREATE TABLE `bbs_article` (
    `NTT_ID` int(10) unsigned NOT NULL AUTO_INCREMENT COMMENT 'PK: 게시글 ID',
    `BBS_ID` int(10) unsigned NOT NULL COMMENT 'FK: 게시판 ID',
    `MENU_ID` int(11) NOT NULL,
    `PARENT_NTT_ID` int(10) unsigned DEFAULT NULL COMMENT '부모 글 ID(답변형)',
    `THREAD_DEPTH` int(10) unsigned DEFAULT 0 COMMENT '답변 깊이',
    `WRITER` varchar(50) NOT NULL COMMENT '작성자',
    `TITLE` varchar(255) NOT NULL COMMENT '제목',
    `content` text DEFAULT NULL COMMENT '내용',
    `NOTICE_STATE` varchar(1) DEFAULT 'N' COMMENT '공지 여부(Y=공지,N=미공지,P=영구공지)',
    `NOTICE_START_DT` datetime DEFAULT curdate() COMMENT '공지 시작일',
    `NOTICE_END_DT` datetime DEFAULT(curdate() + interval 1 day) COMMENT '공지 종료일',
    `PUBLISH_STATE` varchar(1) DEFAULT 'N' COMMENT '게시 여부(Y=게시,N=미게시,P=영구게시)',
    `PUBLISH_START_DT` datetime DEFAULT curdate() COMMENT '게시 시작일',
    `PUBLISH_END_DT` datetime DEFAULT(curdate() + interval 1 day) COMMENT '게시 종료일',
    `EXTERNAL_LINK` varchar(255) DEFAULT NULL COMMENT '외부 링크 URL',
    `HITS` int(11) DEFAULT 0 COMMENT '조회수',
    `CREATED_BY` varchar(36) DEFAULT NULL COMMENT '생성자 ID',
    `CREATED_IP` varchar(45) DEFAULT NULL COMMENT '생성자 IP',
    `CREATED_AT` timestamp NOT NULL DEFAULT current_timestamp() COMMENT '생성 시각',
    `UPDATED_BY` varchar(36) DEFAULT NULL COMMENT '수정자 ID',
    `UPDATED_IP` varchar(45) DEFAULT NULL COMMENT '수정자 IP',
    `UPDATED_AT` timestamp NOT NULL DEFAULT current_timestamp() ON UPDATE current_timestamp() COMMENT '수정 시각',
    `has_image_in_content` tinyint(1) DEFAULT 0 COMMENT '본문 내 이미지 포함 여부',
    PRIMARY KEY (`NTT_ID`),
    KEY `fk_bbs_article_master` (`BBS_ID`),
    KEY `fk_bbs_article_parent` (`PARENT_NTT_ID`),
    KEY `idx_article_search` (`TITLE`, `content` (255)),
    KEY `fk_bbs_article_menu` (`MENU_ID`),
    CONSTRAINT `fk_bbs_article_master` FOREIGN KEY (`BBS_ID`) REFERENCES `bbs_master` (`BBS_ID`) ON DELETE CASCADE,
    CONSTRAINT `fk_bbs_article_menu` FOREIGN KEY (`MENU_ID`) REFERENCES `menu` (`id`) ON DELETE CASCADE,
    CONSTRAINT `fk_bbs_article_parent` FOREIGN KEY (`PARENT_NTT_ID`) REFERENCES `bbs_article` (`NTT_ID`) ON DELETE SET NULL
) ENGINE = InnoDB AUTO_INCREMENT = 142 DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '게시판 게시글';

CREATE TABLE `bbs_article_category` (
    `NTT_ID` int(20) unsigned NOT NULL COMMENT 'FK: 게시글 ID',
    `CATEGORY_ID` int(20) unsigned NOT NULL COMMENT 'FK: 카테고리 ID',
    PRIMARY KEY (`NTT_ID`, `CATEGORY_ID`),
    KEY `IDX_ARTICLE_CATEGORY` (`CATEGORY_ID`, `NTT_ID`),
    CONSTRAINT `bbs_article_category_ibfk_1` FOREIGN KEY (`NTT_ID`) REFERENCES `bbs_article` (`NTT_ID`) ON DELETE CASCADE,
    CONSTRAINT `bbs_article_category_ibfk_2` FOREIGN KEY (`CATEGORY_ID`) REFERENCES `bbs_category` (`CATEGORY_ID`) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '게시글-카테고리 \r\n매핑';

CREATE TABLE `bbs_category` (
    `CATEGORY_ID` int(20) unsigned NOT NULL AUTO_INCREMENT,
    `BBS_ID` int(20) unsigned NOT NULL COMMENT 'FK: 게시판 ID',
    `CODE` varchar(50) NOT NULL COMMENT '카테고리 코드',
    `NAME` varchar(100) NOT NULL COMMENT '카테고리 이름',
    `SORT_ORDER` int(11) DEFAULT 0 COMMENT '카테고리 정렬 순서',
    `DISPLAY_YN` varchar(1) DEFAULT 'Y' COMMENT '노출 여부(Y,N)',
    `CREATED_BY` varchar(36) DEFAULT NULL COMMENT '생성자 ID',
    `CREATED_IP` varchar(45) DEFAULT NULL COMMENT '생성자 IP',
    `CREATED_AT` timestamp NOT NULL DEFAULT current_timestamp() COMMENT '생성 시각',
    `UPDATED_BY` varchar(36) DEFAULT NULL COMMENT '수정자 ID',
    `UPDATED_IP` varchar(45) DEFAULT NULL COMMENT '수정자 IP',
    `UPDATED_AT` timestamp NOT NULL DEFAULT current_timestamp() ON UPDATE current_timestamp() COMMENT '수정 시각',
    PRIMARY KEY (`CATEGORY_ID`),
    UNIQUE KEY `UK_BBS_CATEGORY_CODE` (`BBS_ID`, `CODE`),
    KEY `IDX_CATEGORY_SORT` (
        `BBS_ID`,
        `SORT_ORDER`,
        `DISPLAY_YN`
    ),
    CONSTRAINT `bbs_category_ibfk_1` FOREIGN KEY (`BBS_ID`) REFERENCES `bbs_master` (`BBS_ID`) ON DELETE CASCADE
) ENGINE = InnoDB AUTO_INCREMENT = 4 DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '게시판별 카테고리';

CREATE TABLE `bbs_master` (
    `BBS_ID` int(10) unsigned NOT NULL AUTO_INCREMENT COMMENT 'PK: 게시판 ID',
    `BBS_NAME` varchar(100) NOT NULL COMMENT '게시판 이름',
    `SKIN_TYPE` enum(
        'BASIC',
        'FAQ',
        'QNA',
        'PRESS',
        'FORM'
    ) NOT NULL COMMENT '게시판 스킨 유형',
    `READ_AUTH` varchar(50) NOT NULL COMMENT '읽기 권한 코드',
    `WRITE_AUTH` varchar(50) NOT NULL COMMENT '쓰기 권한 코드',
    `ADMIN_AUTH` varchar(50) NOT NULL COMMENT '관리 권한 코드',
    `DISPLAY_YN` varchar(1) DEFAULT 'Y' COMMENT '노출 여부',
    `SORT_ORDER` varchar(1) DEFAULT 'D' COMMENT '게시판 정렬 순서(A=오름차순,D=내림차순)',
    `NOTICE_YN` varchar(1) DEFAULT 'N' COMMENT '공지 여부(Y=공지,N=미공지)',
    `PUBLISH_YN` varchar(1) DEFAULT 'N' COMMENT '게시 여부(Y=게시,N=미게시)',
    `ATTACHMENT_YN` varchar(1) DEFAULT 'N' COMMENT '첨부파일 기능 사용여부',
    `ATTACHMENT_LIMIT` int(11) DEFAULT 0 COMMENT '첨부파일 최대 개수',
    `ATTACHMENT_SIZE` int(11) DEFAULT 0 COMMENT '첨부파일 최대 용량(MB)',
    `CREATED_BY` varchar(36) DEFAULT NULL COMMENT '생성자 ID',
    `CREATED_IP` varchar(45) DEFAULT NULL COMMENT '생성자 IP',
    `CREATED_AT` timestamp NOT NULL DEFAULT current_timestamp() COMMENT '생성 시각',
    `UPDATED_BY` varchar(36) DEFAULT NULL COMMENT '수정자 ID',
    `UPDATED_IP` varchar(45) DEFAULT NULL COMMENT '수정자 IP',
    `UPDATED_AT` timestamp NOT NULL DEFAULT current_timestamp() ON UPDATE current_timestamp() COMMENT '수정 시각',
    PRIMARY KEY (`BBS_ID`)
) ENGINE = InnoDB AUTO_INCREMENT = 64 DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '게시판 설정';

CREATE TABLE `enroll` (
    `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '수강신청 ID (PK)',
    `user_uuid` varchar(36) NOT NULL COMMENT '사용자 UUID (FK)',
    `lesson_id` bigint(20) NOT NULL COMMENT '강좌 ID',
    `status` varchar(50) NOT NULL COMMENT '신청 상태 (APPLIED, CANCELED, PENDING)',
    `expire_dt` datetime NOT NULL COMMENT '결제 페이지 접근 및 결제 시도 만료 시간 (신청 시점 + 5분)',
    `renewal_flag` tinyint(1) DEFAULT 0 COMMENT '재수강 여부 (0: 아니오, 1: 예)',
    `uses_locker` tinyint(1) NOT NULL DEFAULT 0 COMMENT '라커 사용 여부 (0: 미사용, 1: 사용)',
    `cancel_status` varchar(20) DEFAULT 'NONE' COMMENT '취소 상태 (NONE, REQ, PENDING, APPROVED, DENIED)',
    `cancel_approved_at` timestamp NULL DEFAULT NULL COMMENT '취소 승인 일시',
    `cancel_processed_at` datetime DEFAULT NULL COMMENT '관리자 취소 처리 완료 시각',
    `cancel_reason` varchar(255) DEFAULT NULL COMMENT '취소 사유 (사용자 요청 사유 또는 관리자 취소 시 상세 사유)',
    `admin_cancel_comment` varchar(255) DEFAULT NULL COMMENT '관리자 직접 취소 시 별도 기록용 코멘트',
    `refund_amount` int(11) DEFAULT NULL COMMENT '환불 금액',
    `original_pay_status_before_cancel` varchar(50) DEFAULT NULL COMMENT '취소 전 원래 결제 상태',
    `pay_status` varchar(50) NOT NULL DEFAULT 'UNPAID' COMMENT '결제 상태 (UNPAID, PAID, REFUND_PENDING_ADMIN_CANCEL 등)',
    `locker_allocated` tinyint(1) NOT NULL DEFAULT 0 COMMENT '사물함 실제 할당 여부',
    `locker_pg_token` varchar(100) DEFAULT NULL COMMENT '사물함 배정 관련 PG토큰',
    `membership_type` varchar(50) NOT NULL DEFAULT 'GENERAL',
    `final_amount` int(11) DEFAULT NULL,
    `discount_applied_percentage` int(11) DEFAULT NULL,
    `remain_days` int(11) DEFAULT NULL COMMENT '취소 시 계산된 잔여일수 (감사용)',
    `CREATED_BY` varchar(36) DEFAULT NULL COMMENT '생성자 ID',
    `CREATED_IP` varchar(45) DEFAULT NULL COMMENT '생성자 IP',
    `CREATED_AT` timestamp NOT NULL DEFAULT current_timestamp() COMMENT '생성 시각',
    `UPDATED_BY` varchar(36) DEFAULT NULL COMMENT '수정자 ID',
    `UPDATED_IP` varchar(45) DEFAULT NULL COMMENT '수정자 IP',
    `UPDATED_AT` timestamp NOT NULL DEFAULT current_timestamp() ON UPDATE current_timestamp() COMMENT '수정 시각',
    `cancel_requested_at` datetime DEFAULT NULL COMMENT '취소 요청 시각',
    `discount_type` varchar(50) DEFAULT NULL COMMENT '할인 종류',
    `discount_status` varchar(20) DEFAULT NULL COMMENT '할인 상태 (PENDING, APPROVED, DENIED 등)',
    `discount_approved_at` datetime DEFAULT NULL COMMENT '할인 승인 시각',
    `discount_admin_comment` varchar(255) DEFAULT NULL COMMENT '할인 관련 관리자 코멘트',
    `days_used_for_refund` int(11) DEFAULT NULL COMMENT '환불 계산 시 사용된 실제 사용일수',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_user_lesson_active_status_pay` (
        `user_uuid`,
        `lesson_id`,
        `status`,
        `pay_status`
    ),
    KEY `fk_enroll_user_uuid` (`user_uuid`),
    KEY `idx_status_pay` (`status`, `pay_status`),
    KEY `idx_lesson_status_pay` (
        `lesson_id`,
        `status`,
        `pay_status`
    ),
    KEY `idx_expire_pay_status` (`expire_dt`, `pay_status`),
    KEY `idx_user_pay_status` (`user_uuid`, `pay_status`),
    KEY `idx_renewal` (`renewal_flag`),
    CONSTRAINT `fk_enroll_user_uuid` FOREIGN KEY (`user_uuid`) REFERENCES `user` (`uuid`) ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE = InnoDB AUTO_INCREMENT = 3218 DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '수강 신청 정보';

CREATE TABLE `file` (
    `file_id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT 'PK: 파일 ID',
    `menu` varchar(30) NOT NULL COMMENT '메뉴 코드 (BBS, POPUP 등)',
    `menu_id` bigint(20) NOT NULL COMMENT '메뉴별 리소스 ID',
    `origin_name` varchar(255) NOT NULL COMMENT '원본 파일명',
    `saved_name` varchar(255) NOT NULL COMMENT '저장된 파일명',
    `mime_type` varchar(100) NOT NULL COMMENT 'MIME 타입',
    `size` bigint(20) NOT NULL COMMENT '파일 크기(바이트)',
    `ext` varchar(20) NOT NULL COMMENT '파일 확장자',
    `version` int(11) DEFAULT 1 COMMENT '파일 버전',
    `public_yn` varchar(1) DEFAULT 'Y' COMMENT '공개 여부 (Y/N)',
    `file_order` int(11) DEFAULT 0 COMMENT '파일 순서',
    `created_by` varchar(36) DEFAULT NULL COMMENT '생성자 ID',
    `created_ip` varchar(45) DEFAULT NULL COMMENT '생성자 IP',
    `created_date` datetime DEFAULT current_timestamp() COMMENT '생성 일시',
    `updated_by` varchar(36) DEFAULT NULL COMMENT '수정자 ID',
    `updated_ip` varchar(45) DEFAULT NULL COMMENT '수정자 IP',
    `updated_date` datetime DEFAULT current_timestamp() ON UPDATE current_timestamp() COMMENT '수정 일시',
    PRIMARY KEY (`file_id`),
    UNIQUE KEY `uk_saved_name` (`saved_name`),
    KEY `idx_file_public` (`public_yn`),
    KEY `idx_file_menu` (`menu`, `menu_id`),
    KEY `idx_file_order` (
        `menu`,
        `menu_id`,
        `file_order`
    ),
    KEY `idx_file_public_menu` (
        `menu`,
        `menu_id`,
        `public_yn`
    ),
    CONSTRAINT `chk_public_yn` CHECK (`public_yn` in ('Y', 'N'))
) ENGINE = InnoDB AUTO_INCREMENT = 112 DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = 'CMS 파일';

DELIMITER;

DELIMITER;

CREATE TABLE `groups` (
    `uuid` varchar(36) NOT NULL,
    `name` varchar(100) NOT NULL,
    `description` varchar(255) DEFAULT NULL,
    `created_by` varchar(36) DEFAULT NULL,
    `created_ip` varchar(45) DEFAULT NULL,
    `created_at` timestamp NOT NULL DEFAULT current_timestamp(),
    `updated_by` varchar(36) DEFAULT NULL,
    `updated_ip` varchar(45) DEFAULT NULL,
    `updated_at` timestamp NOT NULL DEFAULT current_timestamp() ON UPDATE current_timestamp(),
    PRIMARY KEY (`uuid`),
    KEY `created_by` (`created_by`),
    KEY `updated_by` (`updated_by`),
    CONSTRAINT `groups_ibfk_1` FOREIGN KEY (`created_by`) REFERENCES `user` (`uuid`),
    CONSTRAINT `groups_ibfk_2` FOREIGN KEY (`updated_by`) REFERENCES `user` (`uuid`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_general_ci;

CREATE TABLE `lesson` (
    `lesson_id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '강습 ID (PK)',
    `title` varchar(100) NOT NULL COMMENT '강습명(예: 초급반, 중급반 등)',
    `display_name` varchar(150) DEFAULT NULL COMMENT '강습 표시명 (예: 힐링수영반)',
    `start_date` date NOT NULL COMMENT '강습 시작일',
    `end_date` date NOT NULL COMMENT '강습 종료일',
    `lesson_year` int(11) GENERATED ALWAYS AS (year(`start_date`)) VIRTUAL COMMENT '강습 연도',
    `lesson_month` int(11) GENERATED ALWAYS AS (month(`start_date`)) VIRTUAL COMMENT '강습 월',
    `capacity` int(11) NOT NULL COMMENT '총 정원 수',
    `price` int(11) NOT NULL COMMENT '강습 비용(원)',
    `instructor_name` varchar(50) DEFAULT NULL COMMENT '강사명',
    `lesson_time` varchar(100) DEFAULT NULL COMMENT '수업 시간 (예: 09:00-09:50 (월수금))',
    `location_name` varchar(100) DEFAULT NULL COMMENT '교육 장소 (예: 아르피나 수영장)',
    `registration_start_datetime` datetime DEFAULT NULL COMMENT '접수 시작 일시',
    `registration_end_datetime` datetime NOT NULL COMMENT '접수 종료 일시',
    `created_at` datetime NOT NULL DEFAULT current_timestamp() COMMENT '등록일시',
    `updated_at` datetime NOT NULL DEFAULT current_timestamp() ON UPDATE current_timestamp() COMMENT '수정일시',
    `created_by` varchar(50) DEFAULT NULL COMMENT '등록자',
    `created_ip` varchar(45) DEFAULT NULL COMMENT '등록 IP',
    `updated_by` varchar(50) DEFAULT NULL COMMENT '수정자',
    `updated_ip` varchar(45) DEFAULT NULL COMMENT '수정 IP',
    PRIMARY KEY (`lesson_id`),
    KEY `idx_date` (`start_date`, `end_date`),
    KEY `idx_year_month` (`lesson_year`, `lesson_month`) COMMENT '연도/월별 조회용 인덱스'
) ENGINE = InnoDB AUTO_INCREMENT = 26 DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '수영 강습 정보 테이블';

CREATE TABLE `locker_inventory` (
    `gender` varchar(10) NOT NULL COMMENT '성별 (MALE, FEMALE) - PK',
    `total_quantity` int(11) NOT NULL DEFAULT 0 COMMENT '총 라커 수',
    `used_quantity` int(11) NOT NULL DEFAULT 0 COMMENT '현재 사용 중인 라커 수',
    `CREATED_BY` varchar(36) DEFAULT NULL COMMENT '생성자 ID',
    `CREATED_IP` varchar(45) DEFAULT NULL COMMENT '생성자 IP',
    `CREATED_AT` timestamp NOT NULL DEFAULT current_timestamp() COMMENT '생성 시각',
    `UPDATED_BY` varchar(36) DEFAULT NULL COMMENT '수정자 ID',
    `UPDATED_IP` varchar(45) DEFAULT NULL COMMENT '수정자 IP',
    `UPDATED_AT` timestamp NOT NULL DEFAULT current_timestamp() ON UPDATE current_timestamp() COMMENT '수정 시각',
    PRIMARY KEY (`gender`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '성별 라커 재고 정보';

CREATE TABLE `map_article` (
    `old_board_seq` int(10) unsigned NOT NULL,
    `new_ntt_id` int(10) unsigned NOT NULL,
    `new_bbs_id` int(10) unsigned NOT NULL,
    `old_parent_seq` int(10) unsigned DEFAULT NULL,
    `new_parent_ntt` int(10) unsigned DEFAULT NULL,
    `migrated_at` timestamp NOT NULL DEFAULT current_timestamp(),
    PRIMARY KEY (`old_board_seq`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_general_ci;

CREATE TABLE `map_bbs` (
    `old_mgr_seq` int(10) unsigned NOT NULL,
    `new_bbs_id` int(10) unsigned NOT NULL,
    `board_name` varchar(500) DEFAULT NULL,
    `migrated_at` timestamp NOT NULL DEFAULT current_timestamp(),
    PRIMARY KEY (`old_mgr_seq`),
    UNIQUE KEY `uk_new_bbs` (`new_bbs_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_general_ci;

CREATE TABLE `map_category` (
    `old_mgr_seq` int(10) unsigned NOT NULL,
    `old_code` varchar(50) NOT NULL,
    `new_category_id` int(10) unsigned NOT NULL,
    `new_bbs_id` int(10) unsigned NOT NULL,
    `migrated_at` timestamp NOT NULL DEFAULT current_timestamp(),
    PRIMARY KEY (`old_mgr_seq`, `old_code`),
    KEY `idx_new_cat` (`new_category_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_general_ci;

CREATE TABLE `menu` (
    `id` int(11) NOT NULL AUTO_INCREMENT,
    `name` varchar(100) NOT NULL,
    `type` enum(
        'LINK',
        'FOLDER',
        'BOARD',
        'CONTENT',
        'PROGRAM'
    ) NOT NULL COMMENT '메뉴 타입',
    `url` varchar(255) DEFAULT NULL,
    `target_id` bigint(20) unsigned DEFAULT NULL COMMENT '연결 대상 ID (BOARD/CONTENT/PROGRAM 타입일 때 필수)',
    `display_position` varchar(50) NOT NULL,
    `visible` tinyint(1) DEFAULT 1,
    `sort_order` int(11) NOT NULL,
    `parent_id` int(11) DEFAULT NULL,
    `created_by` varchar(36) DEFAULT NULL,
    `created_ip` varchar(45) DEFAULT NULL,
    `created_at` timestamp NOT NULL DEFAULT current_timestamp(),
    `updated_by` varchar(36) DEFAULT NULL,
    `updated_ip` varchar(45) DEFAULT NULL,
    `updated_at` timestamp NOT NULL DEFAULT current_timestamp() ON UPDATE current_timestamp(),
    PRIMARY KEY (`id`),
    KEY `parent_id` (`parent_id`),
    KEY `created_by` (`created_by`),
    KEY `updated_by` (`updated_by`),
    CONSTRAINT `menu_ibfk_1` FOREIGN KEY (`parent_id`) REFERENCES `menu` (`id`) ON DELETE SET NULL,
    CONSTRAINT `menu_ibfk_2` FOREIGN KEY (`created_by`) REFERENCES `user` (`uuid`),
    CONSTRAINT `menu_ibfk_3` FOREIGN KEY (`updated_by`) REFERENCES `user` (`uuid`)
) ENGINE = InnoDB AUTO_INCREMENT = 91 DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_general_ci;

CREATE TABLE `organizations` (
    `uuid` varchar(36) NOT NULL,
    `name` varchar(100) NOT NULL,
    `description` varchar(255) DEFAULT NULL,
    `created_by` varchar(36) DEFAULT NULL,
    `created_ip` varchar(45) DEFAULT NULL,
    `created_at` timestamp NOT NULL DEFAULT current_timestamp(),
    `updated_by` varchar(36) DEFAULT NULL,
    `updated_ip` varchar(45) DEFAULT NULL,
    `updated_at` timestamp NOT NULL DEFAULT current_timestamp() ON UPDATE current_timestamp(),
    PRIMARY KEY (`uuid`),
    KEY `created_by` (`created_by`),
    KEY `updated_by` (`updated_by`),
    CONSTRAINT `organizations_ibfk_1` FOREIGN KEY (`created_by`) REFERENCES `user` (`uuid`),
    CONSTRAINT `organizations_ibfk_2` FOREIGN KEY (`updated_by`) REFERENCES `user` (`uuid`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_general_ci;

CREATE TABLE `payment` (
    `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '결제 ID (PK)',
    `enroll_id` bigint(20) NOT NULL COMMENT '수강신청 ID (FK)',
    `status` varchar(50) NOT NULL COMMENT '결제 상태 (PAID, FAILED, CANCELED, PARTIAL_REFUNDED, REFUND_REQUESTED)',
    `paid_at` timestamp NULL DEFAULT NULL COMMENT '결제 일시',
    `moid` varchar(255) DEFAULT NULL COMMENT 'KISPG 주문번호 (temp_*, enroll_* 형식)',
    `tid` varchar(100) DEFAULT NULL COMMENT 'KISPG 거래 ID',
    `paid_amt` int(11) DEFAULT NULL COMMENT '실제 KISPG 확인 금액',
    `lesson_amount` int(11) DEFAULT NULL COMMENT '강습 결제 금액',
    `locker_amount` int(11) DEFAULT NULL COMMENT '사물함 결제 금액',
    `refunded_amt` int(11) DEFAULT 0 COMMENT '환불된 금액',
    `refund_dt` datetime DEFAULT NULL COMMENT '환불 일시',
    `pay_method` varchar(50) DEFAULT NULL COMMENT '결제 수단 (CARD, VBANK 등)',
    `pg_result_code` varchar(20) DEFAULT NULL COMMENT 'PG 결과 코드',
    `pg_result_msg` varchar(255) DEFAULT NULL COMMENT 'PG 결과 메시지',
    `CREATED_BY` varchar(36) DEFAULT NULL COMMENT '생성자 ID',
    `CREATED_IP` varchar(45) DEFAULT NULL COMMENT '생성자 IP',
    `CREATED_AT` timestamp NOT NULL DEFAULT current_timestamp() COMMENT '생성 시각',
    `UPDATED_BY` varchar(36) DEFAULT NULL COMMENT '수정자 ID',
    `UPDATED_IP` varchar(45) DEFAULT NULL COMMENT '수정자 IP',
    `UPDATED_AT` timestamp NOT NULL DEFAULT current_timestamp() ON UPDATE current_timestamp() COMMENT '수정 시각',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_payment_tid` (`tid`),
    KEY `idx_payment_moid` (`moid`),
    KEY `idx_payment_status` (`status`),
    KEY `fk_payment_enroll_id` (`enroll_id`),
    CONSTRAINT `fk_payment_enroll_id` FOREIGN KEY (`enroll_id`) REFERENCES `enroll` (`id`) ON UPDATE CASCADE
) ENGINE = InnoDB AUTO_INCREMENT = 768 DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '결제 정보 (KISPG 전용 최적화)';

CREATE TABLE `template` (
    `TEMPLATE_ID` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '템플릿 고유 식별자',
    `TEMPLATE_NM` varchar(100) NOT NULL COMMENT '템플릿 이름',
    `type` enum('MAIN', 'SUB', 'NORMAL') NOT NULL DEFAULT 'NORMAL' COMMENT '템플릿 역할 (MAIN/SUB/NORMAL)',
    `IS_PUBLISHED` tinyint(1) NOT NULL DEFAULT 0 COMMENT '게시 여부',
    `VERSION_NO` int(11) NOT NULL DEFAULT 1 COMMENT '버전 번호',
    `CREATED_BY` varchar(36) DEFAULT NULL COMMENT '생성자 ID',
    `CREATED_IP` varchar(45) DEFAULT NULL COMMENT '생성 IP',
    `CREATED_AT` timestamp NOT NULL DEFAULT current_timestamp() COMMENT '생성 일시',
    `UPDATED_BY` varchar(36) DEFAULT NULL COMMENT '수정자 ID',
    `UPDATED_IP` varchar(45) DEFAULT NULL COMMENT '수정 IP',
    `UPDATED_AT` timestamp NOT NULL DEFAULT current_timestamp() ON UPDATE current_timestamp() COMMENT '수정 일시',
    `DELETED_YN` tinyint(1) NOT NULL DEFAULT 0 COMMENT '삭제 여부',
    `DESCRIPTION` varchar(500) DEFAULT NULL COMMENT '템플릿 설명',
    `LAYOUT_JSON` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT '레이아웃 JSON 데이터',
    PRIMARY KEY (`TEMPLATE_ID`),
    KEY `IDX_TEMPLATE_PUBLISHED` (`IS_PUBLISHED`) COMMENT '게시 상태 검색용 인덱스',
    KEY `IDX_TEMPLATE_ROLE` (`type`) COMMENT '템플릿 역할 검색용 인덱스'
) ENGINE = InnoDB AUTO_INCREMENT = 3 DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '템플릿 기본 정보 테이블';

CREATE TABLE `template_cell` (
    `CELL_ID` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '셀 고유 식별자',
    `ROW_ID` bigint(20) NOT NULL COMMENT '참조하는 행 ID',
    `ORDINAL` int(11) NOT NULL COMMENT '셀 순서',
    `SPAN_JSON` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '반응형 너비 설정 (base/md/lg/xl)' CHECK (json_valid(`SPAN_JSON`)),
    `CREATED_BY` varchar(36) DEFAULT NULL COMMENT '생성자 ID',
    `CREATED_IP` varchar(45) DEFAULT NULL COMMENT '생성 IP',
    `CREATED_AT` timestamp NOT NULL DEFAULT current_timestamp() COMMENT '생성 일시',
    `UPDATED_BY` varchar(36) DEFAULT NULL COMMENT '수정자 ID',
    `UPDATED_IP` varchar(45) DEFAULT NULL COMMENT '수정 IP',
    `UPDATED_AT` timestamp NOT NULL DEFAULT current_timestamp() ON UPDATE current_timestamp() COMMENT '수정 일시',
    PRIMARY KEY (`CELL_ID`),
    UNIQUE KEY `UK_CELL_ORD` (`ROW_ID`, `ORDINAL`),
    KEY `IDX_TEMPLATE_CELL_ROW` (`ROW_ID`) COMMENT '행별 셀 조회용 인덱스',
    CONSTRAINT `template_cell_ibfk_1` FOREIGN KEY (`ROW_ID`) REFERENCES `template_row` (`ROW_ID`) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '템플릿 셀 정보 테이블';

CREATE TABLE `template_row` (
    `ROW_ID` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '행 고유 식별자',
    `TEMPLATE_ID` bigint(20) NOT NULL COMMENT '참조하는 템플릿 ID',
    `ORDINAL` int(11) NOT NULL COMMENT '행 순서',
    `HEIGHT_PX` int(11) DEFAULT NULL COMMENT '행 높이(픽셀)',
    `BG_COLOR` varchar(20) DEFAULT NULL COMMENT '배경색',
    `CREATED_BY` varchar(36) DEFAULT NULL COMMENT '생성자 ID',
    `CREATED_IP` varchar(45) DEFAULT NULL COMMENT '생성 IP',
    `CREATED_AT` timestamp NOT NULL DEFAULT current_timestamp() COMMENT '생성 일시',
    `UPDATED_BY` varchar(36) DEFAULT NULL COMMENT '수정자 ID',
    `UPDATED_IP` varchar(45) DEFAULT NULL COMMENT '수정 IP',
    `UPDATED_AT` timestamp NOT NULL DEFAULT current_timestamp() ON UPDATE current_timestamp() COMMENT '수정 일시',
    PRIMARY KEY (`ROW_ID`),
    KEY `IDX_TEMPLATE_ROW_TEMPLATE` (`TEMPLATE_ID`) COMMENT '템플릿별 행 조회용 인덱스',
    CONSTRAINT `template_row_ibfk_1` FOREIGN KEY (`TEMPLATE_ID`) REFERENCES `template` (`TEMPLATE_ID`) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '템플릿 행 정보 테이블';

CREATE TABLE `template_type` (
    `CODE` varchar(20) NOT NULL COMMENT '템플릿 타입 코드',
    `NAME` varchar(50) NOT NULL COMMENT '템플릿 타입 이름',
    `DESCRIPTION` varchar(200) DEFAULT NULL COMMENT '템플릿 타입 설명',
    `CREATED_BY` varchar(36) DEFAULT NULL COMMENT '생성자 ID',
    `CREATED_IP` varchar(45) DEFAULT NULL COMMENT '생성 IP',
    `CREATED_AT` timestamp NOT NULL DEFAULT current_timestamp() COMMENT '생성 일시',
    `UPDATED_BY` varchar(36) DEFAULT NULL COMMENT '수정자 ID',
    `UPDATED_IP` varchar(45) DEFAULT NULL COMMENT '수정 IP',
    `UPDATED_AT` timestamp NOT NULL DEFAULT current_timestamp() ON UPDATE current_timestamp() COMMENT '수정 일시',
    PRIMARY KEY (`CODE`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '템플릿 타입 코드 테이블';

CREATE TABLE `template_version` (
    `VERSION_ID` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '버전 고유 식별자',
    `TEMPLATE_ID` bigint(20) NOT NULL COMMENT '참조하는 템플릿 ID',
    `VERSION_NO` int(11) NOT NULL COMMENT '버전 번호',
    `LAYOUT_JSON` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '해당 버전의 레이아웃 JSON 데이터' CHECK (json_valid(`LAYOUT_JSON`)),
    `UPDATER` varchar(50) NOT NULL COMMENT '수정자',
    `CREATED_BY` varchar(36) DEFAULT NULL COMMENT '생성자 ID',
    `CREATED_IP` varchar(45) DEFAULT NULL COMMENT '생성 IP',
    `CREATED_AT` timestamp NOT NULL DEFAULT current_timestamp() COMMENT '생성 일시',
    `UPDATED_BY` varchar(36) DEFAULT NULL COMMENT '수정자 ID',
    `UPDATED_IP` varchar(45) DEFAULT NULL COMMENT '수정 IP',
    `UPDATED_AT` timestamp NOT NULL DEFAULT current_timestamp() ON UPDATE current_timestamp() COMMENT '수정 일시',
    PRIMARY KEY (`VERSION_ID`),
    KEY `IDX_TEMPLATE_VERSION_TEMPLATE` (`TEMPLATE_ID`) COMMENT '템플릿별 버전 조회용 인덱스',
    CONSTRAINT `template_version_ibfk_1` FOREIGN KEY (`TEMPLATE_ID`) REFERENCES `template` (`TEMPLATE_ID`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '템플릿 버전 관리 테이블';

CREATE TABLE `user` (
    `uuid` varchar(36) NOT NULL,
    `username` varchar(50) NOT NULL,
    `name` varchar(100) NOT NULL,
    `email` varchar(100) NOT NULL,
    `password` varchar(255) NOT NULL,
    `role` varchar(20) NOT NULL,
    `avatar_url` varchar(255) DEFAULT NULL,
    `status` varchar(20) NOT NULL,
    `organization_id` varchar(36) DEFAULT NULL COMMENT '기관 ID (FK)',
    `group_id` varchar(36) DEFAULT NULL,
    `car_no` varchar(50) DEFAULT NULL COMMENT '차량번호',
    `temp_pw_flag` tinyint(1) DEFAULT 0 COMMENT '임시비밀번호여부 (0: 아니오, 1: 예)',
    `birth_date` varchar(8) DEFAULT NULL COMMENT '생년월일 (YYYYMMDD)',
    `di` varchar(255) DEFAULT NULL COMMENT '본인인증 DI (암호화 저장)',
    `provider` varchar(50) DEFAULT NULL,
    `phone` varchar(50) DEFAULT NULL COMMENT '전화번호',
    `address` varchar(255) DEFAULT NULL COMMENT '주소',
    `gender` varchar(10) DEFAULT NULL COMMENT '성별 (예: MALE, FEMALE)',
    `reset_token_expiry` timestamp NULL DEFAULT NULL COMMENT '비밀번호 재설정 토큰 만료 시간',
    `reset_token` varchar(255) DEFAULT NULL COMMENT '비밀번호 재설정 토큰',
    `is_temporary` tinyint(1) DEFAULT 0 COMMENT '임시 사용자 여부 (0: 아니오, 1: 예)',
    `created_by` varchar(36) DEFAULT NULL,
    `created_ip` varchar(45) DEFAULT NULL,
    `created_at` timestamp NOT NULL DEFAULT current_timestamp(),
    `updated_by` varchar(36) DEFAULT NULL,
    `updated_at` timestamp NOT NULL DEFAULT current_timestamp() ON UPDATE current_timestamp(),
    `updated_ip` varchar(45) DEFAULT 'NULL',
    `memo` text DEFAULT NULL COMMENT '관리자 메모 내용',
    `memo_updated_at` timestamp NULL DEFAULT NULL COMMENT '메모 최종 수정 일시',
    `memo_updated_by` varchar(36) DEFAULT NULL COMMENT '메모 최종 수정 관리자 UUID',
    PRIMARY KEY (`uuid`),
    UNIQUE KEY `username` (`username`),
    UNIQUE KEY `email` (`email`),
    KEY `created_by` (`created_by`),
    KEY `updated_by` (`updated_by`),
    KEY `fk_user_memo_updated_by` (`memo_updated_by`),
    CONSTRAINT `fk_user_memo_updated_by` FOREIGN KEY (`memo_updated_by`) REFERENCES `user` (`uuid`) ON DELETE SET NULL ON UPDATE CASCADE,
    CONSTRAINT `user_ibfk_1` FOREIGN KEY (`created_by`) REFERENCES `user` (`uuid`),
    CONSTRAINT `user_ibfk_2` FOREIGN KEY (`updated_by`) REFERENCES `user` (`uuid`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_general_ci;

CREATE TABLE `user_activity_log` (
    `uuid` varchar(36) NOT NULL,
    `user_uuid` varchar(36) NOT NULL,
    `group_id` varchar(36) DEFAULT NULL,
    `organization_id` varchar(36) NOT NULL,
    `activity_type` varchar(50) NOT NULL,
    `description` varchar(255) NOT NULL,
    `user_agent` varchar(255) DEFAULT NULL,
    `created_by` varchar(36) DEFAULT NULL,
    `created_ip` varchar(45) DEFAULT NULL,
    `created_at` timestamp NOT NULL DEFAULT current_timestamp(),
    `updated_by` varchar(36) DEFAULT NULL,
    `updated_ip` varchar(45) DEFAULT NULL,
    `updated_at` timestamp NOT NULL DEFAULT current_timestamp() ON UPDATE current_timestamp(),
    PRIMARY KEY (`uuid`),
    KEY `fk_user_activity_log_user` (`user_uuid`),
    KEY `fk_user_activity_log_group` (`group_id`),
    KEY `fk_user_activity_log_organization` (`organization_id`),
    KEY `fk_user_activity_log_created_by` (`created_by`),
    KEY `fk_user_activity_log_updated_by` (`updated_by`),
    CONSTRAINT `fk_user_activity_log_created_by` FOREIGN KEY (`created_by`) REFERENCES `user` (`uuid`) ON DELETE SET NULL,
    CONSTRAINT `fk_user_activity_log_group` FOREIGN KEY (`group_id`) REFERENCES `groups` (`uuid`) ON DELETE SET NULL,
    CONSTRAINT `fk_user_activity_log_organization` FOREIGN KEY (`organization_id`) REFERENCES `organizations` (`uuid`) ON DELETE CASCADE,
    CONSTRAINT `fk_user_activity_log_updated_by` FOREIGN KEY (`updated_by`) REFERENCES `user` (`uuid`) ON DELETE SET NULL,
    CONSTRAINT `fk_user_activity_log_user` FOREIGN KEY (`user_uuid`) REFERENCES `user` (`uuid`) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_general_ci;