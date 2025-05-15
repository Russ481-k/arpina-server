## 🗓️ Schedule Calendar API v1 — **File-Integrated Final Spec** (2025-05-01)

> Base URL /cms | Auth Authorization: Bearer {JWT}
>
> - **조회**(`GET`) : _비로그인_ 허용
> - **등록·수정·삭제** : `STAFF / ADMIN` 이상만 허용

---

### 1. Terminology & UX

| 개념             | UI 표현 · 예시                         | 비고                          |
| ---------------- | -------------------------------------- | ----------------------------- |
| **Schedule**     | 하나의 일정 공고 (날짜 + 시간 + 제목)  | 예) _점핑클레이가 찾아옵니다_ |
| **Status**       | `UPCOMING / ONGOING / ENDED / HIDDEN`  | 서버 계산 필드                |
| 캘린더 월뷰 Cell | 날짜에 Schedule 있으면 **박스** 렌더링 | 회색=기타, 파랑=중요(선택)    |
| 상세 팝업        | 날짜 헤더 + Schedule 리스트(스크롤)    | 다건 가능                     |

_Status 계산_

```
UPCOMING : now < startDateTime
ONGOING  : startDateTime ≤ now < endDateTime
ENDED    : now ≥ endDateTime
HIDDEN   : displayYn = false   (관리자만 조회)

```

---

### 2. Endpoints

| Method | URL                                   | Req.Body    | Resp        | 설명 / 권한               |
| ------ | ------------------------------------- | ----------- | ----------- | ------------------------- |
| GET    | `/schedule/{year}/{month}`            | –           | List        | 월별 일정 조회 _(공개)_   |
| GET    | `/schedule/range/{dateFrom}/{dateTo}` | –           | List        | 기간별 일정 조회 _(공개)_ |
| GET    | `/schedule/{scheduleId}`              | –           | ScheduleDto | 단건 조회 _(공개)_        |
| POST   | `/schedule`                           | ScheduleDto | Created     | 일정 등록 `STAFF+`        |
| PUT    | `/schedule/{scheduleId}`              | ScheduleDto | Updated     | 일정 수정 `STAFF+`        |
| DELETE | `/schedule/{scheduleId}`              | –           | 204         | 일정 삭제 `STAFF+`        |

---

### 3. Schemas

### 3.1 ScheduleDto

```json
{
  "scheduleId": 1,
  "title": "점핑클레이가 찾아옵니다",
  "content": "아이들과 함께하는 만들기 수업…",
  "startDateTime": "2025-02-05T13:00:00",
  "endDateTime": "2025-02-05T14:00:00",
  "displayYn": true,
  "status": "UPCOMING",
  "createdBy": "admin",
  "createdDate": "2025-02-01T10:00:00",
  "updatedBy": "admin",
  "updatedDate": "2025-02-01T10:00:00"
}
```

### 3.2 ScheduleListResponse

```json
{
  "schedules": [
    {
      "scheduleId": 1,
      "title": "점핑클레이가 찾아옵니다",
      "content": "아이들과 함께하는 만들기 수업…",
      "startDateTime": "2025-02-05T13:00:00",
      "endDateTime": "2025-02-05T14:00:00",
      "displayYn": true,
      "status": "UPCOMING",
      "createdBy": "admin",
      "createdDate": "2025-02-01T10:00:00",
      "updatedBy": "admin",
      "updatedDate": "2025-02-01T10:00:00"
    }
  ],
  "totalCount": 1
}
```

---

### 4. Response Wrapper

```json
// 성공 응답
{
  "success": true,
  "message": "성공적으로 처리되었습니다.",
  "data": {
    "schedules": [...],
    "totalCount": 1
  }
}

// 에러 응답
{
  "success": false,
  "message": "일정을 찾을 수 없습니다.",
  "errorCode": "SCHEDULE_NOT_FOUND"
}
```

**Error Codes**

| code               | http | message               |
| ------------------ | ---- | --------------------- |
| SCHEDULE_NOT_FOUND | 404  | 일정 없음             |
| SCHEDULE_DUPLICATE | 409  | 동일 시간이 이미 존재 |
| NO_AUTH            | 403  | 권한 없음             |
| VALIDATION_ERROR   | 400  | 파라미터 오류         |

---

### 5. Database DDL (요약)

```sql
CREATE TABLE schedule (
  schedule_id BIGINT PRIMARY KEY AUTO_INCREMENT,
  title VARCHAR(255) NOT NULL,
  content TEXT,
  start_date_time DATETIME NOT NULL,
  end_date_time DATETIME NOT NULL,
  display_yn BOOLEAN DEFAULT true,
  created_by VARCHAR(36),
  created_ip VARCHAR(45),
  created_date DATETIME DEFAULT CURRENT_TIMESTAMP,
  updated_by VARCHAR(36),
  updated_ip VARCHAR(45),
  updated_date DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_schedule_unique (title, start_date_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

> 중복 방지 : title, start_date_time 유니크
>
> 관리자 등록 시 충돌 → `409 SCHEDULE_DUPLICATE`

---

### 6. Front-End Guidelines (요약)

| 화면                    | Route 예               | 데이터 흐름                                     |
| ----------------------- | ---------------------- | ----------------------------------------------- |
| 달력 월뷰               | `/schedule/guide`      | `GET /schedule/{year}/{month}`                  |
| 일정 상세 팝업          | modal component        | `GET /schedule/{scheduleId}`                    |
| 관리자 – 일정 목록      | `/admin/schedules`     | 월별/기간별 조회                                |
| 관리자 – 일정 등록/수정 | `/admin/schedules/new` | `POST /schedule` / `PUT /schedule/{scheduleId}` |

---

### 7. Example cURL

```bash
# 1) 2025-02 월간 일정 조회
curl http://localhost:8080/cms/schedule/2025/2 \
     | jq .

# 2) 2025-02-01부터 2025-02-28까지 일정 조회
curl http://localhost:8080/cms/schedule/range/2025-02-01/2025-02-28 \
     | jq .

# 3) 일정 등록 (관리자)
curl -X POST http://localhost:8080/cms/schedule \
     -H "Authorization: Bearer $TK" \
     -H "Content-Type: application/json" \
     -d '{
           "title":"점핑클레이가 찾아옵니다",
           "content":"아이들과 함께하는 만들기 수업…",
           "startDateTime":"2025-02-05T13:00:00",
           "endDateTime":"2025-02-05T14:00:00",
           "displayYn":true
         }'

# 4) 일정 숨김 처리
curl -X PUT http://localhost:8080/cms/schedule/1 \
     -H "Authorization: Bearer $TK" \
     -H "Content-Type: application/json" \
     -d '{"displayYn":false}'
```

---

### 8. Change-Log

| ver | date       | 내용                                                      |
| --- | ---------- | --------------------------------------------------------- |
| 1.2 | 2025-05-02 | **엔드포인트 구조 변경** / 페이지네이션 제거              |
| 1.1 | 2025-05-01 | **Audit Mixin(작성자·IP) 추가** / DTO read-only 필드 보강 |
| 1.0 | 2025-05-01 | 최초 스펙 — Popup CRUD & Active API                       |

---
