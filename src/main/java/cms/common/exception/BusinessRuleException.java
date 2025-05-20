package cms.common.exception;

import org.springframework.http.HttpStatus;

public class BusinessRuleException extends CustomBaseException {
    
    // Constructor using a specific ErrorCode (recommended)
    public BusinessRuleException(ErrorCode errorCode) {
        super(errorCode.getDefaultMessage(), errorCode, HttpStatus.BAD_REQUEST); // Default to BAD_REQUEST for business rule violations
    }

    // Constructor to override the default message from ErrorCode
    public BusinessRuleException(String customMessage, ErrorCode errorCode) {
        super(customMessage, errorCode, HttpStatus.BAD_REQUEST);
    }
    
    // Constructor to specify a different HttpStatus if needed (e.g., CONFLICT for duplicate data)
    public BusinessRuleException(String customMessage, ErrorCode errorCode, HttpStatus httpStatus) {
        super(customMessage, errorCode, httpStatus);
    }

    // Constructor to use ErrorCode and also provide additional detail for logging
    public BusinessRuleException(ErrorCode errorCode, String detailMessage) {
        super(errorCode.getDefaultMessage(), errorCode, HttpStatus.BAD_REQUEST, detailMessage);
    }

    // Constructor for ErrorCode and HttpStatus (to fix linter error)
    public BusinessRuleException(ErrorCode errorCode, HttpStatus httpStatus) {
        super(errorCode.getDefaultMessage(), errorCode, httpStatus);
    }

    // Constructor that accepts a cause
    public BusinessRuleException(String customMessage, ErrorCode errorCode, Throwable cause) {
        super(customMessage, errorCode, HttpStatus.BAD_REQUEST, cause);
    }

    public BusinessRuleException(String customMessage, ErrorCode errorCode, HttpStatus httpStatus, Throwable cause) {
        super(customMessage, errorCode, httpStatus, cause);
    }
} 