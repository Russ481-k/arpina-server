- 🏊‍♀️ 수영장 **신청 & 신청내역 확인** — 사용자·관리자-측 통합 개발문서
  _(Frontend SPA + REST API 기준 · **결제 로직 제외판**)_
  ***
  ## 0. 문서 목표
  | 항목      | 내용                                                                                                 |
  | --------- | ---------------------------------------------------------------------------------------------------- |
  | 범위      | **일반 회원**: 강습·사물함 신청·취소·재등록**관리자**: 프로그램·사물함·신청·취소·통계 관리           |
  | 달성 지표 | ① 선착순(신청 저장 시점) ② 신청 후 0 오류 좌석·라커 관리 ③ UX 이탈률 < 2 % ④ 관리자 한 화면 KPI 확인 |
  ***
  ## 1. 용어·역할
  | 코드              | 설명                                    |
  | ----------------- | --------------------------------------- |
  | **USER**          | 일반 회원(성인)                         |
  | **JUNIOR_USER**   | 미성년자(온라인 신청 차단)              |
  | **ENROLL**        | 강습 신청 레코드 (`APPLIED`/`CANCELED`) |
  | **LOCKER**        | 사물함 레코드(`zone`,`gender`)          |
  | **RENEWAL**       | 기존 수강생 재등록 프로세스             |
  | **PROGRAM_ADMIN** | 강습·사물함 담당 운영자                 |
  | **FINANCE_ADMIN** | 결제·환불 담당 운영자                   |
  | **CS_AGENT**      | 신청 현황·취소 검토 담당                |
  ***
  ## 2. 주요 시나리오(Sequence)
  ```mermaid
  sequenceDiagram
      participant U as 사용자
      participant FE as Frontend
      participant API as REST API
      Note over FE,API: 🔒 Tx / 잔여 Lock
      U->>FE: 강습 카드 ‘신청하기’
      FE->>API: POST /enroll (lessonId, lockerId?)
      API-->>FE: {enrollId}
      FE-->>U: 신청 완료 → [마이페이지로 이동] 안내

      U->>FE: (마이페이지) 취소 버튼
      FE->>API: PATCH /mypage/swim/enroll/{id}/cancel
      API-->>FE: 200 OK → 상태 CANCELED

  ```
  ***
  ## 3. **화면 정의**
  | ID        | 화면                | 주요 UI 요소                                  | 전송 API                      |
  | --------- | ------------------- | --------------------------------------------- | ----------------------------- |
  | **P-01**  | 강습 목록           | 필터(월·레벨) · 강습 카드(`신청/마감`)        | GET /public/lesson            |
  | **P-02**  | 신청 폼             | 강습·가격 요약 · 사물함 드롭다운(성별별 잔여) | POST /public/enroll           |
  | **MP-01** | 마이페이지-신청내역 | 신청 카드(Status Badge·취소 버튼)             | GET /mypage/swim/enroll       |
  | **MP-02** | 재등록 모달         | 기존 강습·라커 carry 토글                     | GET·POST /mypage/swim/renewal |
  | **A-01**  | 관리자 Dashboard    | 잔여 좌석·라커, 오늘 신청 수, 미처리 취소 KPI | –                             |
  | **A-02**  | 프로그램 관리       | 강습 CRUD, 일정 복제                          | /admin/lesson/\*              |
  | **A-03**  | 사물함 관리         | 라커존·번호·성별 테이블                       | /admin/locker/\*              |
  | **A-04**  | 신청 현황           | 실시간 신청 리스트(APPLIED/CANCELED)          | /admin/enroll                 |
  | **A-05**  | 취소 검토           | 개강 후 취소 승인·반려                        | /admin/cancel                 |
  > 모바일: P-01 카드는 Masonry → 1 열, 기타 모달 풀스크린.
  ***
  ## 4. API 상세
  ### 4-1. 공통
  | 요소        | 값                                                         |
  | ----------- | ---------------------------------------------------------- |
  | 인증        | OAuth2 Bearer/JWT (로그인 필요 API)                        |
  | 응답 규격   | `status` + `data` + `message`                              |
  | 주 오류코드 | 409 (잔여 없음·중복), 403 (미성년 차단), 404 (리소스 없음) |
  ### 4-2. 엔드포인트 (신청 페이지 & MyPage)
  | Method | URL                             | Req Body/QS           | Res Body         | 비고                    |
  | ------ | ------------------------------- | --------------------- | ---------------- | ----------------------- |
  | GET    | /public/lesson                  | month,status?         | List<LessonDTO>  | 잔여좌석·성별 라커 포함 |
  | GET    | /public/locker/availability     | lessonId              | LockerRemainDTO  | 성별별 잔여             |
  | POST   | /enroll                         | lessonId, lockerId?   | EnrollCreatedDTO | 좌석·라커 Lock          |
  | GET    | /mypage/swim/enroll             | status?               | List<EnrollDTO>  | APPLIED,CANCELED        |
  | PATCH  | /mypage/swim/enroll/{id}/cancel | reason                | 200              | 시작 前 즉시 취소       |
  | GET    | /mypage/swim/renewal            | –                     | List<RenewalDTO> | 재등록 창 기간만        |
  | POST   | /mypage/swim/renewal            | lessonId, carryLocker | 200              | 신규 enroll 생성        |
  ***
  ## 5. DB 구조 (요약)
  | 테이블     | 필드(추가)                                                 | 비고                |
  | ---------- | ---------------------------------------------------------- | ------------------- |
  | **locker** | gender ENUM('M','F')                                       | 남·여 라커 구분     |
  | **enroll** | locker_id FKstatus ENUM('APPLIED','CANCELED')cancel_reason | 결제 컬럼 제거      |
  | **lesson** | male_locker_cap, female_locker_cap                         | 성별 라커 정원 저장 |
  잔여 라커 계산 예
  ```sql
  SELECT female_locker_cap -
         (SELECT COUNT(*) FROM enroll e
           JOIN locker l ON l.locker_id=e.locker_id
           WHERE lesson_id=? AND l.gender='F' AND status='APPLIED') AS remainF

  ```
  ***
  ## 6. **비즈니스 룰**
  | 구분              | 규칙                                                            |
  | ----------------- | --------------------------------------------------------------- |
  | **선착순**        | `/enroll` 시 좌석·라커 `SELECT … FOR UPDATE` → INSERT 성공 기준 |
  | **성별 라커**     | `user.gender` == `locker.gender` & 잔여 > 0                     |
  | **미성년 차단**   | `adult_verified=0` → 403                                        |
  | **재등록 우선권** | 강습 종료 D-7 ~ D-4 동안만 `/mypage/swim/renewal` 오픈          |
  | **취소 가능**     | 레슨 시작 전 사용자 즉시 취소, 시작 후 관리자 검토              |
  ***
  ## 7. 배치/이벤트
  | 이름             | 주기  | 설명                           |
  | ---------------- | ----- | ------------------------------ |
  | renewal-notifier | daily | 재등록 창 오픈 대상자 LMS 발송 |
  ***
  ## 8. 예외 처리 플로우
  1. **동시 신청** → 좌석·라커 Lock 실패 시 409 `SEAT_FULL`
  2. **성별 불일치** → 409 `LOCKER_GENDER_MISMATCH`
  3. **중복 신청** → 409 `DUPLICATE_ENROLL`
  ***
  ## 9. 테스트 케이스 (발췌)
  | ID    | 시나리오                         | 기대                   |
  | ----- | -------------------------------- | ---------------------- |
  | TC-01 | 남·여 라커 모두 0 남은 상태 신청 | 409 `LOCKER_NONE_LEFT` |
  | TC-02 | 동일 user·lesson 중복 신청       | 409 `DUPLICATE_ENROLL` |
  | TC-03 | 미성년자 `/enroll`               | 403 `JUNIOR_BLOCKED`   |
  | TC-04 | 재등록 창 외 `/renewal`          | 403                    |
  ***
  ## 10. 프론트엔드 구현 Tips
  - **LessonCard**: Hover Tooltip “남 {M} · 여 {F}”
  - **LockerSelector**: 잔여 0 라커 disabled + 성별 탭 필터
  - **EnrollCard**: `green` APPLIED / `gray` CANCELED Badge
  - **CancelDialog**: 사유 입력 후 PATCH → Toast “취소 완료”
  ***
  ## 11. 배포 체크리스트
  1. 성별 라커 정원(`male/female_locker_cap`) 데이터 초기 입력
  2. `/public/locker/availability` 캐시 30 초 설정 (트래픽 완화)
  3. Renewal-notifier cron 등록 & LMS 발신 키 테스트
  4. 관리자 Dashboard → 잔여 라커 Widget 연결 확인
  ***
