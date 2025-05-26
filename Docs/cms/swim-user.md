- 🏊‍♀️ 수영장 **신청 & 신청내역 확인** — 사용자-측 개발문서
  _(Frontend SPA + REST API + PG 연동 기준)_

  ***

  ## 0. 문서 목표

  | 항목      | 내용                                                                                                              |
  | --------- | ----------------------------------------------------------------------------------------------------------------- |
  | 범위      | **일반 회원**이 강습을 신청하고, **결제 페이지에서** 사물함을 선택‧결제‧취소‧재등록까지 처리하는 모든 온라인 흐름 |
  | 달성 지표 | ① 선착순(결제 완료 기준) ② **5분** 내 결제 ③ 잔여 좌석·라커 **0 오류** ④ UX 이탈률 < 2 %                          |

  ***

  ## 1. 용어·역할 (User Side)

  | 코드             | 설명                        |
  | ---------------- | --------------------------- |
  | **USER**         | 일반 회원 (성인)            |
  | **JUNIOR_USER**  | 미성년자 (온라인 신청 불가) |
  | **PG**           | 결제대행사 (아임포트 REST)  |
  | **ENROLL**       | 강습 신청 레코드 (통합)     |
  | **LOCKER**       | 사물함 레코드               |
  | **RENEWAL**      | 기존 수강생 재등록 프로세스 |
  | **PAYMENT_PAGE** | 결제 전용 페이지            |

  ***

  ## 2. 주요 시나리오(Sequence)

  ```mermaid
  sequenceDiagram
      participant U as 사용자
      participant FE as Frontend
      participant API as REST API
      participant KISPG_Window as KISPG 결제창
      participant KISPG_Server as KISPG 서버

      Note over FE,API: 🔒 Tx / 잔여 Lock (5분)
      U->>FE: 강습 카드 선택 ('신청하기')
      FE->>API: POST /api/v1/swimming/enroll (lesson_id)
      API-->>FE: EnrollInitiationResponseDto (enrollId, paymentPageUrl, paymentExpiresAt)

      alt 잔여좌석 있음 (결제 페이지 접근 슬롯 가용)
          FE->>U: 결제 페이지로 리디렉션 (paymentPageUrl)
          U->>FE: (결제 페이지) 사물함 선택 (선택 사항)
          FE->>API: GET /api/v1/payment/details/{enrollId} (CMS 정보 로드)
          API-->>FE: PaymentPageDetailsDto (금액, 라커정보, 5분 만료시각 등)
          FE->>API: GET /api/v1/payment/kispg-init-params/{enrollId} (KISPG 파라미터 로드)
          API-->>FE: KISPG Init Params (mid, moid, requestHash 등)
          FE->>U: (결제 페이지) 5분 카운트다운 시작, 결제정보 확인
          U->>FE: (결제 페이지) [결제하기] 클릭
          FE->>KISPG_Window: KISPG 결제창 호출 (수신한 파라미터 사용)
          KISPG_Window->>U: 결제 수단 선택 및 인증
          U->>KISPG_Window: 인증 완료
          KISPG_Window-->>KISPG_Server: 결제 시도
          KISPG_Server-->>API: POST /api/v1/kispg/payment-notification (Webhook - 결제 결과 통지: tid, 성공/실패 등)
          API-->>KISPG_Server: Webhook 수신 응답 ("OK")
          Note over API: Webhook 처리: KISPG 데이터 검증, Enroll.payStatus PAID로 변경, 사물함 배정(Enroll.usesLocker=true 시 locker_inventory 업데이트 및 Enroll.lockerAllocated=true 설정), Payment 레코드 생성/업데이트
          KISPG_Server-->>KISPG_Window: 결제 처리 완료
          KISPG_Window-->>FE: 지정된 Return URL로 사용자 리디렉션 (결제 결과 포함 가능)
          FE->>API: POST /api/v1/payment/confirm/{enrollId} (pgToken=tid from KISPG, wantsLocker) - UX 업데이트 및 최종 사물함 상태 전달
          API-->>FE: 200 OK (상태: PAYMENT_SUCCESSFUL 또는 PAYMENT_PROCESSING)
          FE->>U: (결제 페이지) 최종 결제 상태 안내 -> 마이페이지 이동 안내
      else 잔여좌석 없음 또는 오류 (결제 페이지 슬롯 부족 포함)
          API-->>FE: 오류 응답 (예: 409 LEC001 PAYMENT_PAGE_SLOT_UNAVAILABLE)
          FE->>U: 신청 불가 안내 (예: "죄송합니다, 현재 해당 강습의 결제 페이지에 접근할 수 있는 인원이 가득 찼습니다. 잠시 후 다시 시도해주세요.")
      end

      alt 5분 타임아웃 또는 KISPG 결제창에서 사용자 취소/실패
          FE->>U: (결제 페이지) 이전 페이지로 리디렉션, "시간 초과/결제 실패" 토스트
          Note over FE,API: Enroll.pay_status -> PAYMENT_TIMEOUT (배치) 또는 UNPAID 유지
      end
  ```

  ***

  ## 3. **화면 정의**

  | ID       | 화면                | 주요 UI 요소                                                                                         | 전송 API                                                                                                                                       |
  | -------- | ------------------- | ---------------------------------------------------------------------------------------------------- | ---------------------------------------------------------------------------------------------------------------------------------------------- |
  | **P-01** | 강습 목록           | ① 필터 모달(상태, 월, 시간대)② 강습 카드 Grid - 버튼색: `신청(Y)/잔여없음(gray)`                     | GET /api/v1/swimming/lessons                                                                                                                   |
  | **P-02** | **결제 페이지**     | ① 신청 요약 ② **5분 카운트다운 타이머** ③ 사물함 선택 ④ KISPG 결제 UI 연동                           | GET /api/v1/payment/details/{enrollId}, GET /api/v1/payment/kispg-init-params/{enrollId}, POST /api/v1/payment/confirm/{enrollId} (KISPG 연동) |
  | **P-03** | 마이페이지-신청내역 | ① 리스트(상태 Badge: `PAID`, `CANCELED`, `UNPAID` (결제 계속 가능 시)) ② 취소/환불 버튼 (KISPG 연동) | GET /api/v1/swimming/my-enrolls (또는 Mypage API `/enroll`)                                                                                    |
  | **P-04** | ~~결제처리~~        | (P-02 KISPG 결제 페이지에 통합됨)                                                                    | ~~POST /api/v1/swimming/pay~~ (제거됨)                                                                                                         |
  | **P-05** | 재등록 모달         | 이전 수강 강습 제안 + 사물함 유지 여부                                                               | POST /api/v1/mypage/renewal (성공 시 KISPG 결제 페이지로 이동)                                                                                 |

  > 모바일: P-01, P-03는 Masonry Grid → 1 열, P-02(결제 페이지)는 풀스크린 모달 또는 전용 페이지.

  ***

## 4. API 상세

### 4-1. 공통

| 요소      | 값                                                                                   |
| --------- | ------------------------------------------------------------------------------------ |
| 인증      | OAuth2 Bearer/JWT                                                                    |
| 응답 규격 | `code`(int) + `message` + `data`                                                     |
| 오류코드  | 4001 잔여없음, 4002 만료(결제시간), 4003 미성년, 4008 결제페이지 접근불가, 500X 서버 |

### 4-2. 엔드포인트

| Method       | URL                                              | Req Body/QS                                               | Res Body                                                                         | 비고                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                 |
| ------------ | ------------------------------------------------ | --------------------------------------------------------- | -------------------------------------------------------------------------------- | ---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| GET          | /api/v1/swimming/lessons                         | status, year, month, startDate, endDate, page, size, sort | Page<LessonDTO>                                                                  | 수업 목록 조회 (상태: OPEN, CLOSED, ONGOING, COMPLETED 등, 연도, 월, 기간별 필터링 및 페이징). 'year' 매개변수는 컨트롤러에서 받지만, 현재 서비스 로직에서는 'month'를 주로 사용하며 'year' 단독 필터링은 지원되지 않을 수 있습니다. LessonDTO는 `lesson` 테이블 스키마(DDL 섹션 참조)를 따르며, `registration_end_date`를 포함하지 않습니다.                                                                                                                                                        |
| GET          | /api/v1/swimming/lessons/{lessonId}              | -                                                         | LessonDTO                                                                        | 특정 수업 상세 정보. LessonDTO는 위와 동일.                                                                                                                                                                                                                                                                                                                                                                                                                                                          |
| **DEL** GET  | /api/v1/swimming/lockers/availability            | lessonId                                                  | Map<String, Integer> (e.g. {"maleAvailable":10, "femaleAvailable":5})            | **[삭제됨]** 특정 강습의 성별 사용 가능 라커 수 조회. 글로벌 사물함 관리 방식으로 변경됨에 따라 이 API는 삭제됩니다. 신규 API `GET /api/v1/lockers/availability/status`를 사용하세요.                                                                                                                                                                                                                                                                                                                |
| **NEW** GET  | **/api/v1/lockers/availability/status**          | `gender` (String, "MALE" or "FEMALE")                     | **LockerAvailabilityDto**                                                        | **신규 API.** 회원이 자신의 성별을 API 파라미터로 전달하여 해당 성별의 글로벌 잔여 사물함 수를 조회합니다. `locker_inventory` 테이블에서 정보를 가져와 반환합니다.                                                                                                                                                                                                                                                                                                                                   |
| POST         | /api/v1/swimming/enroll                          | EnrollRequestDto (lessonId: Long)                         | **EnrollInitiationResponseDto** ({enrollId, paymentPageUrl, paymentExpiresAt})   | 수업 초기 신청. 성공 시 `enroll` 레코드 UNPAID 상태로 생성 (`expire_dt`은 5분 후). KISPG 결제 페이지로 이동할 정보 반환. 신청 시점에 즉시 **결제 페이지 접근 슬롯 확보** 시도. 실패 시 `409 LEC001 PAYMENT_PAGE_SLOT_UNAVAILABLE` 반환.                                                                                                                                                                                                                                                              |
| POST         | /api/v1/swimming/enroll/{enrollId}/cancel        | CancelRequestDto                                          | EnrollResponseDto                                                                | 신청 취소 (마이페이지 등에서 사용, KISPG 환불 연동).                                                                                                                                                                                                                                                                                                                                                                                                                                                 |
| GET          | /api/v1/swimming/my-enrolls                      | -                                                         | List<EnrollDTO>                                                                  | 내 신청 내역 전체 조회. `EnrollDTO`는 `user.md` (Mypage API) 문서의 4.3절 스키마를 따르며, `usesLocker` 등의 상세 정보를 포함합니다. `payStatus`에 `PAYMENT_TIMEOUT` 추가. `paymentPageUrl`은 KISPG 결제 페이지로 안내.                                                                                                                                                                                                                                                                              |
| GET          | /api/v1/swimming/my-enrolls/status               | status, page, size                                        | Page<EnrollDTO>                                                                  | 상태별 신청 내역(페이징). `EnrollDTO`는 `user.md` (Mypage API) 문서의 4.3절 스키마를 따릅니다.                                                                                                                                                                                                                                                                                                                                                                                                       |
| GET          | /api/v1/swimming/enrolls/{enrollId}              | -                                                         | EnrollDTO                                                                        | 특정 신청 상세 정보. `EnrollDTO`는 `user.md` (Mypage API) 문서의 4.3절 스키마를 따릅니다.                                                                                                                                                                                                                                                                                                                                                                                                            |
| **NEW** GET  | **/api/v1/payment/details/{enrollId}**           | -                                                         | **PaymentPageDetailsDto**                                                        | **KISPG 결제 페이지 전용.** `enrollId`로 결제에 필요한 CMS 내부 상세 정보(강습명, 금액, 사용자 성별에 따른 **글로벌 라커 옵션**, 결제 만료 시각) 조회. `enroll.expire_dt` 및 `pay_status=UNPAID` 유효성 검사.                                                                                                                                                                                                                                                                                        |
| **NEW** GET  | **/api/v1/payment/kispg-init-params/{enrollId}** | -                                                         | **KISPGInitParamsDto** (가칭, `mid`, `moid`, `requestHash` 등 KISPG 명세에 따름) | **KISPG 결제 페이지 전용.** `enrollId`로 KISPG 결제창 호출에 필요한 파라미터 조회. (상세 내용은 `Docs/cms/kispg-payment-integration.md` 참조)                                                                                                                                                                                                                                                                                                                                                        |
| **NEW** POST | **/api/v1/payment/confirm/{enrollId}**           | `{ pgToken: String, wantsLocker: Boolean }`               | 200 OK (상태: PAYMENT_SUCCESSFUL/PROCESSING) / Error                             | **KISPG 결제 페이지 전용.** KISPG 결제 후 `returnUrl`에서 호출. 주 목적은 UX 관리 및 사용자의 최종 `wantsLocker` 선택을 `Enroll` 테이블에 기록하는 것입니다. 실제 결제 확정, `Enroll.payStatus`를 `PAID`로 변경, 그리고 사물함 재고(`locker_inventory.used_quantity`) 업데이트는 KISPG Webhook (`POST /api/v1/kispg/payment-notification`)이 성공적인 결제를 확인한 후, `Enroll.usesLocker`가 true일 경우에만 사용자의 성별을 기준으로 글로벌 사물함 재고(`locker_inventory`)를 확인하고 처리합니다. |
| GET          | /api/v1/mypage/renewal                           | -                                                         | List<RenewalDTO>                                                                 | (마이페이지 API 경로 사용) 재등록 안내                                                                                                                                                                                                                                                                                                                                                                                                                                                               |
| POST         | /api/v1/mypage/renewal                           | lesson_id, carry_locker(Y/N)                              | EnrollInitiationResponseDto                                                      | (마이페이지 API 경로 사용) **매월 18일~22일 재수강 신청 기간에 사용.** `carry_locker`는 사용자의 희망 사항이며, 실제 사물함 유지는 이전 강습의 사물함 사용 여부와 다음 달 강습의 사물함 선택 여부에 따라 백엔드에서 처리. 성공 시 KISPG 결제 페이지로 이동.                                                                                                                                                                                                                                          |

**EnrollInitiationResponseDto 스키마:**

```json
{
  "enrollId": 12345,
  "lessonId": 678,
  "paymentPageUrl": "/payment/process?enroll_id=12345", // 예시 URL (KISPG 결제 연동)
  "paymentExpiresAt": "2025-07-01T10:05:00Z" // UTC, 5분 후
}
```

**LockerAvailabilityDto 스키마 (신규):**

```json
{
  "gender": "MALE", // 요청한 성별 ("MALE" 또는 "FEMALE")
  "totalQuantity": 100, // 해당 성별의 전체 글로벌 사물함 수 (locker_inventory.total_quantity)
  "usedQuantity": 60, // 해당 성별의 현재 사용 중인 글로벌 사물함 수 (locker_inventory.used_quantity)
  "availableQuantity": 40 // 계산된 잔여 사물함 수 (totalQuantity - usedQuantity)
}
```

**PaymentPageDetailsDto 스키마 (수정):**

```json
{
  "enrollId": 12345,
  "lessonTitle": "고급 수영반 (월수금)",
  "lessonPrice": 80000,
  "userGender": "MALE", // 라커 선택에 필요
  "lockerOptions": {
    // 사용자의 성별에 해당하는 글로벌 라커 잔여 정보 (locker_inventory 기준, *현재 진행 중이거나 예정된 강습에 배정된 사물함을 제외한 수량*)
    "lockerAvailableForUserGender": true, // Boolean: 사용자 성별의 글로벌 사물함 잔여 여부 (availableCountForUserGender > 0)
    "availableCountForUserGender": 40, // Integer: 사용자 성별의 현재 글로벌 잔여 사물함 수
    "lockerFee": 5000 // 사물함 사용료 (0일 수 있음, 강습별 또는 전역 설정 가능)
  },
  "amountToPay": 80000, // 최종 결제 금액 (사물함 선택 시 업데이트 될 수 있음)
  "paymentDeadline": "2025-07-01T10:05:00Z" // enroll.expire_dt
}
```

---

## 5. DB 구조 (사용자 관점 필드 추가)

| 테이블               | 필드(추가/변경)              | 설명                                                                                           |
| -------------------- | ---------------------------- | ---------------------------------------------------------------------------------------------- |
| **enroll**           | `expire_dt` DATETIME         | **결제 페이지 접근 및 결제 시도 만료 시간 (신청 시점 + 5분)**                                  |
|                      | `renewal_flag` TINYINT       | 1 이면 재등록                                                                                  |
|                      | `cancel_reason` VARCHAR(100) | 사용자 입력                                                                                    |
|                      | `uses_locker` BOOLEAN        | 사물함 사용 여부 (결제 페이지에서 최종 확정)                                                   |
|                      | `pay_status` VARCHAR(20)     | 결제 상태 (`UNPAID`, `PAID`, `CANCELED_UNPAID`, **`PAYMENT_TIMEOUT`**)                         |
| **user**             | `adult_verified` TINYINT     | 미성년자 차단                                                                                  |
| **lesson**           | `male_locker_cap` INT        | **[삭제됨]** 강습별 남성 라커 정원                                                             |
|                      | `female_locker_cap` INT      | **[삭제됨]** 강습별 여성 라커 정원                                                             |
| **locker_inventory** | (DDL 참조)                   | 전체 사물함 재고 (성별 총량, 사용량 관리). **이 테이블이 사물함 수량의 유일한 기준이 됩니다.** |

_강습 잔여 좌석은 `lesson.capacity - (SELECT COUNT(*) FROM enroll WHERE lesson_id=? AND pay_status='PAID') - (SELECT COUNT(*) FROM enroll WHERE lesson_id=? AND pay_status='UNPAID' AND expire_dt > NOW())` 로 계산하여 결제 페이지 접근 제어._

_성별 잔여 사물함 수는 `locker_inventory` 테이블의 해당 성별 `total_quantity - used_quantity`로 계산됩니다. 관리자는 `locker_inventory` 테이블에 성별 총 사물함 수를 설정하며, `used_quantity`는 사물함 신청/해지 시점 및 **강습 종료에 따른 자동 회수 시점**에 갱신됩니다._

(DDL 섹션에 `locker_inventory` 추가 예정)

---

## 6. **비즈니스 룰**

| 구분                         | 세부 규칙                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                    |
| ---------------------------- | -------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| **선착순**                   | _KISPG Webhook (`POST /api/v1/kispg/payment-notification`) 처리 완료 시점_ 에 `pay_status=PAID` 로 바뀌어야 정원 확정. `POST /api/v1/swimming/enroll` 시점에 **결제 페이지 접근 슬롯 (정원 - PAID 건수 - 만료 전 UNPAID 건수)이 확보되어야** KISPG 결제 페이지 접근 가능.                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                    |
| **5분 결제 페이지 타임아웃** | `enroll.expire_dt < NOW()` + `pay_status='UNPAID'` ⇒ `PAYMENT_TIMEOUT`. 프론트엔드는 KISPG 결제 페이지에서 타이머 만료 시 이전 페이지로 리디렉션 및 안내 토스트.                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                             |
| **미성년자 제한**            | `user.adult_verified=0` 이면 `/api/v1/swimming/enroll` → 4003                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                |
| **사물함 사용 신청**         | **KISPG 결제 페이지에서** 사용자가 `wantsLocker: true`로 최종 선택 의사를 표시하고, 이 선택은 `/api/v1/payment/confirm` 호출 시 `Enroll.usesLocker` 필드에 기록됩니다. 실제 사물함 배정 및 `locker_inventory.used_quantity` 업데이트는 KISPG Webhook (`POST /api/v1/kispg/payment-notification`)이 성공적인 결제를 확인한 후, `Enroll.usesLocker`가 true일 경우에만 사용자의 성별을 기준으로 글로벌 사물함 재고(`locker_inventory`)를 확인하고 처리합니다. **배정된 사물함은 해당 강습의 종료일까지만 유효하며, 강습 종료 후에는 시스템에 의해 자동으로 회수됩니다 (단, 재수강으로 사물함이 이전되는 경우는 예외).**                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                         |
| **사물함 재고(글로벌)**      | 사물함은 관리자가 `locker_inventory` 테이블에 설정한 성별 글로벌 총량 내에서 사용 가능. **KISPG 결제 페이지 진입 및 최종 결제 시** 사용자의 성별에 해당하는 글로벌 라커 잔여분을 `locker_inventory` 테이블을 통해 확인합니다. 잔여 수량은 현재 진행 중이거나 예정된 강습에 배정된 사물함(재수강으로 이전된 사물함 포함)을 제외하고 계산됩니다. 잔여 수량이 없으면 사물함 선택 불가. **강습이 종료된 신청 건에 할당되었던 사물함(재수강으로 이전되지 않은 경우)은 배치 작업을 통해 `locker_inventory.used_quantity`에서 제외(회수)됩니다.**                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                   |
| **결제 페이지 접근 제한**    | `/api/v1/swimming/enroll` 호출 시, 강습의 **결제 페이지 접근 슬롯 (정원 - PAID 건수 - 만료 전 UNPAID 건수)** 이 0보다 큰 경우에만 결제 페이지 접근 관련 정보(`EnrollInitiationResponseDto`) 반환. 그렇지 않으면 `409 LEC001 PAYMENT_PAGE_SLOT_UNAVAILABLE` 오류 반환.                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                        |
| **재등록 우선권**            | **재수강(기존 회원) 신청 기간: 매월 20일 00:00 ~ 25일 23:59.** 이 기간에 `/api/v1/mypage/renewal` API를 통해 다음 달 강습을 신청할 수 있는 우선권 부여. **신규 회원 신청 기간: 매월 26일 00:00 ~ 말일 23:59.** (또는 강습 시작 전까지). 현재 달의 강습은 해당 월 말일까지 신규 신청 가능. 재수강 기간 외 `/api/v1/mypage/renewal` API 호출 시 403 에러.                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                      |
| **취소/환불**                | **1. 수강 시작일 전 사용자 자가 취소:**<br/> - 사용자는 마이페이지를 통해 수강 시작일 전인 강습에 대해 직접 취소를 신청할 수 있습니다.<br/> - 이 경우, 별도의 위약금 없이 결제된 강습료 전액이 환불됩니다. (사물함 이용료는 환불되지 않음, PG사 정책에 따른 최소한의 취소 수수료가 발생할 수 있습니다.)<br/> - 취소 즉시 KISPG를 통해 자동 환불 절차가 진행됩니다 (또는 환불 요청 상태로 변경 후 배치 처리).<br/><br/>**2. 수강 시작일 후 (수강 중) 사용자 취소 요청:**<br/> - 수강 시작일(해당일 00시 00분 01초부터) 이후에는 사용자가 마이페이지에서 취소 '요청'만 가능하며, 최종 취소 및 환불 처리는 관리자의 승인이 필요합니다.<br/> - **사용일수 기준**: 취소 요청일까지 사용한 것으로 간주되며, 하루 중 일부만 수강했더라도 해당일은 사용한 날로 계산됩니다.<br/> - **일일 정산액**: 수강료는 1일 3,500원으로 계산됩니다. 사물함 이용료는 환불되지 않습니다.<br/> - **위약금**: 없습니다.<br/> - **환불액 계산**: `(결제된 강습료) - (일일 강습료 3,500원 * 사용일수)`. (사물함 이용료는 환불되지 않습니다.)<br/> - 계산된 환불액이 0보다 작을 경우 환불액은 0원입니다.<br/> - 관리자 승인 후 KISPG를 통해 해당 환불액만큼 부분 취소 처리됩니다.<br/> - 사용자는 마이페이지에서 취소 요청 상태 및 최종 환불 내역을 확인할 수 있습니다. |
| **월별 중복 제한**           | 동일 사용자는 같은 달에 하나의 강습만 신청 가능 (오류코드 4004: 월별 중복 신청)                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                              |

---

## 7. 배치/이벤트 (User Side 관련)

| 이름                                       | 주기                | 설명                                                                                                                                                                                                                                                                                                                                            |
| ------------------------------------------ | ------------------- | ----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| **payment-timeout-sweep**                  | 1-5 min             | `UNPAID` & `expire_dt` 초과 레코드 → `PAYMENT_TIMEOUT`, (KISPG 결제 페이지에서 선택했던) locker roll-back 등 처리                                                                                                                                                                                                                               |
| **pg-webhook**                             | 실시간              | KISPG callback 검증, 위·변조 체크 ◀→ enroll·payment 갱신                                                                                                                                                                                                                                                                                        |
| **renewal-notifier**                       | 하루 1회            | 재등록 대상자 알림(LMS)                                                                                                                                                                                                                                                                                                                         |
| **lesson-completion-locker-release-sweep** | 하루 1회 (예: 새벽) | 종료된 강습(`lesson.end_date < 어제`)에 대해 `Enroll.usesLocker=true` 및 `Enroll.lockerAllocated=true`인 신청 건을 찾아 `LockerService.decrementUsedQuantity()` 호출 및 `Enroll.lockerAllocated=false`로 업데이트하여 사물함 회수. **재수강으로 인해 이미 `lockerAllocated=false`로 처리된 이전 강습 건은 이 배치에서 중복 처리되지 않습니다.** |

---

## 8. 예외 처리 플로우

1.  **동시 클릭 (`/enroll`)** → DB `SELECT … FOR UPDATE` + UNIQUE 인덱스(`lesson_id`,`user_id` ON `enroll` table WHERE `pay_status != 'PAYMENT_TIMEOUT' or CANCELED_UNPAID`)

    - 정원 초과 시 결제 페이지 접근 불가 응답.

    - **결제 페이지 접근 슬롯 부족 시 `409 LEC001 PAYMENT_PAGE_SLOT_UNAVAILABLE` 응답.**

2.  **KISPG 결제 실패 (결제 페이지)** → `payment.status=FAIL`, `enroll` 그대로 UNPAID (5분 내 재시도 가능).
3.  **결제 페이지 타임아웃** → 프론트엔드 자동 리디렉션 (e.g. 강습 목록) + "5분 시간 초과" 토스트. `enroll.pay_status`는 배치 또는 다음 접근 시 `PAYMENT_TIMEOUT` 처리.
4.  **Webhook 지연**(KISPG > 5 초) → 프론트 Poll `/api/v1/swimming/enrolls/{id}` (마이페이지에서) 5 회(2 s 간격) 후 로딩 모달 종료 (결제 페이지에서는 즉시 결과 피드백 가정)

---

## 9. 테스트 케이스(요약)

| ID    | 시나리오                                                                                                                 | 예상 결과                                                                                                                                                                                                                 |
| ----- | ------------------------------------------------------------------------------------------------------------------------ | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| TC-01 | 정원 마감 직전 2 명이 동시 `/enroll`                                                                                     | 1 명 결제페이지 접근, 1 명 `409 LEC001 PAYMENT_PAGE_SLOT_UNAVAILABLE` (또는 4001 잔여없음 - 서버 구현에 따라 다름).                                                                                                       |
| TC-02 | 결제 페이지 진입 후 4분 50초에 결제 완료                                                                                 | 상태 PAID                                                                                                                                                                                                                 |
| TC-03 | 결제 페이지 진입 후 5분 1초에 결제 시도 (`/payment/confirm`)                                                             | API 4002(만료) 에러. 프론트는 이미 리디렉션되었어야 함.                                                                                                                                                                   |
| TC-04 | 미성년자 로그인 → `/enroll`                                                                                              | 4003                                                                                                                                                                                                                      |
| TC-05 | 재등록 기간 외 /api/v1/swimming/renewal                                                                                  | 403                                                                                                                                                                                                                       |
| TC-06 | 같은 달에 두 번째 강습 신청 시도                                                                                         | /api/v1/swimming/enroll → 4004(월별 중복)                                                                                                                                                                                 |
| TC-07 | 결제 페이지에서 브라우저 강제 종료 후 10분 뒤 마이페이지 확인                                                            | 해당 신청 건 `PAYMENT_TIMEOUT` 상태로 표시                                                                                                                                                                                |
| TC-08 | 사용 가능한 여성 라커 1개, 남성 라커 0개. 남성 유저가 결제 페이지 진입.                                                  | 라커 선택 불가 또는 남성 라커 선택 시 "사용 불가" 안내. 라커 없이 결제는 가능.                                                                                                                                            |
| TC-09 | 여성 유저 A가 결제 페이지에서 여성 라커 선택. 동시 여성 유저 B가 결제 페이지 진입.                                       | 유저 B는 해당 여성 라커 선택 불가 (또는 잔여 라커 없음으로 표시, 최종 `confirm`에서 한번 더 체크).                                                                                                                        |
| TC-10 | 기존 회원 A가 재수강 기간(18일)에 이전 달에 사용하던 사물함을 포함하여 재수강 신청 및 결제 완료.                         | 새로운 달의 강습 신청에 `lockerAllocated=true`로 설정. `locker_inventory.used_quantity`는 변동 없음 (중복 증가 안함). 이전 달 강습 신청 건은 `lockerAllocated=false`로 업데이트됨. 대시보드의 잔여 라커 수는 정확히 유지. |
| TC-11 | 기존 회원 B가 재수강 기간에 이전 달에는 사물함을 사용하지 않았으나, 이번에는 사물함을 포함하여 재수강 신청 및 결제 완료. | 새로운 달의 강습 신청에 `lockerAllocated=true`로 설정. `locker_inventory.used_quantity`는 1 증가.                                                                                                                         |
| TC-12 | 신규 회원 C가 신규 회원 신청 기간(25일)에 사물함을 포함하여 강습 신청 및 결제 완료.                                      | 강습 신청에 `lockerAllocated=true`로 설정. `locker_inventory.used_quantity`는 1 증가.                                                                                                                                     |

---

## 10. 프론트엔드 구현 Tips (Next.js/React 기반)

- **상태별 버튼색 (강습 목록)**:
  - `OPEN` (신청 가능) → Blue "신청하기"
  - `CLOSED` (정원 마감 등) → Gray "마감"
- **결제 페이지 (`pages/payment/process.jsx` 또는 유사 경로, KISPG 연동)**:
  - **API 호출**: `GET /api/v1/payment/details/{enrollId}` (CMS 정보), `GET /api/v1/payment/kispg-init-params/{enrollId}` (KISPG 파라미터) 호출.
  - **5분 카운트다운 타이머**: React `useState` 및 `useEffect`를 사용하여 명확하게 표시. 만료 시 Next.js `useRouter`를 사용하여 자동 리디렉션 및 `react-toastify` 같은 라이브러리로 토스트 "5분의 시간이 경과되어 결제 이전 창으로 이동합니다." 표시.
- **라커 선택 UI**: `PaymentPageDetailsDto.userGender` 및 `PaymentPageDetailsDto.lockerOptions`의 `lockerAvailableForUserGender`, `availableCountForUserGender` 값을 사용하여 사용자 성별에 따른 사물함 사용 가능 여부 및 잔여 개수 표시. 선택 시 React `useState`로 즉시 총 결제 금액 업데이트. 만약 `availableCountForUserGender`가 0 이하면 사물함 선택 옵션 비활성화.
  - KISPG 연동: KISPG 제공 방식(SDK 또는 form POST)으로 결제창 호출. KISPG `returnUrl` 처리 시 `POST /api/v1/payment/confirm/{enrollId}` 호출.
  - **마이페이지 신청 내역 (`components/EnrollCard.jsx` 또는 유사)**:
    - `UNPAID` (결제 시도 전/실패 후 5분 이내, `enroll.paymentPageUrl` 사용 가능 시, KISPG 연동) → Yellow `<Link href={enroll.paymentPageUrl}><a>결제 페이지로 이동</a></Link>` (만약 `enroll.expire_dt`가 유효하고, 사용자가 결제 페이지에서 이탈한 경우 다시 진입 허용. 단, 이 경우 남은 시간만 카운트) 또는 "결제 진행 중" (만료 전).
    - `PAID` (KISPG 결제 완료) → Green "결제완료" + 영수증 링크 (필요시 KISPG 제공 링크 또는 자체 생성)
    - `CANCELED_*` → Red "취소됨"
    - `PAYMENT_TIMEOUT` → Gray "시간 초과"
  - **Accessibility**: 강습 카드 ALT 텍스트 "{요일·시간대} 초급반 잔여 {n}석"

---

## 11. 배포 체크리스트

1. PG 상용키 교체 & 도메인 whitelisting
2. Webhook URL 방화벽 허용
3. **payment-timeout-sweep** 배치 crontab 등록 (주기: 1-5분, KISPG 연동)
4. 잔여좌석 모니터 Grafana Dashboard (PAID 뿐 아니라 UNPAID+expire_dt 유효 건 포함, KISPG 연동)
5. **신규 KISPG 결제 페이지 UI/UX 최종 검토**
6. **KISPG 결제 페이지 타임아웃 리디렉션 및 토스트 메시지 확인**

---

## 12. 데이터베이스 스키마 (DDL)

```sql
-- 강습 테이블: 수영 강습 정보를 저장하는 테이블
CREATE TABLE lesson (
    lesson_id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '강습 ID (PK)',
    title VARCHAR(100) NOT NULL COMMENT '강습명(예: 초급반, 중급반 등)',
    start_date DATE NOT NULL COMMENT '강습 시작일',
    end_date DATE NOT NULL COMMENT '강습 종료일',
    lesson_year INT GENERATED ALWAYS AS (YEAR(start_date)) VIRTUAL COMMENT '강습 연도',
    lesson_month INT GENERATED ALWAYS AS (MONTH(start_date)) VIRTUAL COMMENT '강습 월',
    capacity INT NOT NULL COMMENT '총 정원 수',
    price INT NOT NULL COMMENT '강습 비용(원)',
    status VARCHAR(20) NOT NULL COMMENT '강습 상태(OPEN, CLOSED, ONGOING, COMPLETED)',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '등록일시',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '수정일시',
    created_by VARCHAR(50) COMMENT '등록자',
    created_ip VARCHAR(45) COMMENT '등록 IP',
    updated_by VARCHAR(50) COMMENT '수정자',
    updated_ip VARCHAR(45) COMMENT '수정 IP',
    INDEX idx_status (status),
    INDEX idx_date (start_date, end_date),
    INDEX idx_year_month (lesson_year, lesson_month) COMMENT '연도/월별 조회용 인덱스'
) COMMENT '수영 강습 정보 테이블';

-- 성별 라커 재고 정보 테이블 (새로 추가)
CREATE TABLE locker_inventory (
  gender varchar(10) NOT NULL COMMENT '성별 (MALE, FEMALE) - PK',
total_quantity int(11) NOT NULL DEFAULT 0 COMMENT '총 라커 수 (관리자 설정)',
used_quantity int(11) NOT NULL DEFAULT 0 COMMENT '현재 사용 중인 라커 수 (시스템 업데이트)',
  CREATED_BY varchar(36) DEFAULT NULL COMMENT '생성자 ID',
  CREATED_IP varchar(45) DEFAULT NULL COMMENT '생성자 IP',
  CREATED_AT timestamp NOT NULL DEFAULT current_timestamp() COMMENT '생성 시각',
  UPDATED_BY varchar(36) DEFAULT NULL COMMENT '수정자 ID',
  UPDATED_IP varchar(45) DEFAULT NULL COMMENT '수정자 IP',
  UPDATED_AT timestamp NOT NULL DEFAULT current_timestamp() ON UPDATE current_timestamp() COMMENT '수정 시각',
  PRIMARY KEY (gender)
) COMMENT='성별 라커 재고 정보. 사물함 수량 관리의 유일한 기준점.';

-- 신청 테이블: 모든 강습 신청(초기, 재수강 등) 정보를 통합 저장하는 테이블
CREATE TABLE enroll (
    enroll_id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '신청 ID (PK)',
    user_id BIGINT NOT NULL COMMENT '사용자 ID (FK)',
    user_name VARCHAR(50) NOT NULL COMMENT '사용자명',
    lesson_id BIGINT NOT NULL COMMENT '강습 ID (FK)',
    uses_locker BOOLEAN NOT NULL DEFAULT FALSE COMMENT '사물함 사용 여부 (결제 페이지에서 최종 확정)',
    status VARCHAR(20) NOT NULL COMMENT '신청 상태(APPLIED, CANCELED, PENDING) - 초기 신청시 상태',
    pay_status VARCHAR(20) NOT NULL DEFAULT 'UNPAID' COMMENT '결제 상태(UNPAID, PAID, CANCELED_UNPAID, PAYMENT_TIMEOUT)',
    expire_dt DATETIME NOT NULL COMMENT '결제 페이지 접근 및 결제 시도 만료 시간 (신청 시점 + 5분)',
    renewal_flag TINYINT(1) NOT NULL DEFAULT 0 COMMENT '재등록 여부(1: 재등록, 0: 신규)',
cancel_reason VARCHAR(255) COMMENT '취소 사유',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '신청일시',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '수정일시',
    created_by VARCHAR(50) COMMENT '등록자',
    created_ip VARCHAR(45) COMMENT '등록 IP',
    updated_by VARCHAR(50) COMMENT '수정자',
    updated_ip VARCHAR(45) COMMENT '수정 IP',
    FOREIGN KEY (user_id) REFERENCES user(user_id),
    FOREIGN KEY (lesson_id) REFERENCES lesson(lesson_id),
    UNIQUE KEY uk_user_lesson_active_enroll (user_id, lesson_id, pay_status) COMMENT '사용자별 동일 강좌에 대한 유효한(PAID, UNPAID 만료전) 신청 중복 방지 (세부 조건 검토 필요)',
    INDEX idx_status_pay (status, pay_status),
    INDEX idx_lesson_status_pay (lesson_id, status, pay_status),
    INDEX idx_expire_pay_status (expire_dt, pay_status), -- 변경: 인덱스명 및 용도 명확화
    INDEX idx_user_pay_status (user_id, pay_status),
    INDEX idx_renewal (renewal_flag)
) COMMENT '모든 강습 신청(초기, 재수강 등) 정보를 통합 저장하는 테이블';

-- 결제 테이블: 결제 정보를 저장하는 테이블 (결제 페이지에서 처리)
CREATE TABLE payment (
    payment_id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '결제 ID (PK)',
    enroll_id BIGINT NOT NULL COMMENT '신청 ID (FK from enroll.enroll_id)',
    tid VARCHAR(100) NOT NULL COMMENT 'PG사 거래 ID',
    pg_provider VARCHAR(20) NOT NULL COMMENT 'PG사 제공업체(kakao, nice 등)',
    amount INT NOT NULL COMMENT '결제 금액',
    refund_amount INT COMMENT '환불 금액',
    refund_dt DATETIME COMMENT '환불 일시',
    pg_auth_code VARCHAR(100) COMMENT 'PG사 인증 코드',
    card_info VARCHAR(100) COMMENT '카드 정보(마스킹 처리)',
    status VARCHAR(20) NOT NULL COMMENT '결제 상태(PAID, CANCELED, PARTIAL_REFUNDED, FAILED)',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '결제일시',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '수정일시',
    created_by VARCHAR(50) COMMENT '등록자',
    created_ip VARCHAR(45) COMMENT '등록 IP',
    updated_by VARCHAR(50) COMMENT '수정자',
    updated_ip VARCHAR(45) COMMENT '수정 IP',
    FOREIGN KEY (enroll_id) REFERENCES enroll(enroll_id),
    UNIQUE KEY uk_tid (tid),
    INDEX idx_enroll (enroll_id),
    INDEX idx_status (status),
    INDEX idx_created_at (created_at)
) COMMENT '결제 정보 테이블 (결제 페이지에서 처리됨)';

-- 취소 요청 테이블: 개강 후 취소 요청 정보를 저장하는 테이블
CREATE TABLE cancel_request (
    request_id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '요청 ID (PK)',
    enroll_id BIGINT NOT NULL COMMENT '신청 ID (FK from enroll.enroll_id)',
    reason VARCHAR(200) NOT NULL COMMENT '취소 사유',
    refund_pct INT COMMENT '환불 비율(%)',
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING' COMMENT '요청 상태(PENDING, APPROVED, DENIED)',
    comment VARCHAR(200) COMMENT '관리자 코멘트',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '요청일시',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '수정일시',
    created_by VARCHAR(50) COMMENT '등록자',
    created_ip VARCHAR(45) COMMENT '등록 IP',
    updated_by VARCHAR(50) COMMENT '수정자',
    updated_ip VARCHAR(45) COMMENT '수정 IP',
    FOREIGN KEY (enroll_id) REFERENCES enroll(enroll_id),
    INDEX idx_status (status),
    INDEX idx_created (created_at)
) COMMENT '강습 취소 요청 정보 테이블 (Mypage에서 관리될 수 있음)';

-- 배치 작업 로그 테이블: 배치 작업의 실행 기록을 저장하는 테이블
CREATE TABLE batch_job_log (
    log_id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '로그 ID (PK)',
    job_name VARCHAR(50) NOT NULL COMMENT '작업 이름 (예: payment-timeout-sweep)',
    status VARCHAR(20) NOT NULL COMMENT '작업 상태(STARTED, COMPLETED, FAILED)',
    start_dt DATETIME NOT NULL COMMENT '작업 시작 시간',
    end_dt DATETIME COMMENT '작업 종료 시간',
    records_processed INT DEFAULT 0 COMMENT '처리된 레코드 수',
    error_message TEXT COMMENT '오류 메시지',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '등록일시',
    INDEX idx_job_name (job_name),
    INDEX idx_start_dt (start_dt)
) COMMENT '배치 작업 로그 테이블';

-- PG Webhook 로그 테이블: 결제 웹훅 요청의 로그를 저장하는 테이블
CREATE TABLE pg_webhook_log (
    log_id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '로그 ID (PK)',
    tid VARCHAR(100) NOT NULL COMMENT 'PG사 거래 ID',
    request_body TEXT NOT NULL COMMENT '웹훅 요청 본문(JSON)',
    status VARCHAR(20) NOT NULL COMMENT '처리 상태(SUCCESS, FAILED, DUPLICATED)',
    ip_address VARCHAR(45) NOT NULL COMMENT '요청 IP 주소',
    verified TINYINT(1) NOT NULL DEFAULT 0 COMMENT '서명 검증 여부',
    error_message VARCHAR(255) COMMENT '오류 메시지',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '요청 수신 시간',
    processed_at DATETIME COMMENT '처리 완료 시간',
    INDEX idx_tid (tid),
    INDEX idx_status (status),
    INDEX idx_created_at (created_at)
) COMMENT 'PG 웹훅 요청 로그 테이블';

-- 재등록 안내 테이블: 재등록 안내 대상자 정보를 저장하는 테이블
CREATE TABLE renewal_notification (
    notification_id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '알림 ID (PK)',
    user_id BIGINT NOT NULL COMMENT '사용자 ID (FK)',
    previous_enroll_id BIGINT NOT NULL COMMENT '이전 신청 ID',
    previous_lesson_id BIGINT NOT NULL COMMENT '이전 강습 ID',
    suggested_lesson_id BIGINT NOT NULL COMMENT '제안할 다음 강습 ID',
    previous_locker_id BIGINT COMMENT '이전 사물함 ID',
    sent_at DATETIME COMMENT '발송 시간',
    is_sent TINYINT(1) NOT NULL DEFAULT 0 COMMENT '발송 여부',
    notification_type VARCHAR(20) NOT NULL DEFAULT 'SMS' COMMENT '알림 유형(SMS, EMAIL, PUSH)',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '생성 시간',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '수정 시간',
    FOREIGN KEY (user_id) REFERENCES user(user_id),
    FOREIGN KEY (previous_lesson_id) REFERENCES lesson(lesson_id),
    FOREIGN KEY (suggested_lesson_id) REFERENCES lesson(lesson_id),
    INDEX idx_user (user_id),
    INDEX idx_sent (is_sent),
    INDEX idx_created_at (created_at)
) COMMENT '재등록 안내 정보 테이블';
```
