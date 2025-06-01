package cms.common.exception;

public class EmailSendingException extends Exception { // 또는 RuntimeException, 필요에 따라
    private final String errorCode;

    public EmailSendingException(String message, String errorCode) {
        super(message);
        this.errorCode = errorCode;
    }

    public EmailSendingException(String message, String errorCode, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }

    public String getErrorCode() {
        return errorCode;
    }
} 