# 팝업 API 명세서 (파일 업로드 포함)

이 문서는 팝업 관리 API에 Lexical Editor 콘텐츠 내 이미지/미디어 파일 업로드 기능을 통합하는 방안을 설명합니다.
기존 시스템의 `BbsArticle` (게시판 게시글) 기능의 파일 처리 로직을 분석하여 이를 팝업 관리에 적용합니다.

> **기반 로직 분석 (`BbsArticle`)**
>
> 1.  **Controller (`BbsArticleController`)**: `multipart/form-data` 요청을 받아 게시글 데이터(JSON), 에디터 콘텐츠(JSON 문자열), 미디어 파일(`List<MultipartFile>`)을 각각 `@RequestPart`로 분리하여 처리합니다.
> 2.  **Service (`BbsArticleService`)**:
>     - `FileService`를 호출하여 미디어 파일을 스토리지에 저장하고, 파일 정보를 DB(`CmsFile`)에 기록합니다. 이때 파일은 `'EDITOR_EMBEDDED_MEDIA'`라는 `menu` 타입과 게시글 ID(`nttId`)로 연결됩니다.
>     - 프론트엔드 에디터에서 생성된 임시 `localId`를 `FileService`가 반환한 영구적인 `fileId`로 교체한 후, 에디터 콘텐츠(JSON)를 DB에 저장합니다.
>     - 게시글 수정 시, 업데이트된 콘텐츠의 `fileId` 목록과 기존 `fileId` 목록을 비교하여 삭제된 파일을 스토리지와 DB에서 제거합니다.
> 3.  **FileService**: 파일 저장, 삭제, 조회 등 파일 관련 로직을 추상화하여 처리합니다.

---

## 팝업 API 변경 사항

팝업의 `content` 필드는 Lexical Editor를 사용하므로, 게시글과 동일한 방식으로 미디어 파일을 처리해야 합니다.
이를 위해 생성 및 수정 API의 `Content-Type`을 `application/json`에서 `multipart/form-data`로 변경하고 요청 구조를 수정합니다.

---

### ✅ 1. [POST] 팝업 등록 (파일 업로드 지원)

- **URL**: `/api/admin/popups`
- **Method**: `POST`
- **Content-Type**: `multipart/form-data`
- **Request Parts**:

| Part Name       | Type                  | Description                                                                                                                                  |
| :-------------- | :-------------------- | :------------------------------------------------------------------------------------------------------------------------------------------- |
| `popupData`     | `application/json`    | 팝업의 메타 데이터 (title, start_date, end_date 등). **`content` 필드는 여기서 제외**됩니다.                                                 |
| `content`       | `text/plain`          | Lexical Editor에서 생성된 전체 JSON 문자열. 내부 이미지 노드에는 프론트에서 생성한 임시 `localId`가 포함될 수 있습니다.                      |
| `mediaFiles`    | `List<MultipartFile>` | 에디터 콘텐츠에 포함된 미디어 파일(이미지, 영상 등). `mediaLocalIds`와 순서 및 개수가 일치해야 합니다. (Optional)                            |
| `mediaLocalIds` | `String[]`            | `mediaFiles`에 포함된 각 파일에 대한 프론트엔드 임시 ID 배열. 서버는 이 ID를 실제 `fileId`로 매핑하여 `content`를 업데이트합니다. (Optional) |

- **Request Example (`curl`)**:

```bash
curl -X POST /api/admin/popups \
-F "popupData={ \"title\": \"새 이벤트 팝업\", \"start_date\": \"2025-07-01T00:00:00\", \"end_date\": \"2025-07-15T23:59:59\", \"is_visible\": true, \"display_order\": 1 };type=application/json" \
-F "content=@/path/to/your/content.json;type=text/plain" \
-F "mediaFiles=@/path/to/your/image1.png" \
-F "mediaFiles=@/path/to/your/image2.jpg" \
-F "mediaLocalIds=local-id-1" \
-F "mediaLocalIds=local-id-2"
```

- **Response**: `201 Created`

```json
{ "id": 10, "message": "팝업이 성공적으로 등록되었습니다." }
```

---

### ✅ 2. [PUT] 팝업 수정 (파일 업로드 지원)

- **URL**: `/api/admin/popups/{id}`
- **Method**: `PUT`
- **Content-Type**: `multipart/form-data`
- **Request Parts**: `POST`와 동일한 구조를 사용합니다. 수정되지 않은 필드도 모두 포함하여 전송하는 것을 원칙으로 합니다.

- **수정 로직 핵심**:

  1.  **신규 파일 업로드**: 요청에 `mediaFiles`가 포함된 경우, 신규 파일을 `FileService`를 통해 업로드합니다.
  2.  **콘텐츠 업데이트**: `content` JSON 문자열의 `localId`를 신규 업로드된 파일의 `fileId`로 교체합니다.
  3.  **고아 파일(Orphaned File) 제거**:
      - 수정된 `content` JSON에서 현재 사용 중인 모든 `fileId`를 추출합니다.
      - DB에서 해당 팝업 ID(`popupId`)에 연결된 기존 `fileId` 목록을 조회합니다.
      - 기존 목록에는 있지만 새 목록에는 없는 `fileId`는 "고아 파일"로 간주하여 `FileService`를 통해 삭제 처리합니다.

- **Request Example (`curl`)**:

```bash
curl -X PUT /api/admin/popups/10 \
-F "popupData={ \"title\": \"(수정) 이벤트 팝업\", \"is_visible\": false };type=application/json" \
-F "content=@/path/to/your/updated_content.json;type=text/plain" \
# (필요시 mediaFiles, mediaLocalIds 파트 추가)
```

- **Response**: `200 OK`

```json
{ "message": "팝업이 성공적으로 수정되었습니다." }
```

---

### ✅ 3. [PATCH] 팝업 노출 여부 변경

- **URL**: `/api/admin/popups/{id}/visibility`
- **Method**: `PATCH`
- **Content-Type**: `application/json`
- **설명**: 특정 팝업의 `is_visible` 상태만 간단히 토글(변경)합니다. 관리자 페이지의 목록에서 스위치 UI와 함께 사용하기에 적합합니다.
- **Request Body**:

```json
{
  "is_visible": true
}
```

- **Response**: `200 OK`

---

### ✅ 4. [PATCH] 팝업 순서 일괄 변경

- **URL**: `/api/admin/popups/order`
- **Method**: `PATCH`
- **Content-Type**: `application/json`
- **설명**: 관리자 페이지에서 드래그 앤 드롭으로 변경된 팝업의 순서 전체를 업데이트합니다.
- **Request Body**:

```json
{
  "orderedIds": [3, 1, 2, 5]
}
```

- **Response**: `200 OK`

---

## 팝업 백엔드 구현 제안

1.  **`Popup` 도메인/엔티티**: 기존 테이블 명세에 따라 `Popup` 엔티티 클래스를 정의합니다.
2.  **`PopupService` 생성**:
    - `FileService`를 주입받아 파일 관련 로직을 처리합니다.
    - `createPopup`, `updatePopup` 메서드를 구현합니다.
3.  **`createPopup` 로직**:
    - `popupData`를 `Popup` 엔티티로 변환하여 DB에 1차 저장합니다 (ID 생성 목적).
    - `mediaFiles`가 있는 경우, `fileService.uploadFiles("POPUP_CONTENT", popupId, mediaFiles)`를 호출하여 파일을 저장하고 `CmsFile` 목록을 받습니다.
    - `mediaLocalIds`와 반환된 `CmsFile` 목록을 매핑하여 `Map<String, Long> localIdToFileIdMap`을 생성합니다.
    - `content` JSON 문자열 내의 `localId`를 실제 `fileId`로 치환하는 유틸리티 메서드(`replaceLocalIdsInJson`)를 호출합니다.
    - 치환된 `content`를 `Popup` 엔티티에 업데이트하고 최종 저장합니다.
4.  **`updatePopup` 로직**:
    - 기존 팝업 정보를 조회합니다.
    - `content` JSON에서 기존 `fileId`들을 모두 추출하여 `Set<Long> oldFileIds`에 저장합니다.
    - `createPopup`과 유사하게 신규 파일을 업로드하고 `content`를 업데이트합니다.
    - 업데이트된 `content`에서 현재 사용중인 `fileId`들을 모두 추출하여 `Set<Long> newFileIds`에 저장합니다.
    - `oldFileIds`에는 있지만 `newFileIds`에는 없는 ID들을 필터링하여 `fileService.deleteFile(fileId)`를 호출하여 고아 파일을 삭제합니다.
5.  **`PopupController`**:
    - `@PostMapping`과 `@PutMapping` (또는 `@PatchMapping`)에서 `consumes = MediaType.MULTIPART_FORM_DATA_VALUE`를 설정합니다.
    - `@RequestPart` 어노테이션을 사용하여 `popupData`, `content`, `mediaFiles`, `mediaLocalIds`를 각각의 파라미터로 받습니다.
    - `PopupService`의 해당 메서드를 호출하여 비즈니스 로직을 수행합니다.
    - 새로운 `PATCH` 엔드포인트(`visibility`, `order`)를 위한 메서드를 추가합니다.

---

### 기타 API (변경 없음)

- `GET /api/admin/popups` - 팝업 전체 목록 조회
- `GET /api/admin/popups/{id}` - 팝업 단건 조회
- `DELETE /api/admin/popups/{id}` - 팝업 삭제
- `PATCH /api/admin/popups/{id}/order` - 팝업 순서 변경
- `GET /api/popups/active` - 사용자 노출용 팝업 리스트

> **참고**: `DELETE /api/admin/popups/{id}` 구현 시, 해당 팝업에 연결된 모든 미디어 파일(`CmsFile`)도 함께 삭제하는 로직이 `PopupService`에 포함되어야 합니다.

---

### 기타 API 요약

- **[GET] `/api/admin/popups`**: 팝업 전체 목록 조회
- **[GET] `/api/admin/popups/{id}`**: 팝업 단건 조회
- **[DELETE] `/api/admin/popups/{id}`**: 팝업 삭제
- **[GET] `/api/popups/active`**: 사용자 노출용 팝업 리스트

> **참고**: `DELETE /api/admin/popups/{id}` 구현 시, 해당 팝업에 연결된 모든 미디어 파일(`CmsFile`)도 함께 삭제하는 로직이 `PopupService`에 포함되어야 합니다.
