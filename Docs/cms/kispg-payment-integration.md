# 💳 KISPG 결제 연동 상세 (신규 결제 및 승인)

## 1. 개요

본 문서는 KISPG V2 API를 사용한 신규 결제 및 승인 프로세스 연동에 대해 상세히 기술한다. 사용자가 서비스 비용을 결제할 때 KISPG 결제창을 이용하고, 서버 간 통신을 통해 결제 결과를 안전하게 처리하는 것을 목표로 한다. "수강 취소 + 부분 환불 PG 연동 보고서"에 기술된 KISPG V2 서버 대 서버 취소 모델과 일관성을 유지한다.

**주요 PG:** KISPG (V2 API)
**모델:** KISPG 결제창 호출 + 서버 대 서버 결과 통지 (Webhook) 및 승인 처리

**결제 기본 흐름:**

1.  **사용자:** 결제 페이지에서 결제 정보 확인 후 [결제하기] 선택.
2.  **프론트엔드 (결제 페이지):** 백엔드에 KISPG 결제 요청에 필요한 파라미터 요청.
3.  **백엔드:** KISPG 결제 요청에 필요한 정보 (상점 ID, 주문번호, 결제금액, 상품명, 결과수신 URL 등) 및 보안 해시값(`encData`와 유사한 요청용 해시 또는 KISPG 명세에 따른 파라미터) 생성하여 프론트엔드에 전달.
4.  **프론트엔드:** 전달받은 파라미터로 KISPG 결제창 호출 (리디렉션 또는 KISPG SDK 사용).
5.  **사용자:** KISPG 결제창에서 결제수단 선택 및 인증 완료.
6.  **KISPG:** 결제 처리 후, 지정된 Notify URL로 결제 결과 (성공/실패, `tid`, 금액, `encData` 등)를 백엔드에 전송 (Server-to-Server Webhook).
7.  **백엔드 (Notify URL 핸들러):**
    - KISPG로부터 받은 `encData`를 `merchantKey`로 검증하여 위변조 확인.
    - 결제 성공 시:
      - `tid` 등 KISPG 거래 정보 기록.
      - 내부적으로 최종 정원/좌석/사물함 등 유효성 재확인.
      - `Enroll` 상태를 `PAID`로 변경, `Payment` 레코드 생성 (`paid_amt`, `tid` 등).
      - (KISPG 정책에 따라, 단순 통지가 아닌 별도 승인 API 호출이 필요하면 이 시점에 `/v2/approve` 와 같은 KISPG 승인 API 호출).
      - KISPG에 Webhook 수신 성공 응답 전송 (예: "OK", "SUCCESS").
    - 결제 실패 시: 관련 정보 로깅.
8.  **KISPG:** (Webhook 처리와 별개로) 사용자 브라우저를 지정된 Return URL로 리디렉션.
9.  **프론트엔드 (Return URL 페이지):** 사용자에게 최종 결제 상태 안내 (필요시 백엔드에 최종 상태 재문의).

## 2. KISPG 연동을 위한 주요 데이터

| 항목              | 예시 값 / 설명                                                                      | 비고 (저장 위치)                      |
| ----------------- | ----------------------------------------------------------------------------------- | ------------------------------------- |
| `mid`             | `kis000001m` (KISPG 제공 상점 ID)                                                   | 설정 파일 / 환경 변수                 |
| `merchantKey`     | KISPG 제공 상점 키 (SHA-256 해시 생성용)                                            | 보안 저장소 / 환경 변수               |
| `moid`            | `enrollId_{timestamp}` (고유 주문번호)                                              | `payment.merchant_uid`                |
| `amt`             | `70000` (결제 요청 금액, 원단위)                                                    | `payment.paid_amt` (최종 승인액 기준) |
| `itemName`        | "고급 수영반 (월수금)" (상품명)                                                     | `lesson.title` 기반                   |
| `buyerName`       | "홍길동" (구매자명)                                                                 | `user.name`                           |
| `buyerTel`        | "010-1234-5678" (구매자 연락처)                                                     | `user.phone`                          |
| `buyerEmail`      | "user@example.com" (구매자 이메일)                                                  | `user.email`                          |
| `returnUrl`       | `https://arpina.kr/payment/kispg-return` (KISPG 결제 후 사용자 리디렉션 URL)        | KISPG 결제 요청 시 전달               |
| `notifyUrl`       | `https://arpina.kr/api/v1/kispg/payment-notification` (KISPG 결과 통지 Webhook URL) | KISPG 결제 요청 시 전달               |
| `tid`             | `kistest00m...` (KISPG 거래 ID)                                                     | `payment.tid`                         |
| `requestHash`     | SHA-256(`mid` + `moid` + `amt` + `merchantKey` 등 KISPG 규격 요청 해시)             | KISPG 결제창 요청 시 생성/사용        |
| `responseEncData` | KISPG가 Notify 시 전달하는 암호화된 결과 데이터                                     | KISPG Webhook 수신 시                 |
| `cancelEncData`   | 취소 요청 시 생성하는 암호화된 데이터 (환불 보고서 참조)                            | `payment` 취소 시 생성/사용           |

## 3. 프론트엔드 (결제 페이지 P-02) 주요 로직

1.  **결제 준비 API 호출:**
    - `GET /api/v1/payment/kispg-init-params/{enrollId}` (신규 엔드포인트 제안)
    - **응답:** `{ mid, moid, amt, itemName, buyerName, buyerTel, buyerEmail, returnUrl, notifyUrl, requestHash }` 등 KISPG 결제창 호출에 필요한 파라미터.
2.  **KISPG 결제창 호출:**
    - 수신한 파라미터를 사용하여 KISPG 결제 페이지로 사용자 리디렉션 또는 KISPG에서 제공하는 JS SDK를 사용하여 결제창을 현재 페이지에 임베드.
    - 일반적으로 HTML form을 동적으로 생성하여 KISPG URL로 POST하는 방식 사용.
3.  **Return URL 처리 (`/payment/kispg-return`):**
    - 사용자가 KISPG 결제창에서 결제를 마치면 이 URL로 리디렉션됨.
    - URL 파라미터로 `moid`, `resultCode` 등이 전달될 수 있음.
    - 이 페이지는 사용자에게 잠시 대기 메시지를 보여주거나, 백엔드에 `GET /api/v1/mypage/enroll/{enrollId}` (또는 `payment.merchant_uid` 기반 조회)를 통해 최종 결제 상태를 확인하여 성공/실패 페이지를 안내. **Notify URL을 통한 서버 간 통지가 더 신뢰성 높음.**

## 4. 백엔드 API 상세

### 4.1. `GET /api/v1/payment/kispg-init-params/{enrollId}` (신규)

- **목적:** KISPG 결제창 호출에 필요한 파라미터 생성 및 프론트엔드에 제공.
- **로직:**
  1.  `enrollId` 유효성 검증 (`Enroll` 상태가 `UNPAID`이고 `expireDt` 이내인지 등).
  2.  `Lesson`, `User` 정보 조회하여 `amt`, `itemName`, `buyerName` 등 KISPG 파라미터 구성.
  3.  `moid` 생성 (예: `enroll_{enrollId}_{timestamp}`).
  4.  `returnUrl`, `notifyUrl` 설정.
  5.  KISPG 규격에 따라 `requestHash` (또는 `encData` 등) 생성.
  6.  필요한 모든 파라미터 응답.

### 4.2. `POST /api/v1/kispg/payment-notification` (신규 Webhook 핸들러)

- **목적:** KISPG로부터 결제 결과 통지(Webhook) 수신 및 처리.
- **KISPG 호출 형식 (예상):** `Content-Type: application/x-www-form-urlencoded`
  - `mid`, `tid`, `moid`, `amt`, `resultCode`, `resultMsg`, `payMethod`, `approveNo`, `cardQuota` 등과 함께 `encData` (SHA256 해시) 전달.
- **로직:**
  1.  **보안 검증:**
      - 수신된 `encData` (또는 유사 필드)를 `merchantKey`와 KISPG 명세에 따른 데이터로 재해시하여 일치 여부 확인. 불일치 시 위변조로 간주하고 오류 처리.
      - (선택적) KISPG 요청 IP 화이트리스트 검증.
  2.  **파라미터 확인:** `moid`로 내부 `Enroll` 및 `Payment` (미생성 시) 정보 확인. `amt` 일치 여부 확인.
  3.  **결제 성공 처리 (`resultCode == "0000"` 또는 KISPG 성공 코드):**
      - **트랜잭션 시작.**
      - `SELECT ... FOR UPDATE`로 `Enroll` 및 `Lesson` 관련 데이터 잠금 (정원/사물함 동시성 제어).
      - 최종 정원 및 사물함 가용성 재확인. 부족 시 관리자 알림 및 PG 망취소 고려 (복잡도 높음, 초기엔 실패 처리).
      - `Payment` 레코드 생성/업데이트: `tid`, `paid_amt` (KISPG 통지된 금액), `pg_provider="KISPG"`, `status="PAID"`, `paid_at=NOW()` 등.
      - `Enroll` 레코드 업데이트: `pay_status="PAID"`, `uses_locker` (최종 선택 기준).
      - **트랜잭션 커밋.**
      - KISPG에 성공 응답 전송 (예: HTTP 200 "OK" 또는 "SUCCESS" 문자열 - KISPG 명세 확인).
  4.  **결제 실패 처리:**
      - 실패 사유 로깅 (`resultCode`, `resultMsg`).
      - `Payment` 레코드에 실패 정보 기록 가능.
      - KISPG에 실패에 대한 응답 전송 (KISPG 명세 확인).
  5.  **기타 예외 처리:** 중복 `tid` 수신, `moid` 불일치 등.

## 5. `encData` 해시 생성 및 검증 (SHA-256 예시)

- **요청 시 (프론트엔드 전달용 `requestHash` 또는 직접 KISPG 전송 데이터):**
  - `hash_data = mid + moid + amt + merchantKey` (KISPG 명세에 따른 필드 순서 및 조합)
  - `requestHash = SHA256(hash_data)`
- **결과 통지 수신 시 (Webhook `encData` 검증):**

  - `hash_data_from_kispg = mid + tid + moid + amt + merchantKey` (KISPG 명세에 따른 필드 순서 및 조합)
  - `calculated_hash = SHA256(hash_data_from_kispg)`
  - `IF calculated_hash == received_encData THEN 유효 ELSE 위변조`

- **취소 요청 시 (환불 보고서 기반):**
  - `hash_data_for_cancel = mid + ediDate + canAmt + merchantKey`
  - `cancelEncData = SHA256(hash_data_for_cancel)`

_중요: KISPG 문서에서 정확한 해시 구성 필드와 순서를 반드시 확인해야 함._

## 6. 데이터베이스 변경 사항 요약 (기존 문서와 통합)

- **`payment` 테이블:**
  - `tid`: VARCHAR(30) (KISPG 거래 ID)
  - `paid_amt`: INT (초기 승인된 총액)
  - `refunded_amt`: INT (누적 환불액)
  - `refund_dt`: DATETIME (마지막 환불 시각)
  - `pg_provider`: VARCHAR(20) (기본값 'KISPG')
  - `merchant_uid`: VARCHAR(255) (`moid` 저장)
  - `status`: VARCHAR(20) (`PAID`, `PARTIALLY_REFUNDED`, `CANCELED`, `FAILED`)
- **`enroll` 테이블:**
  - `pay_status`: VARCHAR(20) (`PAID`, `PARTIALLY_REFUNDED` 등 포함)
  - `remain_days`: INT (취소 시 환불 계산용 잔여일수)

## 7. 보안 고려 사항

- `merchantKey`는 절대로 프론트엔드에 노출되어서는 안 되며, 서버 측에서 안전하게 관리.
- 모든 KISPG와의 통신(특히 Webhook 수신)은 HTTPS 사용.
- Webhook 수신 시 `encData` 검증 및 IP 화이트리스팅(KISPG 제공 시)을 통한 발신처 확인.
- 서버에서 KISPG로 직접 API 호출 시 (예: 취소) SSL/TLS 통신 및 필요한 인증 절차 준수.
- 주요 금융 정보(카드번호 등)는 직접 저장하거나 로깅하지 않음 (PG사 처리).

## 8. 기존 문서 연동 가이드

- **`Docs/cms/payment-page-integration.md`**:
  - 2절 "결제 페이지 (프론트엔드)" 로직이 본 문서 3절 "프론트엔드 (결제 페이지 P-02) 주요 로직"과 연계되도록 수정되었습니다. 프론트엔드는 `GET /api/v1/payment/details/{enrollId}`로 CMS 내부 정보를, `GET /api/v1/payment/kispg-init-params/{enrollId}`로 KISPG 연동 파라미터를 가져옵니다.
  - 해당 문서의 `POST /api/v1/payment/confirm/{enrollId}`는 KISPG의 `returnUrl` 처리 시 프론트엔드에서 호출됩니다. 이 API는 KISPG Webhook (`POST /api/v1/kispg/payment-notification`)이 실제 결제 처리의 주체가 된 후, 사용자 경험(UX)을 관리하고 최종적인 `wantsLocker` 상태를 반영하는 보조적인 역할을 수행합니다. **이 API는 KISPG로 직접 결제 승인/검증을 요청하지 않습니다.**
  - KISPG Webhook (`POST /api/v1/kispg/payment-notification`)이 주 결제 확정 및 상태 변경 경로가 됩니다.
- 기타 문서 (`user.md`, `swim-user.md`, `swim-admin.md`, `swim-overview.md`):
  - 결제 PG를 "KISPG"로 명시.
  - `tid`, `paid_amt`, `refunded_amt` 등 관련 DTO 및 DB 필드 반영.
  - 결제/취소 흐름도에서 PG를 KISPG로 구체화하고 Webhook/API 호출 명시.
  - 관리자 기능에서 KISPG `tid` 조회, KISPG 기반 정산 배치 작업(`pg-reconcile`) 등을 언급.
