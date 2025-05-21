## 📑 Mypage API v1 — **File-Integrated Final Spec** (2025-05-16)

> **Base URL (Authenticated):** `/api/v1/mypage` > **Auth:** `Authorization: Bearer {JWT}` _(필수)_
> 모든 엔드포인트는 **HTTPS** + **JWT** 필수이며, 로그인된 일반 회원 권한(`ROLE_USER`)으로 접근한다.
> **결제**는 **전용 결제 페이지**에서 진행된다. 강습 신청 후 해당 페이지로 리디렉션된다.

---

### 1. Tabs & Functional Structure

| `tab`      | 설명                      | 서브 기능                                                 |
| ---------- | ------------------------- | --------------------------------------------------------- |
| `PROFILE`  | 회원정보 조회/수정        | 이름·주소·전화·차량번호 갱신                              |
| `PASSWORD` | 비밀번호 변경/임시PW 교체 | 강제 변경 경로 `/password?force=1`                        |
| `ENROLL`   | **수강 내역 관리**        | 모든 신청내역 조회, **신청 상태 확인**, 취소, 재수강 신청 |
| `PAYMENT`  | 결제/환불 이력            | 결제 상세, 전액·부분 환불 요청                            |

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

| #   | Method       | URL                             | Req.Body / QS                              | Resp                            | Scope | Comment                                                                 |
| --- | ------------ | ------------------------------- | ------------------------------------------ | ------------------------------- | ----- | ----------------------------------------------------------------------- |
| 1   | GET          | `/enroll`                       | `status?`                                  | List<EnrollDto>                 | USER  | 현 사용자의 모든 enrollments 조회 (payStatus에 `PAYMENT_TIMEOUT` 추가)  |
| 2   | GET          | `/enroll/{id}`                  | –                                          | EnrollDto                       | USER  | 특정 enrollment 상세 조회                                               |
| 3   | ~~**POST**~~ | ~~**`/enroll/{id}/checkout`**~~ | `CheckoutRequestDto ({wantsLocker: Bool})` | `CheckoutDto`                   | USER  | **(제거됨 - 결제 페이지 로직으로 이전)**                                |
| 4   | ~~**POST**~~ | ~~**`/enroll/{id}/pay`**~~      | `{ "pgToken": "" }`                        | 200 / Error                     | USER  | **(제거됨 - 결제 페이지 로직으로 이전)**                                |
| 5   | PATCH        | `/enroll/{id}/cancel`           | `{ "reason": "" }`                         | Requested                       | USER  | enrollment 취소 요청 (결제 전/후 모두 가능)                             |
| 6   | POST         | `/renewal`                      | `RenewalRequestDto`                        | **EnrollInitiationResponseDto** | USER  | 신규 재수강 신청 (enroll 테이블에 레코드 생성, 이후 결제 페이지로 이동) |

#### 3.4 결제 내역 (Payment)

| Method | URL                    | Req.Body | Resp              | Scope |
| ------ | ---------------------- | -------- | ----------------- | ----- |
| GET    | `/payment`             | page…    | List\<PaymentDto> | USER  |
| POST   | `/payment/{id}/cancel` | –        | Requested         | USER  |

> **~~Checkout → Pay 시퀀스~~ (제거됨)** > ~~① FE `POST /enroll/{id}/checkout` (body: `{ "wantsLocker": true/false }`) → 서버가 사물함 가능여부 확인 후 금액·주문번호 `CheckoutDto` 반환~~ > ~~② FE 아임포트 **카드 결제** 실행 (`merchantUid`, `amount` 전달)~~ > ~~③ 성공 시 PG 콜백 파라미터 `pgToken` 수신~~ > ~~④ FE `POST /enroll/{id}/pay` → 서버가 **영수증 검증** 후 `status=PAID` 확정~~

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
  "status": "UNPAID", // pay_status 값 (UNPAID, PAID, PAYMENT_TIMEOUT, CANCELED_UNPAID)
  "applicationDate": "2025-05-17T10:00:00+09:00",
  "paymentExpireDt": "2025-05-17T10:05:00+09:00", // enroll.expire_dt (결제 페이지 만료 시간)
  "usesLocker": true, // 결제 시 확정된 사물함 사용 여부
  "isRenewal": false,
  "cancelStatus": "NONE", // NONE, REQ, APPROVED, DENIED
  "cancelReason": null,
  "renewalWindow": {
    "open": "2025-05-18T00:00:00+09:00",
    "close": "2025-05-22T00:00:00+09:00"
  },
  "canAttemptPayment": false, // (계산된 필드) 현재 이 신청 건에 대해 결제 페이지로 이동하여 결제를 시도할 수 있는지 여부 (status가 UNPAID이고 paymentExpireDt가 지나지 않았을 때 등)
  "paymentPageUrl": "/payment/process?enroll_id=9999" // (추가) status가 UNPAID이고 만료 전일 경우, 결제 페이지로 이동할 URL
}
```

#### 4.4 ~~CheckoutDto~~ (제거됨)

```jsonc
// 이 DTO는 더 이상 Mypage API에서 직접 사용되지 않음.
// 결제 페이지 관련 API (e.g., /api/v1/payment/details/{enrollId})에서 유사한 정보를 제공.
// {
//   "merchantUid": "swim_9999_202505181300",
//   "amount": 65000,
//   "lessonTitle": "수영 강습 프로그램",
//   "userName": "양순민",
//   "pgProvider": "html5_inicis"
// }
```

#### 4.5 RenewalRequestDto

```jsonc
{
  "lessonId": 321,
  "carryLocker": true // 재등록 시 이전 강습의 라커 사용 희망 여부 (결제페이지에서 최종선택)
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

| code                 | http | message                 | 설명                                                          |
| -------------------- | ---- | ----------------------- | ------------------------------------------------------------- |
| ~~SEAT_FULL~~        | 409  | ~~잔여 좌석 없음~~      | (Enroll API에서 처리)                                         |
| ~~LOCKER_TAKEN~~     | 409  | ~~라커 이미 사용중~~    | (Payment API에서 처리)                                        |
| ENROLL_NOT_FOUND     | 404  | 신청 없음               | 잘못된 enrollId                                               |
| ~~PAYMENT_EXPIRED~~  | 400  | ~~결제 가능시간 만료~~  | (Payment API 또는 `enroll.status`로 확인)                     |
| ~~ALREADY_PAID~~     | 409  | ~~이미 결제 완료~~      | (Payment API 또는 `enroll.status`로 확인)                     |
| ~~PG_VERIFY_FAIL~~   | 400  | ~~PG 영수증 검증 실패~~ | (Payment API에서 처리)                                        |
| CANCEL_PENDING       | 409  | 취소 심사 진행중        | 이미 취소 요청 상태                                           |
| INVALID_PW           | 400  | 비밀번호 정책 위반      | 새 비밀번호 규칙 미충족                                       |
| TEMP_PW_REQUIRED     | 403  | 임시 PW 변경 필요       | temp_pw_flag = 1                                              |
| NO_AUTH              | 401  | 인증 필요               | JWT 누락/만료                                                 |
| PAYMENT_TIMEOUT_INFO | 200  | 결제 시간 초과          | (Mypage에서 상태 조회 시) `enroll.pay_status=PAYMENT_TIMEOUT` |

---

### 7. Database DDL

#### 7.1 `user` 테이블 수정

ALTER TABLE `user`
ADD COLUMN `car_no` VARCHAR(50) DEFAULT NULL COMMENT '차량번호' AFTER `group_id`,
ADD COLUMN `temp_pw_flag` TINYINT(1) DEFAULT 0 COMMENT '임시비밀번호여부 (0: 아니오, 1: 예)' AFTER `car_no`, -- 문서에 이미 언급된 필드
ADD COLUMN `phone` VARCHAR(50) DEFAULT NULL COMMENT '전화번호' AFTER `temp_pw_flag`,
ADD COLUMN `address` VARCHAR(255) DEFAULT NULL COMMENT '주소' AFTER `phone`;

#### 7.2 `lesson` 테이블 (참조용)

-- 강습 테이블: 수영 강습 정보를 저장하는 테이블
CREATE TABLE `lesson` (
`lesson_id` BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '강습 ID (PK)',
`title` VARCHAR(100) NOT NULL COMMENT '강습명(예: 초급반, 중급반 등)',
`start_date` DATE NOT NULL COMMENT '강습 시작일',
`end_date` DATE NOT NULL COMMENT '강습 종료일',
`lesson_year` INT GENERATED ALWAYS AS (YEAR(`start_date`)) VIRTUAL COMMENT '강습 연도',
`lesson_month` INT GENERATED ALWAYS AS (MONTH(`start_date`)) VIRTUAL COMMENT '강습 월',
`capacity` INT NOT NULL COMMENT '총 정원 수',
`male_locker_cap` INT NOT NULL COMMENT '남성 사물함 제한 수',
`female_locker_cap` INT NOT NULL COMMENT '여성 사물함 제한 수',
`price` INT NOT NULL COMMENT '강습 비용(원)',
`status` VARCHAR(20) NOT NULL COMMENT '강습 상태(OPEN, CLOSED, FINISHED)',
`created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '등록일시',
`updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '수정일시',
`created_by` VARCHAR(50) DEFAULT NULL COMMENT '등록자',
`created_ip` VARCHAR(45) DEFAULT NULL COMMENT '등록 IP',
`updated_by` VARCHAR(50) DEFAULT NULL COMMENT '수정자',
`updated_ip` VARCHAR(45) DEFAULT NULL COMMENT '수정 IP',
INDEX `idx_status` (`status`),
INDEX `idx_date` (`start_date`, `end_date`),
INDEX `idx_year_month` (`lesson_year`, `lesson_month`) COMMENT '연도/월별 조회용 인덱스'
) COMMENT '수영 강습 정보 테이블';

#### 7.3 `enroll` 테이블 (통합 신청 정보)

CREATE TABLE `enroll` (
`enroll_id` BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '신청 ID (PK)',\
`user_uuid` VARCHAR(36) NOT NULL COMMENT '사용자 UUID (FK from user.uuid)', -- Changed from user_id to user_uuid for consistency with other tables if needed, ensure user table has uuid as PK or indexed.
`user_name` VARCHAR(50) NOT NULL COMMENT '사용자명 (수영강습쪽 DDL참조, user테이블에서 조인하는 대신 중복 저장하는것으로 보임)',\
`lesson_id` BIGINT NOT NULL COMMENT '강습 ID (FK from lesson.lesson_id)',\
`uses_locker` BOOLEAN NOT NULL DEFAULT FALSE COMMENT '사물함 사용 여부 (결제 시 최종 확정)',\
`status` VARCHAR(20) NOT NULL COMMENT '신청 상태(APPLIED, CANCELED, PENDING) - 초기 신청시 상태',\
`pay_status` VARCHAR(20) NOT NULL DEFAULT 'UNPAID' COMMENT '결제 상태(UNPAID, PAID, CANCELED_UNPAID, PAYMENT_TIMEOUT)',\
`expire_dt` DATETIME NOT NULL COMMENT '결제 페이지 접근 및 시도 만료 시간 (신청시점 + 5분)',\
`renewal_flag` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '재등록 여부(1: 재등록, 0: 신규)',\
`cancel_reason` VARCHAR(100) COMMENT '취소 사유',\
`cancel_status` VARCHAR(20) DEFAULT 'NONE' COMMENT '취소 상태 (NONE, REQ, PENDING, APPROVED, DENIED)',\
`refund_amount` INT DEFAULT NULL COMMENT '환불 금액',\
`created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '신청일시',\
`updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '수정일시',\
`created_by` VARCHAR(50) COMMENT '등록자 (수영강습쪽 DDL 참조)',\
`created_ip` VARCHAR(45) COMMENT '등록 IP (수영강습쪽 DDL 참조)',\
`updated_by` VARCHAR(50) COMMENT '수정자 (수영강습쪽 DDL 참조)',\
`updated_ip` VARCHAR(45) COMMENT '수정 IP (수영강습쪽 DDL 참조)',\
-- Ensuring FK to user table matches its actual PK (assuming user.uuid)
FOREIGN KEY (`user_uuid`) REFERENCES `user` (`uuid`) ON DELETE CASCADE ON UPDATE CASCADE,\
FOREIGN KEY (`lesson_id`) REFERENCES `lesson` (`lesson_id`),\
-- locker_id FK는 제거 (uses_locker로 대체)\
UNIQUE KEY `uk_user_lesson_active_enroll` (`user_uuid`, `lesson_id`, `pay_status`) COMMENT '사용자별 동일 강좌에 대한 유효한(PAID, UNPAID 만료전) 신청 중복 방지 (세부 조건 검토)',\
INDEX `idx_status_pay` (`status`, `pay_status`),\
INDEX `idx_lesson_status_pay` (`lesson_id`, `status`, `pay_status`),\
INDEX `idx_expire_pay_status` (`expire_dt`, `pay_status`),\
INDEX `idx_user_pay_status` (`user_uuid`, `pay_status`),\
INDEX `idx_renewal` (`renewal_flag`)\
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='모든 강습 신청(초기, 재수강 등) 정보를 통합 저장하는 테이블';\

#### 7.4 `payment` 테이블 (통합 결제 정보)

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
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='결제 정보 테이블 (결제 페이지에서 처리됨)';\

---

### 8. Security & Workflow

1. **JWT + Refresh** – 모든 호출 보호, 만료 시 자동 재발급
2. **Password Flow** – 임시PW 로그인 시 `/mypage/password?force=1` 강제 이동
3. **Initial Enrollment (`/api/v1/swimming/enroll`)** – 서버가 정원, 중복 등 확인 후 `EnrollInitiationResponseDto` (결제페이지 URL, 만료시간 포함) 반환.
4. **Payment Page (`/api/v1/payment/details/{enrollId}` & `/api/v1/payment/confirm/{enrollId}`)** – 서버가 금액·정원·라커 재검증 후 PG 연동, 성공 시 `enroll.pay_status=PAID` 확정 (선착순 보장).
5. **Payment Timeout (`enroll.expire_dt`)** – `expire_dt < NOW()` & `pay_status='UNPAID'` in `enroll` table → `PAYMENT_TIMEOUT` (배치 또는 후속 접근 시 처리).
6. **Partial Refund** – `/mypage/payment/{id}/cancel` (또는 관리자 API) → 관리자 승인 후 PG `partialCancel`

---

### 9. Front-End Guidelines

| 컴포넌트 (Next.js/React)              | 구현 포인트                                                                                                                                                                                                        |
| ------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------ |
| **EnrollCard (`EnrollCard.jsx`)**     | 상태 Badge (`UNPAID` [만료 전이면 `<Link href={paymentPageUrl}>` 또는 `router.push(paymentPageUrl)`로 "결제 페이지로" 버튼 - 남은 시간 표시], `PAID`, `PAYMENT_TIMEOUT`, `CANCELED_*`) + `Cancel` 버튼 (API 호출)  |
| **~~Countdown~~**                     | (제거됨 - 결제 페이지에서 처리) `enroll.paymentExpireDt`는 마이페이지 카드에 표시될 수 있음.                                                                                                                       |
| **~~Checkout Modal~~**                | (제거됨 - 결제 페이지로 대체)                                                                                                                                                                                      |
| **RenewalModal (`RenewalModal.jsx`)** | 라커 carry 토글, API 호출 성공 시 (백엔드에서 `EnrollInitiationResponseDto`의 `paymentPageUrl` 받아) Next.js `router.push()`로 결제 페이지로 리디렉션. 성공/실패 React Toast 메시지 (e.g., `react-toastify`) 사용. |
| **EmptyState (`EmptyState.jsx`)**     | 일러스트 + "아직 신청 내역이 없어요" (조건부 렌더링)                                                                                                                                                               |

---

### 10. Batch & Event

| Job                     | 주기    | Logic                                                                                                                     |
| ----------------------- | ------- | ------------------------------------------------------------------------------------------------------------------------- |
| `payment-timeout-sweep` | 1-5 min | UNPAID & `expire_dt` < NOW() in `enroll` table → `PAYMENT_TIMEOUT` + (결제 페이지에서 선택했던) 라커 복원 (if applicable) |
| `pg-webhook`            | 실시간  | 아임포트 Webhook 검증 → `enroll`·`payment` 동기화                                                                         |
| `renewal-notifier`      | 1 day   | renewalWindow 오픈 회원에게 LMS 알림                                                                                      |

---

### 11. Example cURL

```bash
# (1) 마이페이지 신청 목록 조회
curl -H "Authorization: Bearer $TK" \
  'https://arpina.kr/api/v1/mypage/enroll?page=1&size=8'

# (2) ~~Checkout~~ (제거됨. /api/v1/swimming/enroll 에서 시작)
# curl -X POST -H "Authorization: Bearer $TK" \
#   https://arpina.kr/api/v1/mypage/enroll/9999/checkout

# (3) ~~Pay~~ (제거됨. /api/v1/payment/confirm/{enrollId} 에서 처리)
# curl -X POST -H "Authorization: Bearer $TK" \
#   -H 'Content-Type: application/json' \
#   -d '{ "pgToken":"imp_1234567890" }' \
#   https://arpina.kr/api/v1/mypage/enroll/9999/pay

# (4) 환불 요청 (Mypage)
curl -X POST -H "Authorization: Bearer $TK" \
  https://arpina.kr/api/v1/mypage/payment/1/cancel
```

---

### 👀 핵심 포인트

1. **Dedicated Payment Page** – 강습 신청 후 즉시 이동, 5분 타임아웃 내 결제 완료.
2. **Mypage 역할 변경** – 결제 직접 처리 대신 신청 상태 조회, 취소, 재수강 시작점으로 기능 축소.
3. **5분 결제 타임아웃** – `enroll.expire_dt` 와 `PAYMENT_TIMEOUT` 상태로 관리, 배치 처리.
4. **부분 환불** – 관리자 승인형, PG `partialCancel` API 연동으로 회계 일원화 (기존 유지).
