# 단일 서버 Reverse Proxy(TLS 종료) 구성 개발 문서

### 1. 목적 · 범위

- **목적** : 단일 클라우드 서버(예: Ubuntu 22.04 LTS)에서 **Next.js 프론트엔드**와 **Spring Boot 백엔드**를 운영하면서, 외부 HTTPS 요청을 프록시(Nginx)에서 한 번에 종료(TLS termination)해 관리 · 보안 · 운영 편의성을 확보한다.
- **범위** : 도메인 · 서브도메인 관리, SSL 인증서 발급·자동 갱신, 리버스 프록시 설정, 애플리케이션 포워딩, 운영 체크리스트까지 포함.

---

### 2. 아키텍처

```
┌─────────────┐    443/TCP (HTTPS)
│   Client    │ ─────────────────────► ┌──────────────┐
└─────────────┘                         │   Nginx      │ 80 → 443 리다이렉트
                                        │  (TLS End)  │
                                        │  /api  /    │
                                        └────┬────────┘
                           127.0.0.1:8080 │   │ 127.0.0.1:3000
                                          │   │
                     ┌────────────────────┘   └───────────────────┐
                     │                                            │
          ┌──────────────────┐                        ┌──────────────────┐
          │  Spring Boot     │                        │     Next.js      │
          │   (`server.jar`) │                        │  (`next start`)  │
          └──────────────────┘                        └──────────────────┘

```

> 기본 도메인 example.com → Next.js백엔드 API example.com/api/\*\* → Spring Boot
> (필요 시 api.example.com 서브도메인으로 분리 가능, 설정 5-2 참고)

---

### 3. 포트·방화벽 설계

| 계층 | 서비스      | 프로세스 포트 | 외부 오픈          | 설명                              |
| ---- | ----------- | ------------- | ------------------ | --------------------------------- |
| Edge | Nginx       | 80, 443       | **Yes**            | 80 → 443 리다이렉트, 443 TLS 종료 |
| App  | Next.js     | 3000          | No                 | 내부 loopback only                |
| App  | Spring Boot | 8080          | No                 | 내부 loopback only                |
| OS   | SSH         | 22            | Yes (IP 제한 권장) | 운영·배포 접속                    |

- UFW 예시

  ```bash
  sudo ufw allow 22/tcp
  sudo ufw allow 80/tcp
  sudo ufw allow 443/tcp
  sudo ufw enable

  ```

---

### 4. SSL /TLS 인증서 발급 · 자동 갱신

1. **Nginx + Certbot** 설치

   ```bash
   sudo apt update
   sudo apt install nginx certbot python3-certbot-nginx

   ```

2. **도메인 레코드**

   ```
   A  example.com  →  <SERVER_IP>

   ```

3. **인증서 발급 + Nginx 자동 구성**

   ```bash
   sudo certbot --nginx -d example.com
   # 여러 도메인(서브도메인) 시: -d example.com -d api.example.com

   ```

4. **자동 갱신 확인**

   시스템 타이머 `/lib/systemd/system/certbot.timer` 가 기본 활성화.

   수동 테스트: `sudo certbot renew --dry-run`

---

### 5. Nginx 설정

### 5-1. 경로 기반 프록시 (`/api`)

`/etc/nginx/sites-available/example.com`

```
server {
    listen 80;
    server_name example.com;
    return 301 https://$host$request_uri;   # HTTP → HTTPS
}

server {
    listen 443 ssl http2;
    server_name example.com;

    ssl_certificate     /etc/letsencrypt/live/example.com/fullchain.pem;
    ssl_certificate_key /etc/letsencrypt/live/example.com/privkey.pem;
    add_header Strict-Transport-Security "max-age=63072000" always;

    # 백엔드 API
    location /api/ {
        proxy_pass         http://127.0.0.1:8080/;
        proxy_set_header   Host $host;
        proxy_set_header   X-Real-IP $remote_addr;
        proxy_set_header   X-Forwarded-Proto https;
        proxy_http_version 1.1;
    }

    # 프론트 SSR
    location / {
        proxy_pass         http://127.0.0.1:3000;
        proxy_set_header   Host $host;
        proxy_set_header   X-Real-IP $remote_addr;
        proxy_set_header   X-Forwarded-Proto https;
    }
}

```

```bash
sudo ln -s /etc/nginx/sites-available/example.com /etc/nginx/sites-enabled/
sudo nginx -t && sudo systemctl reload nginx

```

### 5-2. 서브도메인 분리(선택)

- `example.com` → Next.js
- `api.example.com` → Spring Boot
  (서버블록 두 개 + `certbot --nginx -d example.com -d api.example.com`)

---

### 6. Spring Boot 애플리케이션 설정

`application.properties`

```
server.port=8080
server.forward-headers-strategy=native   # X-Forwarded-* 헤더 신뢰
# ssl.* 옵션은 OFF (프록시에서 종료)

```

- JAR 배포 예시

  ```bash
  java -jar ./app.jar --spring.profiles.active=prod &

  ```

---

### 7. Next.js 애플리케이션 설정

- **Prod 빌드**

  ```bash
  npm run build
  npm run start -p 3000 &

  ```

- **API 호출 경로**

  ```
  const api = process.env.NEXT_PUBLIC_API_BASE ?? '/api';
  fetch(`${api}/users`);

  ```

  > 프론트와 백엔드를 동일 Origin이나 절대 경로 /api로 맞춰 쿠키·CORS 이슈 제거.

---

### 8. 배포 시나리오

### 8-1. **Bare-Metal/VM 서비스** (위 예시)

- `systemd` 유닛으로 두 애플리케이션을 데몬화
  `/etc/systemd/system/next.service`, `/etc/systemd/system/spring.service`

### 8-2. **Docker Compose** (옵션)

```yaml
services:
  nginx:
    image: nginx:1.25
    ports: ["80:80", "443:443"]
    volumes:
      - ./nginx/conf.d:/etc/nginx/conf.d
      - letsencrypt:/etc/letsencrypt
    depends_on: [front, back]

  front:
    image: node:20
    command: sh -c "npm install && npm run build && npm run start -p 3000"
    working_dir: /app
    volumes: ["./front:/app"]
    expose: ["3000"]

  back:
    image: eclipse-temurin:21-jre
    command: ["java", "-jar", "app.jar"]
    working_dir: /app
    volumes: ["./back/target/app.jar:/app/app.jar"]
    expose: ["8080"]

volumes:
  letsencrypt:
```

- Nginx 컨테이너에 Certbot 추가 또는 외부 cron 컨테이너 사용.

---

### 9. 운영·보안 체크리스트

| 항목                     | 권장값                                                 |
| ------------------------ | ------------------------------------------------------ |
| **HSTS**                 | `max-age ≥ 31536000; includeSubDomains; preload`       |
| **TLS 버전**             | 1.2 이상만 허용                                        |
| **Cipher Suite**         | `ssl_prefer_server_ciphers on;`                        |
| **인증서 갱신 모니터링** | `certbot renew --dry-run` 주 1회 cron                  |
| **로그 관리**            | `/var/log/nginx/access.log` → Filebeat/CloudWatch 전송 |
| **프로세스 헬스체크**    | systemd `Restart=on-failure`, Prometheus exporter      |
| **백엔드 타임아웃**      | `proxy_read_timeout 60s;` (대기 API 때 조정)           |
| **방화벽**               | 3000·8080 외부 차단, SSH IP 화이트리스트               |
| **배포 자동화**          | GitHub Actions → SSH or Docker registry deploy         |

---

### 10. 장애 대응 Quick Sheet

| 증상               | 원인 추정              | 대응                                                   |
| ------------------ | ---------------------- | ------------------------------------------------------ |
| 502 Bad Gateway    | 앱 다운 / 포트 변경    | `systemctl status spring.service`, `next.service` 확인 |
| 404 /api 경로      | Nginx location 오탈자  | `sudo nginx -t` → 재로드                               |
| 인증서 만료        | 갱신 실패              | `sudo certbot renew --force-renewal`                   |
| URL `//api//` 중복 | proxy_pass 뒤 `/` 누락 | `proxy_pass http://127.0.0.1:8080/;` 슬래시 체크       |

---

### 11. 유지보수 · 갱신 주기

| 주기         | 작업                                                |
| ------------ | --------------------------------------------------- |
| **월 1회**   | Nginx & Certbot 업데이트, `certbot renew --dry-run` |
| **분기 1회** | OWASP Dependency-Check(JAR)·npm `npm audit fix`     |
| **반기 1회** | TLS 스캐너(SSL Labs) 재점검, HSTS preload 목록 확인 |
| **연간**     | 주요 LTS 버전 마이그레이션 검토 (Node, JDK, Ubuntu) |

---

## 결론

- **Nginx 단일 TLS 종료**는 인증서·포트 관리가 한곳에 집중돼 **초기 구축과 장기 운영 비용 모두 최소화**된다.
- 내부 애플리케이션은 loopback 환경에 격리해 공격 면적을 축소한다.
- 향후 다중 서버 스케일링 시에도 동일 패턴(프록시 → 내부 HTTP)으로 부드럽게 확장 가능하다.

> 추가로 Docker Compose 템플릿, systemd 유닛 파일, 또는 CI/CD 파이프라인 예시가 필요하면 말씀 주세요!
