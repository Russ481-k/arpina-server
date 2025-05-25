# 💳 결제 페이지 & PG 연동 상세

## 1. 개요

본 문서는 전용 **결제 페이지**의 기능과 PG(Payment Gateway) 연동에 대해 상세히 설명하며, 수영 강습 신청을 위한 5분 제한 시간제 결제창 및 정원 관리 기능에 중점을 둡니다.

**주요 목표:**

- 원활하고 빠른 결제 경험 제공.
- 시간 제한이 있는 결제 슬롯을 통해 정원에 따른 공정한 강습 접근 기회 보장.
- 신청, 결제, 사물함 배정 데이터의 무결성 유지.

**일반 흐름:**

1.  사용자가 `POST /api/v1/enrolls`을 통해 강습 신청을 시작합니다.
2.  정원 여유가 있는 경우 (**결제 페이지 접근 슬롯 가용 시** - 상세 로직은 `Docs/cms/lesson-enrollment-capacity.md` 참조), API는 `EnrollInitiationResponseDto` ( `enrollId`, `paymentPageUrl`, `paymentExpiresAt` 포함)를 반환합니다.
3.  사용자는 결제 페이지 (`paymentPageUrl`)로 리디렉션됩니다.
4.  결제 페이지는 `GET /api/v1/payment/details/{enrollId}`를 통해 상세 정보를 가져옵니다.
5.  사용자는 강습 정보, 가격, 사물함 옵션(해당하는 경우), 5분 카운트다운 타이머를 확인합니다.
6.  사용자는 옵션을 선택하고 PG 결제를 시작합니다.
7.  PG 결제 성공 시, 결제 페이지는 PG 토큰 및 사물함 선택 사항과 함께 `POST /api/v1/payment/confirm/{enrollId}`를 호출합니다.
8.  백엔드는 결제를 확인하고, 정원/사물함을 재확인한 후 신청을 최종 완료합니다.
9.  5분 타이머가 확인 전에 만료되면 사용자는 리디렉션되고 해당 슬롯은 해제됩니다.

## 2. 결제 페이지 (프론트엔드: P-02, Next.js/React 기반)

**URL:** `/payment/process?enroll_id={enrollId}` (예시, Next.js `pages` 디렉토리 구조에 따라 `pages/payment/process/[enrollId].jsx` 또는 `pages/payment/process.jsx`에서 `useRouter`로 `enroll_id` 추출)

### 2.1. 페이지 로드 및 초기화 (Next.js `useEffect` 또는 `getServerSideProps`/`getInitialProps` 활용 가능)

1.  URL 쿼리 파라미터에서 **`enrollId` 추출** (Next.js `useRouter` 사용).
2.  **API 호출 (CMS 정보 표시용):** `GET /api/v1/payment/details/{enrollId}` (React `useEffect` 내에서, 또는 SSR/SSG 데이터 페칭 메소드 내에서)
    - **성공 시 (`PaymentPageDetailsDto`):**
      - React `useState`를 사용하여 `enrollId`, `lessonTitle`, `lessonPrice`, `userGender`, `lockerOptions`, `amountToPay`, `paymentDeadline` 등 상태 저장.
      - UI 요소 초기화 (JSX로 렌더링):
        - 강습명, 기본 가격 표시.
        - `lockerOptions`가 존재하고 `lockerOptions.availableForGender`가 true인 경우:
          - 사물함 선택 UI 표시 (예: `<input type="checkbox" ... />` "사물함 사용? (+{lockerFee})").
          - 사용자 성별에 맞는 사용 가능한 사물함 수 표시 (예: "여성 사물함: {total - used}개 사용 가능").
        - 총 `amountToPay` (상태값) 표시.
        - **카운트다운 타이머 시작:** `paymentDeadline` 기준. React `useState`와 `useEffect` (setInterval)를 사용하여 시각적으로 카운트다운(MM:SS)되어야 함.
      - 초기 `amountToPay`, `merchantUid` 등으로 PG 모듈 (예: 아임포트 SDK) 준비.
    - **실패 시:**
      - `404 Enroll Not Found` 또는 `400 PAYMENT_ALREADY_PROCESSED/EXPIRED`: Next.js `useRouter`를 사용하여 마이페이지 또는 강습 목록으로 오류 메시지와 함께 리디렉션. `react-toastify` 등으로 토스트 메시지 표시 가능.
      - 기타 오류: 일반 오류 메시지 컴포넌트 표시.
3.  **API 호출 (KISPG 파라미터 준비용):** `GET /api/v1/payment/kispg-init-params/{enrollId}` (상세 내용은 `Docs/cms/kispg-payment-integration.md` 참조)
    - **성공 시 (KISPG 초기화 파라미터):**
      - KISPG 결제창 호출에 필요한 파라미터 (예: `mid`, `moid`, `requestHash` 등) 상태 저장.
      - KISPG SDK 또는 결제창 호출 준비.
    - **실패 시:** KISPG 연동 불가 오류 처리.

### 2.2. 사용자 상호작용 (React 이벤트 핸들러 사용)

1.  **사물함 선택:**
    - 사용자가 "사물함 사용" 체크/해제 시: `totalAmountToPay` 재계산 및 UI 업데이트.
2.  **결제 시작 ([결제하기] 버튼 클릭, React `onClick` 이벤트 핸들러):**
    - `totalAmountToPay` (상태값) 및 `wantsLocker` (상태값) 가져오기.
    - 저장된 KISPG 파라미터를 사용하여 KISPG 결제창 호출 (상세 내용은 `Docs/cms/kispg-payment-integration.md`의 프론트엔드 로직 참조).
    - **KISPG 결제창 리턴 후 (KISPG `returnUrl` 처리):**
      - PG로부터 `pg_tid` (KISPG `tid`), `merchant_uid` (`moid`) 등 수신 가능.
      - 백엔드 API 호출: `POST /api/v1/payment/confirm/{enrollId}` (주로 UX 업데이트 및 `wantsLocker` 최종 전달 목적)
        ```json
        {
          "pgToken": "kistest_tid_from_pg_return", // KISPG가 returnUrl에 전달한 TID 또는 관련 식별자
          "wantsLocker": true // 또는 false, 사용자의 최종 선택 기준 (React 상태값)
        }
        ```
      - **API 성공 (200 OK):**
        - 백엔드가 웹훅을 통해 이미 결제를 처리했거나, 곧 처리할 것이라는 가정.
        - 카운트다운 타이머 중지.
        - 사용자에게 "결제 처리 중입니다" 또는 최종 "결제 성공!" 메시지 표시 (백엔드 응답 또는 폴링 기반).
        - Next.js `<Link>` 또는 `useRouter`로 "내 수강 내역 가기" 버튼/링크 제공.
      - **API 실패 (예: 400, 409, 500):**
        - API 응답 메시지 표시. `PAYMENT_EXPIRED` 등 처리.
    - **KISPG 실패/사용자 취소:**
      - 메시지 표시. 타이머 유효 시 재시도 가능.

### 2.3. 카운트다운 타이머 및 타임아웃 (React `useEffect`, `useState`)

- 타이머는 매초 시각적으로 업데이트됨 (React 상태 변경에 따른 리렌더링).
- **타이머 만료 시 (00:00 도달):**
  - 결제 버튼 비활성화 (React 상태 사용).
  - `react-toastify` 등으로 토스트/알림 표시: "5분의 시간이 경과되어 결제 이전 창으로 이동합니다."
  - 사용자 리디렉션: Next.js `useRouter().back()` 또는 특정 페이지로 `router.push()`.
  - 백엔드 `payment-timeout-sweep` 배치가 결국 `enroll.pay_status`를 `PAYMENT_TIMEOUT`으로 표시함.

### 2.4. 상태 관리 및 예외 케이스 (Next.js/React)

- **페이지 새로고침:** Next.js 페이지가 새로고침되면, `useEffect` (또는 SSR/SSG 데이터 페칭)를 통해 `GET /api/v1/payment/details/{enrollId}`를 다시 호출하여 페이지 상태 재초기화. `enrollId`가 유효하면 타이머는 남은 시간으로 다시 시작.
- **브라우저 뒤로/앞으로 가기:** 표준 브라우저 동작. Next.js 라우팅 시스템이 상태를 적절히 처리하거나, 필요시 `useEffect`로 재초기화 로직 수행.
- **여러 탭:** 동일한 `enrollId`에 대해 여러 탭에서 동일한 결제 페이지 URL을 열면 동일한 정보와 타이머가 표시됨. 한 탭에서의 결제 확인이 다른 탭에 즉시 반영되려면 웹소켓 또는 주기적인 폴링 (예: `swr` 이나 `react-query`의 `refetchInterval`) 같은 고급 상태 동기화 메커니즘 필요. 단순 구현에서는 다음 상호작용 시 또는 페이지 재방문 시 상태가 업데이트될 수 있음.

## 3. 결제를 위한 백엔드 API 엔드포인트

(KISPG 연동을 위한 핵심 엔드포인트인 `GET /api/v1/payment/kispg-init-params/{enrollId}` 및 Webhook `POST /api/v1/kispg/payment-notification`의 상세 로직은 `Docs/cms/kispg-payment-integration.md` 문서를 참조하십시오. 아래는 결제 페이지 자체에서 사용되는 주요 API입니다.)

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
      - **[수정됨]** 전역 `LockerInventory`에서 사용 가능 수량 확인:
        - `lockerInventory = lockerService.getInventoryByGender(userGender)`.
        - `totalLockersForGender = lockerInventory.getTotalQuantity()`.
        - `usedLockersForGender = lockerInventory.getUsedQuantity()`.
        - `availableForGender = totalLockersForGender > usedLockersForGender`.
      - (**참고**: 기존 `lesson.getMaleLockerCap()`, `lesson.getFemaleLockerCap()` 필드는 DDL 변경으로 제거됨. 현재는 전역 `locker_inventory` 테이블의 성별별 총량과 사용량으로 관리)
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
- **요청 본문:** `{ "pgToken": String, "wantsLocker": Boolean }` (`pgToken`은 KISPG가 `returnUrl`로 전달하는 `tid` 또는 관련 식별자)
- **목적:** KISPG 결제창에서 사용자가 돌아온 후 호출됨. **주 목적은 사용자 경험(UX)을 관리하고, 사용자의 최종 사물함 사용 희망 여부 (`wantsLocker`)를 `Enroll` 테이블에 기록하는 것입니다. 실제 결제 승인 (`Enroll.payStatus`를 `PAID`로 변경), 사물함 자원 할당 (`locker_inventory` 업데이트 및 `Enroll.lockerAllocated` 설정), `Payment` 레코드 생성/업데이트 등 핵심 상태 변경은 KISPG Webhook (`POST /api/v1/kispg/payment-notification`)을 통해 비동기적으로, 그리고 배타적으로 처리되는 것을 전제로 합니다.**
- **트랜잭션 메소드 (주로 `Enroll.usesLocker` 업데이트를 위함).**
- **로직 (주의: 이 API는 `Enroll.usesLocker` 외의 핵심적인 `Enroll` 상태나 `Payment` 레코드, `LockerInventory`를 직접 수정하지 않습니다):**

  1.  `enrollId` 유효성 검사 (기본적인 조회 및 사용자 소유권 확인).
      - `enrollId`로 `Enroll` 조회. 없으면 404.
      - 사용자 소유권 확인 (403).
  2.  `Enroll` 정보에서 현재 `payStatus` 및 `expireDt` 확인.
      - **CASE 1: `payStatus == "PAID"` (웹훅이 이미 처리):**
        - `enroll.setUsesLocker(request.getWantsLocker())` (만약 이전에 설정되지 않았거나 사용자의 최종 선택이 변경된 경우). `Enroll` 저장.
        - `ResponseEntity.ok().body("PAYMENT_SUCCESSFUL")` (또는 결제 완료 상태 DTO) 반환.
      - **CASE 2: `payStatus == "UNPAID"` AND `expireDt` 유효:**
        - 웹훅 처리가 지연되고 있을 수 있음.
        - `enroll.setUsesLocker(request.getWantsLocker())` (사용자의 최종 선택을 기록 - 웹훅은 이 값을 참조하여 사물함 할당 여부 결정). `Enroll` 저장.
        - `ResponseEntity.ok().body("PAYMENT_PROCESSING")` (프론트엔드는 폴링 또는 웹소켓으로 최종 상태 확인 필요) 반환.
        - **주의: 이 엔드포인트는 직접 PG 검증을 수행하지 않으며, `Enroll.payStatus`를 `PAID`로 변경하거나 `LockerInventory`를 직접 업데이트하지 않습니다.** `pgToken`은 로깅 또는 참조용으로만 사용될 수 있습니다.
      - **CASE 3: `payStatus == "PAYMENT_TIMEOUT"` OR `expireDt` 만료:**
        - `BusinessRuleException(ErrorCode.ENROLLMENT_PAYMENT_EXPIRED)` (400) 발생.
      - **CASE 4: 기타 상태 (`CANCELED_UNPAID` 등):**
        - `BusinessRuleException(ErrorCode.INVALID_ENROLLMENT_STATE_FOR_PAYMENT)` (409) 발생.
  3.  **정원/사물함 최종 확인 관련 로직:**
      - KISPG Webhook 핸들러(`POST /api/v1/kispg/payment-notification`)가 정원/사물함 최종 확인 및 `Enroll` 상태를 `PAID`로 변경, `Payment` 레코드 생성, `LockerInventory` 업데이트의 주 책임자입니다.
      - 이 `confirm` 엔드포인트는 웹훅 처리 이후에 호출될 수도 있고, 거의 동시에 호출될 수도 있습니다. 따라서 `Enroll.usesLocker` 정보만 주로 업데이트하고, 최종 자원 할당(좌석, 사물함 등) 및 관련 상태 변경은 웹훅의 트랜잭션 내에서 이루어지도록 합니다.
      - 만약 웹훅 이전에 이 API가 호출되고 `Enroll.usesLocker`가 설정되면, 웹훅 처리 시 해당 선택(`enroll.isUsesLocker()`)을 존중하여 사물함 할당 여부를 결정합니다.

  (기존의 PG 검증, Payment 레코드 생성, Enroll 상태 PAID로 직접 변경, 사물함 직접 할당 등의 로직은 KISPG Webhook 핸들러로 이전되었으므로 여기서는 제거됨)

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

- **`POST /api/v1/enrolls` (5분간 초기 슬롯 예약):**
  - 이 엔드포인트는 `lesson.capacity`를 (`PAID` 수 + `expire_dt > NOW()`인 `UNPAID` 수)와 비교하여 확인해야 함.
  - 정원 확인 시 `Lesson` 레코드에 대한 `SELECT ... FOR UPDATE`는 여러 요청이 동시에 마지막 남은 슬롯을 "사용 가능"으로 보는 것을 방지할 수 있음.
  - `Enroll` 레코드 자체 생성은 사용자/강습별로 고유함 (이미 `PAID` 또는 활성 `UNPAID`가 아닌 경우).
- **`POST /api/v1/payment/confirm/{enrollId}` (최종 슬롯 확인):**
  - 강습 정원 및 사물함 정원 확인이 여기서 중요함.
  - `Lesson` 또는 관련 집계(예: 강습 점유율 요약 테이블)에 비관적 잠금을 사용하는 경우, 현재 카운트 읽기 및 `Enroll` 상태 업데이트를 포함하여 가능한 가장 짧은 시간 동안 유지되어야 함.

## 6. 결제 흐름 관련 특정 오류 코드

| 오류 코드                                               | HTTP | 추천 메시지 (EN/KR)                                                                                                   |
| ------------------------------------------------------- | ---- | --------------------------------------------------------------------------------------------------------------------- |
| `ENROLLMENT_PAYMENT_EXPIRED`                            | 400  | "Payment window has expired." / "결제 가능 시간이 만료되었습니다."                                                    |
| `PAYMENT_ALREADY_COMPLETED`                             | 409  | "This enrollment has already been paid." / "이미 결제가 완료된 신청입니다."                                           |
| `INVALID_ENROLLMENT_STATE_FOR_PAYMENT`                  | 409  | "Enrollment is not in a state that allows payment." / "결제가 불가능한 신청 상태입니다."                              |
| `PAYMENT_PAGE_SLOT_UNAVAILABLE` (`LEC001`)              | 409  | "Payment page slot is unavailable." / "결제 페이지에 접근 가능한 인원이 가득 찼습니다." (주로 `/enroll` API에서 발생) |
| `LESSON_CAPACITY_EXCEEDED_AT_CONFIRM`                   | 409  | "Lesson capacity was filled while processing payment." / "결제 중 정원이 마감되었습니다." (주로 Webhook에서 발생)     |
| `LESSON_LOCKER_CAPACITY_EXCEEDED_FOR_GENDER_AT_CONFIRM` | 409  | "Locker capacity for your gender was filled." / "선택하신 성별의 사물함이 마감되었습니다." (주로 Webhook에서 발생)    |
| `PG_VERIFICATION_FAILED`                                | 400  | "Payment Gateway verification failed." / "결제 게이트웨이 검증에 실패했습니다." (주로 Webhook에서 발생)               |
| `PAYMENT_PAGE_ACCESS_DENIED`                            | 403  | "Access to payment page denied (e.g. no capacity)." / "결제 페이지 접근이 거부되었습니다(정원 초과 등)."              |

이 신규 문서 `Docs/cms/payment-page-integration.md`는 결제 페이지 및 관련 백엔드 프로세스에 대한 보다 집중적이고 상세한 사양을 제공합니다.
