## 📑 Main Media API v1 — **현재 구현 상태** (2024-03-21)

> **Base URL** `/cms/main-media` | **Auth** `Authorization: Bearer {JWT}`  
> 모든 엔드포인트는 HTTPS + JWT 필수.  
> `ADMIN` 또는 `SYSTEM_ADMIN` 권한 필요.

---

### 1. Main Media Types

| `mediaType` | 용도          | 파일 형식           | 특이사항                            |
| ----------- | ------------- | ------------------- | ----------------------------------- |
| `IMAGE`     | 이미지 미디어 | JPG, PNG, GIF, WEBP | 웹 최적화 이미지 권장               |
| `VIDEO`     | 동영상 미디어 | MP4, WEBM           | 자동재생 여부는 프론트엔드에서 설정 |

---

### 2. Common Query String

| param       | type   | default        | note               |
| ----------- | ------ | -------------- | ------------------ |
| `page`      | int    | 1              | 1-based            |
| `size`      | int    | 20             | rows per page      |
| `sort`      | string | `displayOrder` | 정렬 기준          |
| `mediaType` | string | –              | 미디어 타입 필터링 |
| `publicYn`  | string | 'Y'            | 공개 여부 필터링   |

---

### 3. Endpoints

#### 3.1 미디어 목록 조회

**GET** `/`

**Response** (200 OK)

```jsonc
{
  "content": [MainMediaDto],
  "totalElements": 10,
  "totalPages": 1,
  "size": 20,
  "number": 0
}
```

#### 3.2 미디어 상세 조회

**GET** `/{mediaId}`

**Response** (200 OK)

```jsonc
MainMediaDto
```

**Error**

- 404 Not Found: 미디어를 찾을 수 없음

#### 3.3 미디어 등록

**POST** `/`

**Request**

- Content-Type: `multipart/form-data`

```
file: File
data: MainMediaDto (JSON)
```

**Response** (201 Created)

```jsonc
MainMediaDto
```

**Error**

- 400 Bad Request: 잘못된 요청 데이터
- 415 Unsupported Media Type: 지원하지 않는 파일 형식

#### 3.4 미디어 수정

**PUT** `/{mediaId}`

**Request**

- Content-Type: `multipart/form-data`

```
file: File (선택)
data: MainMediaDto (JSON)
```

**Response** (200 OK)

```jsonc
MainMediaDto
```

**Error**

- 404 Not Found: 미디어를 찾을 수 없음
- 400 Bad Request: 잘못된 요청 데이터

#### 3.5 미디어 삭제

**DELETE** `/{mediaId}`

**Response** (204 No Content)

**Error**

- 404 Not Found: 미디어를 찾을 수 없음

#### 3.6 표시 순서 변경

**PUT** `/order`

**Request**

```jsonc
[
  {
    "mediaId": 1,
    "displayOrder": 1
  },
  {
    "mediaId": 2,
    "displayOrder": 2
  }
]
```

**Response** (200 OK)

```jsonc
{
  "success": true,
  "message": "표시 순서가 변경되었습니다."
}
```

**Error**

- 400 Bad Request: 잘못된 순서 데이터
- 404 Not Found: 존재하지 않는 미디어 포함

---

### 4. Schemas

#### 4.1 MainMediaDto

```jsonc
{
  "mediaId": 1,
  "title": "메인 배너",
  "description": "이벤트 배너 이미지",
  "mediaType": "IMAGE",
  "displayOrder": 1,
  "fileId": 123,
  "publicYn": "Y",
  "createdBy": "admin",
  "createdIp": "127.0.0.1",
  "createdDate": "2024-03-21T10:00:00+09:00",
  "updatedBy": "admin",
  "updatedIp": "127.0.0.1",
  "updatedDate": "2024-03-21T10:00:00+09:00"
}
```

#### 4.2 OrderDto

```jsonc
{
  "mediaId": 1,
  "displayOrder": 1
}
```

#### 4.3 Error Codes

| Code                 | Description               |
| -------------------- | ------------------------- |
| `INVALID_REQUEST`    | 잘못된 요청               |
| `UNAUTHORIZED`       | 인증 필요                 |
| `FORBIDDEN`          | 권한 없음                 |
| `NOT_FOUND`          | 리소스를 찾을 수 없음     |
| `CONFLICT`           | 리소스 충돌               |
| `INTERNAL_ERROR`     | 서버 내부 오류            |
| `MEDIA_NOT_FOUND`    | 미디어를 찾을 수 없음     |
| `INVALID_MEDIA_TYPE` | 지원하지 않는 미디어 형식 |
| `FILE_NOT_FOUND`     | 파일을 찾을 수 없음       |

---

### 5. Database Schema

```sql
CREATE TABLE `main_media` (
  `media_id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT 'PK: 메인 미디어 ID',
  `title` varchar(255) NOT NULL COMMENT '미디어 제목',
  `description` text COMMENT '미디어 설명',
  `media_type` ENUM('IMAGE', 'VIDEO') NOT NULL COMMENT '미디어 타입 (IMAGE/VIDEO)',
  `display_order` int(11) NOT NULL DEFAULT 0 COMMENT '화면 표시 순서',
  `file_id` bigint(20) NOT NULL COMMENT 'FK: 파일 ID',
  `public_yn` varchar(1) DEFAULT 'Y' COMMENT '공개 여부 (Y/N)',
  `created_by` varchar(36) DEFAULT NULL COMMENT '생성자 ID',
  `created_ip` varchar(45) DEFAULT NULL COMMENT '생성자 IP',
  `created_date` datetime DEFAULT current_timestamp() COMMENT '생성 일시',
  `updated_by` varchar(36) DEFAULT NULL COMMENT '수정자 ID',
  `updated_ip` varchar(45) DEFAULT NULL COMMENT '수정자 IP',
  `updated_date` datetime DEFAULT current_timestamp() ON UPDATE current_timestamp() COMMENT '수정 일시',
  PRIMARY KEY (`media_id`),
  KEY `idx_main_media_display_public` (`public_yn`, `display_order`) COMMENT '공개 여부, 표시 순서 정렬용 인덱스',
  KEY `idx_main_media_created` (`created_date`) COMMENT '생성일시 정렬용 인덱스',
  KEY `idx_main_media_type_public` (`media_type`, `public_yn`) COMMENT '미디어 타입별 조회용 인덱스',
  CONSTRAINT `fk_main_media_file` FOREIGN KEY (`file_id`) REFERENCES `file` (`file_id`),
  CONSTRAINT `chk_main_media_public_yn` CHECK (`public_yn` in ('Y','N'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='메인 미디어 관리';
```

#### 5.1 Field Descriptions

| 필드명         | 타입        | 필수 | 설명        | 제약조건                   |
| -------------- | ----------- | ---- | ----------- | -------------------------- |
| `title`        | String(255) | Y    | 미디어 제목 | 2~255자                    |
| `description`  | Text        | N    | 미디어 설명 | -                          |
| `mediaType`    | Enum        | Y    | 미디어 타입 | IMAGE, VIDEO               |
| `displayOrder` | Integer     | Y    | 표시 순서   | 0 이상의 정수              |
| `fileId`       | Long        | Y    | 파일 ID     | file 테이블의 file_id 참조 |
| `publicYn`     | String(1)   | N    | 공개 여부   | Y, N (기본값: Y)           |

```

```
