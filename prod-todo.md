### 핵심 요약

- **결제 진입 5분 홀드 도입**: 결제 창 진입 시 5분 동안 슬롯을 임시 점유하여 동시 진입에도 정원 초과 결제를 방지.
- **정원 계산 일원화**: 남은 슬롯 = `capacity - PAID - UNPAID(APPLIED, expireDt>now)`.

### 백엔드 변경

- **결제 준비** `POST /payment/prepare-kispg-payment`
  - 레슨 비관적 잠금 + 정원 확인 후, `UNPAID/APPLIED` 홀드(만료 5분) 생성.
  - 응답에 `holdId`(임시 `moid`), `holdExpireAt`(ISO) 포함.
- **결제 승인** `POST /payment/approve-and-create-enrollment`
  - 기존 홀드가 있으면 해당 레코드를 `PAID`로 승격(없으면 기존 방식으로 생성).
- **홀드 해제 API** `POST /payment/release-pending`
  - Body: `{ holdId }`. 결제 실패/창 닫힘 시 즉시 해제(미호출 시 5분 후 자동 만료).
- **정원/슬롯 노출**
  - `LessonDto`·`GET /swimming/lessons`: `availablePaymentSlots`, `currentPaidCount`, `currentPendingCount` 포함.
  - `GET /swimming/enroll/eligibility`: `availablePaymentSlots` 포함.

### 프론트엔드 변경

- **결제 흐름**: 기존대로 준비 → KISPG 진입 → 승인.
  - 409/400/401 에러 토스트 처리 강화.
  - 결제 창 닫힘 시 `holdId`로 `release-pending` 호출(실패 시 무시, TTL로 소멸).
  - 5분 카운트다운 안내(서버 홀드 TTL과 일치).
- **UX 옵션**: `availablePaymentSlots=0`이면 버튼 비활성화 가능.

### DTO 확장

- `LessonDto`
  - `availablePaymentSlots`, `currentPaidCount`, `currentPendingCount`
- `CheckEnrollmentEligibilityDto`
  - `availablePaymentSlots`
- `KispgInitParamsDto`
  - `holdId`, `holdExpireAt`

### 운영/안정성

- **동시성 제어**: 결제 준비 시 레슨 단위 비관적 잠금 + 트랜잭션.
- **만료 처리**: 5분 `expireDt`로 자동 소멸. 주기 배치(기존 `ExpiredUnpaidEnrollmentCleanupJob`, 5분마다)가 만료 누락분 정리.
- **효과**: 결제 단계 동시 진입 상황에서도 정원 초과 확정 방지, 리스트/자격/결제 단계의 남은 슬롯 계산이 일관.

아래는 결제 진입 5분 홀드(UNPAID/APPLIED, expireDt>now) 포함 정원 제어에 대한 테스트 시나리오입니다. 각 시나리오는 사전조건, 절차, 기대결과로 구성했습니다.

### 사전조건 공통

- 테스트 전용 강습 생성: capacity=2, registrationStart/End 현재 시각 기준 유효.
- 사용자 A, B, C 생성. (A/B 정상, C 권한 오류/중복 테스트 용)
- 사물함 사용 여부/성별 데이터 준비(남/녀 각각 1명).
- 웹훅/승인 엔드포인트 활성, 스케줄러(만료 정리) 활성.

### 정원/홀드 기본

- 시나리오 1: 결제 진입 시 홀드 생성
  - 절차: A가 POST /payment/prepare-kispg-payment 호출.
  - 기대: DB에 UNPAID/APPLIED, expireDt≈now+5분 생성. availableSlots=capacity-PAID-UNPAID(만료전) 반영.
- 시나리오 2: 잔여 0일 때 진입 차단
  - 절차: capacity=0 또는 기존 PAID=2 상태에서 prepare 호출.
  - 기대: 409(LESSON_CAPACITY_EXCEEDED).

### 동시성

- 시나리오 3: 남은 1자리에서 동시 진입 2건
  - 절차: 남은 슬롯=1 상태에서 A/B가 동시에 prepare 호출.
  - 기대: 1건만 성공, 1건 409. DB 홀드 수=1.
- 시나리오 4: 동시 승인 경쟁
  - 절차: 슬롯=1, A/B가 각각 홀드 보유. B가 늦게 준비 성공 못 하도록(이미 1건 보유). 승인 API를 동시 2회 호출.
  - 기대: 1건만 PAID 전환, 나머지는 비즈니스 예외/재검증 실패.

### 승인/전환

- 시나리오 5: 홀드 → 승인 성공
  - 절차: A 홀드 후 approve-and-create-enrollment.
  - 기대: 같은 레코드가 PAID로 승격, expireDt 갱신(강습 종료일 23:59:59 등), remaining 재계산, 중복 생성 없음.
- 시나리오 6: 홀드 없음 승인(후방 호환)
  - 절차: 직접 approve 호출(웹훅/리턴 경로)로 PAID 생성.
  - 기대: 신규 PAID 생성, 정원 계산 일관성 유지.

### 해제/만료

- 시나리오 7: 결제창 닫힘 즉시 해제
  - 절차: FE가 onClose에서 POST /payment/release-pending(holdId) 호출.
  - 기대: 해당 UNPAID 레코드 삭제(true 반환). 재호출 시 false.
- 시나리오 8: 5분 만료 스케줄러
  - 절차: 홀드 생성 후 5분 경과(또는 expireDt 과거로 강제) → 스케줄러 실행.
  - 기대: status=EXPIRED로 업데이트(또는 집계 제외), remaining/availableSlots 증가.

### 중복/정책

- 시나리오 9: 동일 사용자/강습 중복 홀드 방지
  - 절차: A가 동일 강습에 연속 prepare 호출.
  - 기대: 409(DUPLICATE_ENROLLMENT 또는 유사).
- 시나리오 10: 월별 1회 제한
  - 절차: 같은 달 다른 강습 PAID 보유 후 prepare/approve 시도.
  - 기대: eligibility false 또는 prepare/approve 차단.
- 시나리오 11: 신청 기간 정책
  - 절차: 기간 전/후 prepare 호출.
  - 기대: 400(REGISTRATION_PERIOD_INVALID).

### 금액/옵션

- 시나리오 12: 멤버십 할인 적용
  - 절차: membershipType 지정하여 prepare → approve.
  - 기대: 총액/부가세/공급가 반영, 승인 후 Payment 금액 일치.
- 시나리오 13: 사물함 사용 시 라커 검증
  - 절차: usesLocker=true, 성별 누락/비정상 코드.
  - 기대: 400(LOCKER_GENDER_REQUIRED) 또는 라커 할당 로직 경로 정상.

### 보안/권한

- 시나리오 14: 타 사용자 holdId 해제 차단
  - 절차: A의 holdId를 C가 release-pending 호출.
  - 기대: false 반환, 데이터 미변경.
- 시나리오 15: 권한 없이 결제 API 호출
  - 절차: 비로그인/권한 부족으로 prepare/approve 호출.
  - 기대: 401/403.

### 응답/UX(프런트)

- 시나리오 16: lessons/eligibility 슬롯 노출
  - 절차: GET /swimming/lessons, /swimming/enroll/eligibility 호출.
  - 기대: availablePaymentSlots, currentPaidCount, currentPendingCount 일관성 확인, 0일 때 버튼 비활성.
- 시나리오 17: 에러 메시지 렌더링
  - 절차: 409/400/401/403 강제 발생.
  - 기대: 지정 토스트 문구 노출.

### 안정성/회복

- 시나리오 18: 서버 재시작 중 보류 유지
  - 절차: 홀드 생성 후 서버 재시작 → 만료 스케줄러 동작까지 대기.
  - 기대: expireDt 기반으로 집계 제외, 스케줄러가 상태 정리.
- 시나리오 19: 웹소켓 브로드캐스트
  - 절차: 홀드 생성/해제/승인 시 WS 메시지 수신.
  - 기대: 남은 정원/카운트 갱신 메시지 수신.

### 부하/회귀

- 시나리오 20: 부하 테스트
  - 절차: remaining=2일 때 50 동시 prepare.
  - 기대: 성공 2, 실패 48(409). 음수 남은 정원 없음.
- 시나리오 21: 회귀 – 나이스페이 승인 후 단일 PAID
  - 절차: 동일 승인 API 2회 반복.
  - 기대: 중복 생성/이중 승인 없음.

요청 시 자동화 방법(JUnit+SpringBootTest, RestAssured, Gatling/Locust로 동시성)과 샘플 스크립트도 제공하겠습니다.

- 요약
  - 홀드 생성/승격/해제/만료, 동시성, 정책/보안, UX, 부하까지 21개 시나리오 정리. 필요 시 JUnit/부하 테스트 템플릿을 추가로 드릴 수 있습니다.
