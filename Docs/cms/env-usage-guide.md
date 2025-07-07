# 🔧 환경별 .env 파일 사용 가이드

## 📊 환경변수 현황 (정리 완료)

환경변수 정리를 통해 대폭 간소화되었습니다:

- **기존 환경변수**: 60개
- **정리 후**: 35개 (42% 감소)
- **필수 환경변수**: 30개
- **선택적 환경변수**: 5개

### 제거된 환경변수

- S3 관련 설정 (현재 미사용)
- 파일 정책 설정 (현재 미사용)
- 썸네일 설정 (현재 미사용)
- JWT 중복 설정
- 성능 모니터링 설정 (현재 미사용)
- 사용되지 않는 Globals 설정

## 📁 파일 구조 및 네이밍

```
project-root/
├── .env                    # 현재 사용 중인 환경 파일
├── .env.local              # 로컬 개발환경
├── .env.dev                # 개발서버 환경
├── .env.staging            # 임시운영 환경
├── .env.prod               # 운영환경
├── .env.example            # 예시 템플릿 (Git 포함)
└── .gitignore              # .env 파일들 제외 설정
```

## 🎯 1. 환경별 .env 파일 생성

### 로컬 개발환경 (.env.local)

```bash
# 1. 템플릿에서 로컬 환경 파일 생성
cp Docs/cms/env-example.md .env.local

# 2. 필수 항목 수정
# - SPRING_DATASOURCE_USERNAME: 로컬 DB 사용자명
# - SPRING_DATASOURCE_PASSWORD: 로컬 DB 비밀번호
# - JWT_SECRET: 개발용 시크릿 키
# - MAIL_USERNAME, MAIL_PASSWORD: 테스트용 이메일 계정
```

### 개발서버 환경 (.env.dev)

```bash
# 1. 개발서버용 템플릿에서 파일 생성
# environment-templates.md의 개발서버 섹션 복사

# 2. 개발서버 전용 설정
ENVIRONMENT_NAME=dev
CORS_ENABLED=true
LOG_LEVEL=DEBUG
SPRING_DATASOURCE_URL=jdbc:mariadb://dev-db-server:3306/arpina_dev
```

### 임시운영 환경 (.env.staging)

```bash
# 1. 임시운영용 템플릿에서 파일 생성
# environment-templates.md의 임시운영 섹션 복사

# 2. 임시운영 전용 설정
ENVIRONMENT_NAME=staging
CORS_ENABLED=false
LOG_LEVEL=INFO
FILE_STORAGE_TYPE=s3
```

### 운영환경 (.env.prod)

```bash
# 1. 운영용 템플릿에서 파일 생성
# environment-templates.md의 운영 섹션 복사

# 2. 운영 전용 설정
ENVIRONMENT_NAME=prod
CORS_ENABLED=false
LOG_LEVEL=WARN
PROMETHEUS_ENABLED=true
```

## 🔄 2. 환경 전환 방법

### 방법 1: 심볼릭 링크 사용 (권장)

```bash
# 로컬 개발환경으로 전환
ln -sf .env.local .env

# 개발서버로 전환
ln -sf .env.dev .env

# 임시운영으로 전환
ln -sf .env.staging .env

# 운영환경으로 전환
ln -sf .env.prod .env

# 현재 연결된 환경 확인
ls -la .env
```

### 방법 2: 파일 복사 사용

```bash
# 로컬 개발환경으로 전환
cp .env.local .env

# 개발서버로 전환
cp .env.dev .env

# 임시운영으로 전환
cp .env.staging .env

# 운영환경으로 전환
cp .env.prod .env
```

### 방법 3: 스크립트 자동화

```bash
# env-switch.sh 스크립트 생성
#!/bin/bash

case "$1" in
  local)
    ln -sf .env.local .env
    echo "✅ 로컬 개발환경으로 전환됨"
    ;;
  dev)
    ln -sf .env.dev .env
    echo "✅ 개발서버 환경으로 전환됨"
    ;;
  staging)
    ln -sf .env.staging .env
    echo "✅ 임시운영 환경으로 전환됨"
    ;;
  prod)
    ln -sf .env.prod .env
    echo "✅ 운영환경으로 전환됨"
    ;;
  *)
    echo "❌ 사용법: ./env-switch.sh [local|dev|staging|prod]"
    exit 1
    ;;
esac

# 현재 환경 표시
echo "📍 현재 환경: $(readlink .env | sed 's/.env.//')"

# 스크립트 실행 권한 부여
chmod +x env-switch.sh

# 사용 예시
./env-switch.sh local    # 로컬 환경으로 전환
./env-switch.sh dev      # 개발서버로 전환
./env-switch.sh staging  # 임시운영으로 전환
./env-switch.sh prod     # 운영환경으로 전환
```

## 🚀 3. 배포별 사용 방법

### 로컬 개발 시

```bash
# 1. 로컬 환경으로 전환
./env-switch.sh local

# 2. 환경 확인
cat .env | grep ENVIRONMENT_NAME
# 결과: ENVIRONMENT_NAME=local

# 3. 애플리케이션 실행
mvn spring-boot:run
```

### 개발서버 배포 시

```bash
# 1. 개발서버로 전환
./env-switch.sh dev

# 2. 환경 확인
cat .env | grep ENVIRONMENT_NAME
# 결과: ENVIRONMENT_NAME=dev

# 3. 빌드 및 배포
mvn clean package
java -jar target/cms-application.jar
```

### 임시운영 배포 시

```bash
# 1. 임시운영으로 전환
./env-switch.sh staging

# 2. 환경 확인
cat .env | grep ENVIRONMENT_NAME
# 결과: ENVIRONMENT_NAME=staging

# 3. 운영급 빌드
mvn clean package -Pprod
java -jar target/cms-application.jar
```

### 운영환경 배포 시

```bash
# 1. 운영환경으로 전환
./env-switch.sh prod

# 2. 환경 확인
cat .env | grep ENVIRONMENT_NAME
# 결과: ENVIRONMENT_NAME=prod

# 3. 운영 배포
mvn clean package -Pprod
java -jar target/cms-application.jar
```

## 🔐 4. 보안 설정

### .gitignore 설정

```bash
# .gitignore에 추가
echo "# Environment files" >> .gitignore
echo ".env" >> .gitignore
echo ".env.local" >> .gitignore
echo ".env.dev" >> .gitignore
echo ".env.staging" >> .gitignore
echo ".env.prod" >> .gitignore
echo "" >> .gitignore
echo "# Keep only example template" >> .gitignore
echo "!.env.example" >> .gitignore
```

### 민감 정보 관리

```bash
# 1. 민감 정보는 별도 보안 저장소에 관리
# 2. 각 서버에서 직접 .env 파일 생성
# 3. Git에는 절대 커밋하지 않음

# 서버별 보안 설정 예시
# 개발서버
scp .env.dev user@dev-server:/app/.env

# 임시운영
scp .env.staging user@staging-server:/app/.env

# 운영서버
scp .env.prod user@prod-server:/app/.env
```

## 📊 5. 환경 확인 방법

### 애플리케이션 시작 시 로그 확인

```bash
# 로그에서 환경 정보 확인
tail -f log/backend.log | grep "Environment"

# 예시 출력
2024-01-15 10:30:15 INFO  - Environment: local, CORS: enabled
2024-01-15 10:30:15 INFO  - Database URL: jdbc:mariadb://localhost:3306/arpina_local
2024-01-15 10:30:15 INFO  - JWT Access Token Validity: 86400000ms (1 day)
2024-01-15 10:30:15 INFO  - JWT Refresh Token Validity: 2592000000ms (30 days)
```

### Actuator를 통한 환경 확인

```bash
# 헬스 체크
curl http://localhost:8080/actuator/health

# 환경 정보 확인 (개발환경에서만)
curl http://localhost:8080/actuator/env | jq '.propertySources[] | select(.name=="dotenvProperties")'
```

### 환경변수 직접 확인

```bash
# 현재 환경 이름 확인
echo $ENVIRONMENT_NAME

# 데이터베이스 URL 확인 (비밀번호 제외)
echo $SPRING_DATASOURCE_URL

# CORS 설정 확인
echo $CORS_ENABLED
```

## 🔧 6. 문제 해결

### 환경 전환이 안 될 때

```bash
# 1. 심볼릭 링크 확인
ls -la .env

# 2. 파일 존재 여부 확인
ls -la .env.*

# 3. 강제로 다시 연결
rm .env
ln -sf .env.local .env
```

### 환경변수가 로드되지 않을 때

```bash
# 1. .env 파일 문법 확인
cat .env | grep -E "^[A-Z_]+=.*"

# 2. 공백이나 특수문자 확인
cat -A .env | head -10

# 3. 애플리케이션 재시작
./mvnw spring-boot:stop
./mvnw spring-boot:run
```

### 데이터베이스 연결 실패 시

```bash
# 1. DB 접속 정보 확인
echo "URL: $SPRING_DATASOURCE_URL"
echo "Username: $SPRING_DATASOURCE_USERNAME"
# 비밀번호는 보안상 출력하지 않음

# 2. 네트워크 연결 확인
ping db-server-hostname

# 3. DB 서버 포트 확인
telnet db-server-hostname 3306
```

## 📋 7. 체크리스트

### 환경 설정 전 체크리스트

- [ ] .gitignore에 .env 파일들 제외 설정
- [ ] 각 환경별 .env 파일 생성
- [ ] 민감 정보 (DB 비밀번호, JWT 시크릿) 설정
- [ ] 환경별 특화 설정 (CORS, 로깅 등) 확인
- [ ] env-switch.sh 스크립트 생성 및 권한 설정

### 배포 전 체크리스트

- [ ] 올바른 환경으로 전환했는지 확인
- [ ] 환경변수가 제대로 로드되는지 확인
- [ ] 데이터베이스 연결 테스트
- [ ] JWT 토큰 생성/검증 테스트
- [ ] 로그 레벨 및 출력 확인

### 보안 체크리스트

- [ ] .env 파일이 Git에 커밋되지 않았는지 확인
- [ ] 운영환경에서 디버그 모드 비활성화
- [ ] 민감 정보가 로그에 출력되지 않는지 확인
- [ ] 각 환경별로 다른 JWT 시크릿 키 사용
- [ ] 운영환경에서 CORS 비활성화

---

## 🎯 빠른 시작 가이드

```bash
# 1. 환경 전환 스크립트 생성
cat > env-switch.sh << 'EOF'
#!/bin/bash
case "$1" in
  local) ln -sf .env.local .env && echo "✅ 로컬 환경" ;;
  dev) ln -sf .env.dev .env && echo "✅ 개발서버" ;;
  staging) ln -sf .env.staging .env && echo "✅ 임시운영" ;;
  prod) ln -sf .env.prod .env && echo "✅ 운영환경" ;;
  *) echo "❌ 사용법: ./env-switch.sh [local|dev|staging|prod]" ;;
esac
EOF

chmod +x env-switch.sh

# 2. 로컬 환경 파일 생성
cp Docs/cms/env-example.md .env.local

# 3. 로컬 환경으로 전환
./env-switch.sh local

# 4. 필수 설정 수정 후 실행
mvn spring-boot:run
```

이제 환경별로 손쉽게 전환하며 개발할 수 있습니다! 🚀
