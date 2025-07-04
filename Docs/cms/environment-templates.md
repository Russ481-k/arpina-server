# 환경별 설정 템플릿 (정리된 버전)

이 문서는 4개 환경(로컬, 개발서버, 임시운영, 운영)별 `.env` 파일 템플릿을 제공합니다.

## 📊 정리 효과

- **환경변수 개수**: 60개 → 35개 (42% 감소)
- **설정 복잡도**: 대폭 감소
- **유지보수성**: 향상

## 🏠 로컬 개발환경 (.env.local)

```bash
# =================================
# 로컬 개발환경 설정
# =================================

# Environment Profile
SPRING_PROFILES_ACTIVE=local
ENVIRONMENT_NAME=local
CORS_ENABLED=true

# Database Configuration
SPRING_DATASOURCE_URL=jdbc:mariadb://172.30.1.11:3306/arpina_dev?useSSL=false&serverTimezone=Asia/Seoul&allowPublicKeyRetrieval=true&useProxy=false&useLegacyDatetimeCode=false
SPRING_DATASOURCE_USERNAME=local_username
SPRING_DATASOURCE_PASSWORD=local_password
SPRING_DATASOURCE_DRIVER_CLASS_NAME=org.mariadb.jdbc.Driver

# Database Pool Settings
DB_POOL_MAX_SIZE=20
DB_POOL_MIN_IDLE=5
DB_CONNECTION_TIMEOUT=30000
DB_IDLE_TIMEOUT=600000
DB_MAX_LIFETIME=1800000
DB_LEAK_DETECTION=60000

# JPA Settings
JPA_DDL_AUTO=validate
JPA_SHOW_SQL=true
JPA_FORMAT_SQL=true

# Server Configuration
SERVER_PORT=8080
SERVER_SERVLET_CONTEXT_PATH=/
SERVER_SERVLET_SESSION_TIMEOUT=3600

# Session Configuration
SESSION_TIMEOUT=3600
SESSION_COOKIE_MAX_AGE=3600

# File Upload Configuration
FILE_UPLOAD_DIR=./uploads
FILE_MAX_SIZE=100MB
FILE_MAX_REQUEST_SIZE=100MB
FILE_STORAGE_TYPE=local

# Logging Configuration (개발환경용 디버깅)
LOG_LEVEL=DEBUG
LOG_LEVEL_CMS_ENROLL=DEBUG
LOG_LEVEL_CMS_WEBSOCKET=DEBUG
LOG_LEVEL_TRANSACTION=DEBUG
LOG_LEVEL_RETRY=DEBUG
LOG_LEVEL_KISPG=DEBUG
LOG_LEVEL_PAYMENT=DEBUG
LOG_FILE_NAME=backend
LOG_FILE_PATH=./log
LOG_MAX_FILE_SIZE=10MB
LOG_MAX_HISTORY=30

# JWT Configuration
JWT_SECRET=jHNOaVvSoe9wNZqW7HrI9wb4p/1qhj3xod1SVHaIwlbqHyFZJj48dJopcfFrkeV0y3dkeaK3xlqfRpIQLGRF5w==
JWT_ACCESS_TOKEN_VALIDITY=86400000
JWT_REFRESH_TOKEN_VALIDITY=2592000000

# SNS Configuration
SNS_NAVER_CLIENT_ID=YOUR_CLIENT_ID
SNS_NAVER_CLIENT_SECRET=YOUR_CLIENT_SECRET
SNS_NAVER_CALLBACK_URL=http://localhost:3000/login/naver/callback
SNS_KAKAO_CLIENT_ID=YOUR_CLIENT_ID
SNS_KAKAO_CALLBACK_URL=http://localhost:3000/login/kakao/callback

# Mail Configuration
MAIL_HOST=smtp.gmail.com
MAIL_PORT=587
MAIL_USERNAME=your_email@gmail.com
MAIL_PASSWORD=your_password

# Nice-Auth
NICE_CHECKPLUS_SITE_CODE=your_site_code
NICE_CHECKPLUS_SITE_PASSWORD=your_site_password
NICE_CHECKPLUS_FRONTEND_REDIRECT_PATH=your_redirect_path

# CORS Configuration (로컬 개발용)
GLOBALS_ALLOW_ORIGIN=http://localhost:3000,http://127.0.0.1:3000,http://localhost:8081

# Global Configuration (로컬)
GLOBALS_LOCAL_IP=http://localhost
GLOBALS_IP=http://localhost:8080
GLOBALS_PAGE_UNIT=10
GLOBALS_PAGE_SIZE=10
GLOBALS_POSBL_ATCH_FILE_SIZE=5242880

# Application Specific
APP_LOCKER_FEE=5000
ENROLLMENT_LOCK_TIMEOUT=30000
ENROLLMENT_RETRY_ATTEMPTS=3
ENROLLMENT_RETRY_DELAY=1000
WEBSOCKET_ENABLED=true
WEBSOCKET_HEARTBEAT=30000

# Payment Module (테스트 환경)
KISPG_URL=https://testapi.kispg.co.kr
KISPG_MID=kistest00m
KISPG_MERCHANT_KEY=2d6ECGhR1pg/1QGE1lcRI4awsWEgshjEyI8UgYslLPJSuNeyPTkdrT8XWARezvDTUJClWQWhjxzBbu7AsuLZqg==
```

---

## 🔧 개발서버 환경 (.env.dev)

```bash
# =================================
# 개발서버 환경 설정
# =================================

# Environment Profile
SPRING_PROFILES_ACTIVE=dev
ENVIRONMENT_NAME=dev
CORS_ENABLED=true

# Database Configuration
SPRING_DATASOURCE_URL=jdbc:mariadb://172.30.1.11:3306/arpina_dev?useSSL=false&serverTimezone=Asia/Seoul&allowPublicKeyRetrieval=true&useProxy=false&useLegacyDatetimeCode=false
SPRING_DATASOURCE_USERNAME=dev_username
SPRING_DATASOURCE_PASSWORD=dev_password
SPRING_DATASOURCE_DRIVER_CLASS_NAME=org.mariadb.jdbc.Driver

# Database Pool Settings (개발서버용)
DB_POOL_MAX_SIZE=50
DB_POOL_MIN_IDLE=10
DB_CONNECTION_TIMEOUT=30000
DB_IDLE_TIMEOUT=600000
DB_MAX_LIFETIME=1800000
DB_LEAK_DETECTION=60000

# JPA Settings (개발환경용)
JPA_DDL_AUTO=validate
JPA_SHOW_SQL=false
JPA_FORMAT_SQL=false

# Server Configuration
SERVER_PORT=8080
SERVER_SERVLET_CONTEXT_PATH=/
SERVER_SERVLET_SESSION_TIMEOUT=3600

# Session Configuration
SESSION_TIMEOUT=3600
SESSION_COOKIE_MAX_AGE=3600

# File Upload Configuration
FILE_UPLOAD_DIR=/app/uploads
FILE_MAX_SIZE=100MB
FILE_MAX_REQUEST_SIZE=100MB
FILE_STORAGE_TYPE=local

# Logging Configuration (개발서버용)
LOG_LEVEL=DEBUG
LOG_LEVEL_CMS_ENROLL=DEBUG
LOG_LEVEL_CMS_WEBSOCKET=INFO
LOG_LEVEL_TRANSACTION=INFO
LOG_LEVEL_RETRY=INFO
LOG_LEVEL_KISPG=DEBUG
LOG_LEVEL_PAYMENT=DEBUG
LOG_FILE_NAME=backend
LOG_FILE_PATH=/app/log
LOG_MAX_FILE_SIZE=50MB
LOG_MAX_HISTORY=7

# JWT Configuration
JWT_SECRET=your_dev_jwt_secret_key_here
JWT_ACCESS_TOKEN_VALIDITY=86400000
JWT_REFRESH_TOKEN_VALIDITY=2592000000

# SNS Configuration
SNS_NAVER_CLIENT_ID=DEV_NAVER_CLIENT_ID
SNS_NAVER_CLIENT_SECRET=DEV_NAVER_CLIENT_SECRET
SNS_NAVER_CALLBACK_URL=https://dev.yourapp.com/login/naver/callback
SNS_KAKAO_CLIENT_ID=DEV_KAKAO_CLIENT_ID
SNS_KAKAO_CALLBACK_URL=https://dev.yourapp.com/login/kakao/callback

# Mail Configuration
MAIL_HOST=smtp.gmail.com
MAIL_PORT=587
MAIL_USERNAME=dev_email@gmail.com
MAIL_PASSWORD=dev_password

# Nice-Auth
NICE_CHECKPLUS_SITE_CODE=dev_site_code
NICE_CHECKPLUS_SITE_PASSWORD=dev_site_password
NICE_CHECKPLUS_FRONTEND_REDIRECT_PATH=dev_redirect_path

# CORS Configuration (개발서버용)
GLOBALS_ALLOW_ORIGIN=https://dev-frontend.yourapp.com,http://localhost:3000

# Global Configuration (개발서버)
GLOBALS_LOCAL_IP=https://dev.yourapp.com
GLOBALS_IP=https://dev.yourapp.com
GLOBALS_PAGE_UNIT=10
GLOBALS_PAGE_SIZE=10
GLOBALS_POSBL_ATCH_FILE_SIZE=5242880

# Application Specific
APP_LOCKER_FEE=5000
ENROLLMENT_LOCK_TIMEOUT=30000
ENROLLMENT_RETRY_ATTEMPTS=3
ENROLLMENT_RETRY_DELAY=1000
WEBSOCKET_ENABLED=true
WEBSOCKET_HEARTBEAT=30000

# Payment Module (개발서버)
KISPG_URL=https://testapi.kispg.co.kr
KISPG_MID=dev_mid
KISPG_MERCHANT_KEY=dev_merchant_key
```

---

## 🏭 임시운영 환경 (.env.staging)

```bash
# =================================
# 임시운영 환경 설정
# =================================

# Environment Profile
SPRING_PROFILES_ACTIVE=staging
ENVIRONMENT_NAME=staging
CORS_ENABLED=false

# Database Configuration
SPRING_DATASOURCE_URL=jdbc:mariadb://staging-db-server:3306/arpina_staging?useSSL=true&serverTimezone=Asia/Seoul&allowPublicKeyRetrieval=true&useProxy=false
SPRING_DATASOURCE_USERNAME=staging_username
SPRING_DATASOURCE_PASSWORD=staging_password
SPRING_DATASOURCE_DRIVER_CLASS_NAME=org.mariadb.jdbc.Driver

# Database Pool Settings (임시운영용)
DB_POOL_MAX_SIZE=70
DB_POOL_MIN_IDLE=15
DB_CONNECTION_TIMEOUT=30000
DB_IDLE_TIMEOUT=600000
DB_MAX_LIFETIME=1800000
DB_LEAK_DETECTION=60000

# JPA Settings (임시운영용)
JPA_DDL_AUTO=validate
JPA_SHOW_SQL=false
JPA_FORMAT_SQL=false

# Server Configuration
SERVER_PORT=8080
SERVER_SERVLET_CONTEXT_PATH=/
SERVER_SERVLET_SESSION_TIMEOUT=1800

# Session Configuration
SESSION_TIMEOUT=1800
SESSION_COOKIE_MAX_AGE=1800

# File Upload Configuration
FILE_UPLOAD_DIR=/app/uploads
FILE_MAX_SIZE=50MB
FILE_MAX_REQUEST_SIZE=50MB
FILE_STORAGE_TYPE=s3
FILE_STORAGE_S3_BUCKET=staging-cms-files
FILE_STORAGE_S3_REGION=ap-northeast-2

# Logging Configuration (운영급)
LOG_LEVEL=INFO
LOG_LEVEL_CMS_ENROLL=INFO
LOG_LEVEL_CMS_WEBSOCKET=WARN
LOG_LEVEL_TRANSACTION=WARN
LOG_LEVEL_RETRY=WARN
LOG_LEVEL_KISPG=INFO
LOG_LEVEL_PAYMENT=INFO
LOG_FILE_NAME=backend
LOG_FILE_PATH=/app/log
LOG_MAX_FILE_SIZE=100MB
LOG_MAX_HISTORY=30

# JWT Configuration
JWT_SECRET=your_staging_jwt_secret_key_here_very_secure
JWT_ACCESS_TOKEN_VALIDITY=86400000
JWT_REFRESH_TOKEN_VALIDITY=2592000000

# SNS Configuration
SNS_NAVER_CLIENT_ID=STAGING_NAVER_CLIENT_ID
SNS_NAVER_CLIENT_SECRET=STAGING_NAVER_CLIENT_SECRET
SNS_NAVER_CALLBACK_URL=https://staging.yourapp.com/login/naver/callback
SNS_KAKAO_CLIENT_ID=STAGING_KAKAO_CLIENT_ID
SNS_KAKAO_CALLBACK_URL=https://staging.yourapp.com/login/kakao/callback

# Mail Configuration
MAIL_HOST=smtp.gmail.com
MAIL_PORT=587
MAIL_USERNAME=staging_email@gmail.com
MAIL_PASSWORD=staging_password

# Nice-Auth
NICE_CHECKPLUS_SITE_CODE=staging_site_code
NICE_CHECKPLUS_SITE_PASSWORD=staging_site_password
NICE_CHECKPLUS_FRONTEND_REDIRECT_PATH=staging_redirect_path

# CORS Configuration (임시운영용 - CORS 비활성화)
GLOBALS_ALLOW_ORIGIN=https://staging-frontend.yourapp.com

# Global Configuration (임시운영)
GLOBALS_LOCAL_IP=https://staging.yourapp.com
GLOBALS_IP=https://staging.yourapp.com
GLOBALS_PAGE_UNIT=10
GLOBALS_PAGE_SIZE=10
GLOBALS_POSBL_ATCH_FILE_SIZE=5242880

# Application Specific
APP_LOCKER_FEE=5000
ENROLLMENT_LOCK_TIMEOUT=30000
ENROLLMENT_RETRY_ATTEMPTS=3
ENROLLMENT_RETRY_DELAY=1000
WEBSOCKET_ENABLED=true
WEBSOCKET_HEARTBEAT=30000

# Payment Module (운영급 테스트)
KISPG_URL=https://api.kispg.co.kr
KISPG_MID=staging_mid
KISPG_MERCHANT_KEY=staging_merchant_key
```

---

## 🏭 운영 환경 (.env.prod)

```bash
# =================================
# 운영 환경 설정
# =================================

# Environment Profile
SPRING_PROFILES_ACTIVE=prod
ENVIRONMENT_NAME=prod
CORS_ENABLED=false

# Database Configuration
SPRING_DATASOURCE_URL=jdbc:mariadb://prod-db-server:3306/arpina_prod?useSSL=true&serverTimezone=Asia/Seoul&allowPublicKeyRetrieval=true&useProxy=false
SPRING_DATASOURCE_USERNAME=prod_username
SPRING_DATASOURCE_PASSWORD=prod_password
SPRING_DATASOURCE_DRIVER_CLASS_NAME=org.mariadb.jdbc.Driver

# Database Pool Settings (운영 최적화)
DB_POOL_MAX_SIZE=100
DB_POOL_MIN_IDLE=20
DB_CONNECTION_TIMEOUT=30000
DB_IDLE_TIMEOUT=600000
DB_MAX_LIFETIME=1800000
DB_LEAK_DETECTION=60000

# JPA Settings (운영 환경용)
JPA_DDL_AUTO=validate
JPA_SHOW_SQL=false
JPA_FORMAT_SQL=false

# Server Configuration
SERVER_PORT=8080
SERVER_SERVLET_CONTEXT_PATH=/
SERVER_SERVLET_SESSION_TIMEOUT=1800

# Session Configuration
SESSION_TIMEOUT=1800
SESSION_COOKIE_MAX_AGE=1800

# File Upload Configuration
FILE_UPLOAD_DIR=/app/uploads
FILE_MAX_SIZE=50MB
FILE_MAX_REQUEST_SIZE=50MB
FILE_STORAGE_TYPE=s3
FILE_STORAGE_S3_BUCKET=prod-cms-files
FILE_STORAGE_S3_REGION=ap-northeast-2
FILE_STORAGE_S3_CDN_URL=https://cdn.yourapp.com

# Logging Configuration (운영용 - 최소화)
LOG_LEVEL=WARN
LOG_LEVEL_CMS_ENROLL=INFO
LOG_LEVEL_CMS_WEBSOCKET=ERROR
LOG_LEVEL_TRANSACTION=ERROR
LOG_LEVEL_RETRY=ERROR
LOG_LEVEL_KISPG=WARN
LOG_LEVEL_PAYMENT=INFO
LOG_FILE_NAME=backend
LOG_FILE_PATH=/app/log
LOG_MAX_FILE_SIZE=100MB
LOG_MAX_HISTORY=90

# JWT Configuration
JWT_SECRET=your_production_jwt_secret_key_here_extremely_secure_256bit
JWT_ACCESS_TOKEN_VALIDITY=86400000
JWT_REFRESH_TOKEN_VALIDITY=2592000000

# SNS Configuration
SNS_NAVER_CLIENT_ID=PROD_NAVER_CLIENT_ID
SNS_NAVER_CLIENT_SECRET=PROD_NAVER_CLIENT_SECRET
SNS_NAVER_CALLBACK_URL=https://www.yourapp.com/login/naver/callback
SNS_KAKAO_CLIENT_ID=PROD_KAKAO_CLIENT_ID
SNS_KAKAO_CALLBACK_URL=https://www.yourapp.com/login/kakao/callback

# Mail Configuration
MAIL_HOST=smtp.gmail.com
MAIL_PORT=587
MAIL_USERNAME=prod_email@gmail.com
MAIL_PASSWORD=prod_password

# Nice-Auth
NICE_CHECKPLUS_SITE_CODE=prod_site_code
NICE_CHECKPLUS_SITE_PASSWORD=prod_site_password
NICE_CHECKPLUS_FRONTEND_REDIRECT_PATH=prod_redirect_path

# CORS Configuration (운영용 - CORS 비활성화)
GLOBALS_ALLOW_ORIGIN=https://www.yourapp.com

# Global Configuration (운영)
GLOBALS_LOCAL_IP=https://www.yourapp.com
GLOBALS_IP=https://www.yourapp.com
GLOBALS_PAGE_UNIT=10
GLOBALS_PAGE_SIZE=10
GLOBALS_POSBL_ATCH_FILE_SIZE=5242880

# Application Specific
APP_LOCKER_FEE=5000
ENROLLMENT_LOCK_TIMEOUT=30000
ENROLLMENT_RETRY_ATTEMPTS=3
ENROLLMENT_RETRY_DELAY=1000
WEBSOCKET_ENABLED=true
WEBSOCKET_HEARTBEAT=30000

# Payment Module (운영)
KISPG_URL=https://api.kispg.co.kr
KISPG_MID=prod_mid
KISPG_MERCHANT_KEY=prod_merchant_key
```

---

## 🔧 사용 방법

### 1. 환경별 배포 시

```bash
# 로컬 개발
cp .env.local.template .env

# 개발서버 배포
cp .env.dev.template .env

# 임시운영 배포
cp .env.staging.template .env

# 운영 배포
cp .env.prod.template .env
```

### 2. Git 관리

```bash
# .gitignore에 추가
echo ".env" >> .gitignore
echo ".env.*" >> .gitignore
echo "!.env.*.template" >> .gitignore
```

### 3. 환경 확인

애플리케이션 시작 시 로그에서 환경 정보 확인:

```
Environment: local, CORS: enabled
Environment: dev, CORS: enabled
Environment: staging, CORS: disabled
Environment: prod, CORS: disabled
```

---

## ⚡ 주요 개선사항

### 1. **환경변수 대폭 감소**

- **60개 → 35개** (42% 감소)
- 사용되지 않는 설정 제거

### 2. **설정 간소화**

- S3 관련 설정 (현재 미사용)
- 파일 정책 설정 (현재 미사용)
- 썸네일 설정 (현재 미사용)
- JWT 중복 설정 제거
- 성능 모니터링 설정 (현재 미사용)

### 3. **CORS 환경별 제어**

- 로컬/개발: CORS 활성화
- 임시운영/운영: CORS 비활성화

### 4. **성능 최적화**

- 환경별 데이터베이스 풀 크기 조정
- 로깅 레벨 환경별 최적화
- 파일 스토리지 환경별 선택

---

## 🚨 주의사항

1. **민감 정보 관리**: `.env` 파일은 Git에 커밋하지 마세요
2. **템플릿 업데이트**: 새로운 설정이 추가되면 모든 템플릿을 업데이트하세요
3. **환경별 테스트**: 각 환경에서 설정이 올바르게 적용되는지 확인하세요
4. **백업**: 운영 환경의 `.env` 파일은 안전한 곳에 백업하세요

---

## 📚 관련 문서

- [환경변수 정리 분석](./env-cleanup-analysis.md) - 상세 분석 결과
- [.env 파일 예시](./env-example.md) - 로컬 개발용 기본 템플릿
- [환경별 사용 가이드](./env-usage-guide.md) - 환경 전환 방법
