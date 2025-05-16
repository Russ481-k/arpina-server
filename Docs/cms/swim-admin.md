- 🏊‍♀️ 수영장 **관리자 백오피스** — 관리자-측 개발문서
  _(Spring Boot REST API + React Admin SPA 기준)_

  ***

  ## 0. 문서 목표

  | 항목      | 내용                                                                                                     |
  | --------- | -------------------------------------------------------------------------------------------------------- |
  | 범위      | **운영자**가 강습·사물함·신청·결제(환불)·통계를 실시간으로 관리하는 백오피스                             |
  | 달성 지표 | ① 5 분 내 취소·환불 처리 ② 실시간 잔여 좌석 Sync ③ 월 결제 정산 100 % 일치 ④ 모든 관리 작업 3 click 이내 |

  ***

  ## 1. 역할(Role) 정의

  | ROLE              | 설명             | 접근 화면                  |
  | ----------------- | ---------------- | -------------------------- |
  | **SUPER_ADMIN**   | 전체 설정·권한   | Dashboard + 모든 메뉴      |
  | **PROGRAM_ADMIN** | 강습·사물함 CRUD | Lesson, Locker             |
  | **FINANCE_ADMIN** | 결제·환불 승인   | Payment, Cancel Review     |
  | **CS_AGENT**      | 신청 현황 모니터 | Enroll List, Cancel Review |

  ***

  ## 2. 백오피스 화면 구조

  | ID        | 메뉴          | 주요 UI                                  | 설명                         |
  | --------- | ------------- | ---------------------------------------- | ---------------------------- |
  | **AD-01** | Dashboard     | KPI Card(신청·좌석·매출) 잔여 라커 Donut | 실시간 운영 지표             |
  | **AD-02** | Lesson 관리   | DataGrid + 복제 버튼                     | 강습명·기간·정원·가격 CRUD   |
  | **AD-03** | Locker 관리   | 라커존·번호·성별·활성 Toggle             | 라커 등록/비활성             |
  | **AD-04** | Enroll 현황   | Table(Status Badge) + Search             | APPLIED / CANCELED 리스트    |
  | **AD-05** | Cancel Review | Drawer: 출석·환불 % 슬라이더             | 개강 後 취소 승인/반려       |
  | **AD-06** | Payment 관리  | 결제·환불 탭, TID, 엑셀 DL               | 결제 승인·부분/전액 환불     |
  | **AD-07** | 통계·리포트   | Bar & Line Chart + XLS Export            | 월별 매출·이용자·라커 사용률 |
  | **AD-08** | 시스템 설정   | 권한 매핑, Cron 로그                     | 배치·Webhook 모니터          |

  ***

  ## 3. API 상세

  ### 3-1. 공통

  | 요소     | 값                                                        |
  | -------- | --------------------------------------------------------- |
  | Base URL | `/api/v1/admin`                                           |
  | 인증     | JWT + ROLE 체크                                           |
  | 응답     | `status` int · `data` · `message`                         |
  | 에러코드 | 400 Validation · 403 NoAuth · 404 NotFound · 409 Conflict |

  ### 3-2. 엔드포인트

  | 그룹        | Method | URL                            | Req Body/QS     | Resp                 | 권한          |
  | ----------- | ------ | ------------------------------ | --------------- | -------------------- | ------------- |
  | **Lesson**  | GET    | `/lesson`                      | page,size       | List<LessonDto>      | PROGRAM_ADMIN |
  |             | POST   | `/lesson`                      | LessonDto       | Created              | PROGRAM_ADMIN |
  |             | PUT    | `/lesson/{id}`                 | LessonDto       | Updated              | 〃            |
  |             | POST   | `/lesson/{id}/clone`           | `{month}`       | New LessonId         | 〃            |
  | **Locker**  | GET    | `/locker`                      | zone,gender     | List<LockerDto>      | PROGRAM_ADMIN |
  |             | POST   | `/locker`                      | LockerDto       | Created              | 〃            |
  |             | PUT    | `/locker/{id}`                 | LockerDto       | Updated              | 〃            |
  | **Enroll**  | GET    | `/enroll`                      | status,lessonId | List<EnrollAdminDto> | CS_AGENT      |
  | **Cancel**  | GET    | `/cancel`                      | status=PENDING  | List                 | CS_AGENT      |
  |             | POST   | `/cancel/{id}/approve`         | `{refundPct}`   | 200                  | FINANCE_ADMIN |
  |             | POST   | `/cancel/{id}/deny`            | `{comment}`     | 200                  | CS_AGENT      |
  | **Payment** | GET    | `/payment`                     | period,status   | List<PaymentDto>     | FINANCE_ADMIN |
  |             | POST   | `/payment/{id}/partial-refund` | `{amount}`      | 200                  | FINANCE_ADMIN |
  | **Stats**   | GET    | `/stats/summary`               | month           | SummaryDto           | FINANCE_ADMIN |
  | **System**  | GET    | `/system/cron-log`             | jobName         | List                 | SUPER_ADMIN   |

  ***

  ## 4. 주요 DTO (발췌)

  ```
  // LessonDto
  {
    "lessonId": 320,
    "title": "초급반",
    "startDate": "2025-07-01",
    "endDate": "2025-07-30",
    "capacity": 20,
    "maleLockerCap": 10,
    "femaleLockerCap": 10,
    "price": 65000,
    "status": "OPEN"   // OPEN | CLOSED | FINISHED
  }

  // EnrollAdminDto
  {
    "enrollId": 9999,
    "userName": "홍길동",
    "status": "APPLIED",
    "createdAt": "2025-05-16T09:10:00",
    "lessonTitle": "초급반",
    "lockerGender": "F",
    "lockerNumber": "F-12"
  }

  ```

  ***

  ## 5. DB 추가·변경 필드

  | 테이블      | 필드                                    | 설명                |
  | ----------- | --------------------------------------- | ------------------- |
  | **lesson**  | status ENUM('OPEN','CLOSED','FINISHED') | 관리자 수동 마감    |
  | **payment** | refund_amount INTrefund_dt DATETIME     | 부분/전액 환불 기록 |

  ***

  ## 6. 비즈니스 룰 (Admin)

  | 구분              | 내용                                                                        |
  | ----------------- | --------------------------------------------------------------------------- |
  | **강습 마감**     | 정원=0 또는 관리자가 `CLOSED` → 프론트 ‘마감’ 표시                          |
  | **부분 환불**     | `approve(refundPct)` 호출 시 PG partialCancel, `payment.refund_amount` 기록 |
  | **취소 승인**     | 개강 후 취소 요청 `PENDING` → 승인 시 `enroll.status=CANCELED`              |
  | **사물함 비활성** | `is_active=0` → 신청 폼 드롭다운 제외                                       |

  ***

  ## 7. 배치 & 모니터링

  | Job              | 주기   | 관리자 UI      |
  | ---------------- | ------ | -------------- |
  | pg-webhook sync  | 실시간 | AD-08 Cron Log |
  | renewal-notifier | daily  | 스케줄 리스트  |

  Grafana Dashboard → 신청·매출·라커 KPI 실시간 파이프.

  ***

  ## 8. 테스트 케이스 (Admin)

  | ID    | 시나리오                            | 예상 결과                             |
  | ----- | ----------------------------------- | ------------------------------------- |
  | AD-01 | 강습 정원=0 시 자동 `status=CLOSED` | Lesson 목록 ‘마감’                    |
  | AD-02 | 사물함 `is_active=0` 설정           | 신청폼 라커 드롭다운 숨김             |
  | AD-03 | 부분 환불 70 % 승인                 | payment.refund_amount = amount×0.7    |
  | AD-04 | 취소 반려                           | enroll.status 그대로, 회원에게 메시지 |

  ***

  ## 9. 배포 체크리스트

  1. `PROGRAM_ADMIN`·`FINANCE_ADMIN` 역할 초기 계정 발급
  2. 결제 Webhook URL → 방화벽 허용·Slack 알림 연결
  3. Cron Log 테이블 ROLLOVER 정책(30 일) 적용
  4. Grafana Dashboard ID & 데이터소스 연결 테스트

  ***

  ### ✅ 운영자 혜택

  - **대시보드 한눈에**: 잔여 좌석·라커·매출 실시간 파악
  - **드래그 + 인라인 편집**: 강습·라커 관리 2배 빠름
  - **부분 환불 자동화**: PG API 연동으로 회계 오차 0 %

  ***
