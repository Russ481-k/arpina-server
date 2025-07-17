package cms.content.exception;

import cms.common.exception.ErrorCode;
import cms.common.exception.ResourceNotFoundException;

public class ContentBlockNotFoundException extends ResourceNotFoundException {
    public ContentBlockNotFoundException(Long id) {
        super("ContentBlock (ID: " + id + ")을(를) 찾을 수 없습니다.", ErrorCode.CONTENT_BLOCK_NOT_FOUND);
    }
}