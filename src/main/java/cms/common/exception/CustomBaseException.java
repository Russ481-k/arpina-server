package cms.common.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public abstract class CustomBaseException extends RuntimeException {
    private final ErrorCode errorCode;
    private final HttpStatus httpStatus;
    private final String detailMessage; // Optional: For additional details not meant for direct user display but for
                                        // logging

    public CustomBaseException(ErrorCode errorCode, HttpStatus httpStatus) {
        super(errorCode.getDefaultMessage());
        this.errorCode = errorCode;
        this.httpStatus = httpStatus;
        this.detailMessage = null;
    }

    // Constructor to override the default message from ErrorCode
    public CustomBaseException(String customMessage, ErrorCode errorCode, HttpStatus httpStatus) {
        super(customMessage);
        this.errorCode = errorCode;
        this.httpStatus = httpStatus;
        this.detailMessage = null;
    }

    // Constructor to add more detailed information, e.g. for logging or specific
    // cases
    public CustomBaseException(String customMessage, ErrorCode errorCode, HttpStatus httpStatus, String detailMessage) {
        super(customMessage);
        this.errorCode = errorCode;
        this.httpStatus = httpStatus;
        this.detailMessage = detailMessage;
    }

    // Constructor with a cause
    public CustomBaseException(String customMessage, ErrorCode errorCode, HttpStatus httpStatus, Throwable cause) {
        super(customMessage, cause);
        this.errorCode = errorCode;
        this.httpStatus = httpStatus;
        this.detailMessage = null;
    }

    public CustomBaseException(String customMessage, ErrorCode errorCode, HttpStatus httpStatus, String detailMessage,
            Throwable cause) {
        super(customMessage, cause);
        this.errorCode = errorCode;
        this.httpStatus = httpStatus;
        this.detailMessage = detailMessage;
    }
}