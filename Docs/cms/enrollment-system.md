# Swimming Lesson Enrollment System - 동시성 제어 및 성능 최적화

## 📋 **시스템 개요**

이 문서는 수영강습 신청 시스템의 동시성 제어, 성능 최적화, 실시간 업데이트 기능에 대한 기술적 구현 내용을 다룹니다.

### **주요 해결 과제**

- **Race Condition 문제**: 동시 다발적 신청 시 정원 초과 문제
- **성능 최적화**: 대용량 트래픽 처리
- **실시간 정보**: WebSocket을 통한 실시간 정원 현황 업데이트
- **시스템 모니터링**: 성능 지표 수집 및 분석

---

## 🚨 **문제 분석 및 해결 과정**

### **1. Race Condition 시나리오**

#### **시나리오 1: 정원 초과 등록**

```
정원 10명인 강습에 11명이 동시 신청
→ 모든 요청이 동시에 현재 등록자 수를 조회 (10명 미만)
→ 11명 모두 등록 성공 (정원 초과!)
```

#### **시나리오 2: 결제 대기 중 신청**

```
5명 결제 완료 + 7명 동시 신청
→ 7명 모두 현재 등록자 수 조회 (5명)
→ 7명 모두 등록 성공 (총 12명, 정원 10명 초과!)
```

### **2. 해결 방안**

#### **A. 비관적 락 (Pessimistic Locking)**

```java
@Lock(LockModeType.PESSIMISTIC_WRITE)
@Query("SELECT l FROM Lesson l WHERE l.id = :lessonId")
Optional<Lesson> findByIdWithLock(@Param("lessonId") Long lessonId);
```

#### **B. 트랜잭션 격리 수준**

```java
@Transactional(isolation = Isolation.SERIALIZABLE)
public EnrollDto completeEnrollment(Long enrollId, PaymentCompleteDto dto)
```

#### **C. 재시도 메커니즘**

```java
@Retryable(
    value = {DeadlockLoserDataAccessException.class,
             CannotAcquireLockException.class},
    maxAttempts = 3,
    backoff = @Backoff(delay = 1000, multiplier = 1.5)
)
```

---

## ⚙️ **기술적 구현**

### **1. 의존성 관리**

#### **필수 의존성 (pom.xml)**

```xml
<!-- 동시성 제어 -->
<dependency>
    <groupId>org.springframework.retry</groupId>
    <artifactId>spring-retry</artifactId>
</dependency>
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-aop</artifactId>
</dependency>

<!-- 실시간 통신 -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-websocket</artifactId>
</dependency>

<!-- 성능 모니터링 -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-actuator</artifactId>
</dependency>
```

#### **제거된 의존성**

```xml
<!-- Redis Session (제거됨) -->
<!--
<dependency>
    <groupId>org.springframework.session</groupId>
    <artifactId>spring-session-data-redis</artifactId>
</dependency>
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-data-redis</artifactId>
</dependency>
-->
```

### **2. 데이터베이스 설정**

#### **단일 DataSource 구성**

```java
@Configuration
public class EgovConfigAppDataSource {

    @Bean
    @Primary
    @ConfigurationProperties("spring.datasource.hikari")
    public DataSource dataSource() {
        return DataSourceBuilder.create()
                .type(HikariDataSource.class)
                .build();
    }
}
```

#### **HikariCP 최적화**

```yaml
spring:
  datasource:
    hikari:
      maximum-pool-size: 70
      minimum-idle: 10
      connection-timeout: 30000
      idle-timeout: 600000
      max-lifetime: 1800000
      leak-detection-threshold: 60000
      cache-prep-stmts: true
      prep-stmt-cache-size: 250
      prep-stmt-cache-sql-limit: 2048
      use-server-prep-stmts: true
```

### **3. 세션 관리**

#### **기본 세션 설정 (Redis 제거 후)**

```yaml
spring:
  session:
    timeout: 1800 # 30 minutes
    cookie:
      max-age: 1800

# 개발환경
spring:
  session:
    timeout: 3600 # 60 minutes for development
    cookie:
      max-age: 3600
```

### **4. 동시성 제어 핵심 로직**

#### **신청 처리 서비스 (`EnrollmentServiceImpl`)**

수강 신청(`createInitialEnrollment`) 시 **결제 페이지 접근 슬롯**을 우선 확인하고 확보하는 로직이 중요합니다. 이 로직은 `Lesson` 엔티티에 대한 비관적 잠금을 사용하여 동시성을 제어합니다.

```java
@Service
@Transactional
public class EnrollmentServiceImpl {

    private final LessonRepository lessonRepository;
    private final EnrollRepository enrollRepository;
    // ... other dependencies

    @Retryable(
        value = {DeadlockLoserDataAccessException.class,
                 CannotAcquireLockException.class,
                 JpaOptimisticLockingFailureException.class},
        maxAttempts = 3,
        backoff = @Backoff(delay = 1000, multiplier = 1.5)
    )
    @Transactional(isolation = Isolation.SERIALIZABLE) // 또는 READ_COMMITTED 와 함께 명시적 잠금
    public EnrollResponseDto createInitialEnrollment(User user, EnrollRequestDto initialEnrollRequest, String ipAddress) {
        // 1. 비관적 락으로 강습 정보 조회 (lessonId 기준)
        Lesson lesson = lessonRepository.findByIdWithLock(initialEnrollRequest.getLessonId())
            .orElseThrow(() -> new ResourceNotFoundException("Lesson not found", ErrorCode.LESSON_NOT_FOUND));

        // 2. 결제 페이지 접근 슬롯 계산 (lesson.capacity - paidCount - unpaidActiveCount)
        long paidCount = enrollRepository.countByLessonLessonIdAndPayStatus(lesson.getLessonId(), "PAID");
        long unpaidActiveCount = enrollRepository.countByLessonLessonIdAndStatusAndPayStatusAndExpireDtAfter(
                lesson.getLessonId(), "APPLIED", "UNPAID", LocalDateTime.now());
        long availableSlots = lesson.getCapacity() - paidCount - unpaidActiveCount;

        // 3. 슬롯 확인
        if (availableSlots <= 0) {
            throw new BusinessRuleException(ErrorCode.PAYMENT_PAGE_SLOT_UNAVAILABLE, "결제 페이지 접근 슬롯이 없습니다.");
        }

        // 4. 중복 신청 확인, 월별 신청 제한 확인 등 기타 비즈니스 로직
        // ...

        // 5. Enroll 레코드 생성 (payStatus="UNPAID", expireDt 설정)
        Enroll enroll = Enroll.builder()
                // ... fields ...
                .payStatus("UNPAID")
                .expireDt(LocalDateTime.now().plusMinutes(5))
                .build();
        enrollRepository.save(enroll);

        // 6. WebSocket으로 실시간 정원 업데이트 (필요시)
        // broadcastCapacityUpdate(lesson.getId(), ...);

        // 7. EnrollResponseDto 반환 (결제 페이지 URL 포함)
        return new EnrollResponseDto(...);
    }
}
```

#### **KISPG 파라미터 생성 서비스 (`KispgPaymentServiceImpl`)**

결제 직전, KISPG 연동 파라미터를 생성하는 시점에서도 최종적으로 정원(결제 페이지 접근 슬롯)을 다시 한번 확인하여 안전장치를 마련합니다.

```java
@Service
@Transactional(readOnly = true)
public class KispgPaymentServiceImpl {

    // ... dependencies ...

    public KispgInitParamsDto generateInitParams(Long enrollId, User currentUser) {
        // ... Enroll 정보 조회 및 기본 검증 ...

        // 최종 정원 확인 (안전장치)
        Lesson lesson = enroll.getLesson();
        long paidCount = enrollRepository.countByLessonLessonIdAndPayStatus(lesson.getLessonId(), "PAID");
        long unpaidActiveCount = enrollRepository.countByLessonLessonIdAndStatusAndPayStatusAndExpireDtAfter(
                lesson.getLessonId(), "APPLIED", "UNPAID", LocalDateTime.now());
        long availableSlots = lesson.getCapacity() - paidCount - unpaidActiveCount;

        if (availableSlots <= 0 && !isCurrentUserAlreadyInSlot(enrollId, currentUser.getUuid())) { // 현재 사용자가 이미 슬롯을 점유한 경우가 아니라면
            throw new BusinessRuleException(ErrorCode.PAYMENT_PAGE_SLOT_UNAVAILABLE, "결제 진행 중 강습의 정원이 마감되었습니다.");
        }

        // ... KISPG 파라미터 생성 및 해시 생성 ...
        return new KispgInitParamsDto(...);
    }

    private boolean isCurrentUserAlreadyInSlot(Long currentEnrollId, String userUuid) {
        // 현재 요청의 enrollId가 사용자의 만료되지 않은 UNPAID enroll 건인지 확인하는 로직
        return enrollRepository.findById(currentEnrollId)
            .map(e -> "UNPAID".equals(e.getPayStatus()) &&
                      e.getExpireDt().isAfter(LocalDateTime.now()) &&
                      e.getUser().getUuid().equals(userUuid))
            .orElse(false);
    }
}
```

#### **사물함 재고 관리 서비스 (`LockerServiceImpl`)**

v2.2 에서 삭제되었던 `LockerServiceImpl`이 재고 기반 사물함 관리를 위해 복원되었습니다. 이 서비스는 성별 사물함 재고 조회, 사용량 증가/감소 기능을 담당하며, KISPG 결제 웹훅 처리 시 또는 취소 처리 시 호출됩니다.

```java
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class LockerServiceImpl implements LockerService {

    private final LockerInventoryRepository lockerInventoryRepository;

    public LockerAvailabilityDto getLockerAvailabilityByGender(String gender) {
        // ... 로직 ...
    }

    @Transactional
    public void incrementUsedQuantity(String gender) {
        // ... 로직 ...
    }

    @Transactional
    public void decrementUsedQuantity(String gender) {
        // ... 로직 ...
    }
}
```

### **5. 실시간 WebSocket 업데이트**

#### **WebSocket 핸들러**

```java
@Component
public class LessonCapacityWebSocketHandler extends TextWebSocketHandler {

    private final Map<String, Set<WebSocketSession>> lessonSubscriptions =
        new ConcurrentHashMap<>();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        // 연결 시 처리
    }

    public void broadcastCapacityUpdate(Long lessonId, int currentCapacity) {
        LessonCapacityUpdateDto update = LessonCapacityUpdateDto.builder()
            .lessonId(lessonId)
            .currentCapacity(currentCapacity)
            .timestamp(System.currentTimeMillis())
            .build();

        // 해당 강습 구독자들에게 브로드캐스트
        sendToLessonSubscribers(lessonId, update);
    }
}
```

### **6. 성능 모니터링**

#### **메트릭 수집**

```java
@Component
public class EnrollmentMetrics {
    private final AtomicInteger totalEnrollmentAttempts = new AtomicInteger(0);
    private final AtomicInteger successfulEnrollments = new AtomicInteger(0);
    private final AtomicInteger deadlockRetries = new AtomicInteger(0);

    public void recordEnrollmentAttempt() {
        totalEnrollmentAttempts.incrementAndGet();
    }

    public void recordDeadlockRetry() {
        deadlockRetries.incrementAndGet();
    }

    public double getSuccessRate() {
        int total = totalEnrollmentAttempts.get();
        return total > 0 ? (double) successfulEnrollments.get() / total * 100 : 0.0;
    }
}
```

#### **헬스 체크**

```yaml
management:
  endpoints:
    web:
      exposure:
        include: health,metrics,info,env,configprops
  endpoint:
    health:
      show-details: always
    metrics:
      enabled: true
```

---

## 🔧 **개발 환경 설정**

### **1. 로컬 개발 환경**

#### **필수 소프트웨어**

- Java 8+
- Maven 3.6+
- MariaDB 10.3+

#### **Redis 제거로 인한 변경사항**

- ✅ **세션 관리**: 기본 서블릿 세션 사용
- ✅ **캐싱**: 애플리케이션 레벨 캐싱으로 대체
- ✅ **분산 환경**: 단일 인스턴스 운영 권장

### **2. 애플리케이션 실행**

```bash
# Maven 빌드 및 실행
mvn clean compile
mvn spring-boot:run

# 프로필별 실행
mvn spring-boot:run -Dspring-boot.run.profiles=dev
mvn spring-boot:run -Dspring-boot.run.profiles=prod
```

### **3. 개발 도구**

#### **API 테스트**

- Swagger UI: `http://localhost:8080/swagger-ui.html`
- Actuator: `http://localhost:8080/actuator`

#### **WebSocket 테스트**

```javascript
const socket = new WebSocket("ws://localhost:8080/ws/lesson-capacity");
socket.onmessage = function (event) {
  const data = JSON.parse(event.data);
  console.log("Capacity Update:", data);
};
```

---

## 📊 **성능 지표 및 모니터링**

### **1. 주요 메트릭**

| 지표            | 목표 값 | 설명                       |
| --------------- | ------- | -------------------------- |
| 신청 성공률     | > 99%   | 전체 신청 대비 성공 비율   |
| 평균 응답시간   | < 2초   | 신청 완료까지 소요 시간    |
| 데드락 재시도율 | < 5%    | 동시성 충돌로 인한 재시도  |
| 동시 사용자     | 50+     | 동시 처리 가능한 사용자 수 |

### **2. 모니터링 엔드포인트**

```bash
# 전체 메트릭 조회
curl http://localhost:8080/actuator/metrics

# 신청 관련 메트릭 (개발환경)
curl http://localhost:8080/actuator/enrollment-metrics

# 헬스 체크
curl http://localhost:8080/actuator/health
```

### **3. 로그 모니터링**

```yaml
logging:
  level:
    cms.enroll.service: DEBUG
    cms.websocket: DEBUG
    org.springframework.transaction: DEBUG
    org.springframework.retry: DEBUG
```

---

## 🚀 **배포 및 운영**

### **1. 프로덕션 배포**

```bash
# 프로덕션 빌드
mvn clean package -Pprod

# Docker 배포 (선택사항)
docker build -t cms-app .
docker run -d -p 8080:8080 --name cms-app cms-app
```

### **2. 성능 튜닝 가이드**

#### **데이터베이스 최적화**

- 인덱스 최적화: `lesson_id, pay_status, expire_dt`
- 커넥션 풀 조정: `maximum-pool-size` 조정
- 쿼리 최적화: N+1 문제 해결

#### **애플리케이션 최적화**

- JVM 힙 메모리: `-Xmx2g -Xms1g`
- 가비지 컬렉션: `-XX:+UseG1GC`

### **3. 장애 대응**

#### **데드락 발생 시**

1. 로그 확인: `DeadlockLoserDataAccessException`
2. 재시도 메트릭 확인
3. 필요시 트랜잭션 타임아웃 조정

#### **성능 저하 시**

1. Actuator 헬스 체크
2. 데이터베이스 커넥션 상태 확인
3. 메모리 사용량 모니터링

---

## 📚 **참고 자료**

### **관련 문서**

- [KISPG 결제 연동 가이드](./kispg-payment-integration.md)
- [NICE 본인인증 연동 가이드](./NICE_CheckPlus_Integration_Guide.md)
- [강습 정원 관리 가이드](./lesson-enrollment-capacity.md)

### **기술 스택**

- **Framework**: Spring Boot 2.7.18
- **Database**: MariaDB 10.3+
- **Connection Pool**: HikariCP
- **Monitoring**: Spring Actuator + Prometheus
- **Real-time**: WebSocket

### **주요 변경 이력**

- **v1.0**: 기본 신청 시스템
- **v2.0**: 동시성 제어 추가
- **v2.1**: 실시간 업데이트 추가
- **v2.2**: Redis 제거, 세션 관리 단순화, **개별 사물함 관리에서 재고 기반 사물함 관리로 변경, `LockerServiceImpl` 복원.**
- **v2.3**: 성능 모니터링 강화, **결제 페이지 접근 슬롯 제어 로직 `EnrollmentServiceImpl`로 명확화 및 `KispgPaymentServiceImpl`에 최종 방어 로직 추가.**

---

## ⚠️ **주의사항**

### **개발 시 고려사항**

1. **트랜잭션 범위**: 비즈니스 로직과 DB 락 범위 최소화
2. **재시도 로직**: 무한 루프 방지를 위한 최대 재시도 횟수 설정
3. **WebSocket 연결**: 브라우저 호환성 및 연결 관리
4. **세션 관리**: Redis 제거로 인한 세션 분산 불가 (스케일 아웃 제한)

### **운영 시 고려사항**

1. **모니터링**: 실시간 메트릭 수집 및 알림 설정
2. **백업**: 정기적인 데이터베이스 백업
3. **스케일링**: 단일 인스턴스 운영 권장 (세션 공유 불가)
4. **로그 관리**: 디스크 공간 모니터링

---

_최종 업데이트: 2025-05-23_
