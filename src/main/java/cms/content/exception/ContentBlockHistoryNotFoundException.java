package cms.content.exception;

import cms.common.exception.ErrorCode;
import cms.common.exception.ResourceNotFoundException;

public class ContentBlockHistoryNotFoundException extends ResourceNotFoundException {
    public ContentBlockHistoryNotFoundException(Long id) {
        super("ContentBlockHistory (ID: " + id + ")을(를) 찾을 수 없습니다.", ErrorCode.CONTENT_BLOCK_HISTORY_NOT_FOUND);
    }
}