package cms.common.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import cms.common.dto.ApiResponseSchema;
import cms.common.dto.ValidationErrorResponse;

import javax.persistence.EntityNotFoundException;
import javax.validation.ConstraintViolation;
import javax.validation.ConstraintViolationException;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import cms.template.exception.CannotDeleteFixedTemplateException;
import cms.template.exception.TemplateNotFoundException;
import org.springframework.security.access.AccessDeniedException;

import cms.common.exception.DuplicateDiException;
import cms.common.exception.DuplicateEmailException;
import cms.common.exception.DuplicateUsernameException;
import cms.common.exception.NiceVerificationException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponseSchema<Void>> handleBusinessException(BusinessException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponseSchema.error(e.getMessage(), e.getErrorCode()));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiResponseSchema<Void>> handleAccessDeniedException(AccessDeniedException e) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ApiResponseSchema.error("접근 권한이 없습니다.", "NO_AUTH"));
    }

    @ExceptionHandler(BindException.class)
    public ResponseEntity<ApiResponseSchema<Void>> handleBindException(BindException e) {
        String errorMessage = e.getBindingResult().getAllErrors().get(0).getDefaultMessage();
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponseSchema.error(errorMessage, "VALIDATION_ERROR"));
    }

    @ExceptionHandler({ResourceNotFoundException.class, EntityNotFoundException.class})
    public ResponseEntity<ApiResponseSchema<String>> handleNotFoundException(Exception e) {
        String message = e instanceof ResourceNotFoundException 
            ? e.getMessage() 
            : "요청한 리소스를 찾을 수 없습니다.";
            
        return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body(ApiResponseSchema.error(message, "RESOURCE_NOT_FOUND"));
    }

    @ExceptionHandler({AuthenticationException.class, BadCredentialsException.class})
    public ResponseEntity<ApiResponseSchema<String>> handleAuthenticationException(Exception e) {
        String message = e instanceof BadCredentialsException 
            ? "잘못된 자격 증명입니다." 
            : "인증에 실패했습니다.";
            
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(ApiResponseSchema.error(message, "AUTHENTICATION_FAILED"));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiResponseSchema<String>> handleIllegalArgumentException(IllegalArgumentException ex) {
        return ResponseEntity.badRequest()
                .body(ApiResponseSchema.error(ex.getMessage(), "ILLEGAL_ARGUMENT"));
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiResponseSchema<ValidationErrorResponse>> handleConstraintViolationException(
            ConstraintViolationException ex) {
        Set<ConstraintViolation<?>> violations = ex.getConstraintViolations();
        List<ValidationErrorResponse.ErrorDetail> errors = violations.stream()
                .map(violation -> new ValidationErrorResponse.ErrorDetail(
                        violation.getPropertyPath().toString(),
                        violation.getMessage(),
                        violation.getInvalidValue()))
                .collect(Collectors.toList());

        ValidationErrorResponse response = new ValidationErrorResponse("제약 조건 위반이 발생했습니다.", errors);
        return ResponseEntity.badRequest()
                .body(ApiResponseSchema.error(response, "제약 조건 위반이 발생했습니다.", "CONSTRAINT_VIOLATION"));
    }

    @ExceptionHandler(TemplateNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleTemplateNotFoundException(TemplateNotFoundException e) {
        ErrorResponse response = new ErrorResponse(HttpStatus.NOT_FOUND.value(), e.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
    }

    @ExceptionHandler(CannotDeleteFixedTemplateException.class)
    public ResponseEntity<ErrorResponse> handleCannotDeleteFixedTemplateException(CannotDeleteFixedTemplateException e) {
        ErrorResponse response = new ErrorResponse(HttpStatus.BAD_REQUEST.value(), e.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }

    @ExceptionHandler(DuplicateDiException.class)
    public ResponseEntity<ApiResponseSchema<Void>> handleDuplicateDiException(DuplicateDiException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiResponseSchema.error(e.getMessage(), "DUPLICATE_DI"));
    }

    @ExceptionHandler(DuplicateEmailException.class)
    public ResponseEntity<ApiResponseSchema<Void>> handleDuplicateEmailException(DuplicateEmailException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiResponseSchema.error(e.getMessage(), "DUPLICATE_EMAIL"));
    }

    @ExceptionHandler(DuplicateUsernameException.class)
    public ResponseEntity<ApiResponseSchema<Void>> handleDuplicateUsernameException(DuplicateUsernameException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiResponseSchema.error(e.getMessage(), "DUPLICATE_USERNAME"));
    }

    @ExceptionHandler(NiceVerificationException.class)
    public ResponseEntity<ApiResponseSchema<Void>> handleNiceVerificationException(NiceVerificationException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponseSchema.error(e.getMessage(), "NICE_VERIFICATION_FAILED"));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponseSchema<Void>> handleException(Exception e) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponseSchema.error("서버 내부 오류가 발생했습니다. 관리자에게 문의해주세요.", "INTERNAL_SERVER_ERROR"));
    }

    private String getStackTraceAsString(Throwable e) {
        StringBuilder sb = new StringBuilder();
        for (StackTraceElement element : e.getStackTrace()) {
            sb.append(element.toString()).append("\n");
        }
        return sb.toString();
    }
} 