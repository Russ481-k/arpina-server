# 환경변수 사용 현황 분석 및 정리

## 📊 분석 결과

### ✅ **실제 사용되는 필수 환경변수 (유지)**

#### 1. **Spring 기본 설정**

- `SPRING_PROFILES_ACTIVE` - EgovBootApplication.java에서 사용
- `SPRING_DATASOURCE_URL` - DataSource 설정
- `SPRING_DATASOURCE_USERNAME` - DataSource 설정
- `SPRING_DATASOURCE_PASSWORD` - DataSource 설정
- `SPRING_DATASOURCE_DRIVER_CLASS_NAME` - DataSource 설정

#### 2. **데이터베이스 풀 설정**

- `DB_POOL_MAX_SIZE` - HikariCP 설정
- `DB_POOL_MIN_IDLE` - HikariCP 설정
- `DB_CONNECTION_TIMEOUT` - HikariCP 설정
- `DB_IDLE_TIMEOUT` - HikariCP 설정
- `DB_MAX_LIFETIME` - HikariCP 설정
- `DB_LEAK_DETECTION` - HikariCP 설정

#### 3. **JPA 설정**

- `JPA_DDL_AUTO` - Hibernate DDL 설정
- `JPA_SHOW_SQL` - SQL 로깅
- `JPA_FORMAT_SQL` - SQL 포맷팅

#### 4. **서버 설정**

- `SERVER_PORT` - 서버 포트
- `SERVER_SERVLET_CONTEXT_PATH` - 컨텍스트 경로
- `SERVER_SERVLET_SESSION_TIMEOUT` - 세션 타임아웃
- `SESSION_TIMEOUT` - 세션 설정
- `SESSION_COOKIE_MAX_AGE` - 쿠키 설정

#### 5. **파일 업로드 설정**

- `FILE_UPLOAD_DIR` - 파일 업로드 경로 (통합됨)
- `FILE_MAX_SIZE` - 파일 최대 크기
- `FILE_MAX_REQUEST_SIZE` - 요청 최대 크기
- `FILE_STORAGE_TYPE` - 스토리지 타입

#### 6. **로깅 설정**

- `LOG_LEVEL` - 루트 로그 레벨
- `LOG_LEVEL_CMS_ENROLL` - 수강신청 로깅
- `LOG_LEVEL_CMS_WEBSOCKET` - 웹소켓 로깅
- `LOG_LEVEL_TRANSACTION` - 트랜잭션 로깅
- `LOG_LEVEL_RETRY` - 재시도 로깅
- `LOG_LEVEL_KISPG` - KISPG 로깅
- `LOG_LEVEL_PAYMENT` - 결제 로깅
- `LOG_FILE_NAME` - 로그 파일명
- `LOG_FILE_PATH` - 로그 파일 경로
- `LOG_MAX_FILE_SIZE` - 로그 파일 최대 크기
- `LOG_MAX_HISTORY` - 로그 보관 기간

#### 7. **JWT 설정**

- `JWT_SECRET` - JWT 시크릿 키 (JwtTokenProvider.java에서 사용)
- `JWT_ACCESS_TOKEN_VALIDITY` - 액세스 토큰 유효기간
- `JWT_REFRESH_TOKEN_VALIDITY` - 리프레시 토큰 유효기간

#### 8. **SNS 로그인**

- `SNS_NAVER_CLIENT_ID` - 네이버 로그인
- `SNS_NAVER_CLIENT_SECRET` - 네이버 로그인
- `SNS_NAVER_CALLBACK_URL` - 네이버 콜백
- `SNS_KAKAO_CLIENT_ID` - 카카오 로그인
- `SNS_KAKAO_CALLBACK_URL` - 카카오 콜백

#### 9. **메일 설정**

- `MAIL_HOST` - 메일 서버
- `MAIL_PORT` - 메일 포트
- `MAIL_USERNAME` - 메일 계정
- `MAIL_PASSWORD` - 메일 비밀번호

#### 10. **본인인증 (NICE)**

- `NICE_CHECKPLUS_SITE_CODE` - NiceService.java에서 사용
- `NICE_CHECKPLUS_SITE_PASSWORD` - NiceService.java에서 사용
- `NICE_CHECKPLUS_FRONTEND_REDIRECT_PATH` - NiceController.java에서 사용

#### 11. **CORS 설정**

- `GLOBALS_ALLOW_ORIGIN` - SecurityConfig.java에서 사용

#### 12. **Global 설정 (EgovConfigAppProperties.java에서 사용)**

- `GLOBALS_LOCAL_IP` - 로컬 IP
- `GLOBALS_IP` - API 베이스 URL (KispgPaymentServiceImpl.java에서 사용)
- `GLOBALS_PAGE_UNIT` - 페이지 단위
- `GLOBALS_PAGE_SIZE` - 페이지 크기
- `GLOBALS_POSBL_ATCH_FILE_SIZE` - 첨부파일 크기

#### 13. **애플리케이션 설정**

- `APP_LOCKER_FEE` - 사물함 요금 (KispgPaymentServiceImpl.java에서 사용)
- `ENROLLMENT_LOCK_TIMEOUT` - 수강신청 락 타임아웃
- `ENROLLMENT_RETRY_ATTEMPTS` - 재시도 횟수
- `ENROLLMENT_RETRY_DELAY` - 재시도 지연시간
- `WEBSOCKET_ENABLED` - 웹소켓 활성화
- `WEBSOCKET_HEARTBEAT` - 웹소켓 하트비트

#### 14. **환경 제어**

- `ENVIRONMENT_NAME` - 환경명
- `CORS_ENABLED` - CORS 활성화 (SecurityConfig.java에서 사용)

#### 15. **결제 (KISPG)**

- `KISPG_URL` - KISPG API URL (KispgPaymentServiceImpl.java에서 사용)
- `KISPG_MID` - KISPG 가맹점 ID
- `KISPG_MERCHANT_KEY` - KISPG 머천트 키

---

### ❌ **사용되지 않는 환경변수 (제거 대상)**

#### 1. **S3 관련 (현재 사용 안함)**

- `FILE_STORAGE_S3_BUCKET` - 코드에서 사용되지 않음
- `FILE_STORAGE_S3_REGION` - 코드에서 사용되지 않음
- `FILE_STORAGE_S3_CDN_URL` - 코드에서 사용되지 않음

#### 2. **파일 정책 (사용 안함)**

- `FILE_POLICY_MAX_SIZE` - 코드에서 사용되지 않음
- `FILE_POLICY_ALLOWED_TYPES` - 코드에서 사용되지 않음

#### 3. **썸네일 설정 (사용 안함)**

- `FILE_THUMBNAIL_ENABLED` - 코드에서 사용되지 않음
- `FILE_THUMBNAIL_WIDTH` - 코드에서 사용되지 않음
- `FILE_THUMBNAIL_HEIGHT` - 코드에서 사용되지 않음
- `FILE_THUMBNAIL_FORMAT` - 코드에서 사용되지 않음

#### 4. **JWT 중복 설정**

- `JWT_HEADER` - 하드코딩되어 사용되지 않음
- `JWT_PREFIX` - 하드코딩되어 사용되지 않음

#### 5. **Global 설정 (사용 안함)**

- `GLOBALS_DB_TYPE` - 코드에서 사용되지 않음
- `GLOBALS_ADDED_OPTIONS` - EgovConfigAppProperties.java에서만 참조, 실제 사용 안함
- `GLOBALS_MAIN_PAGE` - 코드에서 사용되지 않음
- `GLOBALS_CRYPTO_ALGORITM` - 코드에서 사용되지 않음
- `GLOBALS_FILE_UPLOAD_EXTENSIONS_IMAGES` - 코드에서 사용되지 않음
- `GLOBALS_FILE_UPLOAD_EXTENSIONS` - 코드에서 사용되지 않음

#### 6. **성능 모니터링 (사용 안함)**

- `PERFORMANCE_METRICS_ENABLED` - 코드에서 사용되지 않음
- `SLOW_QUERY_THRESHOLD` - 코드에서 사용되지 않음
- `MANAGEMENT_ENDPOINTS` - 코드에서 사용되지 않음
- `MANAGEMENT_HEALTH_DETAILS` - 코드에서 사용되지 않음
- `MANAGEMENT_HEALTH_COMPONENTS` - 코드에서 사용되지 않음
- `MANAGEMENT_METRICS_ENABLED` - 코드에서 사용되지 않음
- `PROMETHEUS_ENABLED` - 코드에서 사용되지 않음

---

## 🗑️ 정리 계획

### 1단계: application.yml 정리

- 사용되지 않는 환경변수 제거
- 설정 간소화

### 2단계: 환경 템플릿 파일 정리

- env-example.md 정리
- environment-templates.md 정리

### 3단계: 문서 업데이트

- 정리된 환경변수 목록으로 문서 업데이트
- 필수/선택 환경변수 구분

---

## 📈 정리 효과

- **환경변수 개수**: 60개 → 약 35개 (42% 감소)
- **설정 복잡도**: 대폭 감소
- **유지보수성**: 향상
- **설정 오류 가능성**: 감소
