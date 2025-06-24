# 💳 KISPG 결제 연동 상세 (구현 현황 기준)

## 1. 개요

본 문서는 현재 시스템에 구현된 KISPG V2 API 기반의 결제 및 승인 프로세스를 기술한다. 이 방식의 핵심은 **결제 준비 단계에서 데이터베이스 레코드를 생성하지 않고**, KISPG의 최종 결제 성공 통보(Webhook)를 받은 뒤 **서버 대 서버(S2S)로 KISPG에 직접 승인을 요청**하여, 승인이 완료되면 비로소 `Enroll` 및 `Payment` 레코드를 생성하는 것이다.

**주요 특징:**

- **선-결제, 후-레코드 생성:** 사용자가 PG사 결제를 완전히 성공해야만 내부 DB에 기록이 남는다.
- **서버 주도 승인:** Webhook 통지만으로 결제를 신뢰하지 않고, 백엔드가 직접 KISPG 승인 API를 호출하여 최종 확인한다.
- **상태 비저장:** 결제 준비 단계에서 사용자 선택(`usesLocker`, `membershipType`)을 서버에 저장하지 않는다. 이 정보는 KISPG 승인 후 금액을 역산하여 추론한다.

**결제 흐름:**

1.  **사용자:** 프론트엔드에서 강습, 사물함, 할인 유형 등을 선택하고 [결제하기] 버튼 클릭.
2.  **프론트엔드:** 백엔드에 `POST /api/v1/payment/prepare-kispg-payment` API 호출.
3.  **백엔드:**
    - `lessonId`와 사용자 선택 옵션으로 **총 결제액을 계산**.
    - `Enroll` 레코드를 생성하지 않고, `temp_{lessonId}_{userUuid}` 형식의 **임시 주문번호(`moid`)**를 생성.
    - KISPG 결제창 호출에 필요한 파라미터(`mid`, 임시 `moid`, `amt` 등)를 생성하여 프론트엔드에 전달.
4.  **프론트엔드:** 전달받은 파라미터로 KISPG 결제창 호출.
5.  **사용자:** KISPG 결제창에서 결제 완료.
6.  **KISPG:** 지정된 `notifyUrl` (`/kispg/payment-notification`)로 결제 결과(TID, 임시 moid 등)를 **백엔드에 Webhook으로 전송**.
7.  **백엔드 (Webhook 핸들러):**
    - KISPG로부터 받은 Webhook 데이터의 유효성(해시)을 검증.
    - **KISPG 승인 API (`/api/v2/trades/{tid}/payments/approve`)를 직접 호출**하여 최종 결제 승인을 요청.
8.  **백엔드 (승인 성공 시):**
    - KISPG 승인이 성공하면, **트랜잭션 시작.**
    - 임시 `moid`에서 `lessonId`와 `userUuid`를 추출.
    - 최종 승인된 금액과 `lesson`의 원가를 비교하여 `usesLocker`, `membershipType` 등 **누락된 정보를 역산**.
    - `Enroll`과 `Payment` 레코드를 생성하고 DB에 저장.
    - **트랜잭션 커밋.**
9.  **KISPG:** (Webhook과 별개로) 사용자 브라우저를 `returnUrl`로 리디렉션.
10. **프론트엔드 (Return URL):** 최종 처리 결과를 백엔드에 문의하여 사용자에게 성공/실패 안내.

---

## 2. 실제 연동 API 상세

### 2.1. `POST /api/v1/payment/prepare-kispg-payment` (결제 준비)

- **역할:** 프론트엔드로부터 사용자의 강습 선택 정보를 받아 KISPG 결제창 호출에 필요한 파라미터를 생성 및 반환.
- **Request Body (`EnrollRequestDto.java`):**
  ```json
  {
    "lessonId": 123,
    "usesLocker": true,
    "membershipType": "merit"
  }
  ```
- **주요 로직:**
  1.  `lessonId`로 강습 정보 조회. 정원 및 등록 기간 등 기본적인 유효성 검증.
  2.  `usesLocker`, `membershipType`을 기반으로 최종 결제 금액(`amt`) 계산.
  3.  `temp_{lessonId}_{userUuid}` 형식의 임시 `moid` 생성.
  4.  KISPG 요청용 해시(`requestHash`) 생성.
  5.  결제창 호출에 필요한 모든 파라미터를 `KispgInitParamsDto`에 담아 반환.
- **Response Body (`KispgInitParamsDto.java`):** KISPG 결제창 호출에 필요한 모든 파라미터 포함.

### 2.2. `POST /kispg/payment-notification` (Webhook 수신)

- **역할:** KISPG로부터 비동기 결제 결과 통지를 수신하는 엔드포인트.
- **수신 데이터 (`KispgNotificationRequest.java`):** `mid`, `tid`, `moid`, `amt`, `resultCode`, `encData` 등 KISPG가 보내주는 모든 결제 결과 정보.
- **주요 로직:**
  1.  수신된 데이터의 해시값(`encData`)을 `merchantKey`로 검증하여 위변조 확인.
  2.  결과가 성공(`resultCode == "0000"`)이면, `KispgWebhookService`의 `processPaymentNotification` 호출.
  3.  서비스 내에서 **KISPG 결제 승인 API**를 호출하는 핵심 로직으로 연결됨.
  4.  KISPG 명세에 따라 "OK" 또는 "SUCCESS" 문자열을 응답.

### 2.3. 백엔드의 KISPG 승인 API 호출 (내부 로직)

- **호출 시점:** Webhook을 통해 성공적인 결제 통지를 받은 직후.
- **호출 API:** `POST https://{kispg_domain}/api/v2/trades/{tid}/payments/approve`
- **주요 로직:**
  1.  Webhook으로 받은 `tid`와 `amt` 등 정보를 사용하여 KISPG로 보낼 요청 데이터를 구성.
  2.  승인 요청용 해시를 생성하여 `HttpEntity`에 포함.
  3.  `RestTemplate`을 사용하여 KISPG 승인 API를 호출.
  4.  HTTP 응답 코드가 200이면 최종 승인 성공으로 간주.

### 2.4. `POST /api/v1/payment/approve-kispg-payment` (최종 승인 및 레코드 생성)

- **역할:** KISPG 서버 대 서버 승인이 완료된 후, 실제 DB 레코드를 생성. (이 API는 프론트엔드가 직접 호출하는 것이 아니라, Webhook 처리 로직의 일부로 내부적으로 호출됨)
- **Request Body (`PaymentApprovalRequestDto.java`):**
  ```json
  {
    "tid": "kistest00m...",
    "moid": "temp_123_uuid-...",
    "paidAmount": 75000,
    "paidAt": "2023-10-27T10:00:00",
    "payMethod": "CARD"
    // ... 등 KISPG 승인 결과로 받은 정보
  }
  ```
- **주요 로직:**
  1.  **트랜잭션 시작.**
  2.  `moid`에서 `lessonId`와 `userUuid`를 파싱.
  3.  `lessonId`로 강습 원가 조회.
  4.  `paidAmount`와 강습 원가를 비교하여, 결제 시 사용자가 선택했을 `usesLocker`와 `membershipType`을 **역으로 추론**.
  5.  추론된 정보와 KISPG 승인 정보를 모두 사용하여 `Enroll` 및 `Payment` 엔티티 생성.
  6.  데이터베이스에 저장 후 **트랜잭션 커밋**.

---

## 3. 데이터 및 해시 (구현 현황)

- **주문번호 (`moid`):**
  - **결제 준비 시:** `temp_{lessonId}_{userUuid}` 형식의 임시 ID.
  - **DB 저장 시:** `enroll_{enrollId}_{timestamp}` 형식의 최종 ID.
- **사용자 선택 정보 (`usesLocker`, `membershipType`):**
  - 결제 준비(`prepare`) 단계에서만 사용되고 **서버에 저장되지 않음**.
  - 최종 승인(`approve`) 단계에서 `paidAmount`를 기준으로 **역산하여 DB에 기록**됨. (정보 유실 및 부정확성 발생 지점)
- **해시 (`requestHash`, `approvalHash` 등):**
  - `SHA-256` 알고리즘 사용.
  - `(데이터 + merchantKey)`를 조합하여 생성. KISPG 명세에 정의된 정확한 필드 순서와 조합을 따름.

---

## 4. 문제점 및 개선 제안

- **사용자 선택 정보 유실:** 현재 구조의 가장 큰 문제점은 결제 준비 단계에서 사용자가 선택한 `usesLocker`, `membershipType` 정보가 **서버에 저장되지 않고 유실**된다는 점이다. 이로 인해 최종 승인 단계에서 금액만으로 옵션을 부정확하게 역산하고 있으며, 이는 데이터 불일치의 원인이 된다.
- **개선 방안:** KISPG 명세의 `mbsReserved1`, `mbsReserved2`와 같은 **가맹점 예약 필드**를 활용하여, 결제 준비 단계에서 `{"usesLocker": true, "membershipType": "merit"}`와 같은 JSON 문자열을 담아 KISPG로 전달한다. Webhook 수신 시 이 예약 필드를 다시 파싱하여 사용하면, 금액을 역산할 필요 없이 정확한 사용자 선택 정보를 복원할 수 있다.

---

_이 문서는 현재 구현 상태를 기준으로 작성되었으며, 상기된 문제점을 해결하기 위한 개선이 필요함._
