# 💳 결제 페이지 & KISPG 연동 상세 (구현 현황 기준)

## 1. 개요

본 문서는 사용자가 강습을 선택하고 KISPG 결제창을 통해 결제를 완료하기까지의 **결제 페이지** 기능과 백엔드 연동 방식을 상세히 설명합니다. 이 시스템은 **사용자가 PG 결제를 성공적으로 마쳐야만** `Enroll`과 `Payment` 레코드가 생성되는 '선-결제, 후-레코드' 방식을 따릅니다.

**주요 목표:**

- 원활하고 빠른 결제 경험 제공.
- PG사(KISPG)의 최종 승인 후에만 내부 데이터를 생성하여 데이터 정합성 유지.

**결제 페이지 중심 흐름:**

1.  사용자가 강습 상세 페이지에서 [결제하기] 버튼을 클릭합니다.
2.  프론트엔드는 사용자가 선택한 옵션(예: `lessonId`, `usesLocker`, `membershipType`)을 담아 백엔드의 **`POST /api/v1/payment/prepare-kispg-payment`** API를 호출합니다.
3.  백엔드는 이 정보를 바탕으로 결제할 금액을 계산하고, KISPG 결제창 호출에 필요한 파라미터(`mid`, 임시 `moid`, 해시값 등)를 생성하여 프론트엔드에 반환합니다.
4.  프론트엔드는 반환받은 파라미터로 **결제 페이지**(`/payment/process`)를 구성하고, 즉시 KISPG 결제창을 호출합니다.
5.  사용자가 KISPG 결제창에서 결제를 마치면, 브라우저는 지정된 **Return URL** (`/payment/kispg-return`)로 돌아옵니다.
6.  Return URL 페이지에서는 "결제 처리 중"임을 안내하며, 백엔드에서는 KISPG로부터 온 **Webhook**을 비동기적으로 처리하여 실제 DB 레코드를 생성합니다.

---

## 2. 결제 페이지 (프론트엔드: P-02)

**URL:** `/payment/process? ...` (또는 `/payment/kispg-return? ...`)

- 이 페이지는 사용자가 PG 결제 후 돌아오는 Return URL의 역할을 합니다.
- KISPG가 URL 파라미터로 `moid`, `resultCode` 등을 전달하면, 이를 바탕으로 사용자에게 최종 상태를 안내합니다.

### 2.1. 페이지 로드 및 상태 안내

1.  URL 쿼리 파라미터에서 `moid`(주문번호), `resultCode`(결과코드) 등을 추출합니다.
2.  **`resultCode`에 따라 분기 처리:**
    - **성공 시 (`resultCode == '0000'`):**
      - "결제가 성공적으로 완료되었습니다. 잠시 후 수강 내역 페이지로 이동합니다." 와 같은 안내 메시지를 표시합니다.
      - (선택) 백엔드에 `moid`를 사용해 최종 처리 상태를 조회하여 "수강 신청이 완료되었습니다"와 같은 더 명확한 메시지를 보여줄 수 있습니다.
      - 일정 시간 후 마이페이지의 수강 내역으로 리디렉션합니다.
    - **실패 시:**
      - "결제에 실패했습니다: [KISPG 실패 메시지]" 와 같은 오류를 표시합니다.
      - "다시 시도하기" 또는 "강습 목록으로 돌아가기" 버튼을 제공합니다.

### 2.2. 백엔드와의 통신

- 결제 페이지(Return URL)는 사용자에게 최종 결과를 '안내'하는 역할이 주 목적입니다.
- 실제 결제 승인, DB 레코드 생성 등 모든 핵심 처리는 **백엔드의 Webhook 핸들러**가 담당하므로, 결제 페이지에서 백엔드로 `confirm`과 같은 API를 **호출할 필요가 없습니다.** (기존 `POST /api/v1/payment/confirm/{enrollId}` API는 현재 로직에서 사용되지 않음)

---

## 3. 결제를 위한 백엔드 API 엔드포인트

(본 문서에서는 결제 페이지와 직접 관련된 API만 기술하며, Webhook 등 상세한 서버 간 통신은 `Docs/cms/kispg-payment-integration.md` 문서를 참조하십시오.)

### 3.1. `POST /api/v1/payment/prepare-kispg-payment`

- **역할:** KISPG 결제 시작에 필요한 모든 정보를 생성하여 프론트엔드에 제공합니다.
- **서비스:** `KispgPaymentService`의 `preparePaymentWithoutEnroll` 메소드
- **로직:**
  1.  요청받은 `lessonId`, `usesLocker`, `membershipType`의 유효성을 검증합니다.
  2.  정원, 등록 기간 등 강습 상태를 확인합니다.
  3.  요청 정보를 바탕으로 최종 결제 금액(`amt`)을 계산합니다.
  4.  `temp_{lessonId}_{userUuid}` 형식의 임시 주문번호(`moid`)를 생성합니다.
  5.  결제창 호출에 필요한 KISPG 파라미터(해시 포함)를 생성하여 `KispgInitParamsDto`에 담아 반환합니다.
- **핵심:** 이 단계에서는 **어떠한 `Enroll` 레코드도 생성하지 않습니다.**

### 3.2. `POST /kispg/payment-notification` (Webhook)

- **역할:** KISPG로부터 비동기 결제 결과를 수신하여 최종 처리합니다. **사실상의 `confirm` 역할을 수행합니다.**
- **서비스:** `KispgWebhookService` -> `KispgPaymentService`
- **로직:**
  1.  Webhook 데이터의 보안(해시) 검증.
  2.  KISPG로 **서버 대 서버 승인 API**를 호출하여 결제를 최종 확인.
  3.  승인 성공 시, DB에 `Enroll` 및 `Payment` 레코드 생성.
  4.  이 과정에서 `moid`로부터 `lessonId`, `userUuid`를 파싱하고, 최종 결제액으로부터 `usesLocker` 등의 옵션을 역산하여 저장.

---

**(참고) 기존 API의 변경 사항**

- `GET /api/v1/payment/details/{enrollId}`: `enrollId`가 더 이상 결제 시작의 기준이 아니므로, 이 API는 결제 페이지에서 사용되지 않습니다.
- `POST /api/v1/payment/confirm/{enrollId}`: Webhook과 서버 대 서버 승인 모델이 도입되면서, 이 API의 역할(PG 검증, 상태 변경 등)은 Webhook 핸들러로 모두 이전되었습니다. 따라서 현재 로직에서는 사용되지 않습니다.
- **5분 타임아웃 로직**: `Enroll` 레코드를 미리 만들지 않으므로, '5분 내에 결제해야 하는' 정책은 현재 구현에서 제거되었습니다. KISPG 결제창이 열린 후 사용자가 결제를 완료하기까지의 시간은 KISPG의 정책에 따릅니다.
