package cms.common.exception;

import org.springframework.http.HttpStatus;

public class InvalidInputException extends CustomBaseException {

    public InvalidInputException(String message) {
        super(message, ErrorCode.INVALID_INPUT_VALUE, HttpStatus.BAD_REQUEST);
    }

    public InvalidInputException(String message, ErrorCode specificErrorCode) {
        super(message, specificErrorCode, HttpStatus.BAD_REQUEST);
        // Ensure the specificErrorCode is appropriate for an invalid input scenario
        if (specificErrorCode == null || !specificErrorCode.getCode().startsWith("C")) { // Example check
             // Log a warning or handle if a non-generic input error code is used without proper context
        }
    }

    public InvalidInputException(ErrorCode specificErrorCode) {
        super(specificErrorCode.getDefaultMessage(), specificErrorCode, HttpStatus.BAD_REQUEST);
    }
    
    public InvalidInputException(String message, ErrorCode specificErrorCode, Throwable cause) {
        super(message, specificErrorCode, HttpStatus.BAD_REQUEST, cause);
    }
} 