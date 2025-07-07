# .env 파일 예시

이 문서는 프로젝트에서 사용할 `.env` 파일의 예시를 제공합니다.

## 📋 사용 방법

1. 프로젝트 루트에 `.env` 파일을 생성하세요
2. 아래 내용을 복사하여 붙여넣으세요
3. 실제 환경에 맞는 값으로 수정하세요
4. `.env` 파일은 Git에 커밋하지 마세요

## 📄 .env 파일 내용

아래 내용을 복사하여 프로젝트 루트의 `.env` 파일로 저장하세요:

```bash
# =================================
# 환경변수 설정 예시 파일
# 이 파일을 .env로 복사하여 실제 값으로 수정하세요
# =================================

# Environment Profile (local, dev, staging, prod)
SPRING_PROFILES_ACTIVE=local
ENVIRONMENT_NAME=local
CORS_ENABLED=true

# =================================
# Database Configuration
# =================================
SPRING_DATASOURCE_URL=jdbc:mariadb://172.30.1.11:3306/arpina_dev?useSSL=false&serverTimezone=Asia/Seoul&allowPublicKeyRetrieval=true&useProxy=false&useLegacyDatetimeCode=false
SPRING_DATASOURCE_USERNAME=your_db_username
SPRING_DATASOURCE_PASSWORD=your_db_password
SPRING_DATASOURCE_DRIVER_CLASS_NAME=org.mariadb.jdbc.Driver

# Database Pool Settings
DB_POOL_MAX_SIZE=20
DB_POOL_MIN_IDLE=5
DB_CONNECTION_TIMEOUT=30000
DB_IDLE_TIMEOUT=600000
DB_MAX_LIFETIME=1800000
DB_LEAK_DETECTION=60000

# =================================
# JPA Settings
# =================================
JPA_DDL_AUTO=validate
JPA_SHOW_SQL=true
JPA_FORMAT_SQL=true

# =================================
# Server Configuration
# =================================
SERVER_PORT=8080
SERVER_SERVLET_CONTEXT_PATH=/
SERVER_SERVLET_SESSION_TIMEOUT=3600

# Session Configuration
SESSION_TIMEOUT=3600
SESSION_COOKIE_MAX_AGE=3600

# =================================
# File Upload Configuration
# =================================
FILE_UPLOAD_DIR=./uploads
FILE_MAX_SIZE=100MB
FILE_MAX_REQUEST_SIZE=100MB
FILE_STORAGE_TYPE=local

# =================================
# Logging Configuration
# =================================
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

# =================================
# JWT Configuration
# =================================
JWT_SECRET=your-256-bit-secret-key-here-must-be-at-least-256-bits-long-for-security
JWT_ACCESS_TOKEN_VALIDITY=86400000
JWT_REFRESH_TOKEN_VALIDITY=2592000000

# =================================
# SNS Configuration
# =================================
SNS_NAVER_CLIENT_ID=YOUR_NAVER_CLIENT_ID
SNS_NAVER_CLIENT_SECRET=YOUR_NAVER_CLIENT_SECRET
SNS_NAVER_CALLBACK_URL=http://localhost:3000/login/naver/callback
SNS_KAKAO_CLIENT_ID=YOUR_KAKAO_CLIENT_ID
SNS_KAKAO_CALLBACK_URL=http://localhost:3000/login/kakao/callback

# =================================
# Mail Configuration
# =================================
MAIL_HOST=smtp.gmail.com
MAIL_PORT=587
MAIL_USERNAME=your_email@gmail.com
MAIL_PASSWORD=your_app_password

# =================================
# Nice-Auth Configuration
# =================================
NICE_CHECKPLUS_SITE_CODE=your_nice_site_code
NICE_CHECKPLUS_SITE_PASSWORD=your_nice_site_password
NICE_CHECKPLUS_FRONTEND_REDIRECT_PATH=your_redirect_path

# =================================
# CORS Configuration
# =================================
GLOBALS_ALLOW_ORIGIN=http://localhost:3000,http://127.0.0.1:3000,http://localhost:8081

# =================================
# Global Configuration
# =================================
GLOBALS_LOCAL_IP=http://localhost
GLOBALS_IP=http://localhost:8080
GLOBALS_PAGE_UNIT=10
GLOBALS_PAGE_SIZE=10
GLOBALS_POSBL_ATCH_FILE_SIZE=5242880

# =================================
# Application Specific
# =================================
APP_LOCKER_FEE=5000
ENROLLMENT_LOCK_TIMEOUT=30000
ENROLLMENT_RETRY_ATTEMPTS=3
ENROLLMENT_RETRY_DELAY=1000
WEBSOCKET_ENABLED=true
WEBSOCKET_HEARTBEAT=30000

# =================================
# Payment Module (KISPG)
# =================================
# 테스트 환경 설정 (운영환경에서는 실제 값으로 변경)
KISPG_URL=https://testapi.kispg.co.kr
KISPG_MID=kistest00m
KISPG_MERCHANT_KEY=your_kispg_merchant_key
```

## 🔧 빠른 설정 가이드

### 1. 로컬 개발환경 설정

```bash
# 1. .env 파일 생성
touch .env

# 2. 위의 내용을 복사하여 .env 파일에 붙여넣기

# 3. 필수 수정 항목
# - SPRING_DATASOURCE_USERNAME: 실제 DB 사용자명
# - SPRING_DATASOURCE_PASSWORD: 실제 DB 비밀번호
# - JWT_SECRET: 보안을 위한 256비트 랜덤 문자열
# - MAIL_USERNAME, MAIL_PASSWORD: 이메일 발송용 계정 정보
```

### 2. 다른 환경 설정

다른 환경(개발서버, 임시운영, 운영)의 설정은 [environment-templates.md](./environment-templates.md)를 참고하세요.

## ⚠️ 중요 사항

### 보안 관련

- **`.env` 파일은 절대 Git에 커밋하지 마세요**
- `JWT_SECRET`은 최소 256비트의 강력한 랜덤 문자열을 사용하세요
- 데이터베이스 계정 정보는 안전하게 관리하세요

### 환경 관리

- 환경별로 적절한 `CORS_ENABLED` 값을 설정하세요:
  - 로컬/개발: `true`
  - 임시운영/운영: `false`
- 운영환경에서는 로깅 레벨을 `WARN` 이상으로 설정하세요

### Git 설정

`.gitignore`에 다음 내용이 포함되어 있는지 확인하세요:

```gitignore
.env
.env.*
!.env.*.template
```

## 📚 관련 문서

- [환경별 설정 템플릿](./environment-templates.md) - 4개 환경별 상세 설정
- [환불 계산 로직](./refund-calculation-logic.md) - 환불 관련 설정 설명
- [환경변수 정리 분석](./env-cleanup-analysis.md) - 환경변수 사용 현황 분석

## 🆘 문제 해결

환경 설정과 관련하여 문제가 발생하면:

1. 환경변수 이름과 값이 정확한지 확인
2. 애플리케이션 시작 로그에서 환경 정보 확인
3. `SPRING_PROFILES_ACTIVE` 값이 올바른지 확인
4. 필수 환경변수가 누락되지 않았는지 확인

## 📊 정리 효과

이번 환경변수 정리를 통해:

- **환경변수 개수**: 60개 → 35개 (42% 감소)
- **설정 복잡도**: 대폭 감소
- **유지보수성**: 향상
- **설정 오류 가능성**: 감소
