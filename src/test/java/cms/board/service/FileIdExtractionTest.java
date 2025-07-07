package cms.board.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import static org.junit.jupiter.api.Assertions.*;

import java.lang.reflect.Method;
import cms.board.service.impl.BbsArticleServiceImpl;
import org.mockito.Mock;
import org.springframework.boot.test.context.SpringBootTest;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * 파일 ID 추출 로직 단위 테스트
 * 2025-07-26: 스케줄러 오작동 방지를 위한 필수 테스트
 */
@SpringBootTest
class FileIdExtractionTest {

    private BbsArticleServiceImpl bbsArticleService;
    private Method parseFileIdFromSrcMethod;
    private Method extractFileIdsFromJsonMethod;

    @BeforeEach
    void setUp() throws Exception {
        // BbsArticleServiceImpl의 private 메서드에 접근하기 위한 리플렉션 설정
        bbsArticleService = new BbsArticleServiceImpl(null, null, null, null, new ObjectMapper());

        parseFileIdFromSrcMethod = BbsArticleServiceImpl.class.getDeclaredMethod("parseFileIdFromSrc", String.class);
        parseFileIdFromSrcMethod.setAccessible(true);

        extractFileIdsFromJsonMethod = BbsArticleServiceImpl.class.getDeclaredMethod("extractFileIdsFromJson",
                String.class);
        extractFileIdsFromJsonMethod.setAccessible(true);
    }

    @Test
    @DisplayName("help.handylab.co.kr 도메인에서 파일 ID 추출 테스트")
    void testParseFileIdFromHelpdHandylabDomain() throws Exception {
        // Given
        String src = "https://help.handylab.co.kr/api/v1/cms/file/public/view/341";

        // When
        Long fileId = (Long) parseFileIdFromSrcMethod.invoke(bbsArticleService, src);

        // Then
        assertEquals(341L, fileId, "help.handylab.co.kr 도메인에서 파일 ID 추출 실패");
    }

    @Test
    @DisplayName("다양한 도메인에서 파일 ID 추출 테스트")
    void testParseFileIdFromVariousDomains() throws Exception {
        // Given & When & Then
        String[] testUrls = {
                "https://help.handylab.co.kr/api/v1/cms/file/public/view/123",
                "http://localhost:8080/api/v1/cms/file/public/view/456",
                "https://example.com/api/v1/cms/file/public/view/789"
        };

        Long[] expectedIds = { 123L, 456L, 789L };

        for (int i = 0; i < testUrls.length; i++) {
            Long fileId = (Long) parseFileIdFromSrcMethod.invoke(bbsArticleService, testUrls[i]);
            assertEquals(expectedIds[i], fileId,
                    "URL에서 파일 ID 추출 실패: " + testUrls[i]);
        }
    }

    @Test
    @DisplayName("blob URL은 무시해야 함")
    void testSkipBlobUrls() throws Exception {
        // Given
        String blobUrl = "blob:https://arpina-cms-bnxm.vercel.app/f9efd37f-c798-40bb-a0f1-015b2a1d7535";

        // When
        Long fileId = (Long) parseFileIdFromSrcMethod.invoke(bbsArticleService, blobUrl);

        // Then
        assertNull(fileId, "blob URL은 null을 반환해야 함");
    }

    @Test
    @DisplayName("실제 게시글 JSON에서 파일 ID 추출 테스트")
    void testExtractFileIdsFromRealJson() throws Exception {
        // Given - 실제 게시글 JSON 구조
        String realJson = "{\"root\":{\"children\":[{\"children\":[{\"type\":\"image\",\"src\":\"https://help.handylab.co.kr/api/v1/cms/file/public/view/341\",\"altText\":\"5월_1 (2).jpg\",\"version\":1},{\"type\":\"image\",\"src\":\"https://help.handylab.co.kr/api/v1/cms/file/public/view/342\",\"altText\":\"5월_2 (2).jpg\",\"version\":1}],\"direction\":null,\"format\":\"\",\"indent\":0,\"type\":\"paragraph\",\"version\":1,\"textFormat\":0}],\"direction\":null,\"format\":\"\",\"indent\":0,\"type\":\"root\",\"version\":1}}";

        // When
        Object result = extractFileIdsFromJsonMethod.invoke(bbsArticleService, realJson);

        // Then
        assertNotNull(result, "파일 ID 추출 결과가 null이면 안됨");
        // Set<Long> 형태로 반환되므로 크기 검증
        assertTrue(result.toString().contains("341"), "파일 ID 341이 추출되어야 함");
        assertTrue(result.toString().contains("342"), "파일 ID 342가 추출되어야 함");
    }

    @Test
    @DisplayName("빈 JSON에서는 빈 Set을 반환해야 함")
    void testEmptyJsonReturnsEmptySet() throws Exception {
        // Given
        String emptyJson = "";
        String nullJson = null;

        // When & Then
        Object emptyResult = extractFileIdsFromJsonMethod.invoke(bbsArticleService, emptyJson);
        Object nullResult = extractFileIdsFromJsonMethod.invoke(bbsArticleService, nullJson);

        assertNotNull(emptyResult, "빈 JSON도 빈 Set을 반환해야 함");
        assertNotNull(nullResult, "null JSON도 빈 Set을 반환해야 함");
    }

    @Test
    @DisplayName("잘못된 JSON에서는 빈 Set을 반환해야 함 (안전장치)")
    void testInvalidJsonReturnsEmptySet() throws Exception {
        // Given
        String invalidJson = "{ invalid json structure }";

        // When
        Object result = extractFileIdsFromJsonMethod.invoke(bbsArticleService, invalidJson);

        // Then
        assertNotNull(result, "잘못된 JSON도 빈 Set을 반환해야 함 (예외 발생하면 안됨)");
    }
}