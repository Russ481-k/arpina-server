# 🏊‍♂️ Swimming Lesson CMS

수영강습 관리 시스템 - 고성능 동시성 제어 및 실시간 업데이트 지원

## 📋 **시스템 개요**

이 시스템은 수영강습 신청, 결제, 관리를 위한 종합적인 CMS 솔루션입니다.
동시성 제어, 성능 최적화, 실시간 업데이트 기능을 통해 안정적이고 효율적인 서비스를 제공합니다.

### **주요 기능**

- 🔒 **동시성 제어**: Race Condition 해결 및 정원 관리
- ⚡ **성능 최적화**: HikariCP, Connection Pool 최적화
- 📡 **실시간 업데이트**: WebSocket 기반 정원 현황 실시간 알림
- 📊 **모니터링**: Spring Actuator 기반 성능 메트릭 수집
- 🔐 **보안**: JWT 인증, NICE 본인인증 연동
- 💳 **결제**: KISPG 결제 시스템 연동

## 🛠️ **기술 스택**

### **Backend**

- **Framework**: Spring Boot 2.7.18
- **Database**: MariaDB 10.3+
- **Connection Pool**: HikariCP
- **Authentication**: JWT + NICE CheckPlus
- **Payment**: KISPG
- **Monitoring**: Spring Actuator + Prometheus
- **Real-time**: WebSocket

### **주요 변경사항 (v2.2)**

- ❌ **Redis 제거**: 세션 관리 단순화
- ✅ **기본 세션**: 서블릿 세션 관리 사용
- ✅ **단일 DB**: Read Replica 제거, 단일 DataSource

## 🚀 **Quick Start**

### **필수 요구사항**

- Java 8+
- Maven 3.6+
- MariaDB 10.3+

### **설치 및 실행**

1. **프로젝트 클론**

   ```bash
   git clone [repository-url]
   cd server
   ```

2. **환경 설정**

   ```bash
   # .env 파일 생성
   cp .env.example .env

   # 데이터베이스 및 필수 설정 입력
   nano .env
   ```

3. **데이터베이스 설정**

   ```sql
   CREATE DATABASE cms_new CHARACTER SET utf8mb4;
   CREATE USER 'cms_user'@'%' IDENTIFIED BY 'your_password';
   GRANT ALL PRIVILEGES ON cms_new.* TO 'cms_user'@'%';
   ```

4. **애플리케이션 실행**

   ```bash
   # 개발 환경
   mvn spring-boot:run -Dspring-boot.run.profiles=dev

   # 프로덕션 환경
   mvn clean package -Pprod
   java -jar target/handy-new-cms.jar --spring.profiles.active=prod
   ```

## 📊 **핵심 기능**

### **동시성 제어**

- **비관적 락**: 강습별 배타적 잠금
- **재시도 메커니즘**: 데드락 자동 복구
- **트랜잭션 격리**: SERIALIZABLE 수준

```java
@Retryable(value = {DeadlockLoserDataAccessException.class},
           maxAttempts = 3, backoff = @Backoff(delay = 1000))
@Transactional(isolation = Isolation.SERIALIZABLE)
public EnrollDto completeEnrollment(Long enrollId, PaymentCompleteDto dto)
```

### **실시간 업데이트**

WebSocket을 통한 강습 정원 현황 실시간 브로드캐스트

```javascript
const socket = new WebSocket("ws://localhost:8080/ws/lesson-capacity");
socket.onmessage = function (event) {
  const data = JSON.parse(event.data);
  // 정원 현황 업데이트
};
```

### **성능 모니터링**

- **Actuator 엔드포인트**: `/actuator/health`, `/actuator/metrics`
- **커스텀 메트릭**: 신청 성공률, 평균 처리시간, 데드락 재시도율
- **헬스 체크**: 데이터베이스 응답시간 모니터링

## 📁 **프로젝트 구조**

```
src/main/java/cms/
├── config/                 # 설정 클래스
│   ├── PerformanceMonitoringConfig.java
│   └── EgovConfigAppDataSource.java
├── enroll/                 # 신청 관리
│   ├── service/
│   └── controller/
├── websocket/              # 실시간 통신
│   ├── handler/
│   └── config/
└── ...

Docs/cms/                   # 개발 문서
├── enrollment-system.md    # 시스템 아키텍처
├── initial-server-setup-checklist.md
└── ...
```

## 🔧 **개발 환경 설정**

### **IDE 설정**

- **Lombok**: IDE에 Lombok 플러그인 설치 필요
- **JPA 지원**: Hibernate/JPA 플러그인 권장

### **데이터베이스 설정**

```yaml
spring:
  datasource:
    url: jdbc:mariadb://localhost:3306/cms_new
    username: cms_user
    password: your_password
    hikari:
      maximum-pool-size: 70
      minimum-idle: 10
```

### **세션 관리 (Redis 제거 후)**

```yaml
spring:
  session:
    timeout: 1800 # 30 minutes
    cookie:
      max-age: 1800
```

## 📊 **성능 지표**

| 메트릭          | 목표 값 | 설명                       |
| --------------- | ------- | -------------------------- |
| 신청 성공률     | > 99%   | 전체 신청 대비 성공 비율   |
| 평균 응답시간   | < 2초   | 신청 완료까지 소요 시간    |
| 데드락 재시도율 | < 5%    | 동시성 충돌로 인한 재시도  |
| 동시 사용자     | 50+     | 동시 처리 가능한 사용자 수 |

## 🚀 **배포**

### **프로덕션 빌드**

```bash
mvn clean package -Pprod
```

### **Docker 배포 (선택사항)**

```bash
docker build -t cms-app .
docker run -d -p 8080:8080 --name cms-app cms-app
```

### **시스템 서비스 등록**

```bash
sudo systemctl enable cms.service
sudo systemctl start cms.service
```

## 📚 **문서**

- [🏗️ 시스템 아키텍처](./Docs/cms/enrollment-system.md)
- [⚙️ 서버 설정 가이드](./Docs/cms/initial-server-setup-checklist.md)
- [💳 KISPG 결제 연동](./Docs/cms/kispg-payment-integration.md)
- [🔐 NICE 본인인증](./Docs/cms/NICE_CheckPlus_Integration_Guide.md)

## 🔧 **API 문서**

- **Swagger UI**: `http://localhost:8080/swagger-ui.html`
- **Actuator**: `http://localhost:8080/actuator`

## ⚠️ **주의사항**

### **Redis 제거로 인한 제약사항**

- **세션 공유 불가**: 다중 인스턴스 운영 시 세션 분산 처리 제한
- **스케일 아웃 제한**: 단일 인스턴스 운영 권장
- **캐싱**: 애플리케이션 레벨 캐싱으로 대체

### **개발 시 고려사항**

- 트랜잭션 범위 최소화
- WebSocket 연결 관리
- 메모리 사용량 모니터링

## 🐛 **트러블슈팅**

### **일반적인 문제**

**데이터베이스 연결 실패**

```bash
# MariaDB 서비스 확인
sudo systemctl status mariadb
netstat -tlnp | grep 3306
```

**메모리 부족**

```bash
# 메모리 사용량 확인
free -h
ps aux --sort=-%mem | head
```

**데드락 발생**

- 로그 확인: `DeadlockLoserDataAccessException`
- 재시도 메트릭 확인: `/actuator/enrollment-metrics`

## 📞 **지원**

- **기술 문의**: dev@company.com
- **시스템 관리**: sysadmin@company.com

## 📝 **변경 이력**

| 버전     | 날짜           | 주요 변경사항                    |
| -------- | -------------- | -------------------------------- |
| v1.0     | 2025-01-15     | 기본 CMS 시스템                  |
| v2.0     | 2025-03-15     | 동시성 제어 추가                 |
| v2.1     | 2025-04-01     | 실시간 업데이트                  |
| **v2.2** | **2025-05-23** | **Redis 제거, 세션 관리 단순화** |

---

_Made with ❤️ by CMS Development Team_
