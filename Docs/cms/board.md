## 📑 Board (BBS) API v1 — **현재 구현 상태** (2024-03-21)

> **Base URL** `/cms` | **Auth** `Authorization: Bearer {JWT}`  
> 모든 엔드포인트는 HTTPS + JWT 필수.  
> 전역 `ADMIN` 이상이며, 게시판별 **세부 권한**(`readAuth / writeAuth / adminAuth`)을 추가로 검증한다.

---

### 1. Board Skins & UX (현재 구현)

| `skinType` | 대표 용도      | 목록 UI                       | 상세 UI          | 특이사항                  |
| ---------- | -------------- | ----------------------------- | ---------------- | ------------------------- |
| `BASIC`    | 공지, 일반자료 | 표 목록                       | ○                | –                         |
| `FAQ`      | 자주 묻는 질문 | Q 행 → **아코디언** 답변      | ×                | 별도 detail URL 없음      |
| `QNA`      | 답변형(1:1)    | 목록 상단 **[질문하기]** 버튼 | ○ (Q + 관리답변) | `parentNttId`로 답변 연동 |
| `PRESS`    | 보도자료       | 썸네일 카드                   | ○                | 출처·링크 필드 권장       |
| `FORM`     | 설문, 신청     | 목록 + 상세                   | ○                | 커스텀 필드 지원          |

---

### 2. Common Query String (현재 구현)

| param      | type   | default      | note                         |
| ---------- | ------ | ------------ | ---------------------------- |
| `page`     | int    | 1            | 1-based                      |
| `size`     | int    | 20           | rows per page                |
| `search`   | string | –            | 제목+내용+작성자             |
| `sort`     | string | `-createdAt` | `+field` ASC / `-field` DESC |
| `category` | string | –            | 카테고리 코드 필터링         |

---

### 3. Endpoints (현재 구현)

### 3.1 Board Master

| Method | URL                   | Req.Body         | Resp         | 권한    |
| ------ | --------------------- | ---------------- | ------------ | ------- |
| GET    | `/bbs/master`         | – (QS)           | List         | `ADMIN` |
| POST   | `/bbs/master`         | **BbsMasterDto** | Created      | `ADMIN` |
| GET    | `/bbs/master/{bbsId}` | –                | BbsMasterDto | 관리    |
| PUT    | `/bbs/master/{bbsId}` | BbsMasterDto     | Updated      | 관리    |
| DELETE | `/bbs/master/{bbsId}` | –                | 204          | 관리    |

### 3.2 Articles

| Method | URL                    | Req.Body | Resp    | 설명        |
| ------ | ---------------------- | -------- | ------- | ----------- |
| GET    | `/bbs/article`         | – (QS)   | List    | 게시글 목록 |
| GET    | `/bbs/article/{nttId}` | –        | NttDto  | 게시글 상세 |
| POST   | `/bbs/article`         | NttDto   | Created | 글 작성     |
| PUT    | `/bbs/article/{nttId}` | NttDto   | Updated | 글 수정     |
| DELETE | `/bbs/article/{nttId}` | –        | 204     | 글 삭제     |

### 3.3 Attachments

| Method | URL                           | Req.Body | Resp    | 설명          |
| ------ | ----------------------------- | -------- | ------- | ------------- |
| POST   | `/bbs/article/{nttId}/attach` | FormData | Created | 파일 첨부     |
| GET    | `/bbs/article/{nttId}/attach` | –        | List    | 첨부파일 목록 |
| DELETE | `/bbs/attach/{attachmentId}`  | –        | 204     | 파일 삭제     |

### 3.4 Anonymous QNA

| Method | URL                      | Req.Body | Resp    | 설명          |
| ------ | ------------------------ | -------- | ------- | ------------- |
| POST   | `/bbs/article/anonymous` | NttDto   | Created | 비로그인 질문 |
| GET    | `/bbs/article/verify`    | – (QS)   | Token   | 본인확인 토큰 |

---

### 4. Schemas (현재 구현)

#### 4.1 BbsMasterDto

```jsonc
{
  "bbsId": 1,
  "bbsName": "자료실",
  "skinType": "BASIC",
  "readAuth": "PUBLIC",
  "writeAuth": "STAFF",
  "adminAuth": "ADMIN",
  "displayYn": "Y",
  "sortOrder": "D",
  "noticeYn": "N",
  "publishYn": "N",
  "attachmentYn": "N",
  "attachmentLimit": 3,
  "attachmentSize": 10,
  "createdBy": "admin",
  "createdIp": "127.0.0.1",
  "createdAt": "2024-03-21T10:00:00+09:00",
  "updatedBy": "admin",
  "updatedIp": "127.0.0.1",
  "updatedAt": "2024-03-21T10:00:00+09:00"
}
```

#### 4.2 BbsArticleDto

```jsonc
{
  "nttId": 123,
  "bbsId": 5,
  "menuId": 8,
  "parentNttId": null,
  "threadDepth": 0,
  "writer": "홍길동",
  "displayWriter": "관리자",
  "title": "2024년 휴강 안내",
  "content": "<p>내용…</p>",
  "hasImageInContent": true,
  "noticeState": "N",
  "noticeStartDt": "2024-05-01T00:00:00+09:00",
  "noticeEndDt": "2024-05-31T23:59:59+09:00",
  "publishState": "N",
  "publishStartDt": "2024-05-01T00:00:00+09:00",
  "publishEndDt": null,
  "externalLink": null,
  "hits": 100,
  "createdBy": "admin",
  "createdIp": "127.0.0.1",
  "createdAt": "2024-03-21T10:00:00+09:00",
  "postedAt": "2024-03-20T09:00:00+09:00",
  "updatedBy": "admin",
  "updatedIp": "127.0.0.1",
  "updatedAt": "2024-03-21T10:00:00+09:00"
}
```

#### 4.3 BbsAttachmentDto

```jsonc
{
  "attachmentId": 123,
  "nttId": 456,
  "originName": "example.pdf",
  "savedName": "20240321_123456_abc123.pdf",
  "mimeType": "application/pdf",
  "size": 1024000,
  "ext": "pdf",
  "fileOrder": 0,
  "createdBy": "admin",
  "createdIp": "127.0.0.1",
  "createdAt": "2024-03-21T10:00:00+09:00"
}
```

#### 4.4 Error Codes

| Code                        | Description             |
| --------------------------- | ----------------------- |
| `INVALID_REQUEST`           | 잘못된 요청             |
| `UNAUTHORIZED`              | 인증 필요               |
| `FORBIDDEN`                 | 권한 없음               |
| `NOT_FOUND`                 | 리소스를 찾을 수 없음   |
| `CONFLICT`                  | 리소스 충돌             |
| `INTERNAL_ERROR`            | 서버 내부 오류          |
| `BBS_NOT_FOUND`             | 게시판을 찾을 수 없음   |
| `ARTICLE_NOT_FOUND`         | 게시글을 찾을 수 없음   |
| `ATTACHMENT_NOT_FOUND`      | 첨부파일을 찾을 수 없음 |
| `ATTACHMENT_LIMIT_EXCEEDED` | 첨부파일 개수 초과      |
| `ATTACHMENT_SIZE_EXCEEDED`  | 첨부파일 크기 초과      |
| `INVALID_FILE_TYPE`         | 지원하지 않는 파일 형식 |
| `VERIFICATION_FAILED`       | 본인확인 실패           |
| `VERIFICATION_EXPIRED`      | 본인확인 만료           |

#### 4.5 Board Update Fields

##### 4.5.1 BbsMasterDto (게시판 설정)

| 필드명            | 타입    | 필수 | 설명                   | 제약조건                             |
| ----------------- | ------- | ---- | ---------------------- | ------------------------------------ |
| `bbsName`         | String  | Y    | 게시판 이름            | 2~100자                              |
| `skinType`        | Enum    | Y    | 게시판 스킨            | BASIC, FAQ, QNA, PRESS, FORM         |
| `readAuth`        | String  | Y    | 읽기 권한              | PUBLIC, STAFF, ADMIN                 |
| `writeAuth`       | String  | Y    | 쓰기 권한              | PUBLIC, STAFF, ADMIN                 |
| `adminAuth`       | String  | Y    | 관리 권한              | ADMIN                                |
| `displayYn`       | String  | N    | 노출 여부              | Y, N (기본값: Y)                     |
| `sortOrder`       | String  | N    | 정렬 순서              | A(오름차순), D(내림차순) (기본값: D) |
| `noticeYn`        | String  | N    | 공지 기능              | Y, N (기본값: N)                     |
| `publishYn`       | String  | N    | 게시 기능              | Y, N (기본값: N)                     |
| `attachmentYn`    | String  | N    | 첨부파일 기능          | Y, N (기본값: N)                     |
| `attachmentLimit` | Integer | N    | 첨부파일 최대 개수     | 0~10 (기본값: 0)                     |
| `attachmentSize`  | Integer | N    | 첨부파일 최대 용량(MB) | 0~100 (기본값: 0)                    |

##### 4.5.2 BbsArticleDto (게시글)

| 필드명              | 타입     | 필수 | 설명                     | 제약조건                                      |
| ------------------- | -------- | ---- | ------------------------ | --------------------------------------------- |
| `bbsId`             | Long     | Y    | 게시판 ID                | 존재하는 게시판 ID                            |
| `menuId`            | Long     | Y    | 메뉴 ID                  | 존재하는 메뉴 ID                              |
| `parentNttId`       | Long     | N    | 부모 글 ID               | 답변형 게시판에서만 사용                      |
| `writer`            | String   | Y    | 작성자                   | 2~50자                                        |
| `title`             | String   | Y    | 제목                     | 2~255자                                       |
| `content`           | String   | Y    | 내용                     |                                               |
| `noticeState`       | String   | N    | 공지 상태                | N(일반), Y(공지), P(영구공지)                 |
| `noticeStartDt`     | DateTime | N    | 공지 시작일              | noticeState가 Y일 때 필수                     |
| `noticeEndDt`       | DateTime | N    | 공지 종료일              | noticeState가 Y일 때 필수                     |
| `publishState`      | String   | N    | 게시 상태                | N(미게시), Y(게시), P(영구게시)               |
| `publishStartDt`    | DateTime | N    | 게시 시작일              | publishState가 Y일 때 필수                    |
| `publishEndDt`      | DateTime | N    | 게시 종료일              | publishState가 Y일 때 필수                    |
| `externalLink`      | String   | N    | 외부 링크                | URL 형식                                      |
| `hasImageInContent` | boolean  | N    | 내용 내 이미지 포함 여부 | 시스템 자동 설정 (true면 이미지 포함)         |
| `hits`              | Integer  | N    | 조회수                   | **[관리자용]** 임의 수정 가능                 |
| `displayWriter`     | String   | N    | 표시용 작성자            | **[관리자용]** 설정 시, `writer` 대신 노출    |
| `postedAt`          | DateTime | N    | 표시용 게시일            | **[관리자용]** 설정 시, `createdAt` 대신 노출 |

##### 4.5.3 BbsAttachmentDto (첨부파일)

| 필드명       | 타입    | 필수 | 설명        | 제약조건                          |
| ------------ | ------- | ---- | ----------- | --------------------------------- |
| `nttId`      | Long    | Y    | 게시글 ID   | 존재하는 게시글 ID                |
| `originName` | String  | Y    | 원본 파일명 | 1~255자                           |
| `savedName`  | String  | Y    | 저장 파일명 | 시스템 생성                       |
| `mimeType`   | String  | Y    | MIME 타입   | 허용된 MIME 타입                  |
| `size`       | Long    | Y    | 파일 크기   | 게시판 설정의 attachmentSize 이내 |
| `ext`        | String  | Y    | 파일 확장자 | 허용된 확장자                     |
| `fileOrder`  | Integer | N    | 파일 순서   | 0부터 시작                        |

##### 4.5.4 허용된 MIME 타입

| 카테고리     | MIME 타입                                                                                                    | 확장자              |
| ------------ | ------------------------------------------------------------------------------------------------------------ | ------------------- |
| 이미지       | image/jpeg, image/png, image/gif                                                                             | jpg, jpeg, png, gif |
| 문서         | application/pdf, application/msword, application/vnd.openxmlformats-officedocument.wordprocessingml.document | pdf, doc, docx      |
| 스프레드시트 | application/vnd.ms-excel, application/vnd.openxmlformats-officedocument.spreadsheetml.sheet                  | xls, xlsx           |
| 압축파일     | application/zip, application/x-rar-compressed                                                                | zip, rar            |

##### 4.5.5 게시판별 특수 필드

| 스킨 타입 | 특수 필드                              | 설명               |
| --------- | -------------------------------------- | ------------------ |
| FAQ       | `answer`                               | 답변 내용          |
| QNA       | `answer`, `answerWriter`, `answerDate` | 관리자 답변 정보   |
| PRESS     | `source`, `sourceLink`, `pressDate`    | 보도자료 출처 정보 |
| FORM      | `customFields`                         | 커스텀 필드 (JSON) |

---

### 5. Response Wrapper

```jsonc
// Success Response
{
  "success": true,
  "message": "요청이 성공적으로 처리되었습니다.",
  "data": {
    // 응답 데이터
  }
}

// Error Response
{
  "success": false,
  "message": "게시판을 찾을 수 없습니다.",
  "errorCode": "BBS_NOT_FOUND",
  "stackTrace": "..." // 개발 환경에서만 표시
}
```

---

### 6. Database DDL (현재 구현)

```sql
-- 게시판 마스터
CREATE TABLE BBS_MASTER (
  BBS_ID INT UNSIGNED PRIMARY KEY AUTO_INCREMENT COMMENT 'PK: 게시판 ID',
  BBS_NAME VARCHAR(100) NOT NULL COMMENT '게시판 이름',
  SKIN_TYPE ENUM('BASIC','FAQ','QNA','PRESS','FORM') NOT NULL COMMENT '게시판 스킨 유형',
  READ_AUTH VARCHAR(50) NOT NULL COMMENT '읽기 권한 코드',
  WRITE_AUTH VARCHAR(50) NOT NULL COMMENT '쓰기 권한 코드',
  ADMIN_AUTH VARCHAR(50) NOT NULL COMMENT '관리 권한 코드',
  DISPLAY_YN VARCHAR(1) DEFAULT 'Y' COMMENT '노출 여부',
  SORT_ORDER VARCHAR(1) DEFAULT 'D' COMMENT '게시판 정렬 순서(A=오름차순,D=내림차순)',
  NOTICE_YN VARCHAR(1) DEFAULT 'N' COMMENT '공지 여부(Y=공지,N=미공지)',
  PUBLISH_YN VARCHAR(1) DEFAULT 'N' COMMENT '게시 여부(Y=게시,N=미게시)',
  ATTACHMENT_YN VARCHAR(1) DEFAULT 'N' COMMENT '첨부파일 기능 사용여부',
  ATTACHMENT_LIMIT INT DEFAULT 0 COMMENT '첨부파일 최대 개수',
  ATTACHMENT_SIZE INT DEFAULT 0 COMMENT '첨부파일 최대 용량(MB)',
  CREATED_BY VARCHAR(36)  NULL COMMENT '생성자 ID',
  CREATED_IP VARCHAR(45)  NULL COMMENT '생성자 IP',
  CREATED_AT TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '생성 시각',
  UPDATED_BY VARCHAR(36)  NULL COMMENT '수정자 ID',
  UPDATED_IP VARCHAR(45)  NULL COMMENT '수정자 IP',
  UPDATED_AT TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '수정 시각'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='게시판 설정';

-- 카테고리 (게시판별 분류)
CREATE TABLE BBS_CATEGORY (
  CATEGORY_ID int(20) unsigned NOT NULL AUTO_INCREMENT,
  BBS_ID int(20) unsigned NOT NULL COMMENT 'FK: 게시판 ID',
  CODE varchar(50) NOT NULL COMMENT '카테고리 코드',
  NAME varchar(100) NOT NULL COMMENT '카테고리 이름',
  SORT_ORDER int(11) DEFAULT 0 COMMENT '카테고리 정렬 순서',
  DISPLAY_YN varchar(1) DEFAULT 'Y' COMMENT '노출 여부(Y,N)',
  CREATED_BY VARCHAR(36) NULL COMMENT '생성자 ID',
  CREATED_IP VARCHAR(45) NULL COMMENT '생성자 IP',
  CREATED_AT TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '생성 시각',
  UPDATED_BY VARCHAR(36) NULL COMMENT '수정자 ID',
  UPDATED_IP VARCHAR(45) NULL COMMENT '수정자 IP',
  UPDATED_AT TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '수정 시각',
  PRIMARY KEY (CATEGORY_ID),
  UNIQUE KEY UK_BBS_CATEGORY_CODE (BBS_ID, CODE),
  KEY IDX_CATEGORY_SORT (BBS_ID, SORT_ORDER, DISPLAY_YN),
  CONSTRAINT bbs_category_ibfk_1 FOREIGN KEY (BBS_ID) REFERENCES bbs_master (BBS_ID) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='게시판별 카테고리';

-- 게시글
CREATE TABLE BBS_ARTICLE (
  NTT_ID INT UNSIGNED PRIMARY KEY AUTO_INCREMENT COMMENT 'PK: 게시글 ID',
  BBS_ID INT UNSIGNED NOT NULL COMMENT 'FK: 게시판 ID',
  MENU_ID INT(11) NOT NULL COMMENT 'FK: 메뉴 ID',
  PARENT_NTT_ID INT UNSIGNED NULL COMMENT '부모 글 ID(답변형)',
  THREAD_DEPTH INT UNSIGNED DEFAULT 0 COMMENT '답변 깊이',
  WRITER VARCHAR(50) NOT NULL COMMENT '작성자',
  TITLE VARCHAR(255) NOT NULL COMMENT '제목',
  content TEXT COMMENT '내용',
  HAS_IMAGE_IN_CONTENT TINYINT(1) DEFAULT 0 COMMENT '내용 내 이미지 포함 여부 (0: false, 1: true)',
  NOTICE_STATE VARCHAR(1) DEFAULT 'N' COMMENT '공지 여부(Y=공지,N=미공지,P=영구공지)',
  NOTICE_START_DT DATETIME DEFAULT CURRENT_DATE COMMENT '공지 시작일',
  NOTICE_END_DT DATETIME DEFAULT (CURRENT_DATE + INTERVAL 1 DAY) COMMENT '공지 종료일',
  PUBLISH_STATE VARCHAR(1) DEFAULT 'N' COMMENT '게시 여부(Y=게시,N=미게시,P=영구게시)',
  PUBLISH_START_DT DATETIME DEFAULT CURRENT_DATE COMMENT '게시 시작일',
  PUBLISH_END_DT DATETIME DEFAULT (CURRENT_DATE + INTERVAL 1 DAY) COMMENT '게시 종료일',
  EXTERNAL_LINK VARCHAR(255) COMMENT '외부 링크 URL',
  HITS INT DEFAULT 0 COMMENT '조회수',
  CREATED_BY VARCHAR(36) NULL COMMENT '생성자 ID',
  CREATED_IP VARCHAR(45) NULL COMMENT '생성자 IP',
  CREATED_AT TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '생성 시각',
  UPDATED_BY VARCHAR(36) NULL COMMENT '수정자 ID',
  UPDATED_IP VARCHAR(45) NULL COMMENT '수정자 IP',
  UPDATED_AT TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '수정 시각',
  CONSTRAINT fk_bbs_article_master FOREIGN KEY (BBS_ID) REFERENCES BBS_MASTER(BBS_ID) ON DELETE CASCADE,
  CONSTRAINT fk_bbs_article_menu FOREIGN KEY (MENU_ID) REFERENCES MENU(id) ON DELETE CASCADE,
  CONSTRAINT fk_bbs_article_parent FOREIGN KEY (PARENT_NTT_ID) REFERENCES BBS_ARTICLE(NTT_ID) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='게시판 게시글';

-- 게시글-카테고리 매핑 (M:N)
CREATE TABLE BBS_ARTICLE_CATEGORY (
  `NTT_ID` int(20) unsigned NOT NULL COMMENT 'FK: 게시글 ID',
  `CATEGORY_ID` int(20) unsigned NOT NULL COMMENT 'FK: 카테고리 ID',
  PRIMARY KEY (`NTT_ID`,`CATEGORY_ID`),
  KEY `IDX_ARTICLE_CATEGORY` (`CATEGORY_ID`,`NTT_ID`),
  CONSTRAINT `bbs_article_category_ibfk_1` FOREIGN KEY (`NTT_ID`) REFERENCES `bbs_article` (`NTT_ID`) ON DELETE CASCADE,
  CONSTRAINT `bbs_article_category_ibfk_2` FOREIGN KEY (`CATEGORY_ID`) REFERENCES `bbs_category` (`CATEGORY_ID`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='게시글-카테고리 매핑';

-- 게시글 검색을 위한 인덱스 생성
CREATE INDEX idx_article_search ON bbs_article(title, content(255));

-- 게시글 첨부파일
CREATE TABLE BBS_ATTACHMENT (
  ATTACHMENT_ID INT UNSIGNED PRIMARY KEY AUTO_INCREMENT COMMENT 'PK: 첨부파일 ID',
  NTT_ID INT UNSIGNED NOT NULL COMMENT 'FK: 게시글 ID',
  ORIGIN_NAME VARCHAR(255) NOT NULL COMMENT '원본 파일명',
  SAVED_NAME VARCHAR(255) NOT NULL COMMENT '저장 파일명',
  MIME_TYPE VARCHAR(100) NOT NULL COMMENT 'MIME 타입',
  SIZE BIGINT NOT NULL COMMENT '파일 크기(bytes)',
  EXT VARCHAR(20) NOT NULL COMMENT '파일 확장자',
  FILE_ORDER INT DEFAULT 0 COMMENT '파일 순서',
  CREATED_BY VARCHAR(36) NULL COMMENT '생성자 ID',
  CREATED_IP VARCHAR(45) NULL COMMENT '생성자 IP',
  CREATED_AT TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '생성 시각',
  UPDATED_BY VARCHAR(36) NULL COMMENT '수정자 ID',
  UPDATED_IP VARCHAR(45) NULL COMMENT '수정자 IP',
  UPDATED_AT TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '수정 시각',
  CONSTRAINT fk_bbs_attachment_article FOREIGN KEY (NTT_ID) REFERENCES BBS_ARTICLE(NTT_ID) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='게시글 첨부파일';
```

---

### 7. Front-End Guidelines (현재 구현)

| Skin  | Route 예                          | 비고                      |
| ----- | --------------------------------- | ------------------------- |
| FAQ   | `/faq` (same-page accordion)      | JS toggle, no `/detail`   |
| QNA   | `/qna` 목록 ⇢ `/qna/{nttId}` 상세 | `parentNttId`로 답변 연동 |
| PRESS | `/press` ⇢ `/press/{nttId}`       | 카드 UI                   |
| BASIC | `/notice` ⇢ detail                | 표 UI                     |
| FORM  | `/form` ⇢ `/form/{nttId}`         | 커스텀 필드 지원          |

---

### 8. Change-Log (현재 구현)

| ver | date       | 내용                                                 |
| --- | ---------- | ---------------------------------------------------- |
| 1.0 | 2024-03-21 | 기본 게시판 기능 구현 (BASIC, FAQ, QNA, PRESS, FORM) |

---

### 9. Example cURL (현재 구현)

```bash
# 게시판 생성
curl -H "Authorization: Bearer $TK" \
     -H "Content-Type: application/json" \
     -d '{"bbsName":"공지사항","skinType":"BASIC","readAuth":"PUBLIC","writeAuth":"STAFF","adminAuth":"ADMIN"}' \
     https://cms.example.com/cms/bbs/master

# 게시글 등록
curl -H "Authorization: Bearer $TK" \
     -H "Content-Type: application/json" \
     -d '{"bbsId":5,"title":"공지","content":"<p>...</p>","writer":"홍길동"}' \
     https://cms.example.com/cms/bbs/article

# 첨부파일 업로드
curl -H "Authorization: Bearer $TK" \
     -F "files=@./example.pdf" \
     https://cms.example.com/cms/bbs/article/123/attach

# 비로그인 질문 작성
curl -H "Content-Type: application/json" \
     -d '{"bbsId":5,"title":"비밀번호 찾기","content":"비밀번호를 잊어버렸습니다.","writer":"홍길동","email":"user@example.com"}' \
     https://cms.example.com/cms/bbs/article/anonymous
```

---

### ✅ 현재 구현 상태 요약

1. **구현 완료**

   - 게시판 CRUD (마스터)
   - 게시글 CRUD
   - 기본 권한 관리
   - 5가지 스킨 타입 (BASIC, FAQ, QNA, PRESS, FORM)
   - 페이지네이션
   - 검색 기능 (제목, 내용)
   - 정렬 기능
   - 카테고리 관리
   - 게시글 상태 관리 (공지, 게시)
   - 작성자 추적 (ID, IP)
   - 첨부파일 관리
   - 비로그인 질문 작성
   - 에러 코드 체계

2. **미구현 기능**

   - 커스텀 필드
   - 알림 기능
   - 상단/하단 컨텐츠

3. **API 경로**

   - `/cms` prefix 사용
   - 게시글 API는 `/article`로 분리

4. **데이터베이스**

   - 기본 테이블 구조 구현
   - 첨부파일 테이블 구현
   - 검색 인덱스 구현

---

### 🔄 향후 개선 계획

1. 커스텀 필드 지원
2. 알림 기능 구현
3. 상단/하단 컨텐츠 추가

이 문서는 현재 구현된 기능을 기준으로 작성되었으며, 향후 기능 추가 시 업데이트 예정입니다.

### [게시글 메뉴 ID(menuId) 연동 내역 및 가이드]

#### 1. 데이터베이스

- `BBS_ARTICLE` 테이블에 `MENU_ID INT(11) NOT NULL COMMENT 'FK: 메뉴 ID'` 컬럼 추가
- `FOREIGN KEY (MENU_ID) REFERENCES MENU(id) ON DELETE CASCADE` 제약조건 추가

#### 2. Entity/Domain

- `BbsArticleDomain`에 `@ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "menu_id", nullable = false) private Menu menu;` 필드 추가

#### 3. DTO

- `BbsArticleDto`에 `@NotNull private Long menuId;` 필드 추가

#### 4. Repository/Query

- menuId로 게시글을 조회하는 JPA 메서드(`findByMenuId`) 추가

#### 5. Service/비즈니스 로직

- 게시글 생성/수정 시 menuId로 Menu 엔티티를 조회하여 연관관계로 저장
- DTO ↔ Entity 변환 시 menuId 매핑

#### 6. Controller/API

- 게시글 생성/수정/조회 API에서 menuId를 요청/응답에 포함

#### 7. 프론트엔드/테스트

- 게시글 생성/수정/조회 시 menuId를 포함하여 API를 호출 및 검증

---

#### [예시: 게시글 생성 요청]

```jsonc
POST /cms/bbs/article
{
  "bbsId": 1,
  "menuId": 8,
  "title": "2024년 휴강 안내",
  "content": "<p>내용…</p>",
  "writer": "홍길동"
  // ... 기타 필드 ...
}
```

#### [예시: 게시글 응답]

```jsonc
{
  "nttId": 123,
  "bbsId": 1,
  "menuId": 8,
  "title": "2024년 휴강 안내",
  "content": "<p>내용…</p>",
  "writer": "홍길동"
  // ... 기타 필드 ...
}
```

#### [DDL 예시]

```sql
CREATE TABLE BBS_ARTICLE (
  NTT_ID INT UNSIGNED PRIMARY KEY AUTO_INCREMENT COMMENT 'PK: 게시글 ID',
  BBS_ID INT UNSIGNED NOT NULL COMMENT 'FK: 게시판 ID',
  MENU_ID INT(11) NOT NULL COMMENT 'FK: 메뉴 ID',
  -- ... 기타 컬럼 ...
  FOREIGN KEY (BBS_ID) REFERENCES BBS_MASTER(BBS_ID) ON DELETE CASCADE,
  FOREIGN KEY (MENU_ID) REFERENCES MENU(id) ON DELETE CASCADE
);
```

---

> **NOTE:**
> 게시글 관련 모든 로직(엔티티, DTO, 서비스, 컨트롤러, 쿼리, 테스트, 프론트엔드)에 menuId가 반영되어야 하며, 신규 게시글 생성 시 반드시 menuId를 지정해야 합니다.
