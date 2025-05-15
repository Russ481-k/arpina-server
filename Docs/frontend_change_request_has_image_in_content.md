## Frontend Change Request: Display Image Icon for Articles with Images in Content

**Date:** 2024-05-08

**Backend API Updated:** Board Article API (`/cms/bbs/article`)

**Author:** AI Assistant (Gemini)

---

### 1. Overview

To enhance user experience in the board list view, a new feature has been implemented in the backend to indicate whether an article's content contains images (`<img>` tags). This allows the frontend to display an icon (e.g., a small picture icon) next to the article title if it includes embedded images.

### 2. Backend API Change Details

The `BbsArticleDto` returned by the article-related endpoints (e.g., when fetching a list of articles or a single article) now includes a new boolean field:

- **Field Name:** `hasImageInContent`
- **Type:** `boolean`
- **Description:**
  - `true`: Indicates that the article's HTML content (`content` field) contains one or more `<img>` tags.
  - `false`: Indicates that the article's content does not contain any `<img>` tags.
- **Determination:** This flag is set automatically by the backend during article creation and updates by parsing the `content` field.

#### Updated `BbsArticleDto` Example Snippet:

```json
{
  "nttId": 123,
  "bbsId": 5,
  "menuId": 8,
  "writer": "홍길동",
  "title": "사진이 포함된 게시물입니다",
  "content": "<p>여기에 내용이 들어갑니다. <img src='/path/to/image.jpg' alt='예시 이미지'></p>",
  "hasImageInContent": true, // <-- NEW FIELD
  "noticeState": "N",
  // ... other existing fields ...
  "createdAt": "2024-03-21T10:00:00+09:00",
  "updatedAt": "2024-03-21T10:00:00+09:00",
  "attachments": [
    {
      "fileId": 789,
      "originName": "document.pdf"
      // ... other attachment fields
    }
  ]
}
```

### 3. Frontend Implementation Guide

#### 3.1. Board List View

- When rendering the list of articles (e.g., in `BASIC`, `PRESS`, `QNA` skin types), check the `hasImageInContent` field for each article.
- If `article.hasImageInContent` is `true`, display an appropriate icon (e.g., 🖼️, 📷, or a specific image icon from your icon library) next to the article title or in a designated area within the list item.
- This provides users with a quick visual cue about the nature of the article's content before clicking into the detail view.

#### Example Pseudo-code (JavaScript/React):

```javascript
function ArticleListItem({ article }) {
  return (
    <div class="article-item">
      <h3>
        {article.title}
        {article.hasImageInContent && <span class="image-icon">🖼️</span>}
        {/* Optionally, also show attachment icon if article.attachments.length > 0 */}
        {article.attachments && article.attachments.length > 0 && (
          <span class="attachment-icon">📎</span>
        )}
      </h3>
      <p>작성자: {article.writer}</p>
      {/* ... other list item details ... */}
    </div>
  );
}

function ArticleList({ articles }) {
  return (
    <div class="article-list">
      {articles.map((article) => (
        <ArticleListItem key={article.nttId} article={article} />
      ))}
    </div>
  );
}
```

#### 3.2. Considerations

- **Icon Choice:** Select an icon that is clear, unobtrusive, and consistent with the application's UI/UX design.
- **Accessibility:** Ensure the icon has appropriate ARIA labels or alt text if it conveys information not otherwise available textually, or is purely decorative if the information is redundant.
- **Styling:** Style the icon appropriately (size, color, spacing) to fit well within the board list layout.

### 4. Action Required

- Update frontend data models/interfaces for `BbsArticleDto` to include the new `hasImageInContent` field.
- Implement the logic to display the image icon in the board list views based on this new field.
- Test thoroughly across different board skin types and scenarios (articles with and without images, with and without attachments).

---

Please coordinate with the backend team if any clarifications are needed.
