# 콘텐츠 관리 API 문서

이 문서는 웹사이트의 페이지 콘텐츠를 동적으로 관리하는 '콘텐츠 편집 기능'의 백엔드 API 사양을 정의합니다.

## 공통 사항

- **Base URL**: `/cms`
- **인증**: 모든 요청은 유효한 JWT 토큰을 `Authorization` 헤더에 `Bearer <token>` 형태로 포함해야 합니다.
- **성공 응답 형식**:
  ```json
  {
    "success": true,
    "message": "요청이 성공적으로 처리되었습니다.",
    "data": { ... } or [ ... ]
  }
  ```
- **오류 응답 형식**:
  ```json
  {
    "success": false,
    "message": "오류 메시지",
    "errorCode": "ERROR_CODE"
  }
  ```

---

## 1. 메인 페이지 콘텐츠 관리

웹사이트의 메인 페이지에 표시되는 콘텐츠를 관리합니다. 이 콘텐츠는 특정 메뉴에 종속되지 않습니다.

### 1.1 메인 페이지 콘텐츠 블록 목록 조회

메인 페이지의 모든 콘텐츠 블록을 순서(`sortOrder`)에 따라 정렬하여 조회합니다.

- **HTTP Method**: `GET`
- **Endpoint**: `/contents/main`
- **Success Response (200 OK)**:
  - `data` 필드에 콘텐츠 블록 객체의 배열이 포함됩니다. (객체 구조는 2.1 API 응답 참고)

### 1.2 메인 페이지 콘텐츠 블록 생성

메인 페이지에 새로운 콘텐츠 블록을 생성합니다.

- **HTTP Method**: `POST`
- **Endpoint**: `/contents/main`
- **Request Body**:
  - `ContentBlockCreateRequest` 객체 (2.2 API 참고)
- **Success Response (201 Created)**:
  - `data` 필드에 생성된 콘텐츠 블록 객체가 포함됩니다.

---

## 2. 메뉴별 콘텐츠 관리

특정 메뉴 페이지에 종속된 콘텐츠를 관리합니다.

### 2.1 메뉴의 콘텐츠 블록 목록 조회

특정 메뉴에 속한 모든 콘텐츠 블록을 순서(`sortOrder`)에 따라 정렬하여 조회합니다.

- **HTTP Method**: `GET`
- **Endpoint**: `/menus/{menuId}/contents`
- **Path Parameters**:
  - `menuId` (Long): 콘텐츠를 조회할 메뉴의 ID
- **Success Response (200 OK)**:
  ```json
  {
    "success": true,
    "message": "콘텐츠 블록 목록이 성공적으로 조회되었습니다.",
    "data": [
      {
        "id": 1,
        "menuId": 1,
        "type": "TEXT",
        "content": "<h1>환영합니다</h1><p>이것은 텍스트 블록입니다.</p>",
        "fileId": null,
        "fileUrl": null,
        "sortOrder": 1,
        "createdDate": "2025-07-17T10:00:00",
        "createdBy": "admin",
        "updatedDate": "2025-07-17T10:05:00",
        "updatedBy": "admin"
      }
    ]
  }
  ```
- **Error Responses**: `404 Not Found` (메뉴가 없을 경우)

### 2.2 메뉴에 콘텐츠 블록 생성

특정 메뉴에 새로운 콘텐츠 블록을 생성합니다.

- **HTTP Method**: `POST`
- **Endpoint**: `/menus/{menuId}/contents`
- **Path Parameters**:
  - `menuId` (Long): 콘텐츠 블록을 추가할 메뉴의 ID
- **Request Body**: `ContentBlockCreateRequest`
  ```json
  {
    "type": "TEXT",
    "content": "새로운 텍스트 콘텐츠",
    "fileId": null,
    "sortOrder": 3
  }
  ```
- **Success Response (201 Created)**:
  - `data` 필드에 생성된 콘텐츠 블록 객체가 포함됩니다.
- **Error Responses**: `404 Not Found` (메뉴 또는 파일이 없을 경우)

---

## 3. 공통 콘텐츠 블록 관리

### 3.1 콘텐츠 블록 수정

기존 콘텐츠 블록의 내용을 수정합니다. (메인, 메뉴 공통)

- **HTTP Method**: `PUT`
- **Endpoint**: `/contents/{contentId}`
- **Path Parameters**: `contentId` (Long)
- **Request Body**: `ContentBlockUpdateRequest`
  ```json
  {
    "type": "TEXT",
    "content": "수정된 텍스트 콘텐츠입니다.",
    "fileId": null
  }
  ```
- **Success Response (200 OK)**:
  - `data` 필드에 수정된 콘텐츠 블록 객체가 포함됩니다.

### 3.2 콘텐츠 블록 순서 변경

콘텐츠 블록들의 표시 순서를 일괄 변경합니다.

- **HTTP Method**: `PUT`
- **Endpoint**: `/contents/reorder`
- **Request Body**:
  ```json
  {
    "reorderItems": [
      { "id": 1, "sortOrder": 2 },
      { "id": 2, "sortOrder": 1 }
    ]
  }
  ```
- **Success Response (200 OK)**:
  - `data` 필드는 `null` 입니다.

### 3.3 콘텐츠 블록 삭제

콘텐츠 블록을 삭제합니다.

- **HTTP Method**: `DELETE`
- **Endpoint**: `/contents/{contentId}`
- **Success Response (200 OK)**:
  - `data` 필드는 `null` 입니다.

### 3.4 콘텐츠 블록 변경 이력 조회

특정 콘텐츠 블록의 변경 이력 목록을 조회합니다.

- **HTTP Method**: `GET`
- **Endpoint**: `/contents/{contentId}/history`
- **Success Response (200 OK)**:
  - `data` 필드에 히스토리 객체의 배열이 포함됩니다.

### 3.5 특정 버전으로 복원

콘텐츠 블록을 선택한 이력의 상태로 복원합니다.

- **HTTP Method**: `POST`
- **Endpoint**: `/contents/history/{historyId}/restore`
- **Success Response (200 OK)**:
  - `data` 필드에 복원된 콘텐츠 블록 객체가 포함됩니다.
