- 🏊‍♀️ 수영장 **신청 & 신청내역 확인** — 사용자·관리자-측 통합 개발문서
  _(Frontend SPA + REST API 기준 · **결제 로직 변경 반영**)_

  ***

  ## 0. 문서 목표

  | 항목      | 내용                                                                                                                        |
  | --------- | --------------------------------------------------------------------------------------------------------------------------- |
  | 범위      | **일반 회원**: 강습·사물함 신청·취소·재등록 (결제는 전용 페이지) **관리자**: 프로그램·사물함·신청·취소·통계 관리            |
  | 달성 지표 | ① 선착순(결제 완료 기준) ② **5분 내 결제 완료** ③ 신청 후 0 오류 좌석·라커 관리 ④ UX 이탈률 < 2 % ④ 관리자 한 화면 KPI 확인 |

  ***

  ## 1. 용어·역할

  | 코드              | 설명                                                                                           |
  | ----------------- | ---------------------------------------------------------------------------------------------- |
  | **USER**          | 일반 회원(성인)                                                                                |
  | **JUNIOR_USER**   | 미성년자(온라인 신청 차단)                                                                     |
  | **ENROLL**        | 강습 신청 레코드 (`APPLIED`/`CANCELED`, 내부 `payStatus`: `UNPAID`, `PAID`, `PAYMENT_TIMEOUT`) |
  | **LOCKER**        | 사물함 레코드(`zone`,`gender`)                                                                 |
  | **RENEWAL**       | 기존 수강생 재등록 프로세스                                                                    |
  | **PAYMENT_PAGE**  | **결제 전용 페이지 (5분 제한)**                                                                |
  | **PROGRAM_ADMIN** | 강습·사물함 담당 운영자                                                                        |
  | **FINANCE_ADMIN** | 결제·환불 담당 운영자                                                                          |
  | **CS_AGENT**      | 신청 현황·취소 검토 담당                                                                       |

  ***

  ## 2. 주요 시나리오(Sequence)

  ```mermaid
  sequenceDiagram
      participant U as 사용자
      participant FE as Frontend
      participant API as REST API
      Note over FE,API: 🔒 Tx / 잔여 Lock (5분)
      U->>FE: 강습 카드 '신청하기'
      FE->>API: POST /api/v1/swimming/enroll (lessonId)
      alt 정원 및 동시 접근 가능
        API-->>FE: EnrollInitiationResponseDto (enrollId, paymentPageUrl, paymentExpiresAt)
        FE->>U: 결제 페이지로 리디렉션 (5분 타이머 시작)
        U->>FE: (결제 페이지) [결제하기] (PG 연동)
        FE->>API: POST /api/v1/payment/confirm/{enrollId} (pgToken, wantsLocker)
        API-->>FE: {status: PAID}
        FE-->>U: 결제 완료 → [마이페이지로 이동] 안내
      else 정원 초과 또는 접근 불가
        API-->>FE: 오류 (예: 4001 SEAT_FULL, 4008 PAYMENT_PAGE_ACCESS_DENIED)
        FE->>U: 신청 불가 안내
      end
      alt 결제 페이지 5분 타임아웃
        FE->>U: 이전 페이지로 리디렉션 + "시간 초과" 토스트
        Note over API: enroll.pay_status -> PAYMENT_TIMEOUT (배치 처리)
      end

      U->>FE: (마이페이지) 취소 버튼
      FE->>API: PATCH /mypage/swim/enroll/{id}/cancel
      API-->>FE: 200 OK → 상태 CANCELED
  ```

  ***

  ## 3. **화면 정의**

  | ID        | 화면                | 주요 UI 요소                                                                        | 전송 API                                                                        |
  | --------- | ------------------- | ----------------------------------------------------------------------------------- | ------------------------------------------------------------------------------- |
  | **P-01**  | 강습 목록           | 필터(월·레벨) · 강습 카드(`신청/마감`)                                              | GET /public/lesson (또는 /api/v1/swimming/lessons)                              |
  | **P-02**  | **결제 페이지**     | 강습 요약 · **5분 카운트다운** · 사물함 선택 · PG UI                                | GET /api/v1/payment/details/{enrollId}, POST /api/v1/payment/confirm/{enrollId} |
  | **MP-01** | 마이페이지-신청내역 | 신청 카드(Status Badge: PAID, PAYMENT_TIMEOUT, CANCELED·취소 버튼)                  | GET /mypage/swim/enroll                                                         |
  | **MP-02** | 재등록 모달         | 기존 강습·라커 carry 토글                                                           | GET·POST /mypage/swim/renewal (성공 시 결제 페이지로 이동)                      |
  | **A-01**  | 관리자 Dashboard    | 잔여 좌석·라커, 오늘 신청 수 (`PAID` 기준), 미처리 취소 KPI, `PAYMENT_TIMEOUT` 건수 | –                                                                               |
  | **A-02**  | 프로그램 관리       | 강습 CRUD, 일정 복제                                                                | /admin/lesson/\*                                                                |
  | **A-03**  | 사물함 관리         | 라커존·번호·성별 테이블                                                             | /admin/locker/\*                                                                |
  | **A-04**  | 신청 현황           | 실시간 신청 리스트(`PAID`, `UNPAID` (만료 전), `PAYMENT_TIMEOUT`, `CANCELED`)       | /admin/enroll                                                                   |
  | **A-05**  | 취소 검토           | 개강 후 취소 승인·반려                                                              | /admin/cancel                                                                   |

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

  | Method | URL                                    | Req Body/QS                                       | Res Body                                                                       | 비고                                                                                                      |
  | ------ | -------------------------------------- | ------------------------------------------------- | ------------------------------------------------------------------------------ | --------------------------------------------------------------------------------------------------------- |
  | GET    | /api/v1/swimming/lessons               | status, year, month, startDate, endDate, pageable | Page<LessonDTO>                                                                | 수업 목록 조회.                                                                                           |
  | GET    | /public/locker/availability            | lessonId                                          | LockerRemainDTO (e.g. {"maleAvailable":10, "femaleAvailable":5})               | (결제 페이지용) 특정 강습의 성별 사용 가능 라커 수 조회.                                                  |
  | POST   | **/api/v1/swimming/enroll**            | { lessonId: Long }                                | **EnrollInitiationResponseDto** ({enrollId, paymentPageUrl, paymentExpiresAt}) | **핵심 변경.** 좌석 Lock 시도. 성공 시 `enroll` 생성 (UNPAID, 5분 expire_dt), 결제 페이지 이동 정보 반환. |
  | GET    | **/api/v1/payment/details/{enrollId}** | -                                                 | **PaymentPageDetailsDto**                                                      | **신규.** 결제 페이지에서 호출. 결제 필요 정보(금액, 라커, 만료시간) 반환.                                |
  | POST   | **/api/v1/payment/confirm/{enrollId}** | `{ pgToken: String, wantsLocker: Boolean }`       | 200 OK / Error                                                                 | **신규.** 결제 페이지에서 PG 성공 후 호출. 최종 검증 및 `enroll.pay_status=PAID` 처리.                    |
  | GET    | /mypage/swim/enroll                    | status?                                           | List<EnrollDTO> (payStatus에 `PAYMENT_TIMEOUT` 추가)                           | (마이페이지) 내 신청 내역 조회.                                                                           |
  | PATCH  | /mypage/swim/enroll/{id}/cancel        | reason                                            | 200                                                                            | (마이페이지) 신청 취소.                                                                                   |
  | GET    | /mypage/swim/renewal                   | –                                                 | List<RenewalDTO>                                                               | (마이페이지) 재등록 대상 조회.                                                                            |
  | POST   | /mypage/swim/renewal                   | lessonId, carryLocker                             | EnrollInitiationResponseDto (또는 유사, 재등록 후 결제 페이지로 가야 함)       | (마이페이지) 재등록 신청. 성공 시 위 `/enroll`과 유사하게 결제 페이지 이동 정보 반환.                     |

  ***

  ## 5. DB 구조 (요약)

  (참고: `lesson` 테이블의 전체 DDL은 `swim-user.md` 또는 프로젝트 DDL 파일을 기준으로 하며, `registration_end_date` 컬럼을 포함하지 않고, `lesson_year`, `lesson_month` 등의 가상 컬럼을 포함합니다. `lesson` 테이블의 `status` 컬럼은 `OPEN, CLOSED, ONGOING, COMPLETED` 값을 가집니다. `locker_inventory` 테이블은 전체 재고 관리에 사용됩니다.)

  | 테이블               | 필드(추가/변경)                                                            | 비고                                                       |
  | -------------------- | -------------------------------------------------------------------------- | ---------------------------------------------------------- |
  | **locker_inventory** | `gender` (PK), `total_quantity`, `used_quantity`                           | 성별 전체 라커 재고 (swim-user.md DDL 참조)                |
  | **enroll**           | `uses_locker` BOOLEAN, `status` ENUM('APPLIED','CANCELED'),`cancel_reason` | `locker_id` 제거, 사물함 사용 여부 필드 추가               |
  |                      | `pay_status` VARCHAR(20)                                                   | `UNPAID`, `PAID`, `CANCELED_UNPAID`, **`PAYMENT_TIMEOUT`** |
  |                      | `expire_dt` DATETIME                                                       | **결제 페이지 접근 및 시도 만료 시간 (신청 시점 + 5분)**   |
  | **lesson**           | `male_locker_cap`, `female_locker_cap`                                     | 강습별 성별 라커 정원 저장                                 |
  | **user**             | (기존 필드 외) `gender` (ENUM or VARCHAR)                                  | 사용자 성별 (라커 배정을 위해 필요)                        |

  특정 강습의 성별 잔여 라커 계산 예 (lessonId = :targetLessonId, gender = :targetGender):

  ```sql
  SELECT
      (CASE
          WHEN :targetGender = 'MALE' THEN l.male_locker_cap
          WHEN :targetGender = 'FEMALE' THEN l.female_locker_cap
          ELSE 0
      END) -
      (SELECT COUNT(e.enroll_id)
       FROM enroll e
       JOIN user u ON e.user_uuid = u.uuid -- user 테이블의 PK가 uuid라고 가정
       WHERE e.lesson_id = :targetLessonId
         AND e.uses_locker = TRUE
         AND u.gender = :targetGender
         AND (e.pay_status = 'PAID' OR (e.pay_status = 'UNPAID' AND e.expire_dt > NOW())) -- PAID 되었거나, 아직 만료되지 않은 UNPAID (결제 페이지에서 점유 중) 건
      ) AS remaining_lockers
  FROM lesson l
  WHERE l.lesson_id = :targetLessonId;
  ```

  결제 페이지 접근 가능 여부 계산 (정원 기반, lessonId = :targetLessonId):

  ```sql
  SELECT
      l.capacity -
      (SELECT COUNT(*) FROM enroll WHERE lesson_id = :targetLessonId AND pay_status = 'PAID') -
      (SELECT COUNT(*) FROM enroll WHERE lesson_id = :targetLessonId AND pay_status = 'UNPAID' AND expire_dt > NOW())
  FROM lesson l
  WHERE l.lesson_id = :targetLessonId;
  -- 이 값이 0보다 커야 접근 허용
  ```

  ***

  ## 6. **비즈니스 룰**

  | 구분                          | 규칙                                                                                                                                         |
  | ----------------------------- | -------------------------------------------------------------------------------------------------------------------------------------------- |
  | **선착순 (결제 페이지 접근)** | `/enroll` 시 강습의 (PAID 수 + 만료 전 UNPAID 수) < 정원이면 `EnrollInitiationResponseDto` 반환. 아니면 접근 불가.                           |
  | **선착순 (최종 확정)**        | `/payment/confirm` 시점에 최종 PG 결제 성공 및 좌석/라커 재확보 성공 시 `enroll.pay_status = 'PAID'`로 확정.                                 |
  | **5분 결제 타임아웃**         | 결제 페이지 진입 후 `enroll.expire_dt` 도달 시 프론트 자동 이전 페이지 이동 + 토스트. 서버 배치가 `pay_status`를 `PAYMENT_TIMEOUT`으로 변경. |
  | **성별 라커**                 | 결제 페이지에서 `user.gender` 기준 해당 강습의 잔여 라커 확인 후 최종 선택 및 `/payment/confirm` 시점에 확정.                                |
  | **미성년 차단**               | `adult_verified=0` → 403                                                                                                                     |
  | **재등록 우선권**             | 강습 종료 D-7 ~ D-4 동안만 `/mypage/swim/renewal` 오픈 (성공 시 결제 페이지로)                                                               |
  | **취소 가능**                 | 레슨 시작 전 사용자 즉시 취소 (`UNPAID`는 바로 `CANCELED_UNPAID`, `PAID`는 환불 절차). 시작 후 관리자 검토.                                  |

  ***

  ## 7. 배치/이벤트

  | 이름                      | 주기    | 설명                                                                                                   |
  | ------------------------- | ------- | ------------------------------------------------------------------------------------------------------ |
  | renewal-notifier          | daily   | 재등록 창 오픈 대상자 LMS 발송                                                                         |
  | **payment-timeout-sweep** | 1-5 min | `UNPAID` & `expire_dt` 초과 레코드 → `PAYMENT_TIMEOUT`, (결제 페이지에서 선택했던) 라커 자동 회수 처리 |

  ***

  ## 8. 예외 처리 플로우

  1.  **동시 신청 (`/enroll`)** → 좌석/결제페이지 접근 Lock 실패 시 4001 `SEAT_FULL` 또는 4008 `PAYMENT_PAGE_ACCESS_DENIED`.
  2.  **성별 불일치 (라커 선택 시)** → 결제 페이지에서 해당 성별 라커 선택 불가 안내.
  3.  **중복 신청 (유효 건)** → 409 `DUPLICATE_ENROLL` (이미 `PAID` 또는 만료 전 `UNPAID` 건 존재 시).
  4.  **결제 페이지 타임아웃 후 `/payment/confirm` 시도** → 4002 `PAYMENT_EXPIRED`.

  ***

  ## 9. 테스트 케이스 (발췌)

  | ID    | 시나리오                                                                              | 기대                                                                                           |
  | ----- | ------------------------------------------------------------------------------------- | ---------------------------------------------------------------------------------------------- |
  | TC-01 | 남·여 라커 모두 0 남은 상태에서 결제 페이지 진입, 라커 선택 시도                      | 라커 선택 불가 안내. 라커 없이 결제는 가능.                                                    |
  | TC-02 | 동일 user·lesson 중복 `/enroll` (이전 건이 `PAYMENT_TIMEOUT` 상태)                    | 신규 `enroll` 생성 및 결제 페이지 접근 가능.                                                   |
  | TC-03 | 미성년자 `/enroll`                                                                    | 403 `JUNIOR_BLOCKED`                                                                           |
  | TC-04 | 재등록 창 외 `/renewal`                                                               | 403                                                                                            |
  | TC-05 | 결제 페이지에서 5분 초과 후 PG 결제 성공 (PG는 성공했으나, `/confirm` 호출 전에 만료) | `/payment/confirm` 호출 시 4002 `PAYMENT_EXPIRED`. PG는 별도 망취소 필요할 수 있음(운영 정책). |
  | TC-06 | 정원 1명 남음. User A `/enroll` 성공 -> 결제 페이지. User B `/enroll` 시도            | User B는 4008 `PAYMENT_PAGE_ACCESS_DENIED` 또는 4001 `SEAT_FULL`.                              |

  ***

  ## 10. 프론트엔드 구현 Tips

  - **LessonCard**: Hover Tooltip "남 {M} · 여 {F} 잔여", "신청하기" 버튼.
  - **PaymentPage**:
    - **5분 타이머**: 명확히 표시, 만료 시 `alert("5분의 시간이 경과되어 결제 이전 창으로 이동합니다.")` 후 이전 페이지로 `window.history.back()` 또는 특정 경로로 이동.
    - **LockerSelector**: 잔여 0 라커 disabled + 성별 탭 필터. 선택 시 총액 즉시 업데이트.
  - **EnrollCard (Mypage)**: `PAYMENT_TIMEOUT` 회색 Badge, `PAID` green, `CANCELED` gray. `UNPAID` (만료 전) Yellow + "결제 계속" 버튼 (남은 시간 표시하며 결제 페이지로 링크).
  - **CancelDialog**: 사유 입력 후 PATCH → Toast "취소 완료" 또는 "취소 요청 완료".

  ***

  ## 11. 배포 체크리스트

  1. 성별 라커 정원(`male/female_locker_cap`) 데이터 초기 입력
  2. `/public/locker/availability` 캐시 30 초 설정 (트래픽 완화)
  3. Renewal-notifier cron 등록 & LMS 발신 키 테스트
  4. 관리자 Dashboard → 잔여 라커/좌석 Widget 연결 확인 (PAID + 만료 전 UNPAID 포함)
  5. **`payment-timeout-sweep` 배치 등록 및 정상 동작 확인**
  6. **결제 페이지 UI, 타이머, 리디렉션, 토스트 메시지 최종 검증**

  ***
