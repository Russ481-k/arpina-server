## 📑 Menu API v1 — **File-Integrated Final Spec** (2024-03-21)

> **Base URL for Authenticated APIs:** `/api/v1/cms/menu`  
> **Base URL for Public APIs:** `/api/v1/cms/menu/public`  
> **Auth:** `Authorization: Bearer {JWT}` for authenticated APIs.  
> Authenticated endpoints generally require `ADMIN` equivalent privileges (details per endpoint). Public endpoints do not require authentication.

---

### 1. Menu Types & Structure

| `type`    | 용도               | 특징                        |
| --------- | ------------------ | --------------------------- |
| `LINK`    | 외부 링크          | `url` 필드에 외부 URL 지정  |
| `FOLDER`  | 메뉴 그룹          | 하위 메뉴를 가질 수 있음    |
| `BOARD`   | 게시판 연결        | `targetId`에 게시판 ID 지정 |
| `CONTENT` | 컨텐츠 페이지 연결 | `targetId`에 컨텐츠 ID 지정 |

---

### 2. Common Query String

| param  | type   | default | note                         |
| ------ | ------ | ------- | ---------------------------- |
| `page` | int    | 1       | 1-based                      |
| `size` | int    | 20      | rows per page                |
| `sort` | string | `order` | `+field` ASC / `-field` DESC |

> **정렬 예시**
>
> - `?sort=sortOrder&direction=asc`: 정렬순서 오름차순
> - `?sort=name&direction=desc`: 메뉴명 내림차순
> - `?sort=createdAt&direction=desc`: 생성일 최신순

---

### 3. Endpoints

#### 3.1 Menu Management

| Method | URL                         | Req.Body     | Resp           | 권한            |
| ------ | --------------------------- | ------------ | -------------- | --------------- |
| GET    | `/menu`                     | –            | List           | `AUTHENTICATED` |
| POST   | `/menu`                     | MenuDto      | Created        | `AUTHENTICATED` |
| GET    | `/menu/{id}`                | –            | MenuDto        | `AUTHENTICATED` |
| PUT    | `/menu/{id}`                | MenuDto      | Updated        | `AUTHENTICATED` |
| DELETE | `/menu/{id}`                | –            | 204            | `AUTHENTICATED` |
| GET    | `/public/{id}/page-details` | –            | PageDetailsDto | `PUBLIC`        |
| GET    | `/menu/tree`                | –            | Tree           | `AUTHENTICATED` |
| GET    | `/menu/public`              | –            | List           | `PUBLIC`        |
| PUT    | `/menu/{id}/active`         | –            | Updated        | `AUTHENTICATED` |
| PUT    | `/menu/{id}/order`          | –            | Updated        | `AUTHENTICATED` |
| PUT    | `/menu/order`               | MenuOrderDto | Updated        | `AUTHENTICATED` |
| GET    | `/menu/type/{type}`         | –            | List           | `AUTHENTICATED` |

> **권한 관리**
>
> - `AUTHENTICATED` 권한: 메뉴 생성/수정/삭제/순서변경 등 관리 기능 (일반적으로 `ADMIN` 역할 필요).
> - `PUBLIC` 권한: 메뉴 조회만 가능 (공개된 메뉴만).

---

### 4. Schemas

#### 4.1 MenuDto

```jsonc
{
  "id": 1,
  "name": "공지사항",
  "type": "BOARD",
  "url": "/notice",
  "targetId": 5,
  "displayPosition": "TOP",
  "visible": true,
  "sortOrder": 1,
  "parentId": null,
  "children": []
}
```

#### 4.2 MenuOrderDto

```jsonc
{
  "id": 1,
  "targetId": 2,
  "position": "before" // "before" | "after" | "inside"
}
```

#### 4.3 PageDetailsDto

DTO for providing detailed information about a page linked to a menu item.
The content varies based on the `menuType`.

```jsonc
// Example for menuType: "BOARD"
{
  "menuId": 1,
  "menuName": "공지사항",
  "menuType": "BOARD",
  "boardId": 5,
  "boardName": "자유 게시판",
  "boardSkinType": "BASIC", // Or other BbsSkinType enum values
  "boardReadAuth": "PUBLIC",
  "boardWriteAuth": "ROLE_USER",
  "boardAttachmentLimit": 5,
  "boardAttachmentSize": 10 // In MB
}

// Example for menuType: "CONTENT" (Future)
// {
//   "menuId": 2,
//   "menuName": "회사소개",
//   "menuType": "CONTENT",
//   "contentId": 10,
//   "contentLayout": "full_width",
//   ... other content specific fields
// }

// Example for menuType: "PROGRAM" (Future)
// {
//   "menuId": 3,
//   "menuName": "문의하기",
//   "menuType": "PROGRAM",
//   "programPath": "/support/contact",
//   "programDescription": "고객 지원 문의 프로그램",
//   ... other program specific fields
// }
```

---

### 5. Response Wrapper

```jsonc
// List
{
  "status": 200,
  "data": [...],
  "message": "메뉴 목록이 성공적으로 조회되었습니다."
}

// 단건
{
  "status": 200,
  "data": { ... },
  "message": "메뉴 정보를 성공적으로 조회했습니다."
}

// Error
{
  "status": 404,
  "error": {
    "code": "MENU_NOT_FOUND",
    "message": "메뉴를 찾을 수 없습니다."
  }
}
```

**Error Codes**

| code              | http | message          | 설명                                                                     |
| ----------------- | ---- | ---------------- | ------------------------------------------------------------------------ |
| MENU_NOT_FOUND    | 404  | 메뉴 없음        | 존재하지 않는 메뉴 조회 시                                               |
| TARGET_NOT_FOUND  | 404  | 연결 대상 없음   | 메뉴의 `targetId`에 해당하는 리소스(게시판, 컨텐츠 등)를 찾을 수 없을 시 |
| MENU_NAME_DUP     | 409  | 메뉴명 중복      | 중복된 메뉴명 사용 시                                                    |
| MENU_URL_DUP      | 409  | URL 중복         | LINK 타입에서 중복 URL 사용 시                                           |
| NO_AUTH           | 403  | 권한 없음        | 권한이 없는 사용자 접근 시                                               |
| INVALID_MENU_TYPE | 400  | 잘못된 메뉴 타입 | 지원하지 않는 타입 사용 시                                               |
| INVALID_TARGET_ID | 400  | 잘못된 연결 ID   | `BOARD` 타입 메뉴에 `targetId`가 없거나 유효하지 않을 시                 |
| INVALID_ORDER     | 400  | 잘못된 순서      | 유효하지 않은 순서 지정 시                                               |
| HAS_CHILDREN      | 400  | 하위 메뉴 존재   | 하위 메뉴가 있는 메뉴 삭제 시                                            |

---

### 6. Database DDL

```sql
CREATE TABLE menu (
  id BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT COMMENT 'PK: 메뉴 ID',
  name VARCHAR(100) NOT NULL COMMENT '메뉴명',
  type ENUM('LINK','FOLDER','BOARD','CONTENT') NOT NULL COMMENT '메뉴 타입',
  url VARCHAR(255) COMMENT '메뉴 URL (LINK 타입일 때 필수)',
  target_id BIGINT UNSIGNED COMMENT '연결 대상 ID (BOARD/CONTENT 타입일 때 필수)',
  display_position VARCHAR(50) NOT NULL COMMENT '표시 위치 (TOP/LEFT/RIGHT/BOTTOM)',
  visible BOOLEAN DEFAULT true COMMENT '노출 여부',
  sort_order INT NOT NULL COMMENT '정렬 순서',
  parent_id BIGINT UNSIGNED COMMENT '부모 메뉴 ID',
  created_by VARCHAR(36) COMMENT '생성자 ID',
  created_ip VARCHAR(45) COMMENT '생성자 IP',
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '생성 일시',
  updated_by VARCHAR(36) COMMENT '수정자 ID',
  updated_ip VARCHAR(45) COMMENT '수정자 IP',
  updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '수정 일시',
  UNIQUE KEY uk_menu_name (name) COMMENT '메뉴명 중복 방지',
  UNIQUE KEY uk_menu_url (url) COMMENT 'URL 중복 방지',
  FOREIGN KEY (parent_id) REFERENCES menu(id) ON DELETE SET NULL COMMENT '부모 메뉴 참조',
  INDEX idx_menu_parent (parent_id) COMMENT '부모 메뉴 조회 인덱스',
  INDEX idx_menu_order (sort_order) COMMENT '정렬 순서 조회 인덱스'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='메뉴 관리 테이블';
```

---

### 7. 보안 및 인증

1. **권한 관리**

   - `AUTHENTICATED` 권한: 모든 메뉴 관리 기능 사용 가능
   - `PUBLIC` 권한: 공개된 메뉴만 조회 가능
   - JWT 토큰 검증 필수

2. **메뉴 삭제 정책**

   - 하위 메뉴가 있는 경우 삭제 불가 (`HAS_CHILDREN` 에러)
   - 삭제 시 하위 메뉴부터 삭제 필요
   - `ON DELETE SET NULL`로 설정되어 있어 부모 메뉴 삭제 시 하위 메뉴는 보존

3. **데이터 무결성**
   - 메뉴명 중복 방지
   - LINK 타입에서만 URL 중복 체크
   - 부모-자식 관계 무결성 보장

---

### 8. Front-End Guidelines

1. **메뉴 트리 관리**

   - 드래그 앤 드롭으로 순서 변경
   - 부모-자식 관계 설정 가능
   - 실시간 순서 업데이트

2. **메뉴 타입별 처리**

   - `LINK`: 새 창/현재 창 옵션
   - `FOLDER`: 하위 메뉴 표시/숨김
   - `BOARD`: 게시판 목록으로 이동
   - `CONTENT`: 컨텐츠 페이지로 이동

3. **캐싱 전략**
   - 공개 메뉴는 1시간 캐시
   - 관리자 메뉴는 캐시 없음

---

### 9. Change-Log

| ver | date       | 내용                                           |
| --- | ---------- | ---------------------------------------------- |
| 1.0 | 2024-03-21 | 최초 통합본(LINK, FOLDER, BOARD, CONTENT 지원) |

---

### 10. Example cURL

```bash
# 1) 메뉴 생성
curl -H "Authorization: Bearer $TK" \
     -H "Content-Type: application/json" \
     -d '{
       "name": "공지사항",
       "type": "BOARD",
       "url": "/notice",
       "targetId": 5,
       "displayPosition": "TOP",
       "visible": true,
       "sortOrder": 1
     }' \
     https://cms.example.com/api/v1/menu

# 2) 메뉴 순서 변경
curl -H "Authorization: Bearer $TK" \
     -H "Content-Type: application/json" \
     -d '[{
       "id": 1,
       "targetId": 2,
       "position": "before"
     }]' \
     https://cms.example.com/api/v1/menu/order
```

---

### 👀 주요 기능

1. **계층적 메뉴 구조**

   - 무한 깊이의 메뉴 트리 지원
   - 드래그 앤 드롭으로 순서/계층 변경

2. **다양한 메뉴 타입**

   - 외부 링크, 폴더, 게시판, 컨텐츠 연결
   - 타입별 특화 기능 지원

3. **권한 관리**

   - 메뉴별 접근 권한 설정
   - 공개/비공개 설정

4. **캐싱**
   - 공개 메뉴 캐싱으로 성능 최적화
   - 관리자 메뉴는 실시간 반영

---
