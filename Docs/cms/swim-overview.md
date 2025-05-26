- 🏊‍♀️ 수영장 **신청 & 신청내역 확인** — 사용자·관리자-측 통합 개발문서
  _(Frontend SPA + REST API 기준 · **결제 로직 변경 반영**)_

  ***

  ## 0. 문서 목표

  | 항목      | 내용                                                                                                                                               |
  | --------- | -------------------------------------------------------------------------------------------------------------------------------------------------- |
  | 범위      | **일반 회원**: 강습·사물함 신청·취소신청·재등록 (결제는 전용 페이지)<br/>**관리자**: CMS를 통한 강습, 사물함, 신청, 결제, 통계 등 시스템 운영 관리 |
  | 달성 지표 | ① 선착순(결제 완료 기준) ② **5분 내 결제 완료** ③ 신청 후 0 오류 좌석·라커 관리 ④ 관리자 한 화면 KPI 확인                                          |

  ***

  ## 1. 용어·역할

  | 코드             | 설명                                                                                                 |
  | ---------------- | ---------------------------------------------------------------------------------------------------- |
  | **USER**         | 일반 회원(성인)                                                                                      |
  | **JUNIOR_USER**  | 미성년자(온라인 신청 차단)                                                                           |
  | **ENROLL**       | 강습 신청 레코드 (`APPLIED`/`CANCELED`, 내부 `payStatus`: `UNPAID`, `PAID`)                          |
  | **LOCKER**       | (개념 변경) `locker_inventory`를 통해 성별 총량 및 사용량으로 관리. 사용자는 신청 시 사용 여부 선택. |
  | **RENEWAL**      | 기존 수강생 재등록 프로세스                                                                          |
  | **PAYMENT_PAGE** | **결제 전용 페이지 (5분 제한)**                                                                      |
  | **SYSTEM_ADMIN** | (관리자) CMS 전체 시스템 설정, 모든 관리 기능 접근 권한 (최고 관리자).                               |
  | **ADMIN**        | (관리자) CMS를 사용하여 강습, 사물함, 신청, 결제, 환불 등 일반 운영 관리 (운영 관리자).              |

  ***

  ## 2. 주요 시나리오(Sequence)

  ```mermaid
  sequenceDiagram
      participant U as 사용자
      participant FE as Frontend
      participant API as REST API
      participant KISPG_Window as KISPG 결제창
      participant KISPG_Server as KISPG 서버
      %% 관리자 관련 시퀀스는 swim-admin.md 또는 별도 관리자 시퀀스에서 상세 기술
      participant ADM as 관리자 (CMS 사용)
      participant CMS_API as CMS Backend API

      Note over FE,API: 🔒 Tx / 잔여 Lock (5분)
      U->>FE: 강습 카드 '신청하기'
      FE->>API: POST /api/v1/enrolls (lessonId)
      alt 정원 및 동시 접근 가능
        API-->>FE: EnrollInitiationResponseDto (enrollId, paymentPageUrl, paymentExpiresAt)
        FE->>U: KISPG 결제 페이지로 리디렉션 (paymentPageUrl, 5분 타이머 시작)
        U->>FE: (결제 페이지) [결제하기] (KISPG 연동)
        FE->>API: GET /api/v1/payment/kispg-init-params/{enrollId} (호출 전 또는 병행)
        API-->>FE: KISPG Init Params
        FE->>KISPG_Window: KISPG 결제창 호출
        KISPG_Window-->>KISPG_Server: 결제 시도
        KISPG_Server-->>API: POST /api/v1/kispg/payment-notification (Webhook)
        API-->>KISPG_Server: Webhook ACK
        Note over API: Webhook: KISPG 데이터 검증, Enroll/Payment PAID 상태 변경, 사물함 배정(Enroll.usesLocker=true 및 결제 성공 시 locker_inventory 업데이트 및 Enroll.lockerAllocated=true 설정)
        KISPG_Server-->>KISPG_Window: 결제 완료
        KISPG_Window-->>FE: KISPG Return URL로 리디렉션
        FE->>API: POST /api/v1/payment/confirm/{enrollId} (UX용, wantsLocker 전달)
        API-->>FE: {status: PAYMENT_SUCCESSFUL/PROCESSING}
        FE-->>U: 결제 완료/처리중 안내 → [마이페이지로 이동] 안내
      else 정원 초과 또는 접근 불가
        API-->>FE: 오류 (예: 409 LEC001 PAYMENT_PAGE_SLOT_UNAVAILABLE)
        FE->>U: 신청 불가 안내
      end
      alt KISPG 결제 페이지 5분 타임아웃 또는 사용자 취소
        FE->>U: 이전 페이지로 리디렉션 + "시간 초과/취소" 토스트
        Note over API: enroll.pay_status -> PAYMENT_TIMEOUT (배치 처리)
      end

      U->>FE: (마이페이지) 취소 버튼
      FE->>API: PATCH /mypage/enroll/{enrollId}/cancel (취소 요청)
      API-->>FE: 200 OK (요청 접수)

      U->>FE: (마이페이지, 재수강 기간 18~22일) 재수강 신청 (lessonId, carryLocker)
      FE->>API: POST /mypage/renewal (lessonId, carryLocker)
      API-->>FE: EnrollInitiationResponseDto (결제 페이지로 이동)
      Note over API: 재수강 시, 기존 사물함 사용 여부 확인.
      Note over API: 사물함 유지 시: 새 Enroll에 lockerAllocated=true, 이전 Enroll에 lockerAllocated=false. locker_inventory.used_quantity 변동 없음.
      Note over API: 사물함 신규 요청 시: 일반 신청과 동일하게 locker_inventory.used_quantity 증가.
      FE->>U: KISPG 결제 페이지로 리디렉션 (위의 일반 결제 흐름과 유사하게 진행)

      Note over API,ADM: 관리자(ADMIN)는 CMS를 통해 취소 요청 검토 및 승인 (KISPG 연동)
      ADM->>CMS_API: (CMS 통해) 취소 승인 (enrollId)
      CMS_API->>KISPG_Server: KISPG 부분/전액 취소 API 호출 (계산된 환불액, tid)
      KISPG_Server-->>CMS_API: 취소 성공/실패
      CMS_API->>API: (DB 업데이트) enroll.pay_status, payment.refunded_amt 등
      Note over FE: 사용자에게 상태 변경 알림 (예: 마이페이지 업데이트, 알림)
  ```

  ***

  ## 3. **화면 정의** (사용자 및 관리자 주요 화면)

  | ID                | 화면                | 주요 UI 요소                                                                         | 주요 연관 API (예시)                                                                                                              |
  | ----------------- | ------------------- | ------------------------------------------------------------------------------------ | --------------------------------------------------------------------------------------------------------------------------------- |
  | **P-01**          | 강습 목록 (사용자)  | 필터(월·레벨) · 강습 카드(`신청/마감` - KISPG 연동)                                  | GET /api/v1/lessons                                                                                                               |
  | **P-02**          | **결제 페이지**     | 강습 요약 · **5분 카운트다운** · 사물함 선택 · KISPG UI                              | GET /api/v1/payment/details/{enrollId}, GET /api/v1/payment/kispg-init-params/{enrollId}, POST /api/v1/payment/confirm/{enrollId} |
  | **MP-01**         | 마이페이지-신청내역 | 신청 카드(Status Badge: PAID, PAYMENT_TIMEOUT, CANCELED·취소 버튼 - KISPG 환불 연동) | GET /mypage/enroll                                                                                                                |
  | **MP-02**         | 재등록 모달         | 기존 강습·라커 carry 토글                                                            | GET·POST /mypage/renewal                                                                                                          |
  | **A-CMS-DASH**    | CMS Dashboard       | 실시간 KPI (신청, 좌석, 매출, 라커 현황).                                            | GET /api/v1/cms/stats/dashboard                                                                                                   |
  | **A-CMS-LESSON**  | CMS 강습 관리       | 강습 정보 (일정/스케줄, 정원, 가격, 상태 등) CRUD, 복제.                             | GET, POST, PUT, DELETE /api/v1/cms/lessons/\*                                                                                     |
  | **A-CMS-LOCKER**  | CMS 사물함 관리     | 성별 전체 사물함 재고(`locker_inventory`) 현황 조회 및 총 수량 수정.                 | GET, PUT /api/v1/cms/lockers/inventory/\*                                                                                         |
  | **A-CMS-ENROLL**  | CMS 신청자 관리     | 신청 내역(결제 상태, 사물함 사용 여부 등) 조회.                                      | GET /api/v1/cms/enrolls/\*                                                                                                        |
  |                   |                     | 오프라인 등록자를 위한 '임시 등록' 기능.                                             | POST /api/v1/cms/enrollments/temporary                                                                                            |
  | **A-CMS-CANCEL**  | CMS 취소/환불 관리  | 사용자 취소 요청 검토, 승인(PG환불연동)/반려.                                        | GET, POST /api/v1/cms/enrolls/cancel-requests, .../{enrollId}/approve-cancel                                                      |
  | **A-CMS-PAYMENT** | CMS 결제 관리       | 전체 결제/환불 내역 조회, 검색, 예외 건 수동 처리 지원.                              | GET, POST /api/v1/cms/payments/\*                                                                                                 |

  > 모바일: P-01 카드는 Masonry → 1 열, P-02는 풀스크린. 기타 모달 풀스크린.

  ***

  ## 4. API 상세

  ### 4-1. 공통

  | 요소        | 값                                                                                        |
  | ----------- | ----------------------------------------------------------------------------------------- |
  | 인증        | OAuth2 Bearer/JWT (로그인 필요 API)                                                       |
  | 응답 규격   | `status` + `data` + `message`                                                             |
  | 주 오류코드 | 4001 (좌석없음), 4002 (결제시간만료), 4008 (결제페이지접근불가), 409 (중복), 403 (미성년) |

  ### 4-2. 엔드포인트 (주요 흐름 관련)

  | Method | URL                                              | Req Body/QS                                       | Res Body                                                                       | 비고                                                                                                                                                                               |
  | ------ | ------------------------------------------------ | ------------------------------------------------- | ------------------------------------------------------------------------------ | ---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
  | GET    | /api/v1/lessons                                  | status, year, month, startDate, endDate, pageable | Page<LessonDTO>                                                                | (사용자용) 수업 목록 조회.                                                                                                                                                         |
  | POST   | **/api/v1/enrolls**                              | { lessonId: Long }                                | **EnrollInitiationResponseDto** ({enrollId, paymentPageUrl, paymentExpiresAt}) | **핵심 변경.** (사용자용) 좌석 Lock 시도. 성공 시 `enroll` 생성 (UNPAID, 5분 expire_dt), KISPG 결제 페이지 이동 정보 반환. **결제 페이지 접근 슬롯 부족 시 409 LEC001 에러 반환.** |
  | GET    | **/api/v1/payment/details/{enrollId}**           | -                                                 | **PaymentPageDetailsDto**                                                      | **신규 (KISPG 연동).** (사용자용) 결제 페이지에서 호출. 결제 필요 CMS 내부 정보 반환.                                                                                              |
  | GET    | **/api/v1/payment/kispg-init-params/{enrollId}** | -                                                 | **KISPGInitParamsDto** (가칭)                                                  | **신규 (KISPG 연동).** (사용자용) KISPG 결제창 호출에 필요한 파라미터 반환.                                                                                                        |
  | POST   | **/api/v1/payment/confirm/{enrollId}**           | `{ pgToken: String, wantsLocker: Boolean }`       | 200 OK (상태: PAYMENT_SUCCESSFUL/PROCESSING) / Error                           | **신규 (KISPG 연동).** (사용자용) KISPG `returnUrl`에서 호출. UX 및 `wantsLocker` 최종 반영. 주 결제처리는 Webhook.                                                                |
  | POST   | **/api/v1/kispg/payment-notification**           | (KISPG Webhook 명세 따름)                         | "OK" / Error                                                                   | **신규 (KISPG Webhook).** KISPG가 결제 결과 비동기 통지. 주 결제 처리 로직.                                                                                                        |
  | GET    | /mypage/enroll                                   | status?                                           | List<EnrollDTO>                                                                | (사용자용 Mypage) 내 신청 내역 조회.                                                                                                                                               |
  | PATCH  | /mypage/enroll/{id}/cancel                       | reason                                            | 200                                                                            | (사용자용 Mypage) 신청 취소 요청.                                                                                                                                                  |
  | POST   | /mypage/renewal                                  | lessonId, carryLocker                             | EnrollInitiationResponseDto                                                    | (사용자용 Mypage) 재수강 신청. 성공 시 KISPG 결제 페이지 이동 정보 반환.                                                                                                           |

  ***

  ## 5. DB 구조 (요약)

  (참고: `lesson` 테이블에 강사명(`instructor_name`), 수업 시간(`lesson_time`) 등 CMS 관리용 필드 추가 고려. `locker_inventory` 테이블은 전체 재고 관리에 사용. `male_locker_cap`, `female_locker_cap`은 `lesson` 테이블에서 제거됨.)

  | 테이블               | 필드(추가/변경)                                                             | 비고                                                                             |
  | -------------------- | --------------------------------------------------------------------------- | -------------------------------------------------------------------------------- |
  | **locker_inventory** | `gender` (PK), `total_quantity`, `used_quantity`                            | 성별 전체 라커 재고 (swim-user.md DDL 참조)                                      |
  | **enroll**           | `uses_locker` BOOLEAN, `status` ENUM('APPLIED','CANCELED'),`cancel_reason`  | `locker_id` 제거, 사물함 사용 여부 필드 추가                                     |
  |                      | `pay_status` VARCHAR(20)                                                    | `UNPAID`, `PAID`, `PARTIALLY_REFUNDED`, `CANCELED_UNPAID`, **`PAYMENT_TIMEOUT`** |
  |                      | `expire_dt` DATETIME                                                        | **결제 페이지 접근 및 시도 만료 시간 (신청 시점 + 5분)**                         |
  |                      | `remain_days` INT                                                           | **취소 시 계산된 잔여일수 (감사용)** (환불 정책 변경으로 용도 재검토)            |
  | **lesson**           | `instructor_name` VARCHAR(50), `lesson_time` VARCHAR(100)                   | (추가 고려) 강습 스케줄 및 강사 정보 필드                                        |
  | **user**             | (기존 필드 외) `gender` (ENUM or VARCHAR)                                   | 사용자 성별 (라커 배정을 위해 필요)                                              |
  | **payment**          | `tid` VARCHAR(30), `paid_amt` INT, `refunded_amt` INT, `refund_dt` DATETIME | **KISPG 연동 필드 (거래ID, 초기결제액, 누적환불액, 최종환불일)**                 |

  ***

  ## 6. **비즈니스 룰**

  | 구분                          | 규칙                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                |
  | ----------------------------- | ----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
  | **선착순 (결제 페이지 접근)** | `/enroll` 시 강습의 **결제 페이지 접근 슬롯 (정원 - PAID 건수 - 만료 전 UNPAID 건수) > 0** 이면 `EnrollInitiationResponseDto` (KISPG 결제 페이지 URL 포함) 반환. 아니면 `409 LEC001 PAYMENT_PAGE_SLOT_UNAVAILABLE` 오류.                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                            |
  | **선착순 (최종 확정)**        | KISPG Webhook (`/api/v1/kispg/payment-notification`) 처리 시점에 최종 PG 결제 성공 및 좌석/라커 재확보 성공 시 `enroll.pay_status = 'PAID'`로 확정.                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                 |
  | **5분 결제 타임아웃**         | KISPG 결제 페이지 진입 후 `enroll.expire_dt` 도달 시 프론트 자동 이전 페이지 이동 + 토스트. 서버 배치가 `pay_status`를 `PAYMENT_TIMEOUT`으로 변경.                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                  |
  | **성별 라커**                 | KISPG 결제 페이지에서 `user.gender` 기준 사용 가능한 글로벌 라커 확인 후 최종 선택. KISPG Webhook을 통해 결제 성공 시, `Enroll.usesLocker`가 true이면 `locker_inventory` 업데이트 및 `Enroll.lockerAllocated=true` 설정. **배정된 사물함은 강습 종료 시 시스템에 의해 자동 회수 (재수강 이전 예외).** 관리자는 CMS에서 성별 총 사물함 수(`locker_inventory`)를 설정.                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                |
  | **미성년 차단**               | `adult_verified=0` → 403                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                            |
  | **재등록 우선권**             | **재수강(기존 회원) 신청 기간: 매월 20일 00:00 ~ 25일 23:59.** 이 기간에 `/mypage/renewal` API를 통해 다음 달 강습을 신청. **신규 회원 신청 기간: 매월 26일 00:00 ~ 말일 23:59.** 현재 달의 강습은 해당 월 말일까지 신규 신청 가능. 관리자는 CMS에서 다음 달 강습을 미리 생성/복제하여 준비.                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                        |
  | **취소 가능 및 환불 정책**    | **1. 수강 시작일 전 사용자 자가 취소:**<br/> - 사용자는 마이페이지를 통해 수강 시작일 전인 강습에 대해 직접 취소를 신청할 수 있습니다.<br/> - 이 경우, 별도의 위약금 없이 결제된 강습료 전액이 환불됩니다. (사물함 이용료는 환불되지 않음, PG사 정책에 따른 최소한의 취소 수수료가 발생할 수 있습니다.)<br/> - 취소 즉시 KISPG를 통해 자동 환불 절차가 진행됩니다 (또는 환불 요청 상태로 변경 후 배치 처리).<br/><br/>**2. 수강 시작일 후 (수강 중) 사용자 취소 요청:**<br/> - 수강 시작일(해당일 00시 00분 01초부터) 이후에는 사용자가 마이페이지에서 취소 '요청'만 가능하며, 최종 취소 및 환불 처리는 관리자의 승인이 필요합니다.<br/> - **사용일수 기준**: 취소 요청일까지 사용한 것으로 간주되며, 하루 중 일부만 수강했더라도 해당일은 사용한 날로 계산됩니다.<br/> - **일일 정산액**: 수강료는 1일 3,500원으로 계산됩니다. 사물함 이용료는 환불되지 않습니다.<br/> - **위약금**: 없습니다.<br/> - **환불액 계산**: `(결제된 강습료) - (일일 강습료 3,500원 * 사용일수)`. (사물함 이용료는 환불되지 않습니다.)<br/> - 계산된 환불액이 0보다 작을 경우 환불액은 0원입니다.<br/> - 관리자 승인 후 KISPG를 통해 해당 환불액만큼 부분 취소 처리됩니다.<br/> - 사용자는 마이페이지에서 취소 요청 상태 및 최종 환불 내역을 확인할 수 있습니다.<br/><br/>레슨 시작 전 사용자 즉시 취소. `PAID` 건은 **관리자 검토 및 CMS를 통한 승인 시 KISPG 연동 환불 처리.** <br/>**강습 시작 후 환불 정책:**<br/>1. **강습료 사용분 차감**: 1일 3,500원 기준 사용일수만큼 차감.<br/>2. **사물함 이용료**: 환불되지 않음.<br/>3. **위약금**: 없음.<br/>4. 최종 환불액은 CMS에서 자동 계산되어 처리. |
  | **관리자 CMS 기능**           | **ADMIN** 또는 **SYSTEM_ADMIN**은 CMS를 통해 강습(스케줄, 정원, 가격, 상태 등) 관리, 성별 사물함 총 수량 관리, 전체 신청/결제 현황 조회 (오프라인 결제자를 위한 '임시 등록' 기능 포함), 취소 요청 처리(환불 연동), 통계 확인, 시스템 설정(SYSTEM_ADMIN) 등의 작업을 수행. 각 기능은 역할 기반 접근 제어(RBAC)를 따름. (상세 내용은 `Docs/cms/swim-admin.md` 참조)                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                   |

  ***

  ## 7. 배치/이벤트

  | 이름                                       | 주기     | 설명                                                                                                                                          |
  | ------------------------------------------ | -------- | --------------------------------------------------------------------------------------------------------------------------------------------- |
  | renewal-notifier                           | daily    | 재등록 창 오픈 대상자 LMS 발송                                                                                                                |
  | **payment-timeout-sweep**                  | 1-5 min  | `UNPAID` & `expire_dt` 초과 레코드 (KISPG 결제 미완료) → `PAYMENT_TIMEOUT`, (결제 페이지에서 선택했던) 라커 자동 회수 처리                    |
  | **cancel-retry**                           | 5 min    | **PG 취소 실패 건 (`pending` 상태) 자동 재시도 (KISPG)**                                                                                      |
  | **pg-reconcile**                           | daily    | **KISPG 거래내역과 DB 정합성 대사**                                                                                                           |
  | **lesson-completion-locker-release-sweep** | 하루 1회 | 종료된 강습에 배정된 사물함 자동 회수 처리. **재수강으로 인해 `lockerAllocated=false` 처리된 이전 강습 건은 이 배치에서 중복 처리되지 않음.** |

  ***

  ## 8. 예외 처리 플로우

  1.  **동시 신청 (`/enroll`)** → 좌석/결제페이지 접근 Lock 실패 시 `409 LEC001 PAYMENT_PAGE_SLOT_UNAVAILABLE`.
  2.  **성별 불일치 (라커 선택 시)** → 결제 페이지에서 해당 성별 라커 선택 불가 안내.
  3.  **중복 신청 (유효 건)** → 409 `DUPLICATE_ENROLL` (이미 `PAID` 또는 만료 전 `UNPAID` 건 존재 시).
  4.  **결제 페이지 타임아웃 후 `/payment/confirm` 시도** → 4002 `PAYMENT_EXPIRED`.

  ***

  ## 9. 테스트 케이스 (발췌)

  | ID    | 시나리오                                                                                                       | 기대                                                                                                                                       |
  | ----- | -------------------------------------------------------------------------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------ |
  | TC-01 | 남·여 라커 모두 0 남은 상태에서 KISPG 결제 페이지 진입, 라커 선택 시도                                         | 라커 선택 불가 안내. 라커 없이 KISPG 결제는 가능.                                                                                          |
  | TC-02 | 동일 user·lesson 중복 `/enroll` (이전 건이 `PAYMENT_TIMEOUT` 상태, KISPG 결제 미완료)                          | 신규 `enroll` 생성 및 KISPG 결제 페이지 접근 가능.                                                                                         |
  | TC-03 | 미성년자 `/enroll`                                                                                             | 403 `JUNIOR_BLOCKED`                                                                                                                       |
  | TC-04 | 재등록 창 외 `/renewal` (KISPG 결제 연동)                                                                      | 403                                                                                                                                        |
  | TC-05 | KISPG 결제 페이지에서 5분 초과 후 PG 결제 성공 (PG는 성공했으나, `/confirm` 호출 전에 만료)                    | `/payment/confirm` 호출 시 4002 `PAYMENT_EXPIRED`. KISPG는 별도 망취소 필요할 수 있음(운영 정책).                                          |
  | TC-06 | 정원 1명 남음. User A `/enroll` 성공 -> KISPG 결제 페이지. User B `/enroll` 시도                               | User B는 `409 LEC001 PAYMENT_PAGE_SLOT_UNAVAILABLE` (또는 4001 `SEAT_FULL` - 상세 구현에 따라).                                            |
  | TC-07 | 기존 회원 A가 재수강 기간(18~22일)에 이전 강습에서 사용하던 사물함을 유지하며 재수강 신청 및 결제 완료.        | API: 새 Enroll에 `lockerAllocated=true`, 이전 Enroll은 `lockerAllocated=false`. `locker_inventory.used_quantity` 변동 없음. FE: 결제 완료. |
  | TC-08 | 기존 회원 B가 재수강 기간에 이전 강습에서 사물함을 사용하지 않았으나, 재수강 시 사물함 신규 신청 및 결제 완료. | API: 새 Enroll에 `lockerAllocated=true`. `locker_inventory.used_quantity` 1 증가. FE: 결제 완료.                                           |
  | TC-09 | 신규 회원 C가 신규 회원 신청 기간(25일 이후)에 강습 신청 및 사물함 포함 결제 완료.                             | API: 새 Enroll에 `lockerAllocated=true`. `locker_inventory.used_quantity` 1 증가. FE: 결제 완료.                                           |

  ***

  ## 10. 프론트엔드 구현 Tips (Next.js/React 기반)

  - **LessonCard (`components/LessonCard.jsx` 또는 유사)**: Hover Tooltip "남 {M} · 여 {F} 잔여", "신청하기" 버튼 (클릭 시 Next.js `useRouter().push('/payment/process?lesson_id=...')` 또는 API 호출 후 `router.push(paymentPageUrl)`).
  - **PaymentPage (`pages/payment/process.jsx` 또는 유사, KISPG 연동)**:
    - **API 호출**: `GET /api/v1/payment/details/{enrollId}` (CMS 정보), `GET /api/v1/payment/kispg-init-params/{enrollId}` (KISPG 파라미터) 호출.
    - **5분 타이머**: React `useState` 및 `useEffect`로 명확히 표시. 만료 시 `alert("5분의 시간이 경과되어 결제 이전 창으로 이동합니다.")` 후 Next.js `useRouter().back()` 또는 특정 경로로 `router.push()`.
    - **LockerSelector (`components/LockerSelector.jsx` 또는 유사)**: 잔여 0 라커 disabled + 성별 탭 필터. React `useState`로 선택 시 총액 즉시 업데이트.
    - **KISPG 연동**: 프론트엔드에서 KISPG Init Params로 KISPG 결제창 직접 호출 또는 SDK 연동. Return URL 처리 시 `/api/v1/payment/confirm` 호출.
  - **EnrollCard (Mypage, `components/MypageEnrollCard.jsx` 또는 유사, KISPG 연동)**: `PAYMENT_TIMEOUT` 회색 Badge, `PAID` green, `CANCELED` gray. `UNPAID` (만료 전, KISPG 결제 가능 시) Yellow + `<Link href={enroll.paymentPageUrl}><a>결제 계속</a></Link>` 버튼 (남은 시간 표시하며 KISPG 결제 페이지로 링크).
  - **CancelDialog (`components/CancelDialog.jsx` 또는 유사, KISPG 연동)**: 사유 입력 후 PATCH API 호출 → 성공/실패 시 `react-toastify` 등으로 Toast 메시지 "취소 완료" 또는 "취소 요청 완료".

  ***

  ## 11. 배포 체크리스트

  1. 성별 라커 정원(`male/female_locker_cap`) 데이터 초기 입력
  2. `/public/locker/availability` 캐시 30 초 설정 (트래픽 완화)
  3. Renewal-notifier cron 등록 & LMS 발신 키 테스트 (KISPG와 직접 관련은 없으나 전체 플로우의 일부)
  4. 관리자 Dashboard → 잔여 라커/좌석 Widget 연결 확인 (PAID + 만료 전 UNPAID 포함, KISPG 데이터 기준)
  5. **`payment-timeout-sweep` 배치 등록 및 정상 동작 확인 (KISPG 결제 만료 건 처리)**
  6. **KISPG 결제 페이지 UI, 타이머, 리디렉션, 토스트 메시지 최종 검증**
  7. CMS 관리자 계정 생성 및 권한 설정 추가 고려

  ***

  ## 관련 문서

  - **[신청 시스템 개발 가이드](./enrollment-system.md)** - 동시성 제어, 성능 최적화, 실시간 업데이트
  - **[사용자 가이드](./swim-user.md)** - 일반 사용자 기능
  - **[관리자 가이드](./swim-admin.md)** - 관리자 기능
