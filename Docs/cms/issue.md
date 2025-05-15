### 서버 세팅 관련 이슈 문서화

#### 1. **톰캣 서버 다운 문제**

* **이슈**: 톰캣 서버가 실행되었다가 바로 종료되는 문제
* **원인**:

  * `application.yml`에서 환경변수 설정 및 데이터베이스 연결 설정이 잘못되어 발생한 문제
  * DB 연결 오류 또는 환경 변수 미설정
* **조치**:

  * `application.yml` 파일에 올바른 환경 설정 및 DB 연결 정보 추가
  * 서버 로그 분석(`tail -f catalina.out`)을 통해 상세 원인 파악

---

#### 2. **Swagger UI 접속 오류 (HTTP 404)**

* **이슈**: Swagger UI에 접속할 때 HTTP 404 오류 발생
* **원인**:

  * Swagger API 문서 경로 설정이 잘못되었거나, 설정이 누락되어 발생
  * `springdoc.swagger-ui.path` 또는 `springdoc.api-docs.path` 설정이 잘못되었거나 누락
* **조치**:

  * `application.yml`에서 `springdoc.api-docs.path`와 `springdoc.swagger-ui.path` 경로를 확인 및 수정
  * `@Operation`, `@Tag` 어노테이션으로 Swagger 문서화 설정을 명확히 확인
  * `curl -I 'http://localhost:8081/swagger-ui/index.html?configUrl=/api/v1/v3/api-docs'` 를 통해 경로가 맞는지 확인

---

#### 3. **JWT 인증 실패 (HTTP 401)**

* **이슈**: `POST /auth/login` 요청 시 401 인증 실패 오류 발생
* **원인**:

  * 로그인 API에서 잘못된 요청 파라미터 또는 잘못된 인증 정보로 인해 발생
  * `curl` 명령어를 실행할 때 Authorization 토큰을 잘못 처리한 경우
* **조치**:

  * Swagger UI에서 `POST /auth/login`의 요청 Body 형식을 확인하고 `username`과 `password` 값을 올바르게 입력
  * 로그인 시 받는 `accessToken`을 제대로 받아오는지 확인
  * `curl -v` 옵션을 사용하여 응답 로그 확인, `401` 오류 발생 시 토큰 생성 부분을 재점검

---

#### 4. **Swagger 로그인 및 인증 토큰 문제**

* **이슈**: Swagger UI에서 `POST /auth/login` 후 JWT 토큰을 받지 못함
* **원인**:

  * 잘못된 사용자 자격 증명 (username/password)
  * `Authorization` 헤더에 토큰을 포함시키지 않은 채 API 호출
* **조치**:

  * 올바른 사용자 자격 증명 사용 (admin/secret 등)
  * 로그인 후 발급된 토큰을 `Authorization` 헤더에 포함하여 API 호출
  * `curl -H "Authorization: Bearer <token>"` 방식으로 로그인 후 인증된 API 접근 시도

---

#### 5. **API 호출 경로 설정 오류**

* **이슈**: `GET /api/ping` 및 `GET /actuator/health` 호출 시 401 오류 발생
* **원인**:

  * `Authorization` 토큰이 없는 상태에서 API 호출
  * Spring Security에서 인증이 필요한 경로로 설정된 상태
* **조치**:

  * `POST /auth/login`을 통해 JWT 토큰을 발급받고, 그 토큰을 사용해 API를 호출
  * 인증된 상태에서만 `/api/ping` 및 `/actuator/health` 경로를 호출할 수 있도록 설정 확인

---

#### 6. **`jq` 명령어 미설치 문제**

* **이슈**: `curl` 명령어로 로그인 후 받은 토큰을 `jq` 명령어로 파싱하려고 했으나 `jq`가 설치되지 않음
* **원인**:

  * 시스템에 `jq` 명령어가 기본적으로 설치되지 않아서 발생한 문제
* **조치**:

  * `sudo apt install jq` 명령어로 `jq` 설치 후 토큰 파싱
  * 설치 후 `curl`로 받은 JSON 응답에서 `accessToken` 값을 추출하는 방법 적용

---

#### 7. **JWT 토큰 검증 실패**

* **이슈**: `POST /auth/verify` API 호출 시 401 오류 발생
* **원인**:

  * `Authorization` 헤더에 JWT 토큰이 포함되지 않거나 잘못된 토큰을 사용
* **조치**:

  * 로그인 후 받은 JWT 토큰을 `Authorization: Bearer <token>` 형태로 헤더에 포함하여 API 호출
  * 토큰이 유효한지 서버 측에서 검증 후 응답 처리

---

#### 8. **서버 로그에서 `ThreadLocal` 관련 경고 발생**

* **이슈**: 톰캣 서버 로그에서 `ThreadLocal` 관련 메모리 누수 경고 발생
* **원인**:

  * 웹 애플리케이션이 종료될 때 `ThreadLocal` 변수들을 제대로 정리하지 않아서 발생
* **조치**:

  * `ThreadLocal` 변수 사용 후 명시적으로 `remove()` 메서드를 호출하여 메모리 누수 방지
  * Spring Boot 애플리케이션에서 `SpringBootExceptionHandler`와 같은 객체의 `ThreadLocal` 관리

---

#### 9. **Nginx와 Tomcat 연동 문제**

* **이슈**: Nginx에서 Tomcat을 프록시할 때 요청이 제대로 전달되지 않음
* **원인**:

  * Nginx 설정에서 `proxy_pass` 경로가 올바르게 설정되지 않거나, Tomcat의 포트와 일치하지 않음
* **조치**:

  * Nginx 설정에서 `proxy_pass` 경로가 Tomcat의 `localhost:8081`과 정확히 일치하도록 설정
  * `nginx.conf` 파일에 `proxy_set_header`로 헤더 값을 전달하도록 수정

---
### 결론

위 이슈들을 해결하기 위한 주요 조치는 Swagger 설정, 인증 과정, 데이터베이스 연결 및 환경 변수 설정을 포함합니다. 각 단계에서 발생한 문제들을 서버 로그, API 요청 및 응답을 통해 정확히 확인하고, 필요한 설정을 수정하여 서버가 정상적으로 작동하도록 합니다.
