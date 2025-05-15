## 📦 File Management API v1 — **수정 반영** (2024-05-07)

> **Base URL** `/cms` | **Auth** `Authorization: Bearer {JWT}`  
> 관리자 API는 HTTPS + JWT 필수.  
> 공개 파일 API는 인증 불필요.  
> 전역 `ADMIN` 이상이며, 파일별 **세부 권한**(`readAuth / writeAuth / adminAuth`)을 추가로 검증한다.

---

### 1. File Types & Storage (수정)

| `menu`    | 대표 용도       | 파일 타입              | 특이사항         | 공개 여부                 |
| --------- | --------------- | ---------------------- | ---------------- | ------------------------- |
| `BBS`     | 게시판 첨부파일 | 모든 타입              | 게시글과 연동    | 게시판 설정에 따라 다름   |
| `POPUP`   | 팝업 이미지     | 이미지 (JPG, PNG, GIF) | 썸네일 자동 생성 | 팝업 설정에 따라 다름     |
| `CONTENT` | 페이지 리소스   | 이미지, CSS, JS        | 캐시 관리        | 콘텐츠 설정에 따라 다름   |
| `PROGRAM` | 프로그램 자료   | PDF, DOC, XLS          | 다운로드 카운트  | 프로그램 설정에 따라 다름 |

---

### 2. Common Query String (변경 없음)

| param      | type   | default | note                                                                                                    |
| ---------- | ------ | ------- | ------------------------------------------------------------------------------------------------------- |
| `menu`     | string | –       | 파일이 속한 메뉴 (예: `ARTICLE_ATTACHMENT`, `POPUP` 등)                                                 |
| `menuId`   | long   | –       | 메뉴 내 리소스 ID. `menu`가 `ARTICLE_ATTACHMENT`이면 게시글의 `nttId`, `POPUP`이면 팝업 ID 등이 됩니다. |
| `publicYn` | string | 'Y'     | 공개 여부 (Y/N)                                                                                         |

---

### 3. Endpoints (현재 구현)

### 3.1 File Upload (관리자용)

| Method | URL                | Req.Body            | Resp          | 권한    |
| ------ | ------------------ | ------------------- | ------------- | ------- |
| POST   | `/cms/file/upload` | multipart/form-data | List[FileDto] | `ADMIN` |

### 3.2 File Management (관리자용)

| Method | URL                          | Req.Body           | Resp    | 설명           |
| ------ | ---------------------------- | ------------------ | ------- | -------------- |
| GET    | `/cms/file/private/list`     | – (QS)             | List    | 파일 목록      |
| GET    | `/cms/file/private/{fileId}` | –                  | FileDto | 파일 상세      |
| PUT    | `/cms/file/private/{fileId}` | FileDto            | Updated | 파일 수정      |
| DELETE | `/cms/file/private/{fileId}` | –                  | 204     | 파일 삭제      |
| PUT    | `/cms/file/private/order`    | List[FileOrderDto] | Updated | 순서 변경      |
| GET    | `/cms/file/private/all`      | - (QS)             | List    | 전체 파일 목록 |

### 3.3 Public File Access (공개용)

| Method | URL                                  | Req.Body | Resp | 설명           |
| ------ | ------------------------------------ | -------- | ---- | -------------- |
| GET    | `/cms/file/public/{fileId}`          | –        | File | 파일 조회      |
| GET    | `/cms/file/public/download/{fileId}` | –        | File | 파일 다운로드  |
| GET    | `/cms/file/public/list`              | – (QS)   | List | 공개 파일 목록 |

---

### 4. Schemas (현재 구현)

#### 4.1 FileDto

```jsonc
{
  "fileId": 123,
  "menu": "ARTICLE_ATTACHMENT",
  "menuId": 987,
  "originName": "schedule.pdf",
  "savedName": "2025/04/29/abc123.pdf",
  "mimeType": "application/pdf",
  "size": 204800,
  "ext": "pdf",
  "version": 1,
  "publicYn": "Y",
  "fileOrder": 0,
  "createdBy": "admin",
  "createdIp": "127.0.0.1",
  "createdDate": "2024-05-03T10:00:00+09:00",
  "updatedBy": "admin",
  "updatedIp": "127.0.0.1",
  "updatedDate": "2024-05-03T10:00:00+09:00"
}
```

#### 4.2 FileOrderDto

```jsonc
{
  "fileId": 123,
  "order": 0
}
```

---

### 5. Response Wrapper (변경 없음)

```jsonc
// List
{
  "success": true,
  "message": "파일 목록이 성공적으로 조회되었습니다.",
  "data": [...]
}

// 단건
{
  "success": true,
  "message": "파일이 성공적으로 업로드되었습니다.",
  "data": { ... }
}

// Error
{
  "success": false,
  "message": "파일을 찾을 수 없습니다.",
  "errorCode": "FILE_NOT_FOUND"
}
```

---

### 6. Database DDL (수정: `savedName` 설명)

```sql
CREATE TABLE file (
  file_id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT 'PK: 파일 ID',
  menu VARCHAR(30) NOT NULL COMMENT '메뉴 코드 (BBS, POPUP 등)',
  menu_id BIGINT NOT NULL COMMENT '메뉴별 리소스 ID',
  origin_name VARCHAR(255) NOT NULL COMMENT '원본 파일명',
  saved_name VARCHAR(255) NOT NULL COMMENT '저장된 파일명',
  mime_type VARCHAR(100) NOT NULL COMMENT 'MIME 타입',
  size BIGINT NOT NULL COMMENT '파일 크기(바이트)',
  ext VARCHAR(20) NOT NULL COMMENT '파일 확장자',
  version INT DEFAULT 1 COMMENT '파일 버전',
  public_yn VARCHAR(1) DEFAULT 'Y' COMMENT '공개 여부 (Y/N)',
  file_order INT DEFAULT 0 COMMENT '파일 순서',
  created_by VARCHAR(36) NULL COMMENT '생성자 ID',
  created_ip VARCHAR(45) NULL COMMENT '생성자 IP',
  created_date DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '생성 일시',
  updated_by VARCHAR(36) NULL COMMENT '수정자 ID',
  updated_ip VARCHAR(45) NULL COMMENT '수정자 IP',
  updated_date DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '수정 일시',
  -- 인덱스
  INDEX idx_file_menu (menu, menu_id),
  INDEX idx_file_public (public_yn),
  INDEX idx_file_order (menu, menu_id, file_order),
  INDEX idx_file_public_menu (menu, menu_id, public_yn),
  -- 제약조건
  CONSTRAINT chk_public_yn CHECK (public_yn IN ('Y', 'N')),
  CONSTRAINT uk_saved_name UNIQUE (saved_name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='CMS 파일';

-- 트리거
DELIMITER //
CREATE TRIGGER before_insert_file
BEFORE INSERT ON file
FOR EACH ROW
BEGIN
    IF NEW.public_yn NOT IN ('Y', 'N') THEN
        SIGNAL SQLSTATE '45000'
        SET MESSAGE_TEXT = 'public_yn must be Y or N';
    END IF;
END//
DELIMITER ;

-- 트리거
DELIMITER //
CREATE TRIGGER before_update_file
BEFORE UPDATE ON file
FOR EACH ROW
BEGIN
    IF NEW.public_yn NOT IN ('Y', 'N') THEN
        SIGNAL SQLSTATE '45000'
        SET MESSAGE_TEXT = 'public_yn must be Y or N';
    END IF;
END//
DELIMITER ;
```

---

### 7. Front-End Guidelines (변경 없음)

| Menu    | Route 예                    | 비고                 |
| ------- | --------------------------- | -------------------- |
| BBS     | `/bbs/{bbsId}/file`         | 게시글 첨부파일 관리 |
| POPUP   | `/popup/{popupId}/file`     | 팝업 이미지 관리     |
| CONTENT | `/page/{contentId}/file`    | 페이지 리소스 관리   |
| PROGRAM | `/program/{programId}/file` | 프로그램 자료 관리   |

### 7.1 Public File Access (공개용) (변경 없음)

| Menu    | Route 예                           | 비고                 |
| ------- | ---------------------------------- | -------------------- |
| BBS     | `/bbs/{bbsId}/file/public`         | 공개 게시글 첨부파일 |
| POPUP   | `/popup/{popupId}/file/public`     | 공개 팝업 이미지     |
| CONTENT | `/page/{contentId}/file/public`    | 공개 페이지 리소스   |
| PROGRAM | `/program/{programId}/file/public` | 공개 프로그램 자료   |

---

### 8. Change-Log (수정)

| ver | date       | 내용                                                    |
| --- | ---------- | ------------------------------------------------------- |
| 1.2 | 2024-05-04 | 모듈(module) 대신 메뉴(menu) 정보 사용으로 변경         |
| 1.1 | 2024-05-04 | 공개 파일 접근 API 추가 (public 경로)                   |
| 1.0 | 2024-05-03 | 기본 파일 관리 기능 구현 (업로드, 다운로드, 목록, 삭제) |

---

### 9. Example cURL (수정: 업로드 파라미터)

```bash
# 파일 업로드 (관리자용)
curl -H "Authorization: Bearer $TK" \
     -F "menu=BBS" \
     -F "menuId=987" \
     -F "files=@./schedule.pdf" \
     https://cms.example.com/cms/file/upload

# 게시글 첨부파일 목록 조회 (관리자용 - menuId는 게시글 nttId)
curl -H "Authorization: Bearer $TK" \
     "https://cms.example.com/cms/file/list?menu=BBS&menuId=987"

# 파일 삭제 (관리자용)
curl -X DELETE -H "Authorization: Bearer $TK" \
     https://cms.example.com/cms/file/private/123

# 공개 파일 조회 (공개용)
curl "https://cms.example.com/cms/file/public/123"

# 공개 파일 다운로드 (공개용)
curl "https://cms.example.com/cms/file/public/download/123"

# 특정 게시글의 공개 첨부파일 목록 조회 (공개용 - menuId는 게시글 nttId)
curl "https://cms.example.com/cms/file/public/list?menu=ARTICLE_ATTACHMENT&menuId=101"
```

### 4. Request Parameters

#### 4.1 Query Parameters

| Parameter | Type   | Required | Description                               |
| --------- | ------ | -------- | ----------------------------------------- |
| menu      | String | Yes      | 메뉴 코드 (BBS, POPUP, CONTENT, PROGRAM)  |
| menuId    | Long   | Yes      | 메뉴별 리소스 ID                          |
| publicYn  | String | No       | 공개 여부 (Y/N). 미지정 시 모든 파일 조회 |

#### 4.2 Request Body (FileDto)

```json
// FileDto (상세 내용 위 4.1 참조)
{
  "fileId": "Long", ...
}

// FileOrderDto (상세 내용 위 4.2 참조)
{
  "fileId": "Long",
  "fileOrder": "Integer"
}
```

#### Error Responses (변경 없음)

| Status | Code                  | Description          |
| ------ | --------------------- | -------------------- |
| 400    | BAD_REQUEST           | 잘못된 요청 파라미터 |
| 401    | UNAUTHORIZED          | 인증되지 않은 요청   |
| 403    | FORBIDDEN             | 권한이 없는 요청     |
| 404    | NOT_FOUND             | 파일이 존재하지 않음 |
| 500    | INTERNAL_SERVER_ERROR | 서버 내부 오류       |

#### Validation Rules (수정: `savedName` 구조)

1. `publicYn` 값은 'Y' 또는 'N'만 허용
2. `fileOrder`는 0 이상의 정수
3. `menu`는 지정된 코드만 허용
4. `savedName`은 중복 불가
5. 파일 크기는 0보다 커야 함
