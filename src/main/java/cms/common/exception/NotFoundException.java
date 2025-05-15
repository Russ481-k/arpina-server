package cms.common.exception;

import lombok.Getter;

@Getter
public class NotFoundException extends RuntimeException {
    private final String code;
    private final String message;

    public NotFoundException(String code, String message) {
        super(message);
        this.code = code;
        this.message = message;
    }
} 