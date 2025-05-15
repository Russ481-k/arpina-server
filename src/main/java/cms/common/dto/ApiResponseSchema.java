package cms.common.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@Schema(description = "API 응답")
public class ApiResponseSchema<T> {
    @Schema(description = "성공 여부", example = "true")
    private boolean success;

    @Schema(description = "응답 메시지", example = "요청이 성공적으로 처리되었습니다.")
    private String message;

    @Schema(description = "응답 데이터")
    private T data;

    @Schema(description = "에러 코드")
    private String errorCode;

    @Schema(description = "스택 트레이스 (개발 환경에서만 표시)")
    private String stackTrace;

    public static <T> ApiResponseSchema<T> success(T data) {
        ApiResponseSchema<T> response = new ApiResponseSchema<>();
        response.setSuccess(true);
        response.setData(data);
        return response;
    }

    public static <T> ApiResponseSchema<T> success(T data, String message) {
        ApiResponseSchema<T> response = new ApiResponseSchema<>();
        response.setSuccess(true);
        response.setData(data);
        response.setMessage(message);
        return response;
    }

    public static ApiResponseSchema<Void> success(String message) {
        ApiResponseSchema<Void> response = new ApiResponseSchema<>();
        response.setSuccess(true);
        response.setMessage(message);
        return response;
    }

    public static <T> ApiResponseSchema<T> error(T data, String message) {
        ApiResponseSchema<T> response = new ApiResponseSchema<>();
        response.setSuccess(false);
        response.setData(data);
        response.setMessage(message);
        return response;
    }

    public static <T> ApiResponseSchema<T> error(String message, String errorCode) {
        ApiResponseSchema<T> response = new ApiResponseSchema<>();
        response.setSuccess(false);
        response.setMessage(message);
        response.setErrorCode(errorCode);
        return response;
    }

    public static <T> ApiResponseSchema<T> error(String message, String errorCode, String stackTrace) {
        ApiResponseSchema<T> response = new ApiResponseSchema<>();
        response.setSuccess(false);
        response.setMessage(message);
        response.setErrorCode(errorCode);
        response.setStackTrace(stackTrace);
        return response;
    }

    public static <T> ApiResponseSchema<T> error(T data, String message, String errorCode) {
        ApiResponseSchema<T> response = new ApiResponseSchema<>();
        response.setSuccess(false);
        response.setData(data);
        response.setMessage(message);
        response.setErrorCode(errorCode);
        return response;
    }
} 