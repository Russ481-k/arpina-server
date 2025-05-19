package cms.common.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.BAD_REQUEST) // 400 Bad Request
public class NiceVerificationException extends RuntimeException {
    public NiceVerificationException(String message) {
        super(message);
    }

    public NiceVerificationException(String message, Throwable cause) {
        super(message, cause);
    }
} 