package cms.common.exception;

public class DuplicateRoleNameException extends RuntimeException {
    public DuplicateRoleNameException(String message) {
        super(message);
    }

    public DuplicateRoleNameException(String message, Throwable cause) {
        super(message, cause);
    }
} 