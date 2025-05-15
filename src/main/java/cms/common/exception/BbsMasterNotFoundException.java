package cms.common.exception;
 
public class BbsMasterNotFoundException extends RuntimeException {
    public BbsMasterNotFoundException(Long bbsId) {
        super("게시판을 찾을 수 없습니다. (bbsId: " + bbsId + ")");
    }
} 