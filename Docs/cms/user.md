## 📑 Mypage API v1 — **File-Integrated Final Spec** (2025-05-16)

> **Base URL (Authenticated):** `/api/v1/mypage` > **Auth:** `Authorization: Bearer {JWT}` _(필수)_
> 모든 엔드포인트는 **HTTPS** + **JWT** 필수이며, 로그인된 일반 회원 권한(`ROLE_USER`)으로 접근한다.
> **결제(Checkout → Pay)** 플로우는 **ENROLL 탭(마이페이지 내부)에서만** 실행된다. 외부 화면에서는 _신청만_ 가능하며, 결제 버튼은 존재하지 않는다.

---

### 1. Tabs & Functional Structure

| `tab`      | 설명                      | 서브 기능                            |
| ---------- | ------------------------- | ------------------------------------ |
| `PROFILE`  | 회원정보 조회/수정        | 이름·주소·전화·차량번호 갱신         |
| `PASSWORD` | 비밀번호 변경/임시PW 교체 | 강제 변경 경로 `/password?force=1`   |
| `ENROLL`   | **신청·결제·취소·재등록** | Checkout → Pay, 카운트다운, 취소요청 |
| `PAYMENT`  | 결제/환불 이력            | 결제 상세, 전액·부분 환불 요청       |

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

#### 3.3 수영장 신청 & 결제 (Enroll)

| #   | Method   | URL                         | Req.Body / QS       | Resp             | Scope |
| --- | -------- | --------------------------- | ------------------- | ---------------- | ----- |
| 1   | GET      | `/enroll`                   | `status?`           | List\<EnrollDto> | USER  |
| 2   | GET      | `/enroll/{id}`              | –                   | EnrollDto        | USER  |
| 3   | **POST** | **`/enroll/{id}/checkout`** | –                   | `CheckoutDto`    | USER  |
| 4   | **POST** | **`/enroll/{id}/pay`**      | `{ "pgToken":"" }`  | 200 / Error      | USER  |
| 5   | PATCH    | `/enroll/{id}/cancel`       | `{ "reason":"" }`   | Requested        | USER  |
| 6   | POST     | `/renewal`                  | `RenewalRequestDto` | Created          | USER  |

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
  "status": "SUCCESS" // SUCCESS | CANCELED | PARTIAL
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

### 7. Database DDL (추가 필드)

```sql
ALTER TABLE user
  ADD temp_pw_flag TINYINT DEFAULT 0 COMMENT '임시 비밀번호 여부';

ALTER TABLE enroll
  ADD expire_dt DATETIME,
  ADD renewal_flag TINYINT DEFAULT 0,
  ADD cancel_status ENUM('NONE','REQ','PENDING','APPROVED','DENIED') DEFAULT 'NONE',
  ADD cancel_reason VARCHAR(150),
  ADD refund_amount INT DEFAULT 0;
```

---

### 8. Security & Workflow

1. **JWT + Refresh** – 모든 호출 보호, 만료 시 자동 재발급
2. **Password Flow** – 임시PW 로그인 시 `/mypage/password?force=1` 강제 이동
3. **Checkout** – 서버가 금액·정원·라커 재검증 후 `CheckoutDto` 반환
4. **Pay** – 서버가 PG 영수증 검증 후 `status=PAID` 확정 (선착순 보장)
5. **Payment Timeout** – `expire_dt < NOW()` & `status='UNPAID'` → `CANCELED_UNPAID`
6. **Partial Refund** – `/payment/{id}/cancel` → 관리자 승인 후 PG `partialCancel`

---

### 9. Front-End Guidelines

| 컴포넌트           | 구현 포인트                                              |
| ------------------ | -------------------------------------------------------- |
| **EnrollCard**     | 상태 Badge + 버튼 (`Checkout` red / `Cancel` blue)       |
| **Countdown**      | `expireDt` diff 실시간 표시, 0 초 → 카드 회색 “결제만료” |
| **Checkout Modal** | `CheckoutDto` 요약 + 아임포트 Script 호출                |
| **Renewal Modal**  | 라커 carry 토글, 성공 Toast                              |
| **Empty State**    | 일러스트 + “아직 신청 내역이 없어요”                     |

---

### 10. Batch & Event

| Job                | 주기   | Logic                                                      |
| ------------------ | ------ | ---------------------------------------------------------- |
| `unpaid-timeout`   | 5 min  | UNPAID & expire_dt < NOW() → `CANCELED_UNPAID` + 라커 복원 |
| `pg-webhook`       | 실시간 | 아임포트 Webhook 검증 → enroll·payment 동기화              |
| `renewal-notifier` | 1 day  | renewalWindow 오픈 회원에게 LMS 알림                       |

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
