\***\*- 🏊‍♀️ 수영장 **관리자 백오피스\*\* — 관리자-측 개발문서
_(Spring Boot REST API + React Admin SPA 기준)_

---

## 0. 문서 목표

| 항목      | 내용                                                                                                     |
| --------- | -------------------------------------------------------------------------------------------------------- |
| 범위      | **운영자**가 강습·사물함·신청·결제(환불)·통계를 실시간으로 관리하는 백오피스                             |
| 달성 지표 | ① 5 분 내 취소·환불 처리 ② 실시간 잔여 좌석 Sync ③ 월 결제 정산 100 % 일치 ④ 모든 관리 작업 3 click 이내 |

---

## 1. 역할(Role) 정의

| ROLE              | 설명             | 접근 화면                  |
| ----------------- | ---------------- | -------------------------- |
| **SUPER_ADMIN**   | 전체 설정·권한   | Dashboard + 모든 메뉴      |
| **PROGRAM_ADMIN** | 강습·사물함 CRUD | Lesson, Locker             |
| **FINANCE_ADMIN** | 결제·환불 승인   | Payment, Cancel Review     |
| **CS_AGENT**      | 신청 현황 모니터 | Enroll List, Cancel Review |

---

## 2. 백오피스 화면 구조

| ID        | 메뉴          | 주요 UI                                  | 설명                                                                                                                                                                 |
| --------- | ------------- | ---------------------------------------- | -------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| **AD-01** | Dashboard     | KPI Card(신청·좌석·매출) 잔여 라커 Donut | 실시간 운영 지표 (매출에는 `PAID` 건만, 좌석에는 `PAID` + 유효 `UNPAID`건 고려)                                                                                      |
| **AD-02** | Lesson 관리   | DataGrid + 복제 버튼                     | 강습명·기간·정원·가격 CRUD. 강습별 남녀 라커 정원(`male_locker_cap` 등) 설정.                                                                                        |
| **AD-03** | Locker 관리   | 성별 총 라커 수, 현재 사용량 관리        | 전체 라커 재고(`locker_inventory`) 관리 (예: 남/여 총량 수정)                                                                                                        |
| **AD-04** | Enroll 현황   | Table(Status Badge) + Search             | `APPLIED` (내부 `payStatus`: `PAID`, `PARTIALLY_REFUNDED`, `UNPAID` (결제만료 전), `PAYMENT_TIMEOUT`), `CANCELED` 리스트. 사물함 사용 여부 표시. `remain_days` 표시. |
| **AD-05** | Cancel Review | Drawer: 출석·환불 % 슬라이더             | 개강 後 취소 승인/반려. **환불액 자동계산 (`paid_amt`, `remain_days` 기반) 및 KISPG 부분취소 연동.**                                                                 |
| **AD-06** | Payment 관리  | 결제·환불 탭, KISPG TID, 엑셀 DL         | 결제 승인·부분/전액 환불. **`tid`, `paid_amt`, `refunded_amt` 등 KISPG 관련 정보 표시.** KISPG Webhook (`payment-notification`)으로 자동 처리. 수동 개입은 예외적.   |
| **AD-07** | 통계·리포트   | Bar & Line Chart + XLS Export            | 월별 매출·이용자·라커 사용률 (KISPG `paid_amt` 기준)                                                                                                                 |
| **AD-08** | 시스템 설정   | 권한 매핑, Cron 로그                     | 배치(`payment-timeout-sweep`, KISPG `cancel-retry`, `pg-reconcile`)·Webhook (`kispg/payment-notification`) 모니터                                                    |

---

## 3. API 상세

### 3-1. 공통

| 요소     | 값                                                        |
| -------- | --------------------------------------------------------- |
| Base URL | `/api/v1/admin`                                           |
| 인증     | JWT + ROLE 체크                                           |
| 응답     | `status` int · `data` · `message`                         |
| 에러코드 | 400 Validation · 403 NoAuth · 404 NotFound · 409 Conflict |

### 3-2. 엔드포인트

| 그룹                  | Method | URL                                         | Req Body/QS               | Resp                         | 권한                                 | 비고                                                                                                                          |
| --------------------- | ------ | ------------------------------------------- | ------------------------- | ---------------------------- | ------------------------------------ | ----------------------------------------------------------------------------------------------------------------------------- |
| **Lesson**            | GET    | /swimming/lessons                           | pageable                  | Page<LessonDto>              | PROGRAM_ADMIN, SUPER_ADMIN           | 모든 강습 목록 조회 (상태 필터 시: OPEN, CLOSED, ONGOING, COMPLETED)                                                          |
|                       | GET    | /swimming/lessons/status/{status}           | pageable                  | Page<LessonDto>              | PROGRAM_ADMIN, SUPER_ADMIN, CS_AGENT | 특정 상태(OPEN, CLOSED, ONGOING, COMPLETED) 강습 목록 조회                                                                    |
|                       | GET    | /swimming/lessons/{lessonId}                | -                         | LessonDto                    | PROGRAM_ADMIN, SUPER_ADMIN, CS_AGENT | 강습 상세 조회                                                                                                                |
|                       | POST   | /swimming/lesson                            | LessonDto                 | Created                      | PROGRAM_ADMIN, SUPER_ADMIN           | 새 강습 생성 (DTO에서 `male_locker_cap`, `female_locker_cap` **제거됨**)                                                      |
|                       | PUT    | /swimming/lesson/{id}                       | LessonDto                 | Updated                      | PROGRAM_ADMIN, SUPER_ADMIN           | 강습 수정 (DTO에서 `male_locker_cap`, `female_locker_cap` **제거됨**)                                                         |
|                       | POST   | /swimming/lesson/{id}/clone                 | `{month}`                 | New LessonId                 | PROGRAM_ADMIN, SUPER_ADMIN           | 강습 복제 (경로 확인 필요)                                                                                                    |
| **Locker Inventory**  | GET    | /swimming/lockers/inventory                 | -                         | List<LockerInventoryDto>     | PROGRAM_ADMIN, SUPER_ADMIN           | 전체 성별 라커 재고 현황 조회                                                                                                 |
|                       | PUT    | /swimming/lockers/inventory/{gender}        | LockerInventoryUpdateDto  | Updated                      | PROGRAM_ADMIN, SUPER_ADMIN           | 특정 성별 라커 총 수량 수정                                                                                                   |
| _(Old Locker System)_ | GET    | /swimming/lockers                           | zone,gender               | List<LockerDto>              | PROGRAM_ADMIN, SUPER_ADMIN           | (Deprecated?) 개별 라커 목록 조회. 현재 시스템은 재고 기반.                                                                   |
|                       | POST   | /swimming/locker                            | LockerDto                 | Created                      | PROGRAM_ADMIN, SUPER_ADMIN           | (Deprecated?) 개별 라커 생성.                                                                                                 |
|                       | PUT    | /swimming/locker/{id}                       | LockerDto                 | Updated                      | PROGRAM_ADMIN, SUPER_ADMIN           | (Deprecated?) 개별 라커 수정.                                                                                                 |
| **Enroll**            | GET    | /swimming/enrolls                           | status,lessonId, pageable | Page<EnrollAdminResponseDto> | CS_AGENT, SUPER_ADMIN                | 신청 내역 조회 (DTO에 `usesLocker`, `payStatus`(`PAYMENT_TIMEOUT` 포함) 필드 포함). `status`는 `payStatus` 기준 필터링 가능.  |
| **Cancel**            | GET    | /swimming/enrolls/cancel-requests           | status=PENDING, pageable  | Page<CancelRequestDto>       | CS_AGENT, SUPER_ADMIN                | 취소 요청 목록. **DTO에 `paid_amt`, `calculated_refund_amt`, KISPG `tid` 포함.**                                              |
|                       | POST   | /swimming/enrolls/{enrollId}/approve-cancel | `{ adminComment: "..." }` | 200                          | FINANCE_ADMIN, SUPER_ADMIN           | 취소 요청 승인. **서버에서 잔여일수/환불액 자동 계산 후 KISPG 부분취소 API 호출. `payment.refunded_amt` 업데이트.**           |
|                       | POST   | /swimming/enrolls/{enrollId}/deny-cancel    | `{comment}`               | 200                          | CS_AGENT, SUPER_ADMIN                | 취소 요청 거부                                                                                                                |
| **Payment**           | GET    | /payment                                    | period,status,pg_tid      | List<PaymentAdminDto>        | FINANCE_ADMIN                        | (경로 /swimming/payment 등 확인 필요). **`PaymentAdminDto`에 KISPG `tid`, `paid_amt`, `refunded_amt`, `refund_dt` 포함.**     |
|                       | POST   | /payment/{paymentId}/manual-refund          | `{ amount, reason, tid }` | 200                          | FINANCE_ADMIN, SUPER_ADMIN           | **(주의) KISPG와 별개로 DB만 환불 처리 또는 KISPG 수동 처리 후 DB 반영. 비상시 또는 KISPG 어드민에서 이미 처리된 건 기록용.** |
| **Stats**             | GET    | /stats/summary                              | month                     | SummaryDto                   | FINANCE_ADMIN                        | (경로 /swimming/stats 등 확인 필요, KISPG `paid_amt` 기준 집계)                                                               |
| **System**            | GET    | /system/cron-log                            | jobName                   | List                         | SUPER_ADMIN                          | (경로 /swimming/system 등 확인 필요). `payment-timeout-sweep`, KISPG `cancel-retry`, `pg-reconcile` 로그 조회.                |
|                       | GET    | /system/webhook-log/kispg                   | date, tid                 | List                         | SUPER_ADMIN                          | KISPG `payment-notification` Webhook 수신 로그 조회.                                                                          |

---

## 4. 주요 DTO (발췌)

```json
// LessonDto (기존과 유사, maleLockerCap, femaleLockerCap 등 포함)
{
  "lessonId": 320,
  "title": "초급반",
  "startDate": "2025-07-01",
  "endDate": "2025-07-30",
  "capacity": 20,
  "price": 65000,
  "status": "OPEN"   // OPEN | CLOSED | ONGOING | COMPLETED
}

// EnrollAdminResponseDto (swim-user.md의 EnrollResponseDto와 유사하나 관리자 정보 추가 가능)
{
  "enrollId": 9999,
  "userId": "uuid-user-123",
  "userName": "홍길동",
  "status": "APPLIED", // APPLIED, CANCELED 등 Enroll의 주 상태
  "payStatus": "PAID", // UNPAID, PAID, PARTIALLY_REFUNDED, PAYMENT_TIMEOUT, CANCELED_UNPAID
  "usesLocker": true,
  "userGender": "FEMALE",
  "createdAt": "2025-05-16T09:10:00",
  "expireDt": "2025-05-16T09:15:00", // 결제 만료 시각
  "lessonTitle": "초급반",
  "lessonId": 101,
  "payment_tid": "kistest00m...", // (추가) KISPG TID
  "paid_amt": 70000, // (추가) KISPG 초기 결제액
  "refunded_amt": 0, // (추가) KISPG 누적 환불액
  "remain_days_at_cancel": null // (추가) 취소 시점 계산된 잔여일수
}

// CancelRequestDto (관리자용)
{
  "requestId": 123,
  "enrollId": 9999,
  "userId": "uuid-user-123",
  "userName": "홍길동",
  "lessonTitle": "초급반",
  "paid_amt": 70000, // KISPG 초기 결제 금액
  "calculated_refund_amt": 35000, // 시스템 계산 환불 예상액 (KISPG 기준)
  "requested_at": "2025-06-10T14:00:00",
  "reason": "이사로 인해 수강 불가",
  "kispg_tid": "kistest00m..." // (추가) KISPG TID 참조용
}

// PaymentAdminDto (관리자용)
{
  "paymentId": 1,
  "enrollId": 9999,
  "tid": "kistest00m...",
  "paid_amt": 70000,
  "refunded_amt": 35000,
  "status": "PARTIALLY_REFUNDED",
  "paid_at": "2025-05-16T09:12:00",
  "last_refund_dt": "2025-06-10T15:00:00",
  "pgProvider": "KISPG"
}

```

---

## 5. DB 추가·변경 필드

(참고: `lesson` 테이블의 전체 DDL은 `swim-user.md` 또는 프로젝트 DDL 파일을 기준으로 하며, `registration_end_date` 컬럼을 포함하지 않습니다. `locker_inventory` 테이블이 추가되었습니다. `enroll` 테이블에 `pay_status`에 `PAYMENT_TIMEOUT`이 추가되고, `expire_dt`의 의미가 변경됩니다.)

| 테이블               | 필드                                             | 설명                                                                             |
| -------------------- | ------------------------------------------------ | -------------------------------------------------------------------------------- |
| **lesson**           | status VARCHAR(20)                               | 관리자 수동 마감. 상태값: OPEN, CLOSED, ONGOING, COMPLETED                       |
|                      | `male_locker_cap` INT, `female_locker_cap` INT   | **[제거됨]** 강습별 성별 라커 최대 할당 수 (글로벌 `locker_inventory`로 대체)    |
| **payment**          | `tid` VARCHAR(30)                                | **KISPG 거래번호**                                                               |
|                      | `paid_amt` INT                                   | **KISPG 초기 승인 총액**                                                         |
|                      | `refunded_amt` INT DEFAULT 0                     | **KISPG 누적 환불액**                                                            |
|                      | `refund_dt` DATETIME                             | **KISPG 마지막 환불 시각**                                                       |
|                      | refund_amount INT, refund_dt DATETIME            | 부분/전액 환불 기록 (KISPG 연동)                                                 |
| **enroll**           | `uses_locker` BOOLEAN                            | 사물함 사용 신청 여부 (결제 시 확정)                                             |
|                      | `pay_status` VARCHAR(20)                         | `UNPAID`, `PAID`, `PARTIALLY_REFUNDED`, `CANCELED_UNPAID`, **`PAYMENT_TIMEOUT`** |
|                      | `expire_dt` DATETIME                             | 결제 페이지 접근 및 시도 만료 시간 (신청 시점 + 5분)                             |
|                      | `remain_days` INT                                | **취소 시 계산된 잔여일수 (감사용)**                                             |
| **locker_inventory** | `gender` (PK), `total_quantity`, `used_quantity` | 전체 사물함 재고 관리 (이 DDL은 swim-user.md 참조)                               |

---

## 6. 비즈니스 룰 (Admin)

| 구분                       | 내용                                                                                                                                                                                                                                                                                                                                                                         |
| -------------------------- | ---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| **강습 마감**              | (lesson.capacity - (PAID 수강생 + 만료 전 UNPAID 수강생)) <= 0 또는 관리자가 `CLOSED` → 프론트 '마감' 표시.                                                                                                                                                                                                                                                                  |
| **부분 환불**              | `approve-cancel` 호출 시, 서버는 **잔여일수/환불액 자동 계산 (`max(0, EndDate – Today + 1)` 등) 후 KISPG 부분 취소 API (`partCanFlg=1`, `canAmt=환불액`) 호출.** `payment.refunded_amt` 누적, `enroll.pay_status` 등 업데이트. KISPG `tid` 필수.                                                                                                                             |
| **취소 승인**              | 개강 후 취소 요청 `PENDING` → 승인 시 `enroll.status=CANCELED`, `enroll.pay_status`는 환불 상태로 변경 (예: `PARTIALLY_REFUNDED`, `CANCELED`). **위 "부분 환불" 로직과 연동.**                                                                                                                                                                                               |
| **라커 재고 관리**         | 관리자는 `locker_inventory`에서 성별 전체 라커 수를 설정. 사용자가 강습 신청 후 결제 페이지에서 `uses_locker`를 선택하고 KISPG Webhook을 통해 결제가 최종 확인되면, 해당 성별의 글로벌 `locker_inventory.used_quantity`가 업데이트됩니다. `PAYMENT_TIMEOUT`된 신청건의 라커 예약 시도는 자동으로 반영되지 않거나, 해당 `used_quantity`가 롤백됩니다 (세부 구현에 따라 다름). |
| **`PAYMENT_TIMEOUT` 처리** | 관리자는 `PAYMENT_TIMEOUT` 상태의 신청 목록을 조회하고, 필요한 경우 후속 조치(예: 사용자에게 알림)를 할 수 있다. 이 상태의 신청은 KISPG 결제 시도 실패 또는 만료로 간주.                                                                                                                                                                                                     |
| **KISPG 연동 보안**        | KISPG 취소 API 호출 시 `encData` (SHA-256 해시: `mid+ediDate+canAmt+merchantKey`) 사용. KISPG Webhook (`payment-notification`) 수신 시 IP 화이트리스트 (`1.233.179.201`) 및 해시 검증. `merchantKey` 서버 보안 저장.                                                                                                                                                         |
| **트랜잭션 관리**          | KISPG 부분취소는 `payment` 행에 `SELECT ... FOR UPDATE` 잠금 후 KISPG API 호출 및 DB 업데이트를 단일 트랜잭션으로 처리하여 경합 방지. 실패 시 롤백 및 알림. KISPG Webhook 처리 시에도 정원/라커 확인 및 DB 업데이트는 트랜잭션으로 관리.                                                                                                                                     |

---

## 7. 배치 & 모니터링

| Job                       | 주기    | 관리자 UI      | 설명                                                                               |
| ------------------------- | ------- | -------------- | ---------------------------------------------------------------------------------- |
| pg-webhook sync           | 실시간  | AD-08 Cron Log | KISPG Webhook (`payment-notification`) 수신 및 처리. 관리자 UI에서 로그 확인 가능. |
| renewal-notifier          | daily   | 스케줄 리스트  |                                                                                    |
| **payment-timeout-sweep** | 1-5 min | AD-08 Cron Log | KISPG 결제 페이지 만료 건 처리.                                                    |
| **cancel-retry**          | 5 min   | AD-08 Cron Log | **`pending` 상태의 KISPG 취소 실패 건 자동 재시도 (최대 3회)**                     |
| **pg-reconcile**          | daily   | AD-08 Cron Log | **KISPG `/v2/order` API로 전일 KISPG 결제/취소 내역과 DB 대사 작업**               |

Grafana Dashboard → 신청·매출·라커 KPI 실시간 파이프. (KPI에는 `PAYMENT_TIMEOUT` 건 제외, KISPG `paid_amt` 기준). **"KISPG 부분취소 실패율 < 0.5%" 알람 추가.** "KISPG Webhook 수신 지연/실패" 알람 추가.

---

## 8. 테스트 케이스 (Admin)

| ID    | 시나리오                                                     | 예상 결과                                                                                                          |
| ----- | ------------------------------------------------------------ | ------------------------------------------------------------------------------------------------------------------ |
| AD-01 | 강습 정원=0 시 자동 `status=CLOSED` (유효 신청자 고려)       | Lesson 목록 '마감'                                                                                                 |
| AD-02 | 사물함 `is_active=0` 설정 (만약 개별 라커 관리 시나리오라면) | 결제 페이지 라커 드롭다운에 미표시 또는 비활성화                                                                   |
| AD-03 | 부분 환불 70 % 승인                                          | `payment.refunded_amt` = `paid_amt`×0.7 (KISPG API 성공 후). `enroll.pay_status` 변경. KISPG `tid` 사용.           |
| AD-04 | 취소 반려                                                    | enroll.status 그대로, 회원에게 메시지                                                                              |
| AD-05 | Enroll 현황에서 `PAYMENT_TIMEOUT` 상태 조회                  | KISPG 결제 시간 초과된 신청 목록 확인 가능.                                                                        |
| AD-06 | `payment-timeout-sweep` 배치 실행 후                         | 만료된 `UNPAID` 신청이 `PAYMENT_TIMEOUT`으로 변경되고, 해당 신청이 KISPG 결제 페이지에서 사용하려던 라커가 회수됨. |
| AD-07 | KISPG 부분 취소 API 호출 실패 시 (네트워크 오류 등)          | DB 롤백, 관리자 알림. `cancel-retry` 배치가 재시도.                                                                |
| AD-08 | `pg-reconcile` 배치 실행 시 KISPG 내역과 DB 불일치 발견      | 관리자 알림 및 수동 확인 필요.                                                                                     |

---

## 9. 배포 체크리스트

1. `PROGRAM_ADMIN`·`FINANCE_ADMIN` 역할 초기 계정 발급
2. KISPG 결제 Webhook URL (`/api/v1/kispg/payment-notification`) → 방화벽 허용·Slack 알림 연결. **KISPG Webhook IP (`1.233.179.201`) 화이트리스트 등록.**
3. Cron Log 테이블 ROLLOVER 정책(30 일) 적용. `payment-timeout-sweep`, **KISPG `cancel-retry`, `pg-reconcile`** 배치 등록 및 모니터링.
4. Grafana Dashboard ID & 데이터소스 연결 테스트 (KISPG 결제 상태별 통계 정확성 확인). **KISPG 부분취소 실패율 및 Webhook 오류 알람 설정.**
5. **KISPG 연동용 `merchantKey` 등 설정 정보 안전하게 배포.**

---

### ✅ 운영자 혜택 (React Admin SPA 기반)

- **대시보드 한눈에**: 잔여 좌석·라커·매출 실시간 파악 (결제 타임아웃 건 자동 반영) - React 컴포넌트 기반 대시보드 위젯 활용.
- **드래그 + 인라인 편집**: 강습·라커 관리 2배 빠름 - React Admin의 `Datagrid`, `EditButton`, `TextInput` 등 활용.
- **부분 환불 자동화**: PG API 연동으로 회계 오차 0 % - 관리자 화면 내에서 API 호출 및 결과 피드백 (예: React `useState`로 로딩/성공/실패 상태 관리). **취소 승인 시 환불액 자동 계산 및 표시 (수정 불가).**

---
