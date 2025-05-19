package cms.common.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.CONFLICT) // 409 Conflict
public class DuplicateDiException extends RuntimeException {
    public DuplicateDiException(String message) {
        super(message);
    }
} 