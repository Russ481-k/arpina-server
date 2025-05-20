package cms.common.exception;

import org.springframework.http.HttpStatus;

public class ResourceNotFoundException extends CustomBaseException {
    
    // Constructor for specific resource and ID
    public ResourceNotFoundException(String resourceName, Object id) {
        super(resourceName + " (ID: " + id + ")을(를) 찾을 수 없습니다.", ErrorCode.RESOURCE_NOT_FOUND, HttpStatus.NOT_FOUND);
    }

    // Constructor for a general message or when ErrorCode itself is specific enough
    public ResourceNotFoundException(String message) {
        super(message, ErrorCode.RESOURCE_NOT_FOUND, HttpStatus.NOT_FOUND);
    }
    
    // Constructor to use a more specific ErrorCode if available (e.g., USER_NOT_FOUND)
    public ResourceNotFoundException(ErrorCode specificErrorCode) {
        super(specificErrorCode.getDefaultMessage(), specificErrorCode, HttpStatus.NOT_FOUND);
        if (!specificErrorCode.getCode().startsWith("U") && !specificErrorCode.getCode().startsWith("E") && !specificErrorCode.getCode().startsWith("P") && !specificErrorCode.equals(ErrorCode.RESOURCE_NOT_FOUND)) {
            // This is a basic check; ideally, ResourceNotFoundException should only be used with codes indicating a missing resource.
            // Consider logging a warning if a non-resource-not-found type ErrorCode is used.
        }
    }

    // Constructor to use a more specific ErrorCode and allow overriding the message
    public ResourceNotFoundException(String message, ErrorCode specificErrorCode) {
        super(message, specificErrorCode, HttpStatus.NOT_FOUND);
    }
} 
 
 
 