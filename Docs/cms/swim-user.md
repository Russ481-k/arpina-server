- 🏊‍♀️ 수영장 **신청 & 신청내역 확인** — 사용자-측 개발문서
  _(Frontend SPA + REST API + PG 연동 기준)_

  ***

  ## 0. 문서 목표

  | 항목      | 내용                                                                                          |
  | --------- | --------------------------------------------------------------------------------------------- |
  | 범위      | **일반 회원**이 강습을 신청하고, 사물함을 선택‧결제‧취소‧재등록까지 처리하는 모든 온라인 흐름 |
  | 달성 지표 | ① 선착순(결제 완료 기준) ② 1 시간 내 결제 ③ 잔여 좌석·라커 **0 오류** ④ UX 이탈률 < 2 %       |

  ***

  ## 1. 용어·역할 (User Side)

  | 코드            | 설명                        |
  | --------------- | --------------------------- |
  | **USER**        | 일반 회원 (성인)            |
  | **JUNIOR_USER** | 미성년자 (온라인 신청 불가) |
  | **PG**          | 결제대행사 (아임포트 REST)  |
  | **ENROLL**      | 강습 신청 레코드 (통합)     |
  | **LOCKER**      | 사물함 레코드               |
  | **RENEWAL**     | 기존 수강생 재등록 프로세스 |

  ***

  ## 2. 주요 시나리오(Sequence)

  ```mermaid
  sequenceDiagram
      participant U as 사용자
      participant FE as Frontend
      participant API as REST API
      participant PG as PG
      Note over FE,API: 🔒 Tx / 잔여 Lock
      U->>FE: 강습 카드 선택
      FE->>API: POST /api/v1/swimming/enroll (lesson_id, locker_id?)
      API-->>FE: enroll_id + expire_dt(60m)
      FE->>U: 신청정보 확인 페이지
      U->>FE: [마이페이지에서 결제] 클릭
      FE->>API: GET /api/v1/swimming/enrolls/{id}
      FE->>PG: 결제창 호출 (amount)
      PG-->>FE: SUCCESS (pg_tid)
      FE->>API: POST /api/v1/swimming/pay (enroll_id, pg_tid)
      API-->>PG: 검증
      API-->>FE: 200 OK → 상태 PAID
      FE->>U: 결제 완료 화면

  ```

  ***

  ## 3. **화면 정의**

  | ID       | 화면                | 주요 UI 요소                                                                     | 전송 API                        |
  | -------- | ------------------- | -------------------------------------------------------------------------------- | ------------------------------- |
  | **P-01** | 강습 목록           | ① 필터 모달(상태, 월, 시간대)② 강습 카드 Grid - 버튼색: `신청(Y)/잔여없음(gray)` | GET /api/v1/swimming/lessons    |
  | **P-02** | 신청정보 확인       | 신청 요약 테이블✔ 사물함 선택 체크박스✔ "마이페이지에서 결제" CTA                | POST /api/v1/swimming/enroll    |
  | **P-03** | 마이페이지-신청내역 | ① 리스트(상태 Badge)② 1h 카운트다운(UNPAID만)③ 결제/취소/환불 버튼               | GET /api/v1/swimming/my-enrolls |
  | **P-04** | 결제처리            | PG 드롭인 UI (카드만)성공 이후 상태변경                                          | POST /api/v1/swimming/pay       |
  | **P-05** | 재등록 모달         | 이전 수강 강습 제안 + 사물함 유지 여부                                           | GET /api/v1/swimming/renewal    |

  > 모바일: P-01, P-03는 Masonry Grid → 1 열, 나머지 모달 UI 유지.

  ***

  ## 4. API 상세

  ### 4-1. 공통

  | 요소      | 값                                               |
  | --------- | ------------------------------------------------ |
  | 인증      | OAuth2 Bearer/JWT                                |
  | 응답 규격 | `code`(int) + `message` + `data`                 |
  | 오류코드  | 4001 잔여없음, 4002 만료, 4003 미성년, 500X 서버 |

  ### 4-2. 엔드포인트

  | Method | URL                                       | Req Body/QS                  | Res Body          | 비고                                        |
  | ------ | ----------------------------------------- | ---------------------------- | ----------------- | ------------------------------------------- |
  | GET    | /api/v1/swimming/lessons                  | page, size, sort             | Page<LessonDTO>   | 열린 수업 목록(페이징)                      |
  | GET    | /api/v1/swimming/lessons/{lessonId}       | -                            | LessonDTO         | 특정 수업 상세 정보                         |
  | GET    | /api/v1/swimming/lessons/period           | startDate, endDate           | List<LessonDTO>   | 특정 기간 수업 목록                         |
  | GET    | /api/v1/swimming/lockers                  | gender                       | List<LockerDTO>   | 사용 가능한 사물함 목록                     |
  | GET    | /api/v1/swimming/lockers/{lockerId}       | -                            | LockerDTO         | 특정 사물함 상세 정보                       |
  | POST   | /api/v1/swimming/enroll                   | EnrollRequestDto             | EnrollResponseDto | 수업 초기 신청 (결제는 마이페이지에서 진행) |
  | POST   | /api/v1/swimming/enroll/{enrollId}/cancel | CancelRequestDto             | EnrollResponseDto | 신청 취소                                   |
  | GET    | /api/v1/swimming/my-enrolls               | -                            | List<EnrollDTO>   | 내 신청 내역 전체 조회                      |
  | GET    | /api/v1/swimming/my-enrolls/status        | status, page, size           | Page<EnrollDTO>   | 상태별 신청 내역(페이징)                    |
  | GET    | /api/v1/swimming/enrolls/{enrollId}       | -                            | EnrollDTO         | 특정 신청 상세 정보                         |
  | POST   | /api/v1/swimming/pay                      | enroll_id, pg_token          | 200/400           | (구현 예정) Webhook 병행                    |
  | GET    | /api/v1/swimming/renewal                  | -                            | List<RenewalDTO>  | (구현 예정) 재등록 안내                     |
  | POST   | /api/v1/swimming/renewal                  | lesson_id, carry_locker(Y/N) | 200               | (구현 예정) 기존 locker 유지 가능           |

  ***

  ## 5. DB 구조 (사용자 관점 필드 추가)

  | 테이블     | 필드(추가)                   | 설명          |
  | ---------- | ---------------------------- | ------------- |
  | **enroll** | `expire_dt` DATETIME         | 신청 타임아웃 |
  |            | `renewal_flag` TINYINT       | 1 이면 재등록 |
  |            | `cancel_reason` VARCHAR(100) | 사용자 입력   |
  | **locker** | `gender` ENUM('M','F')       | 성별 라커존   |
  | **user**   | `adult_verified` TINYINT     | 미성년자 차단 |

  _강습 잔여 좌석은_
  `lesson.capacity - (SELECT COUNT(*) FROM enroll WHERE lesson_id=? AND pay_status='PAID')`

  ***

  ## 6. **비즈니스 룰**

  | 구분                | 세부 규칙                                                                                                        |
  | ------------------- | ---------------------------------------------------------------------------------------------------------------- |
  | **선착순**          | *PG 결제 SUCCESS 시점*에 `pay_status=PAID` 로 바뀌어야 정원 확정                                                 |
  | **1 시간 타임아웃** | `expire_dt < NOW()` + `pay_status='UNPAID'` ⇒ 배치가 `CANCELED_UNPAID`                                           |
  | **미성년자 제한**   | `user.adult_verified=0` 이면 `/api/v1/swimming/enroll` → 4003                                                    |
  | **사물함 선택**     | 최초 POST /api/v1/swimming/enroll 시에만 `locker_id` 전송 허용                                                   |
  | **사물함 재고**     | `locker.is_active=1` AND `locker_id NOT IN (SELECT locker_id FROM enroll WHERE pay_status IN ('UNPAID','PAID'))` |
  | **재등록 우선권**   | 재등록 기간 D-7~D-5, 신규 오픈 D-4; 기간 외 API 403                                                              |
  | **취소/환불**       | 사용 개시 전 취소 → PG 전액 취소, 이후 관리자 검토(부분 환불)                                                    |
  | **월별 중복 제한**  | 동일 사용자는 같은 달에 하나의 강습만 신청 가능 (오류코드 4004: 월별 중복 신청)                                  |

  ***

  ## 7. 배치/이벤트 (User Side 관련)

  | 이름                 | 주기     | 설명                                                                                   |
  | -------------------- | -------- | -------------------------------------------------------------------------------------- |
  | **unpaid-timeout**   | 5 min    | UNPAID & expire_dt 초과 레코드 → `CANCELED_UNPAID`, locker roll-back (in enroll table) |
  | **pg-webhook**       | 실시간   | PG callback 검증, 위·변조 체크 ◀→ enroll·payment 갱신                                  |
  | **renewal-notifier** | 하루 1회 | 재등록 대상자 알림(LMS)                                                                |

  ***

  ## 8. 예외 처리 플로우

  1. **동시 클릭** → DB `SELECT … FOR UPDATE` + UNIQUE 인덱스(`lesson_id`,`user_id`) on enroll table
  2. **PG 결제 실패** → `payment.status=FAIL`, `enroll` 그대로 UNPAID(시간 내 재시도 가능)
  3. **Webhook 지연**(PG > 5 초) → 프론트 Poll `/api/v1/swimming/enrolls/{id}` 5 회(2 s 간격) 후 로딩 모달 종료

  ***

  ## 9. 테스트 케이스(요약)

  | ID    | 시나리오                                | 예상 결과                                 |
  | ----- | --------------------------------------- | ----------------------------------------- |
  | TC-01 | 정원 마감 직전 2 명이 동시 신청         | 1 명 PAID, 1 명 4001(잔여없음)            |
  | TC-02 | 신청 후 59 분 결제                      | 상태 PAID                                 |
  | TC-03 | 신청 후 61 분 결제 시도                 | /api/v1/swimming/pay → 4002(만료)         |
  | TC-04 | 미성년자 로그인 → 신청                  | 4003                                      |
  | TC-05 | 재등록 기간 외 /api/v1/swimming/renewal | 403                                       |
  | TC-06 | 같은 달에 두 번째 강습 신청 시도        | /api/v1/swimming/enroll → 4004(월별 중복) |

  ***

  ## 10. 프론트엔드 구현 Tips

  - **상태별 버튼색**:
    - `UNPAID` → Yellow "결제하기"
    - `PAID` → Green "결제완료" + 영수증 링크
    - `CANCELED_*` → Red "취소됨"
  - **1 시간 카운트다운**: dayjs + react-countdown-circle; 타임아웃 시 자동 refresh
  - **Accessibility**: 강습 카드 ALT 텍스트 "{요일·시간대} 초급반 잔여 {n}석"

  ***

  ## 11. 배포 체크리스트

  1. PG 상용키 교체 & 도메인 whitelisting
  2. Webhook URL 방화벽 허용
  3. unpaid-timeout 배치 crontab 등록
  4. 잔여좌석 모니터 Grafana Dashboard

  ***

  ## 12. 데이터베이스 스키마 (DDL)

  ```sql
  -- 강습 테이블: 수영 강습 정보를 저장하는 테이블
  CREATE TABLE lesson (
      lesson_id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '강습 ID (PK)',
      title VARCHAR(100) NOT NULL COMMENT '강습명(예: 초급반, 중급반 등)',
      start_date DATE NOT NULL COMMENT '강습 시작일',
      end_date DATE NOT NULL COMMENT '강습 종료일',
      lesson_year INT GENERATED ALWAYS AS (YEAR(start_date)) VIRTUAL COMMENT '강습 연도',
      lesson_month INT GENERATED ALWAYS AS (MONTH(start_date)) VIRTUAL COMMENT '강습 월',
      capacity INT NOT NULL COMMENT '총 정원 수',
      male_locker_cap INT NOT NULL COMMENT '남성 사물함 제한 수',
      female_locker_cap INT NOT NULL COMMENT '여성 사물함 제한 수',
      price INT NOT NULL COMMENT '강습 비용(원)',
      status VARCHAR(20) NOT NULL COMMENT '강습 상태(OPEN, CLOSED, FINISHED)',
      created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '등록일시',
      updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '수정일시',
      created_by VARCHAR(50) COMMENT '등록자',
      created_ip VARCHAR(45) COMMENT '등록 IP',
      updated_by VARCHAR(50) COMMENT '수정자',
      updated_ip VARCHAR(45) COMMENT '수정 IP',
      INDEX idx_status (status),
      INDEX idx_date (start_date, end_date),
      INDEX idx_year_month (lesson_year, lesson_month) COMMENT '연도/월별 조회용 인덱스'
  ) COMMENT '수영 강습 정보 테이블';

  -- 사물함 테이블: 사용 가능한 사물함 정보를 저장하는 테이블
  CREATE TABLE locker (
      locker_id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '사물함 ID (PK)',
      locker_number VARCHAR(20) NOT NULL COMMENT '사물함 번호(예: F-12)',
      zone VARCHAR(30) NOT NULL COMMENT '구역 정보',
      gender ENUM('M', 'F') NOT NULL COMMENT '성별(M: 남성, F: 여성)',
      is_active TINYINT(1) NOT NULL DEFAULT 1 COMMENT '사용 가능 여부(1: 활성, 0: 비활성)',
      created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '등록일시',
      updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '수정일시',
      created_by VARCHAR(50) COMMENT '등록자',
      created_ip VARCHAR(45) COMMENT '등록 IP',
      updated_by VARCHAR(50) COMMENT '수정자',
      updated_ip VARCHAR(45) COMMENT '수정 IP',
      UNIQUE KEY uk_locker_number (locker_number),
      INDEX idx_gender_active (gender, is_active),
      INDEX idx_zone (zone)
  ) COMMENT '사물함 정보 테이블';

  -- 신청 테이블: 모든 강습 신청(초기, 재수강 등) 정보를 통합 저장하는 테이블
  CREATE TABLE enroll (
      enroll_id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '신청 ID (PK)',
      user_id BIGINT NOT NULL COMMENT '사용자 ID (FK)',
      user_name VARCHAR(50) NOT NULL COMMENT '사용자명',
      lesson_id BIGINT NOT NULL COMMENT '강습 ID (FK)',
      locker_id BIGINT COMMENT '사물함 ID (FK), NULL 가능',
      status VARCHAR(20) NOT NULL COMMENT '신청 상태(APPLIED, CANCELED, PENDING) - 초기 신청시 상태',
      pay_status VARCHAR(20) NOT NULL DEFAULT 'UNPAID' COMMENT '결제 상태(UNPAID, PAID, CANCELED_UNPAID) - Mypage에서 결제 관리',
      expire_dt DATETIME NOT NULL COMMENT '신청 만료 시간(타임아웃)',
      renewal_flag TINYINT(1) NOT NULL DEFAULT 0 COMMENT '재등록 여부(1: 재등록, 0: 신규)',
      cancel_reason VARCHAR(100) COMMENT '취소 사유',
      created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '신청일시',
      updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '수정일시',
      created_by VARCHAR(50) COMMENT '등록자',
      created_ip VARCHAR(45) COMMENT '등록 IP',
      updated_by VARCHAR(50) COMMENT '수정자',
      updated_ip VARCHAR(45) COMMENT '수정 IP',
      FOREIGN KEY (user_id) REFERENCES user(user_id),
      FOREIGN KEY (lesson_id) REFERENCES lesson(lesson_id),
      FOREIGN KEY (locker_id) REFERENCES locker(locker_id),
      UNIQUE KEY uk_user_lesson_paid (user_id, lesson_id, pay_status) COMMENT '사용자별 강습 중복 신청 방지 (유료 기준)',
      INDEX idx_status_pay (status, pay_status),
      INDEX idx_lesson_status_pay (lesson_id, status, pay_status),
      INDEX idx_expire (expire_dt, pay_status),
      INDEX idx_user_pay_status (user_id, pay_status),
      INDEX idx_renewal (renewal_flag)
  ) COMMENT '모든 강습 신청(초기, 재수강 등) 정보를 통합 저장하는 테이블';

  -- 결제 테이블: 결제 정보를 저장하는 테이블 (모든 결제는 Mypage에서 처리)
  CREATE TABLE payment (
      payment_id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '결제 ID (PK)',
      enroll_id BIGINT NOT NULL COMMENT '신청 ID (FK from enroll.enroll_id)',
      tid VARCHAR(100) NOT NULL COMMENT 'PG사 거래 ID',
      pg_provider VARCHAR(20) NOT NULL COMMENT 'PG사 제공업체(kakao, nice 등)',
      amount INT NOT NULL COMMENT '결제 금액',
      refund_amount INT COMMENT '환불 금액',
      refund_dt DATETIME COMMENT '환불 일시',
      pg_auth_code VARCHAR(100) COMMENT 'PG사 인증 코드',
      card_info VARCHAR(100) COMMENT '카드 정보(마스킹 처리)',
      status VARCHAR(20) NOT NULL COMMENT '결제 상태(PAID, CANCELED, PARTIAL_REFUNDED, FAILED)',
      created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '결제일시',
      updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '수정일시',
      created_by VARCHAR(50) COMMENT '등록자',
      created_ip VARCHAR(45) COMMENT '등록 IP',
      updated_by VARCHAR(50) COMMENT '수정자',
      updated_ip VARCHAR(45) COMMENT '수정 IP',
      FOREIGN KEY (enroll_id) REFERENCES enroll(enroll_id),
      UNIQUE KEY uk_tid (tid),
      INDEX idx_enroll (enroll_id),
      INDEX idx_status (status),
      INDEX idx_created_at (created_at)
  ) COMMENT '결제 정보 테이블 (모든 결제는 Mypage에서 처리됨)';

  -- 취소 요청 테이블: 개강 후 취소 요청 정보를 저장하는 테이블
  CREATE TABLE cancel_request (
      request_id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '요청 ID (PK)',
      enroll_id BIGINT NOT NULL COMMENT '신청 ID (FK from enroll.enroll_id)',
      reason VARCHAR(200) NOT NULL COMMENT '취소 사유',
      refund_pct INT COMMENT '환불 비율(%)',
      status VARCHAR(20) NOT NULL DEFAULT 'PENDING' COMMENT '요청 상태(PENDING, APPROVED, DENIED)',
      comment VARCHAR(200) COMMENT '관리자 코멘트',
      created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '요청일시',
      updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '수정일시',
      created_by VARCHAR(50) COMMENT '등록자',
      created_ip VARCHAR(45) COMMENT '등록 IP',
      updated_by VARCHAR(50) COMMENT '수정자',
      updated_ip VARCHAR(45) COMMENT '수정 IP',
      FOREIGN KEY (enroll_id) REFERENCES enroll(enroll_id),
      INDEX idx_status (status),
      INDEX idx_created (created_at)
  ) COMMENT '강습 취소 요청 정보 테이블 (Mypage에서 관리될 수 있음)';

  -- 배치 작업 로그 테이블: 배치 작업의 실행 기록을 저장하는 테이블
  CREATE TABLE batch_job_log (
      log_id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '로그 ID (PK)',
      job_name VARCHAR(50) NOT NULL COMMENT '작업 이름',
      status VARCHAR(20) NOT NULL COMMENT '작업 상태(STARTED, COMPLETED, FAILED)',
      start_dt DATETIME NOT NULL COMMENT '작업 시작 시간',
      end_dt DATETIME COMMENT '작업 종료 시간',
      records_processed INT DEFAULT 0 COMMENT '처리된 레코드 수',
      error_message TEXT COMMENT '오류 메시지',
      created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '등록일시',
      INDEX idx_job_name (job_name),
      INDEX idx_start_dt (start_dt)
  ) COMMENT '배치 작업 로그 테이블';

  -- PG Webhook 로그 테이블: 결제 웹훅 요청의 로그를 저장하는 테이블
  CREATE TABLE pg_webhook_log (
      log_id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '로그 ID (PK)',
      tid VARCHAR(100) NOT NULL COMMENT 'PG사 거래 ID',
      request_body TEXT NOT NULL COMMENT '웹훅 요청 본문(JSON)',
      status VARCHAR(20) NOT NULL COMMENT '처리 상태(SUCCESS, FAILED, DUPLICATED)',
      ip_address VARCHAR(45) NOT NULL COMMENT '요청 IP 주소',
      verified TINYINT(1) NOT NULL DEFAULT 0 COMMENT '서명 검증 여부',
      error_message VARCHAR(255) COMMENT '오류 메시지',
      created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '요청 수신 시간',
      processed_at DATETIME COMMENT '처리 완료 시간',
      INDEX idx_tid (tid),
      INDEX idx_status (status),
      INDEX idx_created_at (created_at)
  ) COMMENT 'PG 웹훅 요청 로그 테이블';

  -- 재등록 안내 테이블: 재등록 안내 대상자 정보를 저장하는 테이블
  CREATE TABLE renewal_notification (
      notification_id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '알림 ID (PK)',
      user_id BIGINT NOT NULL COMMENT '사용자 ID (FK)',
      previous_enroll_id BIGINT NOT NULL COMMENT '이전 신청 ID',
      previous_lesson_id BIGINT NOT NULL COMMENT '이전 강습 ID',
      suggested_lesson_id BIGINT NOT NULL COMMENT '제안할 다음 강습 ID',
      previous_locker_id BIGINT COMMENT '이전 사물함 ID',
      sent_at DATETIME COMMENT '발송 시간',
      is_sent TINYINT(1) NOT NULL DEFAULT 0 COMMENT '발송 여부',
      notification_type VARCHAR(20) NOT NULL DEFAULT 'SMS' COMMENT '알림 유형(SMS, EMAIL, PUSH)',
      created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '생성 시간',
      updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '수정 시간',
      FOREIGN KEY (user_id) REFERENCES user(user_id),
      FOREIGN KEY (previous_lesson_id) REFERENCES lesson(lesson_id),
      FOREIGN KEY (suggested_lesson_id) REFERENCES lesson(lesson_id),
      INDEX idx_user (user_id),
      INDEX idx_sent (is_sent),
      INDEX idx_created_at (created_at)
  ) COMMENT '재등록 안내 정보 테이블';
  ```
