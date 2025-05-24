# 배포 서버 초기 세팅 체크리스트 (Nginx, Spring Boot 이중화, Swagger)

**주의: 아래 체크리스트는 SSL/TLS 및 HTTPS 리버스 프록시 설정을 개발 완료 후로 미루는 경우를 가정합니다. 이로 인해 보안 리스크가 발생하며, 개발 환경과 운영 환경 간 불일치로 인한 문제가 발생할 수 있습니다. 가급적 초기부터 HTTPS 환경을 구성하는 것을 권장합니다.**

## 📋 시스템 요구사항 및 변경사항

### 필수 소프트웨어

- [ ] **Java 8 이상** (Spring Boot 애플리케이션 실행)
- [ ] **Maven 3.6 이상** (빌드 도구)
- [ ] **MariaDB 10.3 이상** (주 데이터베이스)
- [ ] **Nginx** (웹서버/리버스 프록시)

### 제거된 요구사항 (v2.2 이후)

- ❌ ~~Redis 서버~~ - **더 이상 필요없음**
- ❌ ~~Redis Session 저장소~~ - **기본 서블릿 세션 관리로 변경**
- ❌ ~~Read Replica DB~~ - **단일 DB 인스턴스 사용**

### 주요 변경사항 (2025-05-23)

- ✅ **세션 관리**: Redis → 기본 서블릿 세션
- ✅ **캐싱**: Redis → 애플리케이션 레벨 캐싱
- ✅ **분산 환경**: 제한적 (세션 공유 불가)
- ✅ **성능**: HikariCP 최적화로 보완

## 1. 기본 서버 환경 설정

- [ ] **서버 접속 및 기본 업데이트**
  - [ ] 서버 접속 (SSH)
  - [ ] `sudo apt update && sudo apt upgrade -y`
- [ ] **시간 동기화 설정**
  - [ ] `sudo timedatectl set-timezone Asia/Seoul` (또는 해당 지역)
  - [ ] `chrony` 또는 `ntp` 설치 및 설정 (필요시)
- [ ] **방화벽 설정 (UFW)**
  - [ ] `sudo ufw allow ssh` (또는 특정 SSH 포트)
  - [ ] `sudo ufw allow http` (80/tcp) # 초기에는 HTTP만 허용
  # - [ ] `sudo ufw allow https` (443/tcp) # 개발 완료 후 HTTPS 설정 시 추가
  - [ ] `sudo ufw enable`
  - [ ] `sudo ufw status verbose` (설정 확인)
  - [ ] (참고) Spring Boot 애플리케이션 포트(예: 8081, 8082)는 외부에서 직접 접근하지 않도록 차단 (Nginx를 통해서만 접근)

## 2. 필수 패키지 설치

- [ ] **Nginx 설치**
  - [ ] `sudo apt install nginx -y`
  - [ ] `sudo systemctl start nginx`
  - [ ] `sudo systemctl enable nginx`
- [ ] **Java (JDK/JRE) 설치** (Spring Boot 애플리케이션 실행용)
  - [ ] `sudo apt install openjdk-17-jdk -y` (또는 애플리케이션 호환 버전)
  - [ ] `java -version` (설치 확인)

## 3. 도메인 및 DNS 설정

- [ ] **도메인 준비** (예: `example.com`)
- [ ] **DNS 레코드 설정**
  - [ ] A 레코드: `example.com` → `<SERVER_IP>`

## 4. Spring Boot 애플리케이션 배포 및 실행 (이중화 구성 - HTTP)

- [ ] **애플리케이션 빌드**
  - [ ] 프로젝트 루트에서 `./mvnw clean package` 또는 `gradle clean build` (JAR 파일 생성)
- [ ] **애플리케이션 파일 서버 업로드**
  - [ ] 생성된 JAR 파일 (예: `app.jar`)을 서버의 적절한 위치에 업로드 (예: `/home/ubuntu/app1/app.jar`, `/home/ubuntu/app2/app.jar`)
- [ ] **애플리케이션 실행 (인스턴스 1)**
  - [ ] `java -jar /home/ubuntu/app1/app.jar --server.port=8081 --spring.profiles.active=prod > /home/ubuntu/app1/app.log 2>&1 &`
  - [ ] (참고) `server.port`를 각 인스턴스마다 다르게 설정 (예: 8081, 8082)
  - [ ] (참고) `spring.profiles.active=prod`로 운영 프로파일 지정
  - [ ] (참고) 로그 파일 경로 지정
- [ ] **애플리케이션 실행 (인스턴스 2)**
  - [ ] `java -jar /home/ubuntu/app2/app.jar --server.port=8082 --spring.profiles.active=prod > /home/ubuntu/app2/app.log 2>&1 &`
- [ ] **systemd 서비스 등록 (애플리케이션 자동 시작 및 관리)** - 각 인스턴스에 대해

  - [ ] `/etc/systemd/system/springapp1.service` 파일 생성

    ```ini
    [Unit]
    Description=Spring Boot App Instance 1
    After=network.target

    [Service]
    User=ubuntu # 애플리케이션 실행 유저
    WorkingDirectory=/home/ubuntu/app1
    ExecStart=/usr/bin/java -jar app.jar --server.port=8081 --spring.profiles.active=prod
    SuccessExitStatus=143
    Restart=on-failure
    RestartSec=10

    [Install]
    WantedBy=multi-user.target
    ```

  - [ ] `/etc/systemd/system/springapp2.service` 파일 생성 (유사하게, 포트 및 경로 수정)
  - [ ] `sudo systemctl daemon-reload`
  - [ ] `sudo systemctl enable springapp1.service`
  - [ ] `sudo systemctl start springapp1.service`
  - [ ] `sudo systemctl status springapp1.service` (상태 확인)
  - [ ] (springapp2.service에 대해서도 동일하게 진행)

## 5. Nginx 설정 (HTTP 리버스 프록시, 로드 밸런싱, Swagger 연동)

- [ ] **Nginx 설정 파일 열기/생성**
  - [ ] `sudo nano /etc/nginx/sites-available/example.com`
- [ ] **Upstream 설정 (로드 밸런싱)**
  ```nginx
  upstream tomcat_servers {
      server 127.0.0.1:8081; # Spring Boot 인스턴스 1
      server 127.0.0.1:8082; # Spring Boot 인스턴스 2
  }
  ```
- [ ] **Server 블록 설정 (HTTP 전용)**

  ```nginx
  server {
      listen 80;
      server_name example.com www.example.com; # 사용하는 도메인

      location / {
          proxy_pass http://tomcat_servers; # upstream 그룹명
          proxy_set_header Host $host;
          proxy_set_header X-Real-IP $remote_addr;
          proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
          proxy_set_header X-Forwarded-Proto http; # 초기에는 HTTP로 설정
          proxy_http_version 1.1;
          proxy_set_header Upgrade $http_upgrade;
          proxy_set_header Connection "upgrade";
          proxy_read_timeout 300s;
          proxy_connect_timeout 75s;
      }

      access_log /var/log/nginx/example.com.access.log;
      error_log /var/log/nginx/example.com.error.log;
  }
  ```

- [ ] **Nginx 설정 파일 심볼릭 링크 생성**
  - [ ] `sudo ln -s /etc/nginx/sites-available/example.com /etc/nginx/sites-enabled/`
  - [ ] (주의) 기존 `default` 설정과 충돌하지 않도록 `default` 링크 제거 또는 비활성화: `sudo rm /etc/nginx/sites-enabled/default`
- [ ] **Nginx 설정 테스트**
  - [ ] `sudo nginx -t`
- [ ] **Nginx 서비스 재시작/리로드**
  - [ ] `sudo systemctl restart nginx`

## 6. 테스트 (HTTP)

- [ ] 웹 브라우저에서 `http://example.com` 접속하여 애플리케이션 동작 확인
- [ ] 웹 브라우저에서 `http://example.com/swagger-ui/index.html` (또는 `/swagger-ui.html`) 접속하여 Swagger UI 정상 동작 확인
- [ ] 이중화 확인 (한쪽 인스턴스 중지 시 서비스 지속 여부)

---

## 7. 프론트엔드 애플리케이션 배포 및 Nginx 연동

- [ ] **사전 준비:**
  - [ ] 프론트엔드 소스 코드 준비 완료
  - [ ] (서버에서 빌드 시) 서버에 Node.js 및 npm/yarn 설치
- [ ] **프론트엔드 애플리케이션 빌드 (필요시):**
  - [ ] 프론트엔드 프로젝트 디렉토리로 이동 (예: `cd /path/to/frontend-project`)
  - [ ] 의존성 설치: `npm install` 또는 `yarn install`
  - [ ] 프로덕션용 빌드: `npm run build` 또는 `yarn build`
  - [ ] 빌드 결과물 디렉토리 확인 (예: `dist`, `build`)
- [ ] **프론트엔드 파일 서버 배포:**
  - [ ] 프론트엔드용 루트 디렉토리 생성: `sudo mkdir -p /var/www/frontend_app`
  - [ ] (선택) 디렉토리 권한 설정: `sudo chown -R $USER:$USER /var/www/frontend_app` (웹서버 실행 유저 또는 실제 $USER로 변경)
  - [ ] 빌드된 프론트엔드 파일 (또는 정적 파일)을 `/var/www/frontend_app` 디렉토리로 업로드/복사
- [ ] **Nginx 설정 업데이트 (프론트엔드 및 백엔드 연동):**

  - [ ] Nginx 설정 파일 열기 (예: `sudo nano /etc/nginx/sites-available/default` 또는 사용 중인 사이트 파일)
  - [ ] **`server` 블록 수정:**

    - 프론트엔드는 루트 (`/`)에서 제공하고, 백엔드 API, Swagger UI 등 특정 경로는 백엔드(`tomcat_servers`)로 프록시하도록 구성합니다.
    - **주의:** 백엔드로 프록시해야 하는 모든 경로 접두사 (예: `/api/`, `/swagger-ui/`, `/login`, `/logout`, `/cmm/`, `/resources/`, `/static/` 등)를 정확히 파악하여 아래 `location` 블록 예시에 추가하거나 수정해야 합니다. 누락된 백엔드 경로는 프론트엔드의 `index.html`로 처리되어 오류가 발생할 수 있습니다.

    ```nginx
    upstream tomcat_servers {
        server 127.0.0.1:8081; # Spring Boot 인스턴스 1
        server 127.0.0.1:8082; # Spring Boot 인스턴스 2
        # least_conn; # 또는 다른 로드밸런싱 방식 선택 가능
    }

    server {
        listen 80 default_server; # 또는 listen 80;
        server_name _; # 또는 추후 사용할 도메인 (예: example.com www.example.com)

        # 백엔드 애플리케이션으로 프록시할 경로들
        # 애플리케이션의 실제 백엔드 경로 접두사를 여기에 명시합니다.
        # 예시: /api/, /swagger-ui/, /login, /logout, /cmm/, /resources/, /static/ 등
        # 정확한 경로 패턴을 위해 정규표현식을 사용하거나 각 경로를 개별 location 블록으로 정의할 수 있습니다.
        location ~ ^/(api|swagger-ui|login|logout|cmm|resources|static|files|uploads)(/.*)?$ { # 주요 백엔드 경로 접두사 예시
            proxy_pass http://tomcat_servers; # Spring Boot 애플리케이션 (context-path가 '/'로 가정)
            proxy_set_header Host $host;
            proxy_set_header X-Real-IP $remote_addr;
            proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
            proxy_set_header X-Forwarded-Proto http;
            proxy_http_version 1.1;
            proxy_set_header Upgrade $http_upgrade;
            proxy_set_header Connection "upgrade";
            proxy_read_timeout 300s;
            proxy_connect_timeout 75s;
        }

        # 프론트엔드 정적 파일 제공 (위 location 블록에 해당하지 않는 모든 요청 처리)
        location / {
            root /var/www/frontend_app; # 프론트엔드 파일이 위치한 디렉토리
            index index.html index.htm;
            try_files $uri $uri/ /index.html; # SPA 라우팅 지원 (HTML5 history mode)
        }

        # 로그 설정 (기존 Nginx 설정을 따르거나, 사이트별 로그 파일 지정)
        # access_log /var/log/nginx/default.access.log;
        # error_log /var/log/nginx/default.error.log;
    }
    ```

  - [ ] (참고) `proxy_pass http://tomcat_servers;` 설정 시, Spring Boot 애플리케이션은 Nginx `location` 경로를 포함한 전체 URI (예: `/api/some/endpoint`, `/swagger-ui/index.html`)로 요청을 받게 됩니다. Spring Boot의 `server.servlet.context-path`가 `/`인 경우 이 방식이 적합합니다.
  - [ ] Nginx 설정 테스트: `sudo nginx -t`
  - [ ] Nginx 서비스 재시작: `sudo systemctl restart nginx`

- [ ] **프론트엔드 테스트:**
  - [ ] 웹 브라우저에서 서버 IP (예: `http://<SERVER_IP>/`) 접속하여 프론트엔드 UI 확인
  - [ ] 프론트엔드 기능 동작 및 백엔드 API 연동 확인 (개발자 도구 네트워크 탭 확인)
  - [ ] Swagger UI 접속 확인: `http://<SERVER_IP>/swagger-ui/index.html` (또는 Spring Boot에 설정된 Swagger 경로)

---

## (개발 완료 후 진행) 8. SSL/TLS 인증서 설정 및 Nginx HTTPS 구성 (외부 발급 인증서 사용)

- [ ] **외부 서비스 통해 SSL 인증서 발급**
  - [ ] 인증서 파일 (`fullchain.pem` 또는 유사 파일) 및 개인 키 파일 (`privkey.pem` 또는 유사 파일) 확보
- [ ] **서버에 인증서 및 개인 키 업로드**
  - [ ] 예: `sudo mkdir -p /etc/nginx/ssl/your_domain`
  - [ ] 예: `sudo cp fullchain.pem /etc/nginx/ssl/your_domain/`
  - [ ] 예: `sudo cp privkey.pem /etc/nginx/ssl/your_domain/`
  - [ ] (참고) 보안을 위해 개인 키 파일은 `root` 또는 `nginx` 사용자만 읽을 수 있도록 권한 설정 (`sudo chmod 600 /etc/nginx/ssl/your_domain/privkey.pem`)
- [ ] **방화벽에 HTTPS 포트 추가**
  - [ ] `sudo ufw allow https` (또는 `sudo ufw allow 443/tcp`)
- [ ] **Nginx 설정 파일 수정** (예: `/etc/nginx/sites-available/default` 또는 사용하는 사이트 파일)

  - [ ] 기존 HTTP `server` 블록 수정 또는 신규 `server` 블록 추가 (HTTPS 용)

  ```nginx
  # HTTP 요청을 HTTPS로 리다이렉션 (기존 listen 80 서버 블록 내에 추가하거나, 별도 서버 블록으로 구성)
  server {
      listen 80 default_server; # 또는 listen 80;
      server_name _; # 또는 your_domain.com www.your_domain.com;
      return 301 https://$host$request_uri;
  }

  server {
      listen 443 ssl http2 default_server; # 또는 listen 443 ssl http2;
      server_name _; # 또는 your_domain.com www.your_domain.com;

      # SSL 인증서 경로 (실제 파일 경로로 수정)
      ssl_certificate /etc/nginx/ssl/your_domain/fullchain.pem;
      ssl_certificate_key /etc/nginx/ssl/your_domain/privkey.pem;

      # 기타 SSL 관련 권장 설정 (필요시 추가 및 Docs/reverse-proxy.md 참고)
      # ssl_protocols TLSv1.2 TLSv1.3;
      # ssl_prefer_server_ciphers on;
      # ssl_ciphers 'ECDHE-ECDSA-AES256-GCM-SHA384:ECDHE-RSA-AES256-GCM-SHA384:ECDHE-ECDSA-CHACHA20-POLY1305:ECDHE-RSA-CHACHA20-POLY1305:ECDHE-ECDSA-AES128-GCM-SHA256:ECDHE-RSA-AES128-GCM-SHA256';
      # ssl_session_timeout 1d;
      # ssl_session_cache shared:SSL:10m; # 약 40,000 세션
      # ssl_session_tickets off;
      # ssl_stapling on; # OCSP Stapling (인증기관에서 지원 시)
      # ssl_stapling_verify on;
      # resolver 8.8.8.8 8.8.4.4 valid=300s; # OCSP Stapling용 DNS resolver (Google DNS 예시)
      # resolver_timeout 5s;

      # HSTS 헤더 (6개월간 HTTPS 강제, 서브도메인 포함, preload 목록 등재 가능)
      # add_header Strict-Transport-Security "max-age=15768000; includeSubDomains; preload" always;

      # 백엔드 애플리케이션으로 프록시할 경로들 (HTTP 설정과 동일하게 구성)
      location ~ ^/(api|swagger-ui|login|logout|cmm|resources|static|files|uploads)(/.*)?$ {
          proxy_pass http://tomcat_servers;
          proxy_set_header Host $host;
          proxy_set_header X-Real-IP $remote_addr;
          proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
          proxy_set_header X-Forwarded-Proto https; # HTTPS로 변경
          proxy_http_version 1.1;
          proxy_set_header Upgrade $http_upgrade;
          proxy_set_header Connection "upgrade";
          proxy_read_timeout 300s;
          proxy_connect_timeout 75s;
      }

      # 프론트엔드 정적 파일 제공 (HTTP 설정과 동일하게 구성)
      location / {
          root /var/www/frontend_app;
          index index.html index.htm;
          try_files $uri $uri/ /index.html;
      }

      # access_log /var/log/nginx/default.ssl.access.log; # 사이트별 로그 파일 지정
      # error_log /var/log/nginx/default.ssl.error.log;
  }
  ```

  - [ ] `X-Forwarded-Proto` 헤더가 `https`로 설정되었는지 확인

- [ ] **Nginx 설정 테스트**
  - [ ] `sudo nginx -t`
- [ ] **Nginx 서비스 재시작/리로드**
  - [ ] `sudo systemctl restart nginx`
- [ ] **인증서 갱신 주기 확인 및 수동 갱신 절차 숙지** (외부 서비스 가이드라인 따름)

## (개발 완료 후 진행) 9. Spring Boot 애플리케이션 설정 확인 (X-Forwarded 헤더 처리)

- [ ] `application.properties` 또는 `application.yml` 파일 확인
  - `server.forward-headers-strategy=native` (또는 `framework`) 설정이 되어있는지 확인. (Spring Boot 2.2 이상 기본값은 `native`)
  - 이 설정은 Nginx에서 `X-Forwarded-Proto: https` 헤더를 전송하면 Spring Boot가 이를 인지하여 `request.isSecure()`, 리다이렉션 URL 생성 등이 HTTPS 환경 기준으로 동작하도록 합니다.

## (개발 완료 후 진행) 10. 테스트 (HTTPS)

- [ ] 웹 브라우저에서 `https://your_domain.com` (또는 `https://<SERVER_IP>`) 접속하여 애플리케이션 동작 확인 (HTTP 접속 시 HTTPS 리다이렉션 포함)
- [ ] 웹 브라우저에서 `https://your_domain.com/swagger-ui/index.html` 접속하여 Swagger UI 정상 동작 확인
- [ ] 이중화 확인 (한쪽 인스턴스 중지 시 서비스 지속 여부)

---

## 11. 모니터링 및 로깅 (공통)

- [ ] **Nginx 로그 확인**
  - [ ] `/var/log/nginx/access.log` (또는 사이트별 로그: `/var/log/nginx/default.access.log`)
  - [ ] `/var/log/nginx/error.log` (또는 사이트별 로그: `/var/log/nginx/default.error.log`)
  - [ ] (HTTPS 사용 시) `/var/log/nginx/default.ssl.access.log` 등
- [ ] **Spring Boot 애플리케이션 로그 확인**
  - [ ] systemd 서비스 로그: `sudo journalctl -u springapp1.service -f`, `sudo journalctl -u springapp2.service -f`
  - [ ] 또는 파일 로그: `/web/webRoot/app1_logs/app.log`, `/web/webRoot/app2_logs/app.log` (systemd 설정에 따라 다름)
- [ ] (선택) 로그 로테이션 설정 (`logrotate`)
- [ ] (선택) 모니터링 도구 설정 (Prometheus, Grafana 등)

## 12. 보안 강화 (HTTPS 적용 후 추가 권장 사항 - `Docs/reverse-proxy.md` 참고)

- [ ] **HSTS (Strict-Transport-Security)** 헤더 강화 (`preload` 옵션 등, Nginx 설정에서 확인)
- [ ] **TLS 버전 및 Cipher Suite** 최신 권장값 사용 (Nginx 설정에서 `ssl_protocols`, `ssl_ciphers` 확인 및 조정)
- [ ] 기타 보안 헤더 적용 (`X-Frame-Options`, `X-Content-Type-Options`, `Referrer-Policy`, `Permissions-Policy` 등)
- [ ] SSH 접근 IP 제한 및 포트 변경 고려
- [ ] 정기적인 시스템 및 애플리케이션 패치 및 보안 업데이트
- [ ] 불필요한 서비스 및 포트 비활성화

## 13. 백업 및 복구 (공통)

- [ ] **Nginx 설정 백업** (`/etc/nginx/` 디렉토리 전체 또는 주요 설정 파일)
- [ ] **SSL 인증서 및 개인 키 백업** (외부에서 발급받은 파일)
- [ ] **Spring Boot 애플리케이션 JAR 파일 및 설정 백업** (`/web/webRoot/handy-new-cms.jar`, `.env` 파일 등)
- [ ] **데이터베이스 백업** (주기적인 자동 백업 설정 권장)
- [ ] (선택) 서버 전체 스냅샷 (클라우드 환경 등)
- [ ] 백업 및 복구 절차 문서화 및 정기 테스트

---

**참고:**

- 위 경로는 예시이며, 실제 환경에 맞게 수정해야 합니다.
- `your_domain.com`은 실제 사용하는 도메인으로 변경해야 합니다. (도메인 설정 단계에서)
- Spring Boot 애플리케이션의 포트, 경로, 실행 유저 등은 환경에 맞게 조정합니다.
- 이 체크리스트는 기본적인 가이드이며, 운영 환경의 특성에 따라 추가적인 설정이 필요할 수 있습니다.
- 프론트엔드 및 백엔드 경로 구성은 애플리케이션의 실제 구조에 따라 면밀히 검토하고 Nginx `location` 블록을 조정해야 합니다.
