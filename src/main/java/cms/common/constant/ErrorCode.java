package cms.common.constant;

public class ErrorCode {
    // 공통 에러
    public static final String INVALID_REQUEST = "INVALID_REQUEST";
    public static final String UNAUTHORIZED = "UNAUTHORIZED";
    public static final String FORBIDDEN = "FORBIDDEN";
    public static final String NOT_FOUND = "NOT_FOUND";
    public static final String CONFLICT = "CONFLICT";
    public static final String INTERNAL_ERROR = "INTERNAL_ERROR";

    // 게시판 관련 에러
    public static final String BBS_NOT_FOUND = "BBS_NOT_FOUND";
    public static final String ARTICLE_NOT_FOUND = "ARTICLE_NOT_FOUND";
    public static final String ATTACHMENT_NOT_FOUND = "ATTACHMENT_NOT_FOUND";
    public static final String ATTACHMENT_LIMIT_EXCEEDED = "ATTACHMENT_LIMIT_EXCEEDED";
    public static final String ATTACHMENT_SIZE_EXCEEDED = "ATTACHMENT_SIZE_EXCEEDED";
    public static final String INVALID_FILE_TYPE = "INVALID_FILE_TYPE";
    public static final String VERIFICATION_FAILED = "VERIFICATION_FAILED";
    public static final String VERIFICATION_EXPIRED = "VERIFICATION_EXPIRED";
} 