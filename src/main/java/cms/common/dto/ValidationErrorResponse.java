package cms.common.dto;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class ValidationErrorResponse {
    private String message;
    private List<ErrorDetail> errors;

    public ValidationErrorResponse(String message, List<ErrorDetail> errors) {
        this.message = message;
        this.errors = errors;
    }

    @Getter
    @Setter
    public static class ErrorDetail {
        private String field;
        private String message;
        private Object rejectedValue;

        public ErrorDetail(String field, String message, Object rejectedValue) {
            this.field = field;
            this.message = message;
            this.rejectedValue = rejectedValue;
        }
    }
} 