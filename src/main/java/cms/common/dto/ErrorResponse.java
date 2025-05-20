package cms.common.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.Map;

@Getter
@Setter
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ErrorResponse {
    private LocalDateTime timestamp;
    private int status;
    private String error; // HTTP status error reason phrase (e.g., "Not Found", "Bad Request")
    private String errorCode; // Application-specific error code (e.g., "U001", "E002")
    private String message; // User-friendly message
    private String path;
    private Map<String, String> validationErrors; // For @Valid failures

    public ErrorResponse(int status, String error, String message, String path, String errorCode) {
        this.timestamp = LocalDateTime.now();
        this.status = status;
        this.error = error;
        this.message = message;
        this.path = path;
        this.errorCode = errorCode;
    }
} 