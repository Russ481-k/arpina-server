package cms.common.exception;
 
public class BbsArticleNotFoundException extends RuntimeException {
    public BbsArticleNotFoundException(Long nttId) {
        super("게시글을 찾을 수 없습니다. (nttId: " + nttId + ")");
    }
} 