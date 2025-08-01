# 게시판 카테고리별 조회 및 검색 기능 수정 완료

## 수정 배경

기존 게시판 시스템에서 다음과 같은 문제가 발생했습니다:

1. **카테고리별 조회 불가:** 특정 카테고리 선택 시 게시글이 조회되지 않음
2. **카테고리 내 검색 미지원:** 검색어와 카테고리를 동시에 사용할 수 없음
3. **페이지네이션 시 카테고리 정보 손실:** 페이지 이동 시 카테고리 선택이 초기화됨

## 수정 내용

### 1. 컨트롤러 로직 개선 (`BbsArticleController.java`)

**기존 코드:**

```java
if (keyword != null && !keyword.trim().isEmpty()) {
    articles = bbsArticleService.searchArticles(bbsId, menuId, keyword, pageable, isAdmin);
} else if (categoryId != null) {
    articles = bbsArticleService.getArticles(bbsId, menuId, categoryId, pageable, isAdmin);
} else {
    articles = bbsArticleService.getArticles(bbsId, menuId, pageable, isAdmin);
}
```

**수정된 코드:**

```java
boolean hasKeyword = keyword != null && !keyword.trim().isEmpty();
boolean hasCategoryId = categoryId != null;

if (hasKeyword && hasCategoryId) {
    // 카테고리 내 검색
    articles = bbsArticleService.searchArticlesInCategory(bbsId, menuId, categoryId, keyword, pageable, isAdmin);
} else if (hasKeyword) {
    // 전체 검색
    articles = bbsArticleService.searchArticles(bbsId, menuId, keyword, pageable, isAdmin);
} else if (hasCategoryId) {
    // 카테고리 필터링
    articles = bbsArticleService.getArticles(bbsId, menuId, categoryId, pageable, isAdmin);
} else {
    // 전체 조회
    articles = bbsArticleService.getArticles(bbsId, menuId, pageable, isAdmin);
}
```

### 2. 서비스 인터페이스 확장 (`BbsArticleService.java`)

새로운 메소드 추가:

```java
Page<BbsArticleDto> searchArticlesInCategory(Long bbsId, Long menuId, Long categoryId, String keyword, Pageable pageable, boolean isAdmin);
```

### 3. 서비스 구현부 추가 (`BbsArticleServiceImpl.java`)

카테고리 내 검색 기능 구현:

```java
@Override
@Transactional(readOnly = true)
public Page<BbsArticleDto> searchArticlesInCategory(Long bbsId, Long menuId, Long categoryId, String keyword, Pageable pageable, boolean isAdmin) {
    Page<BbsArticleDomain> articlesPage;
    if (isAdmin) {
        articlesPage = bbsArticleRepository.searchAllByKeywordAndMenuIdAndCategoryId(bbsId, menuId, categoryId, keyword, pageable);
    } else {
        articlesPage = bbsArticleRepository.searchPublishedByKeywordAndMenuIdAndCategoryId(bbsId, menuId, categoryId, keyword, pageable);
    }
    return toDtoPageWithArticleNumber(articlesPage, pageable);
}
```

### 4. 리포지토리 쿼리 메소드 추가 (`BbsArticleRepository.java`)

카테고리 내 검색을 위한 JPQL 쿼리 추가:

```java
@Query("SELECT a FROM BbsArticleDomain a JOIN a.categories ac WHERE a.bbsMaster.bbsId = :bbsId AND a.menu.id = :menuId AND ac.category.categoryId = :categoryId AND (a.title LIKE %:keyword% OR a.content LIKE %:keyword% OR a.writer LIKE %:keyword% OR FUNCTION('TO_CHAR', a.postedAt, 'YYYY-MM-DD') LIKE %:keyword%) AND a.publishState IN ('Y', 'P') ORDER BY a.noticeState DESC, a.postedAt DESC")
Page<BbsArticleDomain> searchPublishedByKeywordAndMenuIdAndCategoryId(@Param("bbsId") Long bbsId, @Param("menuId") Long menuId, @Param("categoryId") Long categoryId, @Param("keyword") String keyword, Pageable pageable);

@Query("SELECT a FROM BbsArticleDomain a JOIN a.categories ac WHERE a.bbsMaster.bbsId = :bbsId AND a.menu.id = :menuId AND ac.category.categoryId = :categoryId AND (a.title LIKE %:keyword% OR a.content LIKE %:keyword% OR a.writer LIKE %:keyword% OR FUNCTION('TO_CHAR', a.postedAt, 'YYYY-MM-DD') LIKE %:keyword%) ORDER BY a.noticeState DESC, a.postedAt DESC")
Page<BbsArticleDomain> searchAllByKeywordAndMenuIdAndCategoryId(@Param("bbsId") Long bbsId, @Param("menuId") Long menuId, @Param("categoryId") Long categoryId, @Param("keyword") String keyword, Pageable pageable);
```

## 기대 효과

1. **카테고리별 조회 정상화:** 특정 카테고리 선택 시 해당 카테고리의 게시글만 정확히 조회됩니다.
2. **카테고리 내 검색 지원:** 특정 카테고리 내에서 키워드 검색이 가능합니다.
3. **통합된 필터링:** `categoryId`와 `keyword` 파라미터가 독립적으로 동작하며, 필요에 따라 조합하여 사용할 수 있습니다.
4. **관리자/일반 사용자 권한 분리:** 기존과 동일하게 관리자는 모든 게시글을, 일반 사용자는 게시된 게시글만 조회할 수 있습니다.

## API 사용 예시

### 전체 조회

```
GET /api/v1/cms/bbs/article?bbsId=1&menuId=79&page=0&size=10&sort=createdAt,desc
```

### 카테고리별 조회

```
GET /api/v1/cms/bbs/article?bbsId=1&menuId=79&categoryId=4&page=0&size=10&sort=createdAt,desc
```

### 전체 검색

```
GET /api/v1/cms/bbs/article?bbsId=1&menuId=79&keyword=공지&page=0&size=10&sort=createdAt,desc
```

### 카테고리 내 검색

```
GET /api/v1/cms/bbs/article?bbsId=1&menuId=79&categoryId=4&keyword=공지&page=0&size=10&sort=createdAt,desc
```

## 검증

수정 후 다음과 같은 시나리오를 테스트하여 정상 동작을 확인할 수 있습니다:

1. 카테고리 없이 전체 게시글 조회
2. 특정 카테고리만 선택하여 조회
3. 키워드만으로 전체 검색
4. 특정 카테고리 내에서 키워드 검색
5. 각 경우의 페이지네이션 동작 확인

---

**수정 완료일:** 2025년 1월 27일  
**수정자:** AI Assistant  
**관련 파일:**

- `cms/board/controller/BbsArticleController.java`
- `cms/board/service/BbsArticleService.java`
- `cms/board/service/impl/BbsArticleServiceImpl.java`
- `cms/board/repository/BbsArticleRepository.java`
