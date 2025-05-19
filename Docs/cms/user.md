## 📑 Mypage API v1 — **File-Integrated Final Spec** (2025-05-16)

> **Base URL (Authenticated):** `/api/v1/mypage` > **Auth:** `Authorization: Bearer {JWT}` _(필수)_
> 모든 엔드포인트는 **HTTPS** + **JWT** 필수이며, 로그인된 일반 회원 권한(`ROLE_USER`)으로 접근한다.
> **결제(Checkout → Pay)** 플로우는 **ENROLL 탭(마이페이지 내부)에서만** 실행된다. 외부 화면에서는 _신청만_ 가능하며, 결제 버튼은 존재하지 않는다.

---

### 1. Tabs & Functional Structure

| `tab`      | 설명                      | 서브 기능                                   |
| ---------- | ------------------------- | ------------------------------------------- |
| `PROFILE`  | 회원정보 조회/수정        | 이름·주소·전화·차량번호 갱신                |
| `PASSWORD` | 비밀번호 변경/임시PW 교체 | 강제 변경 경로 `/password?force=1`          |
| `ENROLL`   | **수강 내역 관리**        | 모든 신청내역 조회, 결제, 취소, 재수강 신청 |
| `PAYMENT`  | 결제/환불 이력            | 결제 상세, 전액·부분 환불 요청              |

---

### 2. Common Query String

| param  | type | default      | note                         |
| ------ | ---- | ------------ | ---------------------------- |
| `page` | int  | 1            | 1-based                      |
| `size` | int  | 20           | rows per page                |
| `sort` | str  | `-createdAt` | `+field` ASC / `-field` DESC |

---

### 3. Endpoints

#### 3.1 회원정보 (Profile)

| Method | URL        | Req.Body     | Resp         | Scope |
| ------ | ---------- | ------------ | ------------ | ----- |
| GET    | `/profile` | –            | `ProfileDto` | USER  |
| PATCH  | `/profile` | `ProfileDto` | Updated      | USER  |

#### 3.2 비밀번호 (Pass & Temp)

| Method | URL              | Req.Body              | Resp | Scope |
| ------ | ---------------- | --------------------- | ---- | ----- |
| PATCH  | `/password`      | `PasswordChangeDto`   | 200  | USER  |
| POST   | `/password/temp` | `{ "userId": "..." }` | Sent | USER  |

#### 3.3 수강 내역 관리 (Enrollments on Mypage)

| #   | Method   | URL                         | Req.Body / QS       | Resp             | Scope | Comment                                        |
| --- | -------- | --------------------------- | ------------------- | ---------------- | ----- | ---------------------------------------------- |
| 1   | GET      | `/enroll`                   | `status?`           | List\<EnrollDto> | USER  | 현 사용자의 모든 enrollments 조회              |
| 2   | GET      | `/enroll/{id}`              | –                   | EnrollDto        | USER  | 특정 enrollment 상세 조회                      |
| 3   | **POST** | **`/enroll/{id}/checkout`** | –                   | `CheckoutDto`    | USER  | enrollment 결제 준비 (Mypage 전용)             |
| 4   | **POST** | **`/enroll/{id}/pay`**      | `{ "pgToken": "" }` | 200 / Error      | USER  | enrollment 결제 처리 (Mypage 전용)             |
| 5   | PATCH    | `/enroll/{id}/cancel`       | `{ "reason": "" }`  | Requested        | USER  | enrollment 취소 요청                           |
| 6   | POST     | `/renewal`                  | `RenewalRequestDto` | Created          | USER  | 신규 재수강 신청 (enroll 테이블에 레코드 생성) |

#### 3.4 결제 내역 (Payment)

| Method | URL                    | Req.Body | Resp              | Scope |
| ------ | ---------------------- | -------- | ----------------- | ----- |
| GET    | `/payment`             | page…    | List\<PaymentDto> | USER  |
| POST   | `/payment/{id}/cancel` | –        | Requested         | USER  |

> **Checkout → Pay 시퀀스**
> ① FE `POST /enroll/{id}/checkout` → 서버가 금액·주문번호 `CheckoutDto` 반환
> ② FE 아임포트 **카드 결제** 실행 (`merchantUid`, `amount` 전달)
> ③ 성공 시 PG 콜백 파라미터 `pgToken` 수신
> ④ FE `POST /enroll/{id}/pay` → 서버가 **영수증 검증** 후 `status=PAID` 확정

---

### 4. Schemas

#### 4.1 ProfileDto

```jsonc
{
  "name": "양순민",
  "userId": "smyang",
  "phone": "010-9143-6650",
  "address": "부산광역시 ...",
  "email": "user@arpina.kr",
  "carNo": "12모 3456"
}
```

#### 4.2 PasswordChangeDto

```jsonc
{
  "currentPw": "string",
  "newPw": "string"
}
```

#### 4.3 EnrollDto (카드 데이터)

```jsonc
{
  "enrollId": 9999,
  "lesson": {
    "title": "수영 강습 프로그램",
    "period": "2025-05-01 ~ 2025-05-30",
    "time": "(월,화,수,목,금) 오전 07:00 ~ 07:50",
    "price": 65000
  },
  "status": "UNPAID",
  "expireDt": "2025-05-18T14:13:00+09:00",
  "locker": { "id": 12, "zone": "여성A", "carryOver": true },
  "renewalWindow": {
    "open": "2025-05-18T00:00:00+09:00",
    "close": "2025-05-22T00:00:00+09:00"
  }
}
```

#### 4.4 CheckoutDto

```jsonc
{
  "merchantUid": "swim_9999_202505181300",
  "amount": 65000,
  "lessonTitle": "수영 강습 프로그램",
  "userName": "양순민",
  "pgProvider": "html5_inicis"
}
```

#### 4.5 RenewalRequestDto

```jsonc
{
  "lessonId": 321,
  "carryLocker": true
}
```

#### 4.6 PaymentDto

```jsonc
{
  "paymentId": 1,
  "enrollId": 9999,
  "amount": 65000,
  "paidAt": "2025-04-18T13:00:00+09:00",
  "status": "SUCCESS" // SUCCESS | CANCELED | PARTIAL | REFUND_REQUESTED
}
```

---

### 5. Response Wrapper

```jsonc
{
  "status": 200,
  "data": { ... | [...] },
  "message": "성공"
}
```

---

### 6. Error Codes

| code             | http | message             | 설명                        |
| ---------------- | ---- | ------------------- | --------------------------- |
| SEAT_FULL        | 409  | 잔여 좌석 없음      | 동시 신청 Race Condition    |
| LOCKER_TAKEN     | 409  | 라커 이미 사용중    | locker 중복                 |
| ENROLL_NOT_FOUND | 404  | 신청 없음           | 잘못된 enrollId             |
| PAYMENT_EXPIRED  | 400  | 결제 가능시간 만료  | expireDt 이후 checkout/pay  |
| ALREADY_PAID     | 409  | 이미 결제 완료      | 중복 checkout/pay           |
| PG_VERIFY_FAIL   | 400  | PG 영수증 검증 실패 | pay 단계 영수증 금액 불일치 |
| CANCEL_PENDING   | 409  | 취소 심사 진행중    | 이미 취소 요청 상태         |
| INVALID_PW       | 400  | 비밀번호 정책 위반  | 새 비밀번호 규칙 미충족     |
| TEMP_PW_REQUIRED | 403  | 임시 PW 변경 필요   | temp_pw_flag = 1            |
| NO_AUTH          | 401  | 인증 필요           | JWT 누락/만료               |

---

### 7. Database DDL

#### 7.1 `user` 테이블 수정

ALTER TABLE `user`
ADD COLUMN `car_no` VARCHAR(50) DEFAULT NULL COMMENT '차량번호' AFTER `group_id`,
ADD COLUMN `temp_pw_flag` TINYINT(1) DEFAULT 0 COMMENT '임시비밀번호여부 (0: 아니오, 1: 예)' AFTER `car_no`, -- 문서에 이미 언급된 필드
ADD COLUMN `phone` VARCHAR(50) DEFAULT NULL COMMENT '전화번호' AFTER `temp_pw_flag`,
ADD COLUMN `address` VARCHAR(255) DEFAULT NULL COMMENT '주소' AFTER `phone`;

#### 7.2 `enroll` 테이블 (통합 신청 정보)

CREATE TABLE `enroll` (
`enroll_id` BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '신청 ID (PK)',\
`user_uuid` VARCHAR(36) NOT NULL COMMENT '사용자 UUID (FK from user.uuid)', -- Changed from user_id to user_uuid for consistency with other tables if needed, ensure user table has uuid as PK or indexed.
`user_name` VARCHAR(50) NOT NULL COMMENT '사용자명 (수영강습쪽 DDL참조, user테이블에서 조인하는 대신 중복 저장하는것으로 보임)',\
`lesson_id` BIGINT NOT NULL COMMENT '강습 ID (FK from lesson.lesson_id)',\
`locker_id` BIGINT COMMENT '사물함 ID (FK from locker.locker_id), NULL 가능',\
`status` VARCHAR(20) NOT NULL COMMENT '신청 상태(APPLIED, CANCELED, PENDING) - 초기 신청시 상태',\
`pay_status` VARCHAR(20) NOT NULL DEFAULT 'UNPAID' COMMENT '결제 상태(UNPAID, PAID, CANCELED_UNPAID) - Mypage에서 결제 관리',\
`expire_dt` DATETIME NOT NULL COMMENT '신청 만료 시간(타임아웃)',\
`renewal_flag` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '재등록 여부(1: 재등록, 0: 신규)',\
`cancel_reason` VARCHAR(100) COMMENT '취소 사유', -- swim-user.md DDL VARCHAR(100), mypage DDL VARCHAR(255). 통일 필요 -> 100으로 변경
`cancel_status` VARCHAR(20) DEFAULT 'NONE' COMMENT '취소 상태 (NONE, REQ, PENDING, APPROVED, DENIED)', -- Added from mypage DDL, swim-user.md DDL에는 없었음
`refund_amount` INT DEFAULT NULL COMMENT '환불 금액', -- Added from mypage DDL
`locker_zone` VARCHAR(50) DEFAULT NULL COMMENT '라커 구역', -- Added from mypage DDL. swim-user.md DDL에는 locker_id만 있었음.
`locker_carry_over` TINYINT(1) DEFAULT 0 COMMENT '라커 연장 여부', -- Added from mypage DDL
`created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '신청일시',\
`updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '수정일시',\
`created_by` VARCHAR(50) COMMENT '등록자 (수영강습쪽 DDL 참조)',\
`created_ip` VARCHAR(45) COMMENT '등록 IP (수영강습쪽 DDL 참조)',\
`updated_by` VARCHAR(50) COMMENT '수정자 (수영강습쪽 DDL 참조)',\
`updated_ip` VARCHAR(45) COMMENT '수정 IP (수영강습쪽 DDL 참조)',\
-- Ensuring FK to user table matches its actual PK (assuming user.uuid)
FOREIGN KEY (`user_uuid`) REFERENCES `user` (`uuid`) ON DELETE CASCADE ON UPDATE CASCADE,\
FOREIGN KEY (`lesson_id`) REFERENCES `lesson` (`lesson_id`),\
FOREIGN KEY (`locker_id`) REFERENCES `locker` (`locker_id`),\
UNIQUE KEY `uk_user_lesson_paid` (`user_uuid`, `lesson_id`, `pay_status`) COMMENT '사용자별 강습 중복 신청 방지 (유료 기준)',\
INDEX `idx_status_pay` (`status`, `pay_status`),\
INDEX `idx_lesson_status_pay` (`lesson_id`, `status`, `pay_status`),\
INDEX `idx_expire` (`expire_dt`, `pay_status`),\
INDEX `idx_user_pay_status` (`user_uuid`, `pay_status`),\
INDEX `idx_renewal` (`renewal_flag`)\
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='모든 강습 신청(초기, 재수강 등) 정보를 통합 저장하는 테이블';\

#### 7.3 `payment` 테이블 (통합 결제 정보)

CREATE TABLE `payment` (\
`payment_id` BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '결제 ID (PK)',\
`enroll_id` BIGINT NOT NULL COMMENT '신청 ID (FK from enroll.enroll_id)',\
`tid` VARCHAR(100) NOT NULL COMMENT 'PG사 거래 ID',\
`pg_provider` VARCHAR(20) NOT NULL COMMENT 'PG사 제공업체(kakao, nice 등)', -- swim-user.md has VARCHAR(20), mypage had VARCHAR(50). 20으로 통일.
`amount` INT NOT NULL COMMENT '결제 금액',\
`paid_at` TIMESTAMP NULL DEFAULT NULL COMMENT '결제 일시 (mypage DDL)', -- swim-user.md DDL에는 없었지만 유용하여 추가
`refund_amount` INT COMMENT '환불 금액 (swim-user.md DDL)',\
`refund_dt` DATETIME COMMENT '환불 일시 (swim-user.md DDL)',\
`pg_auth_code` VARCHAR(100) COMMENT 'PG사 인증 코드 (swim-user.md DDL)',\
`card_info` VARCHAR(100) COMMENT '카드 정보(마스킹 처리) (swim-user.md DDL)',\
`status` VARCHAR(20) NOT NULL COMMENT '결제 상태(PAID, CANCELED, PARTIAL_REFUNDED, FAILED)', -- swim-user.md VARCHAR(20), mypage VARCHAR(50). 20으로 통일.
`pg_token` VARCHAR(255) DEFAULT NULL COMMENT 'PG 거래 토큰 (mypage DDL)', -- swim-user.md DDL에는 없었음. 추가.
`merchant_uid` VARCHAR(255) DEFAULT NULL COMMENT '가맹점 주문번호 (mypage DDL)', -- swim-user.md DDL에는 없었음. 추가.
`created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '결제/생성일시',\
`updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '수정일시',\
`created_by` VARCHAR(50) COMMENT '등록자 (swim-user.md DDL)',\
`created_ip` VARCHAR(45) COMMENT '등록 IP (swim-user.md DDL)',\
`updated_by` VARCHAR(50) COMMENT '수정자 (swim-user.md DDL)',\
`updated_ip` VARCHAR(45) COMMENT '수정 IP (swim-user.md DDL)',\
FOREIGN KEY (`enroll_id`) REFERENCES `enroll` (`enroll_id`) ON DELETE RESTRICT ON UPDATE CASCADE,\
UNIQUE KEY `uk_tid` (`tid`),\
INDEX `idx_enroll` (`enroll_id`),\
INDEX `idx_status` (`status`),\
INDEX `idx_created_at` (`created_at`)\
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='결제 정보 테이블 (모든 결제는 Mypage에서 처리됨)';\

---

### 8. Security & Workflow

1. **JWT + Refresh** – 모든 호출 보호, 만료 시 자동 재발급
2. **Password Flow** – 임시PW 로그인 시 `/mypage/password?force=1` 강제 이동
3. **Checkout** – 서버가 금액·정원·라커 재검증 후 `CheckoutDto` 반환 (for any `enroll` record, Mypage only)
4. **Pay** – 서버가 PG 영수증 검증 후 `status=PAID` 확정 (for any `enroll` record, Mypage only, 선착순 보장)
5. **Payment Timeout** – `expire_dt < NOW()` & `status='UNPAID'` in `enroll` table → `CANCELED_UNPAID`
6. **Partial Refund** – `/payment/{id}/cancel` → 관리자 승인 후 PG `partialCancel`

---

### 9. Front-End Guidelines

| 컴포넌트           | 구현 포인트                                              |
| ------------------ | -------------------------------------------------------- |
| **EnrollCard**     | 상태 Badge + 버튼 (`Checkout` red / `Cancel` blue)       |
| **Countdown**      | `expireDt` diff 실시간 표시, 0 초 → 카드 회색 "결제만료" |
| **Checkout Modal** | `CheckoutDto` 요약 + 아임포트 Script 호출                |
| **Renewal Modal**  | 라커 carry 토글, 성공 Toast                              |
| **Empty State**    | 일러스트 + "아직 신청 내역이 없어요"                     |

---

### 10. Batch & Event

| Job                | 주기   | Logic                                                                                        |
| ------------------ | ------ | -------------------------------------------------------------------------------------------- |
| `unpaid-timeout`   | 5 min  | UNPAID & expire_dt < NOW() in `enroll` table → `CANCELED_UNPAID` + 라커 복원 (if applicable) |
| `pg-webhook`       | 실시간 | 아임포트 Webhook 검증 → `enroll`·`payment` 동기화                                            |
| `renewal-notifier` | 1 day  | renewalWindow 오픈 회원에게 LMS 알림                                                         |

---

### 11. Example cURL

```bash
# (1) 신청 목록 조회
curl -H "Authorization: Bearer $TK" \
  'https://arpina.kr/api/v1/mypage/enroll?page=1&size=8'

# (2) Checkout (마이페이지 카드의 "결제하기" 클릭)
curl -X POST -H "Authorization: Bearer $TK" \
  https://arpina.kr/api/v1/mypage/enroll/9999/checkout

# (3) Pay (PG 성공 콜백 후)
curl -X POST -H "Authorization: Bearer $TK" \
  -H 'Content-Type: application/json' \
  -d '{ "pgToken":"imp_1234567890" }' \
  https://arpina.kr/api/v1/mypage/enroll/9999/pay

# (4) 환불 요청
curl -X POST -H "Authorization: Bearer $TK" \
  https://arpina.kr/api/v1/mypage/payment/1/cancel
```

---

### 👀 핵심 포인트

1. **Checkout → Pay** 2-Step 결제 – 금액 검증 & PG 영수증 이중 확인으로 오결제 차단
2. **마이페이지 중앙집중** – 결제·취소·재등록 UX를 하나의 탭에서 일괄 제공
3. **1 시간 결제 타임아웃** – 카운트다운 + 배치 취소로 정원 잠금 해제 자동화
4. **부분 환불** – 관리자 승인형, PG `partialCancel` API 연동으로 회계 일원화
