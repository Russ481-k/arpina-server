\***\*- 🏊‍♀️ 수영장 **관리자 백오피스\*\* — 관리자-측 개발문서
_(Spring Boot REST API + React Admin SPA 기준)_

---

## 0. 문서 목표

| 항목      | 내용                                                                                                     |
| --------- | -------------------------------------------------------------------------------------------------------- |
| 범위      | **운영자**가 강습·사물함·신청·결제(환불)·통계를 실시간으로 관리하는 CMS(콘텐츠 관리 시스템) 백오피스     |
| 달성 지표 | ① 5 분 내 취소·환불 처리 ② 실시간 잔여 좌석 Sync ③ 월 결제 정산 100 % 일치 ④ 모든 관리 작업 3 click 이내 |

---

## 1. 역할(Role) 정의

| ROLE             | 설명                                                                         |
| ---------------- | ---------------------------------------------------------------------------- |
| **SYSTEM_ADMIN** | 전체 시스템 설정, 모든 CMS 기능 접근, 다른 관리자 계정 관리 (최고 관리자).   |
| **ADMIN**        | 강습, 사물함 재고, 신청/결제 현황, 취소/환불 처리 등 CMS 전반의 운영 관리자. |

---

## 2. 백오피스 화면 구조 (CMS 기준)

| ID              | 메뉴           | 주요 UI                                                                                                                                                                                                                                                                                    | 설명                                                                                                                                                                                                                                                                                                                         |
| --------------- | -------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------ | ---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| **CMS-DASH**    | Dashboard      | KPI Card(신청·좌석·매출) 잔여 라커 Donut                                                                                                                                                                                                                                                   | 실시간 운영 지표. (기존 AD-01 내용과 유사)                                                                                                                                                                                                                                                                                   |
| **CMS-LESSON**  | 강습 관리      | DataGrid + 생성/수정/복제 버튼                                                                                                                                                                                                                                                             | 강습 정보(명칭, 강사, 기간, 시간, 정원, 가격, 상태 등) CRUD. **강습 스케줄(기간, 시간) 조정 기능 포함.** 상태(OPEN, CLOSED, ONGOING, COMPLETED) 관리.                                                                                                                                                                        |
| **CMS-LOCKER**  | 사물함 관리    | 성별 총 라커 수, 현재 사용량 표시/수정                                                                                                                                                                                                                                                     | `locker_inventory` 기반. **성별 전체 라커 수량 수정, 현재 사용 중인 라커 수 조회.** (개별 라커 배정은 자동 로직 따름)                                                                                                                                                                                                        |
| **CMS-ENROLL**  | 신청자 관리    | **상단 연월 필터.** Table(Status Badge: 결제여부(승인/취소), 사물함신청여부, **할인회원 유무/종류, 관리자 할인 승인여부(툴팁:승인시각, 드롭다운:승인/거절/대기)**) + Search(회원이름, 아이디)/Filter. 컬럼: 회원이름(클릭 시 회원 메모폼), 아이디, 전화번호, 신규/재수강, 관리자취소(버튼) | 강습별/사용자별 신청 내역 조회. 신청자 정보, 결제 상태, 할인 정보 등 확인. 특정 신청 건 상세 조회 및 관리. **회원 클릭 시 해당 회원에 대한 간단한 메모 작성/조회 팝업.** **관리자 취소 버튼 클릭 시, 해당 건 즉시 취소 처리 (환불 정책은 별도 정의 따름).** **'임시 등록' 버튼을 통해 오프라인 등록자 정보 입력 기능 제공.** |
| **CMS-CANCEL**  | 취소/환불 관리 | Dialog: **실사용일수 입력 폼 (수정 가능)**, 환불 정보 표시/입력, **취소승인여부(버튼)**                                                                                                                                                                                                    | 사용자의 취소 요청 목록 검토 및 승인/반려. **승인 시, 시스템은 기본 환불액(일할+위약금) 자동 계산. 관리자는 필요시 실사용일수 조정하여 환불액 재계산 후 최종 승인 가능.** 최종 승인 시 KISPG 연동.                                                                                                                           |
| **CMS-PAYMENT** | 결제 정보 관리 | 결제·환불 탭, KISPG TID, 엑셀 DL                                                                                                                                                                                                                                                           | 모든 결제 및 환불 내역 상세 조회. **KISPG TID, 결제 수단, 결과 코드 등 확인.** 예외 건 수동 처리 지원.                                                                                                                                                                                                                       |
| **CMS-STATS**   | 통계·리포트    | Bar & Line Chart + XLS Export                                                                                                                                                                                                                                                              | 월별 매출·이용자·라커 사용률 등. (기존 AD-07 내용과 유사)                                                                                                                                                                                                                                                                    |
| **CMS-SYSTEM**  | 시스템 설정    | 권한 매핑, Cron 로그                                                                                                                                                                                                                                                                       | **(SYSTEM_ADMIN 전용)** 배치 작업 모니터링, Webhook 로그 조회, 관리자 계정/권한 관리. (기존 AD-08 내용과 유사)                                                                                                                                                                                                               |

---

## 3. API 상세 (CMS 기준)

### 3-1. 공통

| 요소     | 값                                                        |
| -------- | --------------------------------------------------------- |
| Base URL | `/api/v1/cms`                                             |
| 인증     | JWT + ROLE 체크                                           |
| 응답     | `status` int · `data` · `message`                         |
| 에러코드 | 400 Validation · 403 NoAuth · 404 NotFound · 409 Conflict |

### 3-2. 엔드포인트

| 그룹                | Method | URL                                     | Req Body/QS                                                                              | Resp                             | 권한                | 비고                                                                                                                                                                |
| ------------------- | ------ | --------------------------------------- | ---------------------------------------------------------------------------------------- | -------------------------------- | ------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| **Lesson**          | GET    | /lessons                                | status, year, month, pageable                                                            | Page<LessonDto>                  | ADMIN, SYSTEM_ADMIN | 강습 목록 조회. 스케줄 정보 포함.                                                                                                                                   |
|                     | GET    | /lessons/{lessonId}                     | -                                                                                        | LessonDto                        | ADMIN, SYSTEM_ADMIN | 특정 강습 상세 조회.                                                                                                                                                |
|                     | POST   | /lessons                                | LessonDto                                                                                | LessonDto (Created)              | ADMIN, SYSTEM_ADMIN | 새 강습 생성. 스케줄(기간, 시간) 포함.                                                                                                                              |
|                     | PUT    | /lessons/{lessonId}                     | LessonDto                                                                                | LessonDto (Updated)              | ADMIN, SYSTEM_ADMIN | 강습 정보 및 스케줄 수정.                                                                                                                                           |
|                     | DELETE | /lessons/{lessonId}                     | -                                                                                        | 204 No Content                   | ADMIN, SYSTEM_ADMIN | 강습 삭제 (조건부).                                                                                                                                                 |
|                     | POST   | /lessons/{lessonId}/clone               | `{ newStartDate: "YYYY-MM-DD" }`                                                         | LessonDto (Cloned)               | ADMIN, SYSTEM_ADMIN | 강습 복제.                                                                                                                                                          |
| **LockerInventory** | GET    | /lockers/inventory                      | -                                                                                        | List<LockerInventoryDto>         | ADMIN, SYSTEM_ADMIN | 성별 전체 라커 재고 현황 (총량, 사용량, 가용량) 조회.                                                                                                               |
|                     | PUT    | /lockers/inventory/{gender}             | `{ totalQuantity: number }`                                                              | LockerInventoryDto (Updated)     | ADMIN, SYSTEM_ADMIN | 특정 성별 라커의 **총 수량(total_quantity)** 수정. 사용량(used_quantity)은 시스템이 관리.                                                                           |
| **Enrollment**      | GET    | /enrollments                            | **year, month,** lessonId, userId, payStatus, pageable                                   | Page<EnrollAdminResponseDto>     | ADMIN, SYSTEM_ADMIN | 신청자 목록 조회. **연월 필터 추가.** 결제 정보, 할인 정보 연동.                                                                                                    |
|                     | GET    | /enrollments/{enrollId}                 | -                                                                                        | EnrollAdminResponseDto           | ADMIN, SYSTEM_ADMIN | 특정 신청자 상세 조회.                                                                                                                                              |
|                     | PUT    | /enrollments/{enrollId}/admin-cancel    | `{ adminComment: "..." }` (선택적)                                                       | EnrollAdminResponseDto (Updated) | ADMIN, SYSTEM_ADMIN | **신규.** 관리자가 특정 신청 건을 직접 취소 처리. `status`는 "CANCELED_BY_ADMIN", `payStatus`는 "REFUND_PENDING_ADMIN_CANCEL"로 변경됨. 실제 환불 처리는 별도 필요. |
|                     | PUT    | /enrollments/{enrollId}/discount-status | `{ discountType: "...", discountStatus: "APPROVED/DENIED/PENDING", adminComment: "..."}` | EnrollAdminResponseDto (Updated) | ADMIN, SYSTEM_ADMIN | **신규.** 특정 신청 건의 할인 상태 및 종류 변경. 승인 시각 기록.                                                                                                    |
|                     | POST   | /enrollments/temporary                  | `TemporaryEnrollmentRequestDto`                                                          | EnrollAdminResponseDto (Created) | ADMIN, SYSTEM_ADMIN | **신규.** 오프라인 등록자를 위한 임시 등록. 시스템에 최소 정보로 신청 건 생성.                                                                                      |
| **User**            | POST   | /users/{userId}/memo                    | `{ memoContent: "..." }`                                                                 | UserMemoDto (Updated)            | ADMIN, SYSTEM_ADMIN | **신규.** 특정 회원에 대한 메모 작성/수정.                                                                                                                          |
|                     | GET    | /users/{userId}/memo                    | -                                                                                        | UserMemoDto                      | ADMIN, SYSTEM_ADMIN | **신규.** 특정 회원 메모 조회.                                                                                                                                      |
| **Cancel/Refund**   | GET    | /enrollments/cancel-requests            | status=REQ, pageable                                                                     | Page<CancelRequestAdminDto>      | ADMIN, SYSTEM_ADMIN | 취소 요청 목록 조회.                                                                                                                                                |
|                     | POST   | /enrollments/{enrollId}/approve-cancel  | `{ adminComment: "...", manualUsedDays?: number}`                                        | EnrollAdminResponseDto (Updated) | ADMIN, SYSTEM_ADMIN | 취소 요청 승인 및 환불 처리 (PG 연동). **관리자가 manualUsedDays 전달 시 해당 값을 기준으로 환불액 재계산.**                                                        |
|                     | POST   | /enrollments/{enrollId}/deny-cancel     | `{ adminComment: "..." }`                                                                | EnrollAdminResponseDto (Updated) | ADMIN, SYSTEM_ADMIN | 취소 요청 거부.                                                                                                                                                     |
| **Payment**         | GET    | /payments                               | enrollId, userId, tid, period, status, pageable                                          | Page<PaymentAdminDto>            | ADMIN, SYSTEM_ADMIN | 결제 및 환불 내역 조회.                                                                                                                                             |
|                     | POST   | /payments/{paymentId}/manual-refund     | `{ amount, reason, adminNote: "..." }`                                                   | PaymentAdminDto (Updated)        | ADMIN, SYSTEM_ADMIN | 수동 환불 처리 (DB만 처리 또는 PG사 수동 처리 후 기록).                                                                                                             |
| **System**          | GET    | /system/logs/cron                       | jobName, pageable                                                                        | Page<CronLogDto>                 | SYSTEM_ADMIN        | 배치 작업 로그 조회.                                                                                                                                                |
|                     | GET    | /system/logs/webhook/kispg              | date, tid, pageable                                                                      | Page<WebhookLogDto>              | SYSTEM_ADMIN        | KISPG Webhook 수신 로그 조회.                                                                                                                                       |

---

## 4. 주요 DTO (발췌)

```json
// LessonDto (관리자용 Lesson 생성/수정/조회 시 사용)
{
  "lessonId": 320,
"title": "초급반 (오전)",
"instructorName": "김철수 강사",
"lessonTime": "09:00-09:50 (월수금)", // 상세 스케줄 정보 (텍스트 또는 별도 객체 구조화 가능)
  "startDate": "2025-07-01",
  "endDate": "2025-07-30",
  "capacity": 20,
  "price": 65000,
  "status": "OPEN"   // OPEN | CLOSED | ONGOING | COMPLETED
}

// LockerInventoryDto (관리자용 조회 및 수정 응답)
{
"gender": "MALE",
"totalQuantity": 100,
"usedQuantity": 60, // 시스템 계산
"availableQuantity": 40 // total - used
}

// EnrollAdminResponseDto (관리자용 신청자 목록/상세 조회 시 사용)
{
"enrollId": 101,
"lessonTitle": "중급반 (저녁)",
"payStatus": "PAID", // PAID, UNPAID, PAYMENT_TIMEOUT, REFUNDED, CANCELED_UNPAID, REFUND_PENDING_ADMIN_CANCEL 등
  "usesLocker": true,
"userName": "김수영",
"userId": "swimKim",
"userPhone": "010-1234-5678",
"isRenewal": false,
"discountInfo": {
"type": "장애인할인",
"status": "APPROVED",
"approvedAt": "2025-07-15T10:00:00Z",
"adminComment": "서류 확인 완료"
},
"cancelRequestInfo": {
"status": "REQ",
"reason": "개인 사정",
"requestedAt": "2025-07-20T14:30:00Z",
"calculatedRefundAmount": 45000,
"manualUsedDays": null,
"finalRefundAmount": 45000,
"adminCommentForCancel": "승인 처리됨."
},
"paymentInfo": {
"tid": "kistest_xxxxxxxx",
"paidAmount": 70000,
"paidAt": "2025-07-01T09:00:00Z"
}
}

// TemporaryEnrollmentRequestDto (신규)
{
"lessonId": 123, // 현재 필터링된 강습 ID 또는 선택된 강습 ID
"userName": "홍길동", // 필수
"userPhone": "010-1234-5678", // 선택적 또는 필수 (정책에 따라)
"usesLocker": false, // 기본값 false, 또는 관리자 선택 UI 제공
"memo": "오프라인 현장 접수" // 선택적
}

// UserMemoDto (신규)
{
"userId": "swimKim",
"memoContent": "2025년 8월 재수강 문의 있었음.",
"updatedAt": "2025-07-18T11:00:00Z",
"updatedBy": "admin_park"
}

// CancelRequestAdminDto 와 PaymentAdminDto는 EnrollAdminResponseDto에 통합되거나,
// 기존 정의가 있다면 해당 정의를 따르되, 필요한 정보가 EnrollAdminResponseDto에 포함되도록 함.
```

---

## 5. DB 추가·변경 필드

(기존 내용 대부분 유지. `lesson` 테이블에 `instructor_name`, `lesson_time` 등 필드 추가 고려)

| 테이블               | 필드                                                                                                                     | 설명                                                                                                        |
| -------------------- | ------------------------------------------------------------------------------------------------------------------------ | ----------------------------------------------------------------------------------------------------------- |
| **lesson**           | `instructor_name` VARCHAR(50)                                                                                            | (추가 고려) 강사명                                                                                          |
|                      | `lesson_time` VARCHAR(100)                                                                                               | (추가 고려) 수업 시간 설명 (예: "09:00-09:50 (월수금)")                                                     |
|                      | status VARCHAR(20)                                                                                                       | 관리자 수동 마감. 상태값: OPEN, CLOSED, ONGOING, COMPLETED                                                  |
|                      | `male_locker_cap` INT, `female_locker_cap` INT                                                                           | **[제거됨]** 강습별 성별 라커 최대 할당 수 (글로벌 `locker_inventory`로 대체)                               |
| **payment**          | `tid` VARCHAR(30)                                                                                                        | **KISPG 거래번호**                                                                                          |
|                      | `paid_amt` INT                                                                                                           | **KISPG 초기 승인 총액**                                                                                    |
|                      | `refunded_amt` INT DEFAULT 0                                                                                             | **KISPG 누적 환불액**                                                                                       |
|                      | `refund_dt` DATETIME                                                                                                     | **KISPG 마지막 환불 시각**                                                                                  |
|                      | `lesson_amount` INT NULL                                                                                                 | **(신규/변경)** 결제된 강습료 (PG에서 받은 총액 중)                                                         |
|                      | `locker_amount` INT NULL                                                                                                 | **(신규/변경)** 결제된 사물함료 (PG에서 받은 총액 중)                                                       |
| **enroll**           | `uses_locker` BOOLEAN                                                                                                    | 사물함 사용 신청 여부 (결제 시 확정)                                                                        |
|                      | `pay_status` VARCHAR(50)                                                                                                 | `UNPAID`, `PAID`, `PARTIALLY_REFUNDED`, `CANCELED_UNPAID`, `REFUND_PENDING_ADMIN_CANCEL` (VARCHAR(50) 권장) |
|                      | `expire_dt` DATETIME                                                                                                     | 결제 페이지 접근 및 시도 만료 시간 (신청 시점 + 5분)                                                        |
|                      | `remain_days` INT                                                                                                        | **(용도 변경/검토)** 취소 시 계산된 잔여일수 또는 실제 사용일수 (`days_used_for_refund` 로 대체 고려)       |
|                      | `discount_type` VARCHAR(50) NULL                                                                                         | **(신규)** 적용된 할인 종류 (예: "다자녀", "장애인")                                                        |
|                      | `discount_status` VARCHAR(20) NULL                                                                                       | **(신규)** 할인 승인 상태 (Enum: PENDING, APPROVED, DENIED)                                                 |
|                      | `discount_approved_at` DATETIME NULL                                                                                     | **(신규)** 할인 승인/거부 시각                                                                              |
|                      | `discount_admin_comment` VARCHAR(255) NULL                                                                               | **(신규)** 할인 처리 관련 관리자 메모                                                                       |
|                      | `cancel_requested_at` DATETIME NULL                                                                                      | **(신규)** 사용자 취소 요청 시각                                                                            |
|                      | `cancel_processed_at` DATETIME NULL                                                                                      | **(신규)** 관리자 취소 처리 시각                                                                            |
|                      | `days_used_for_refund` INT NULL                                                                                          | **(신규)** 환불 계산 시 적용된 사용일수 (관리자 수정 가능)                                                  |
|                      | `calculated_refund_amount` INT NULL                                                                                      | **(신규)** 시스템이 계산한 최초 예상 환불액                                                                 |
|                      | `penalty_amount_lesson` INT NULL                                                                                         | **(신규)** 수강료 위약금                                                                                    |
|                      | `penalty_amount_locker` INT NULL                                                                                         | **(신규)** 사물함 위약금                                                                                    |
|                      | `final_refund_amount` INT NULL                                                                                           | **(신규)** 최종 확정된 환불액                                                                               |
|                      | `admin_cancel_comment` VARCHAR(255) NULL                                                                                 | **(신규)** 관리자 직접 취소 시 사유                                                                         |
| **locker_inventory** | `gender` (PK), `total_quantity`, `used_quantity`                                                                         | 전체 사물함 재고 관리                                                                                       |
| **user_memo**        | `memo_id` BIGINT PK, `user_uuid` VARCHAR, `memo_content` TEXT, `created_at`, `updated_at`, `updated_by_admin_id` VARCHAR | **(신규 테이블)** 사용자 관련 관리자 메모                                                                   |
| **user**             | `is_temporary` BOOLEAN DEFAULT FALSE                                                                                     | **(추가 고려)** 임시 등록 사용자인지 여부. `username`은 규칙에 따라 자동 생성될 수 있음.                    |

---

## 6. 비즈니스 룰 (CMS Admin)

| 구분                             | 내용                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                            |
| -------------------------------- | --------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| **강습(Lesson) 관리**            | **ADMIN**은 강습의 모든 정보(명칭, 강사, 기간, 시간, 정원, 가격 등)를 생성(Create), 조회(Read), 수정(Update), 삭제(Delete)할 수 있다. 강습 스케줄(시작일, 종료일, 수업시간 설명) 변경이 가능하다. 강습 상태(`OPEN`, `CLOSED`, `ONGOING`, `COMPLETED`)를 수동으로 변경할 수 있다. 특정 강습을 기준으로 다음 기간의 강습을 복제(Clone)하여 쉽게 생성할 수 있다.                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                   |
| **라커(Locker) 재고 관리**       | **ADMIN**은 성별(`MALE`/`FEMALE`) 전체 사물함의 총 수량(`total_quantity`)을 `locker_inventory` 테이블에서 직접 수정할 수 있다. 현재 사용 중인 사물함 수(`used_quantity`)는 시스템에 의해 자동으로 계산되며, 관리자는 이 값을 조회하여 잔여 사물함 수를 파악한다. (개별 라커 배정/회수는 사용자 결제/취소 및 배치 작업 로직에 따름)                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                              |
| **신청자(Enroll) 관리**          | **ADMIN**은 특정 강습의 신청자 목록 또는 전체 신청 내역을 다양한 조건(사용자, 결제 상태, **연월** 등)으로 검색하고 조회할 수 있다. 신청자 상세 정보(개인 정보, 신청 상태, 결제 정보, 사물함 사용 여부, **할인 정보** 등)를 확인할 수 있다. **관리자는 신청자 목록에서 특정 회원의 이름을 클릭하여 해당 회원에 대한 관리자 메모를 확인하거나 새로 작성/수정할 수 있다. 관리자는 '관리자 취소' 기능을 통해 특정 신청 건을 즉시 취소시킬 수 있다. 이때, 신청 건의 `status`는 `CANCELED_BY_ADMIN`으로, `payStatus`는 `REFUND_PENDING_ADMIN_CANCEL`로 변경된다. 해당 상태의 신청 건은 이후 별도의 환불 처리(예: KISPG 수동 환불 또는 배치 작업)가 필요하다.** (관리자가 직접 신청 상태를 변경하는 것은 KISPG 연동 및 데이터 정합성을 위해 주의해야 하며, 주로 조회 및 확인 목적으로 사용)                                                                                                                                                                                                                            |
| **임시 등록 관리**               | **ADMIN**은 '임시 등록' 기능을 통해 오프라인 결제자 등의 정보를 시스템에 수동으로 입력할 수 있다. <br/> - **입력 정보**: 최소 정보로 강습 ID, 성명, 연락처, (선택적) 메모, 사물함 사용 여부 등을 받을 수 있다. <br/> - **사용자 처리**: 시스템에 정식 계정이 없는 경우, 백엔드는 임시 사용자 프로필을 생성하거나 `enroll` 레코드에 사용자 정보를 직접 기록할 수 있다. 이 경우 `user_id`는 특정 임시 ID를 가리키거나, `enroll` 테이블에 직접 `user_name`, `user_phone` 등을 저장하고 `user_id`는 null일 수 있다. `userLoginId`는 표시되지 않거나 "임시등록" 등으로 표시될 수 있다. <br/> - **상태**: 생성된 신청 건의 `payStatus`는 `PAID_OFFLINE` (또는 유사한 상태, 예: `TEMP_PAID`)으로 설정하여 온라인 결제 건과 구분한다. `enroll.status`는 `APPLIED` (또는 `TEMP_APPLIED`)로 설정한다. <br/> - **중복 방지**: 백엔드는 동일 강습에 대해 이름과 전화번호를 기준으로 중복 등록을 방지하는 로직을 포함할 수 있다. <br/> - 등록된 정보는 일반 신청자와 마찬가지로 신청자 목록에 표시되어 정원 관리에 포함된다. |
| **취소/환불 처리**               | 사용자가 수강 시작일 이후 취소 요청한 건에 대해 **ADMIN**이 검토 후 승인 또는 반려한다. 승인 시, 시스템은 문서에 정의된 환불 정책(일일 사용료 차감)에 따라 자동으로 기본 환불액을 계산하여 관리자 화면에 제시한다. **관리자는 필요한 경우 '실사용일수'를 직접 수정 입력할 수 있으며, 이 경우 시스템은 수정된 사용일수를 기준으로 환불액을 즉시 재계산하여 보여준다.** 관리자가 최종 환불액을 확인하고 '취소 승인' 버튼을 클릭하면, 해당 금액으로 KISPG에 부분 취소를 요청한다. 처리 결과에 따라 `Enroll` 및 `Payment` 테이블의 상태가 업데이트된다. **관리자는 할인회원 신청 건에 대해 증빙자료 검토 후 할인 적용 상태(승인/거절/대기)를 변경할 수 있으며, 승인 시각이 기록된다.**                                                                                                                                                                                                                                                                                                                              |
| **결제(Payment) 관리**           | **ADMIN**은 시스템 내 모든 결제 및 환불 거래 내역을 상세히 조회하고 KISPG 거래 ID 등으로 검색할 수 있다. PG사 시스템 장애 또는 특이 케이스 발생 시, 수동으로 결제 정보를 기록하거나 환불 처리 후 DB 상태를 맞추는 기능을 예외적으로 사용할 수 있다. (이때 `manual-refund` API 사용)                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                             |
| **강습 마감 (기존 유지)**        | (lesson.capacity - (PAID 수강생 + 만료 전 UNPAID 수강생(결제 페이지 접근 슬롯 점유 중))) <= 0 또는 관리자가 `CLOSED` → 프론트 '마감' 표시.                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                      |
| **부분 환불 (기존 유지 상세화)** | `approve-cancel` 호출 시, 서버는 다음 규칙에 따라 환불액을 자동 계산 후 KISPG 부분 취소 API (`partCanFlg=1`, `canAmt=환불액`) 호출. `payment.refunded_amt` 누적, `enroll.pay_status` 등 업데이트. KISPG `tid` 필수.<br/>**환불액 계산 (강습 시작 후 사용자 요청 또는 관리자 승인 시):**<br/>1. **사용일수 계산**: 관리자가 `manualUsedDays`를 입력하면 해당 값을 사용. 입력하지 않으면 `(취소요청일자 또는 관리자 승인일자 - 강습시작일자 + 1)`로 계산 (취소 요청일 당일까지 사용으로 간주). 1초라도 경과 시 1일 사용으로 처리.<br/>2. **강습료 사용분 차감**: `강습료 사용액 = 사용일수 * 3,500원`.<br/>3. **사물함 사용료**: 환불되지 않음.<br/>4. **위약금**: 없음.<br/>5. **최종 환불액**: `(결제된 강습료 - 강습료 사용액)`. 단, 환불액이 0보다 작을 경우 0으로 처리. <br/> _강습 시작 전 취소 시에는 별도 규정(예: 전액 환불 또는 PG사 수수료 제외 후 환불)이 적용됨._                                                                                                                                    |
| **기타 기존 룰 유지**            | 라커 재고 관리, 재수강 시 사물함 처리, KISPG 연동 보안, 트랜잭션 관리 등 기존 정의된 관리자 관련 비즈니스 룰은 계속 유효함.                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                     |

---

## 7. 배치 & 모니터링

| Job                                        | 주기     | 관리자 UI      | 설명                                                                                                                                          |
| ------------------------------------------ | -------- | -------------- | --------------------------------------------------------------------------------------------------------------------------------------------- |
| pg-webhook sync                            | 실시간   | AD-08 Cron Log | KISPG Webhook (`payment-notification`) 수신 및 처리. 관리자 UI에서 로그 확인 가능.                                                            |
| renewal-notifier                           | daily    | 스케줄 리스트  |                                                                                                                                               |
| **payment-timeout-sweep**                  | 1-5 min  | AD-08 Cron Log | KISPG 결제 페이지 만료 건 처리.                                                                                                               |
| **cancel-retry**                           | 5 min    | AD-08 Cron Log | **`pending` 상태의 KISPG 취소 실패 건 자동 재시도 (최대 3회)**                                                                                |
| **pg-reconcile**                           | daily    | AD-08 Cron Log | **KISPG `/v2/order` API로 전일 KISPG 결제/취소 내역과 DB 대사 작업**                                                                          |
| **lesson-completion-locker-release-sweep** | 하루 1회 | AD-08 Cron Log | 종료된 강습에 배정된 사물함 자동 회수 처리. **재수강으로 인해 `lockerAllocated=false` 처리된 이전 강습 건은 이 배치에서 중복 처리되지 않음.** |

Grafana Dashboard → 신청·매출·라커 KPI 실시간 파이프. (KPI에는 `PAYMENT_TIMEOUT` 건 제외, KISPG `paid_amt` 기준). **"KISPG 부분취소 실패율 < 0.5%" 알람 추가.** "KISPG Webhook 수신 지연/실패" 알람 추가.

---

## 8. 테스트 케이스 (Admin)

| ID            | 시나리오                                                         | 예상 결과                                                                                             |
| ------------- | ---------------------------------------------------------------- | ----------------------------------------------------------------------------------------------------- |
| **CMS-L-01**  | ADMIN이 새 강습 생성 (모든 필드 정상 입력)                       | 강습 정상 생성, 목록에 표시, 상태 OPEN.                                                               |
| **CMS-L-02**  | ADMIN이 기존 강습의 스케줄(기간, 시간), 정원, 가격, 상태 등 수정 | 정보 정상 변경, 강습 상세 및 목록에 반영.                                                             |
| **CMS-L-03**  | ADMIN이 특정 강습 복제 (다음 달 시작일 지정)                     | 새 강습이 지정된 시작일로 복제되어 생성.                                                              |
| **CMS-LO-01** | ADMIN이 남성 라커 총 수량을 100에서 120으로 수정.                | `locker_inventory`의 남성 `total_quantity`가 120으로 변경. 사용량/가용량은 이에 따라 재계산되어 표시. |
| **CMS-E-01**  | ADMIN이 특정 강습의 신청자 목록 조회 (결제완료 필터)             | 해당 강습의 결제 완료된 신청자 목록 및 상세 정보(결제내역 포함) 표시.                                 |
| **CMS-CR-01** | ADMIN이 취소 요청건 승인 (강습 시작 후, 환불 발생 케이스)        | 시스템이 환불액 자동 계산, KISPG 연동(가상), `Enroll` 및 `Payment` 상태 정상 업데이트.                |
| **CMS-P-01**  | ADMIN이 특정 TID로 결제 내역 검색                                | 해당 TID의 결제 상세 정보 표시.                                                                       |
| **CMS-E-02**  | ADMIN이 '임시 등록' 기능을 사용하여 오프라인 신청자 정보를 입력  | API 호출 성공, 신청자 목록에 해당 정보 추가 (예: 상태 'PAID_OFFLINE'). 정원 현황에 반영.              |

---

## 9. 배포 체크리스트

1.  **SYSTEM_ADMIN**, **ADMIN** 역할 초기 계정 발급 및 권한 확인.
2.  KISPG 결제 Webhook URL (`/api/v1/kispg/payment-notification`) → 방화벽 허용·Slack 알림 연결. **KISPG Webhook IP (`1.233.179.201`) 화이트리스트 등록.**
3.  Cron Log 테이블 ROLLOVER 정책(30 일) 적용. 모든 배치 (`payment-timeout-sweep`, KISPG `cancel-retry`, `pg-reconcile`, `lesson-completion-locker-release-sweep`) 등록 및 모니터링.
4.  Grafana Dashboard ID & 데이터소스 연결 테스트 (KISPG 결제 상태별 통계 정확성 확인). **KISPG 부분취소 실패율 및 Webhook 오류 알람 설정.**
5.  **KISPG 연동용 `merchantKey` 등 설정 정보 안전하게 배포.**

---

### ✅ 운영자 혜택 (React Admin SPA 기반)

- **대시보드 한눈에**: 잔여 좌석·라커·매출 실시간 파악 (결제 타임아웃 건 자동 반영) - React 컴포넌트 기반 대시보드 위젯 활용.
- **직관적 관리**: 강습, 신청자, 결제 정보 등을 통합된 CMS 내에서 편리하게 관리.
- **부분 환불 자동화**: PG API 연동으로 회계 오차 최소화 - 관리자 화면 내에서 API 호출 및 결과 피드백. **취소 승인 시 환불액 자동 계산 및 표시 (수정 불가).**

---
