# 💳 결제 페이지 & PG 연동 상세

## 1. 개요

본 문서는 전용 **결제 페이지**의 기능과 PG(Payment Gateway) 연동에 대해 상세히 설명하며, 수영 강습 신청을 위한 5분 제한 시간제 결제창 및 정원 관리 기능에 중점을 둡니다.

**주요 목표:**

- 원활하고 빠른 결제 경험 제공.
- 시간 제한이 있는 결제 슬롯을 통해 정원에 따른 공정한 강습 접근 기회 보장.
- 신청, 결제, 사물함 배정 데이터의 무결성 유지.

**일반 흐름:**

1.  사용자가 `POST /api/v1/swimming/enroll`을 통해 강습 신청을 시작합니다.
2.  정원 여유가 있는 경우 (결제 완료(`PAID`) 건 + 결제창에서 활성 상태인 미결제(`UNPAID`) 건 고려), API는 `EnrollInitiationResponseDto` ( `enrollId`, `paymentPageUrl`, `paymentExpiresAt` 포함)를 반환합니다.
3.  사용자는 결제 페이지 (`paymentPageUrl`)로 리디렉션됩니다.
4.  결제 페이지는 `GET /api/v1/payment/details/{enrollId}`를 통해 상세 정보를 가져옵니다.
5.  사용자는 강습 정보, 가격, 사물함 옵션(해당하는 경우), 5분 카운트다운 타이머를 확인합니다.
6.  사용자는 옵션을 선택하고 PG 결제를 시작합니다.
7.  PG 결제 성공 시, 결제 페이지는 PG 토큰 및 사물함 선택 사항과 함께 `POST /api/v1/payment/confirm/{enrollId}`를 호출합니다.
8.  백엔드는 결제를 확인하고, 정원/사물함을 재확인한 후 신청을 최종 완료합니다.
9.  5분 타이머가 확인 전에 만료되면 사용자는 리디렉션되고 해당 슬롯은 해제됩니다.

## 2. 결제 페이지 (프론트엔드: P-02)

**URL:** `/payment/process?enroll_id={enrollId}` (예시)

### 2.1. 페이지 로드 및 초기화

1.  URL 쿼리 파라미터에서 **`enrollId` 추출**.
2.  **API 호출:** `GET /api/v1/payment/details/{enrollId}`
    - **성공 시 (`PaymentPageDetailsDto`):**
      - `enrollId`, `lessonTitle`, `lessonPrice`, `userGender`, `lockerOptions`, `amountToPay`, `paymentDeadline` 저장.
      - UI 요소 초기화:
        - 강습명, 기본 가격 표시.
        - `lockerOptions`가 존재하고 `lockerOptions.availableForGender`가 true인 경우:
          - 사물함 선택 UI 표시 (예: "사물함 사용? (+{lockerFee})" 체크박스).
          - 사용자 성별에 맞는 사용 가능한 사물함 수 표시 (예: "여성 사물함: {total - used}개 사용 가능").
        - 총 `amountToPay` 표시.
        - **카운트다운 타이머 시작:** `paymentDeadline`부터. 타이머는 시각적으로 카운트다운(MM:SS)되어야 함.
      - 초기 `amountToPay`, `merchantUid` (`enrollId` + 타임스탬프 또는 백엔드가 `PaymentPageDetailsDto`에서 생성 가능), `lessonTitle`, `userName` 등으로 PG 모듈 (예: 아임포트) 준비.
    - **실패 시:**
      - `404 Enroll Not Found` 또는 `400 PAYMENT_ALREADY_PROCESSED/EXPIRED`: 마이페이지 또는 강습 목록으로 오류 메시지와 함께 리디렉션 (예: "이 결제 링크는 유효하지 않거나 만료되었습니다.").
      - 기타 오류: 일반 오류 메시지 표시.

### 2.2. 사용자 상호작용

1.  **사물함 선택:**
    - 사용자가 "사물함 사용" 체크/해제 시:
      - `totalAmountToPay` = `lessonPrice` + (`wantsLocker` ? `lockerFee` : 0) 재계산.
      - 표시된 총액 업데이트.
      - 필요한 경우 PG 모듈 파라미터 업데이트 (예: `IMP.request_pay` 금액).
2.  **결제 시작 ([결제하기] 버튼 클릭):**
    - 현재 `totalAmountToPay` 및 `wantsLocker` 상태 가져오기.
    - PG SDK의 결제 시작 메소드 호출 (예: `IMP.request_pay({ pg: 'html5_inicis', merchant_uid: 'enroll_{enrollId}_{timestamp}', amount: totalAmountToPay, name: lessonTitle, ... })`).
    - **PG 성공 콜백:**
      - PG로부터 `pg_tid` (또는 `imp_uid`), `merchant_uid` 등 수신.
      - 즉시 백엔드 호출: `POST /api/v1/payment/confirm/{enrollId}` 요청 본문 포함:
        ```json
        {
          "pgToken": "pg_tid_from_pg", // 또는 imp_uid
          "wantsLocker": true // 또는 false, 사용자의 최종 선택 기준
        }
        ```
      - **API 성공 (200 OK):**
        - 카운트다운 타이머 중지.
        - "결제 성공!" 메시지 표시.
        - "내 수강 내역 가기" 버튼/링크 제공 (마이페이지로 리디렉션).
      - **API 실패 (예: 400, 409, 500):**
        - API 응답에서 특정 오류 표시 (예: "사물함이 사용 불가능해졌습니다.", "강습 정원이 초과되었습니다.", "PG 검증 실패.").
        - 타이머가 만료되지 않았고 오류가 복구 가능한 경우(예: PG 일시적 문제) 사용자는 PG 결제 재시도 가능.
        - `402 PAYMENT_EXPIRED` (이상적으로는 타이머에 의해 포착되어야 함): 타임아웃 메시지와 함께 리디렉션.
    - **PG 실패/사용자 취소:**
      - "결제에 실패했거나 취소되었습니다." 표시.
      - 타이머가 만료되지 않은 경우 사용자는 재시도 가능.

### 2.3. 카운트다운 타이머 및 타임아웃

- 타이머는 매초 시각적으로 업데이트됨.
- **타이머 만료 시 (00:00 도달):**
  - 결제 버튼 비활성화.
  - 토스트/알림 표시: "5분의 시간이 경과되어 결제 이전 창으로 이동합니다."
  - 사용자 리디렉션: `window.history.back()` 또는 강습 목록과 같은 기본 페이지로 이동.
  - 백엔드 `payment-timeout-sweep` 배치가 결국 `enroll.pay_status`를 `PAYMENT_TIMEOUT`으로 표시함.

### 2.4. 상태 관리 및 예외 케이스

- **페이지 새로고침:** 사용자가 새로고침하면 `GET /api/v1/payment/details/{enrollId}`를 호출하여 페이지가 다시 초기화되어야 함. `enrollId`가 여전히 유효하면 (UNPAID 상태이고 `paymentDeadline` 이내), 타이머는 남은 시간으로 다시 시작됨.
- **브라우저 뒤로/앞으로 가기:** 표준 브라우저 동작. 사용자가 다른 곳으로 이동했다가 돌아오고 (서버 관점에서 타이머가 만료되지 않은 경우) 페이지는 다시 초기화되어야 함.
- **여러 탭:** 동일한 `enrollId`에 대해 여러 탭에서 동일한 결제 페이지 URL을 열면 동일한 정보와 타이머가 표시됨. 한 탭에서의 결제 확인은 이상적으로 다른 탭에도 이를 반영해야 함 (예: 다음 상호작용 시 또는 폴링 구현 시. 단, 이 페이지에 폴링이 반드시 필요한 것은 아님).

## 3. 결제를 위한 백엔드 API 엔드포인트

### 3.1. `GET /api/v1/payment/details/{enrollId}`

- **컨트롤러:** `PaymentController` (신규 또는 기존, 예: `SwimmingController`)
- **서비스:** `PaymentService` (신규 또는 `EnrollmentService`)
- **로직:**
  1.  `enrollId` 유효성 검사:
      - `enrollId`로 `Enroll` 정보 조회. 없으면 `ResourceNotFoundException` (404) 발생.
      - `enroll.getUser()`가 인증된 사용자와 일치하는지 확인. 아니면 `AccessDeniedException` (403) 발생.
      - `enroll.getPayStatus()` 확인:
        - `PAID`이면 `BusinessRuleException(ErrorCode.PAYMENT_ALREADY_COMPLETED)` (409) 발생.
        - `PAYMENT_TIMEOUT` 또는 `CANCELED_UNPAID`이면 `BusinessRuleException(ErrorCode.ENROLLMENT_EXPIRED_OR_CANCELLED)` (400) 발생.
        - 반드시 `UNPAID`여야 함.
      - `enroll.getExpireDt()` 확인: `LocalDateTime.now().isAfter(enroll.getExpireDt())`이면 `enroll.setPayStatus("PAYMENT_TIMEOUT")`으로 업데이트, 저장 후 `BusinessRuleException(ErrorCode.ENROLLMENT_PAYMENT_EXPIRED)` (400) 발생.
  2.  `enroll.getLesson()`에서 `Lesson` 조회. 없으면 `ResourceNotFoundException`.
  3.  보안 컨텍스트에서 `User`를 가져와 `user.getGender()` 확인.
  4.  **사물함 사용 가능 여부 로직 (강습에 사물함이 있는 경우):**
      - `userGender = user.getGender().toUpperCase()`.
      - `lessonLockerCapacityForGender = (userGender.equals("MALE") ? lesson.getMaleLockerCap() : lesson.getFemaleLockerCap())`.
      - `usedLockersForGender = enrollRepository.countActiveLockersForLessonAndGender(lesson.getLessonId(), userGender, LocalDateTime.now())`. 이는 사물함을 사용하는 `PAID` 상태의 신청 건과 `usesLocker=true`인 `UNPAID` 상태(유효한 `expireDt` 내)의 신청 건을 카운트함 (단, `usesLocker`는 일반적으로 confirm 시점에 설정되므로, 이 DTO에서는 사용자가 이전에 시도했다가 포기한 경우 잠재적 사용을 반영할 수 있음). 더 안전한 카운트는 `PAID` 상태 및 현재 _다른_ 사용자의 유효한 결제창에 있는 건을 기반으로 함.
      - `availableForGender = lessonLockerCapacityForGender > usedLockersForGender`.
  5.  `PaymentPageDetailsDto` 구성:
      - `enrollId`, `lessonTitle`, `lessonPrice`.
      - `userGender`.
      - `lockerOptions`: 정원, 사용된 수, `lockerFee` ( `Lesson` 엔티티에 있는 경우), `availableForGender` 정보 채우기.
      - `amountToPay`: 초기에는 `lesson.getPrice()`. 사물함 선택 시 프론트엔드에서 조정.
      - `paymentDeadline`: `enroll.getExpireDt()`.
      - 선택적으로, 프론트엔드에서 PG에 사용할 `merchantUid`를 여기서 미리 생성 가능.
  6.  `PaymentPageDetailsDto` 반환.

### 3.2. `POST /api/v1/payment/confirm/{enrollId}`

- **컨트롤러:** `PaymentController`
- **서비스:** `PaymentService`
- **요청 본문:** `{ "pgToken": String, "wantsLocker": Boolean }`
- **트랜잭션 메소드.**
- **로직:**
  1.  **비관적 잠금 (선택 사항이지만 권장):** DB가 잘 지원하는 경우 `Lesson` 행을 잠그거나 `Enroll`에 대해 `SELECT ... FOR UPDATE` 사용. 이는 마지막 몇 개의 슬롯/사물함에 대한 동시 확인을 처리하기 위함.
  2.  `enrollId` 유효성 검사 (`details` 엔드포인트와 유사하지만 재확인 중요):
      - `enrollId`로 `Enroll` 조회. 없으면 404.
      - 사용자 소유권 확인 (403).
      - `enroll.getPayStatus()`가 `UNPAID`가 아니면 `BusinessRuleException(ErrorCode.INVALID_ENROLLMENT_STATE_FOR_PAYMENT)` (409 - 예: 이미 PAID, TIMEOUT) 발생.
      - `LocalDateTime.now().isAfter(enroll.getExpireDt())`이면 상태를 `PAYMENT_TIMEOUT`으로 업데이트, 저장 후 `BusinessRuleException(ErrorCode.ENROLLMENT_PAYMENT_EXPIRED)` (400) 발생.
  3.  `enroll.getLesson()`에서 `Lesson` 조회.
  4.  **강습 정원 확인 (최종):**
      - `paidCount = enrollRepository.countByLessonLessonIdAndPayStatus(lesson.getLessonId(), "PAID")`.
      - `paidCount >= lesson.getCapacity()`이면 `BusinessRuleException(ErrorCode.LESSON_CAPACITY_EXCEEDED_AT_CONFIRM)` (409) 발생.
  5.  **사물함 배정 및 정원 확인 (최종, `wantsLocker`가 true인 경우):**
      - `User`를 가져와 `user.getGender()` 확인.
      - `userGender = user.getGender().toUpperCase()`.
      - `lessonLockerCapacityForGender = (userGender.equals("MALE") ? lesson.getMaleLockerCap() : lesson.getFemaleLockerCap())`.
      - `usedLockersForGender = enrollRepository.countByLessonLessonIdAndUserGenderAndUsesLockerTrueAndPayStatus(lesson.getLessonId(), userGender, "PAID")`.
      - `usedLockersForGender >= lessonLockerCapacityForGender`이면 `BusinessRuleException(ErrorCode.LESSON_LOCKER_CAPACITY_EXCEEDED_FOR_GENDER_AT_CONFIRM)` (409) 발생.
      - 성공 시 `enroll.setUsesLocker(true)`.
  6.  그렇지 않으면 (`wantsLocker`가 false인 경우), `enroll.setUsesLocker(false)`.
  7.  `expectedAmount = lesson.getPrice() + (enroll.isUsesLocker() ? lesson.getLockerFee() : 0)` 계산 ( `lesson.getLockerFee()`가 요금을 반환하는 메소드인지 확인하거나 Lesson 엔티티에 추가).
  8.  **PG 검증 (PG 서비스/모듈 통해):**
      - PG 제공업체의 API를 호출하여 `pgToken` 검증 (예: 아임포트의 `imp_uid`로 결제 데이터 가져오기).
      - PG의 결제 `status`가 `paid` (또는 동등한 상태)인지 확인.
      - PG의 `amount`가 `expectedAmount`와 일치하는지 확인.
      - 검증 실패 시 (상태가 paid가 아니거나, 금액 불일치, 토큰 무효), `BusinessRuleException(ErrorCode.PG_VERIFICATION_FAILED)` (400) 발생. PG 오류 상세 정보 캡처.
  9.  **모든 확인 통과 시:**
      - `enroll.setPayStatus("PAID")`.
      - `enroll.setUpdatedAt(LocalDateTime.now())`.
      - `enroll.setUpdatedBy(user.getUsername())`.
      - `enroll` 저장.
      - `Payment` 레코드 생성 및 저장:
        - `enrollId`, `tid` (`pgToken` 또는 PG 응답에서), `pgProvider`, `amount` (검증된 금액), `status="SUCCESS"`, `paidAt=LocalDateTime.now()` 등.
  10. `ResponseEntity.ok().build()` 반환.
  11. **오류 처리 및 롤백:** PG 검증 이후 단계에서 이전 확인이 통과한 후 오류 발생 시 광범위하게 로깅. 보상 트랜잭션(예: `Payment` 레코드 저장 실패 시 즉시 PG 환불 시도)이 필요한지/실현 가능한지 고려. 이는 복잡함. PG 성공 후 대부분의 DB 저장 실패의 경우, 관리자/수동 조정 프로세스가 초기에 더 실용적일 수 있음.

## 4. `payment-timeout-sweep` 배치 작업

- **스케줄:** 1~5분마다 실행.
- **로직:**
  1.  쿼리: `SELECT e FROM Enroll e WHERE e.payStatus = 'UNPAID' AND e.expireDt < NOW()`.
  2.  조회된 각 `Enroll` 레코드에 대해:
      - 로그: "신청 ID {id}가 타임아웃되었습니다. payStatus를 PAYMENT_TIMEOUT으로 변경합니다."
      - `enroll.setPayStatus("PAYMENT_TIMEOUT")`.
      - `enroll.setUpdatedAt(LocalDateTime.now())`.
      - `enroll.setUpdatedBy("SYSTEM_BATCH_TIMEOUT")`.
      - `enroll` 저장.
- **모니터링:** 작업 시작, 종료, 처리된 레코드 수, 모든 오류 로깅.

## 5. 동시성 및 잠금

- **`POST /api/v1/swimming/enroll` (5분간 초기 슬롯 예약):**
  - 이 엔드포인트는 `lesson.capacity`를 (`PAID` 수 + `expire_dt > NOW()`인 `UNPAID` 수)와 비교하여 확인해야 함.
  - 정원 확인 시 `Lesson` 레코드에 대한 `SELECT ... FOR UPDATE`는 여러 요청이 동시에 마지막 남은 슬롯을 "사용 가능"으로 보는 것을 방지할 수 있음.
  - `Enroll` 레코드 자체 생성은 사용자/강습별로 고유함 (이미 `PAID` 또는 활성 `UNPAID`가 아닌 경우).
- **`POST /api/v1/payment/confirm/{enrollId}` (최종 슬롯 확인):**
  - 강습 정원 및 사물함 정원 확인이 여기서 중요함.
  - `Lesson` 또는 관련 집계(예: 강습 점유율 요약 테이블)에 비관적 잠금을 사용하는 경우, 현재 카운트 읽기 및 `Enroll` 상태 업데이트를 포함하여 가능한 가장 짧은 시간 동안 유지되어야 함.

## 6. 결제 흐름 관련 특정 오류 코드

| 오류 코드                                               | HTTP | 추천 메시지 (EN/KR)                                                                                      |
| ------------------------------------------------------- | ---- | -------------------------------------------------------------------------------------------------------- |
| `ENROLLMENT_PAYMENT_EXPIRED`                            | 400  | "Payment window has expired." / "결제 가능 시간이 만료되었습니다."                                       |
| `PAYMENT_ALREADY_COMPLETED`                             | 409  | "This enrollment has already been paid." / "이미 결제가 완료된 신청입니다."                              |
| `INVALID_ENROLLMENT_STATE_FOR_PAYMENT`                  | 409  | "Enrollment is not in a state that allows payment." / "결제가 불가능한 신청 상태입니다."                 |
| `LESSON_CAPACITY_EXCEEDED_AT_CONFIRM`                   | 409  | "Lesson capacity was filled while processing payment." / "결제 중 정원이 마감되었습니다."                |
| `LESSON_LOCKER_CAPACITY_EXCEEDED_FOR_GENDER_AT_CONFIRM` | 409  | "Locker capacity for your gender was filled." / "선택하신 성별의 사물함이 마감되었습니다."               |
| `PG_VERIFICATION_FAILED`                                | 400  | "Payment Gateway verification failed." / "결제 게이트웨이 검증에 실패했습니다."                          |
| `PAYMENT_PAGE_ACCESS_DENIED`                            | 403  | "Access to payment page denied (e.g. no capacity)." / "결제 페이지 접근이 거부되었습니다(정원 초과 등)." |

이 신규 문서 `Docs/cms/payment-page-integration.md`는 결제 페이지 및 관련 백엔드 프로세스에 대한 보다 집중적이고 상세한 사양을 제공합니다.
