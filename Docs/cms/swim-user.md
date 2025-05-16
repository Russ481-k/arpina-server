- 🏊‍♀️ 수영장 **신청 & 신청내역 확인** — 사용자-측 개발문서
  _(Frontend SPA + REST API + PG 연동 기준)_
  ***
  ## 0. 문서 목표
  | 항목      | 내용                                                                                          |
  | --------- | --------------------------------------------------------------------------------------------- |
  | 범위      | **일반 회원**이 강습을 신청하고, 사물함을 선택‧결제‧취소‧재등록까지 처리하는 모든 온라인 흐름 |
  | 달성 지표 | ① 선착순(결제 완료 기준) ② 1 시간 내 결제 ③ 잔여 좌석·라커 **0 오류** ④ UX 이탈률 < 2 %       |
  ***
  ## 1. 용어·역할 (User Side)
  | 코드            | 설명                        |
  | --------------- | --------------------------- |
  | **USER**        | 일반 회원 (성인)            |
  | **JUNIOR_USER** | 미성년자 (온라인 신청 불가) |
  | **PG**          | 결제대행사 (아임포트 REST)  |
  | **ENROLL**      | 강습 신청 레코드            |
  | **LOCKER**      | 사물함 레코드               |
  | **RENEWAL**     | 기존 수강생 재등록 프로세스 |
  ***
  ## 2. 주요 시나리오(Sequence)
  ```mermaid
  sequenceDiagram
      participant U as 사용자
      participant FE as Frontend
      participant API as REST API
      participant PG as PG
      Note over FE,API: 🔒 Tx / 잔여 Lock
      U->>FE: 강습 카드 선택
      FE->>API: POST /enroll (lesson_id, locker_id?)
      API-->>FE: enroll_id + expire_dt(60m)
      FE->>U: 신청정보 확인 페이지
      U->>FE: [마이페이지에서 결제] 클릭
      FE->>API: GET /my/enroll/{id}
      FE->>PG: 결제창 호출 (amount)
      PG-->>FE: SUCCESS (pg_tid)
      FE->>API: POST /pay (enroll_id, pg_tid)
      API-->>PG: 검증
      API-->>FE: 200 OK → 상태 PAID
      FE->>U: 결제 완료 화면

  ```
  ***
  ## 3. **화면 정의**
  | ID       | 화면                | 주요 UI 요소                                                                     | 전송 API               |
  | -------- | ------------------- | -------------------------------------------------------------------------------- | ---------------------- |
  | **P-01** | 강습 목록           | ① 필터 모달(상태, 월, 시간대)② 강습 카드 Grid - 버튼색: `신청(Y)/잔여없음(gray)` | GET /public/lesson     |
  | **P-02** | 신청정보 확인       | 신청 요약 테이블✔ 사물함 선택 체크박스✔ “마이페이지에서 결제” CTA                | POST /public/enroll    |
  | **P-03** | 마이페이지-신청내역 | ① 리스트(상태 Badge)② 1h 카운트다운(UNPAID만)③ 결제/취소/환불 버튼               | GET /public/my/enroll  |
  | **P-04** | 결제처리            | PG 드롭인 UI (카드만)성공 이후 상태변경                                          | POST /public/my/pay    |
  | **P-05** | 재등록 모달         | 이전 수강 강습 제안 + 사물함 유지 여부                                           | GET /public/my/renewal |
  > 모바일: P-01, P-03는 Masonry Grid → 1 열, 나머지 모달 UI 유지.
  ***
  ## 4. API 상세
  ### 4-1. 공통
  | 요소      | 값                                               |
  | --------- | ------------------------------------------------ |
  | 인증      | OAuth2 Bearer/JWT                                |
  | 응답 규격 | `code`(int) + `message` + `data`                 |
  | 오류코드  | 4001 잔여없음, 4002 만료, 4003 미성년, 500X 서버 |
  ### 4-2. 엔드포인트
  | Method | URL                           | Req Body                     | Res Body               | 비고                              |
  | ------ | ----------------------------- | ---------------------------- | ---------------------- | --------------------------------- |
  | GET    | /public/lesson                | month, status?               | List<LessonDTO>        | 잔여좌석, 가격 포함               |
  | POST   | /public/enroll                | lesson_id, locker_id?        | {enroll_id, expire_dt} | Tx; 잔여-좌석 Lock                |
  | GET    | /public/my/enroll             | status?                      | List<EnrollDTO>        | UNPAID, PAID, CANCELED            |
  | POST   | /public/my/pay                | enroll_id, pg_token          | 200/400                | Webhook 병행                      |
  | PATCH  | /public/my/enroll/{id}/cancel | -                            | 200                    | (PAID → 환불정책)                 |
  | GET    | /public/my/renewal            | -                            | List<RenewalDTO>       | 기간 내 노출                      |
  | POST   | /public/my/renewal            | lesson_id, carry_locker(Y/N) | 200                    | 기존 locker 유지시 동일 locker_id |
  ***
  ## 5. DB 구조 (사용자 관점 필드 추가)
  | 테이블     | 필드(추가)                   | 설명          |
  | ---------- | ---------------------------- | ------------- |
  | **enroll** | `expire_dt` DATETIME         | 신청 타임아웃 |
  |            | `renewal_flag` TINYINT       | 1 이면 재등록 |
  |            | `cancel_reason` VARCHAR(100) | 사용자 입력   |
  | **locker** | `gender` ENUM('M','F')       | 성별 라커존   |
  | **user**   | `adult_verified` TINYINT     | 미성년자 차단 |
  _강습 잔여 좌석은_
  `lesson.capacity - (SELECT COUNT(*) FROM enroll WHERE lesson_id=? AND pay_status='PAID')`
  ***
  ## 6. **비즈니스 룰**
  | 구분                | 세부 규칙                                                                                                        |
  | ------------------- | ---------------------------------------------------------------------------------------------------------------- |
  | **선착순**          | *PG 결제 SUCCESS 시점*에 `pay_status=PAID` 로 바뀌어야 정원 확정                                                 |
  | **1 시간 타임아웃** | `expire_dt < NOW()` + `pay_status='UNPAID'` ⇒ 배치가 `CANCELED_UNPAID`                                           |
  | **미성년자 제한**   | `user.adult_verified=0` 이면 `/public/enroll` → 4003                                                             |
  | **사물함 선택**     | 최초 POST /enroll 시에만 `locker_id` 전송 허용                                                                   |
  | **사물함 재고**     | `locker.is_active=1` AND `locker_id NOT IN (SELECT locker_id FROM enroll WHERE pay_status IN ('UNPAID','PAID'))` |
  | **재등록 우선권**   | 재등록 기간 D-7~D-5, 신규 오픈 D-4; 기간 외 API 403                                                              |
  | **취소/환불**       | 사용 개시 전 취소 → PG 전액 취소, 이후 관리자 검토(부분 환불)                                                    |
  ***
  ## 7. 배치/이벤트 (User Side 관련)
  | 이름                 | 주기     | 설명                                                                 |
  | -------------------- | -------- | -------------------------------------------------------------------- |
  | **unpaid-timeout**   | 5 min    | UNPAID & expire_dt 초과 레코드 → `CANCELED_UNPAID`, locker roll-back |
  | **pg-webhook**       | 실시간   | PG callback 검증, 위·변조 체크 ◀→ enroll·payment 갱신                |
  | **renewal-notifier** | 하루 1회 | 재등록 대상자 알림(LMS)                                              |
  ***
  ## 8. 예외 처리 플로우
  1. **동시 클릭** → DB `SELECT … FOR UPDATE` + UNIQUE 인덱스(`lesson_id`,`user_id`)
  2. **PG 결제 실패** → `payment.status=FAIL`, `enroll` 그대로 UNPAID(시간 내 재시도 가능)
  3. **Webhook 지연**(PG > 5 초) → 프론트 Poll `/my/enroll/{id}` 5 회(2 s 간격) 후 로딩 모달 종료
  ***
  ## 9. 테스트 케이스(요약)
  | ID    | 시나리오                        | 예상 결과                      |
  | ----- | ------------------------------- | ------------------------------ |
  | TC-01 | 정원 마감 직전 2 명이 동시 신청 | 1 명 PAID, 1 명 4001(잔여없음) |
  | TC-02 | 신청 후 59 분 결제              | 상태 PAID                      |
  | TC-03 | 신청 후 61 분 결제 시도         | /public/my/pay → 4002(만료)    |
  | TC-04 | 미성년자 로그인 → 신청          | 4003                           |
  | TC-05 | 재등록 기간 외 /my/renewal      | 403                            |
  ***
  ## 10. 프론트엔드 구현 Tips
  - **상태별 버튼색**:
    - `UNPAID` → Yellow “결제하기”
    - `PAID` → Green “결제완료” + 영수증 링크
    - `CANCELED_*` → Red “취소됨”
  - **1 시간 카운트다운**: dayjs + react-countdown-circle; 타임아웃 시 자동 refresh
  - **Accessibility**: 강습 카드 ALT 텍스트 “{요일·시간대} 초급반 잔여 {n}석”
  ***
  ## 11. 배포 체크리스트
  1. PG 상용키 교체 & 도메인 whitelisting
  2. Webhook URL 방화벽 허용
  3. unpaid-timeout 배치 crontab 등록
  4. 잔여좌석 모니터 Grafana Dashboard
  ***
