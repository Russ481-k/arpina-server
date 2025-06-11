package cms.common.exception.handler;

import cms.common.dto.ErrorResponse;
import cms.common.exception.CustomBaseException;
import cms.common.exception.ErrorCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.BindException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import javax.persistence.EntityNotFoundException;
import javax.validation.ConstraintViolationException;
import java.util.Map;
import java.util.stream.Collectors;

// Merged custom exception imports
import cms.template.exception.TemplateNotFoundException;
import cms.template.exception.CannotDeleteFixedTemplateException;
import cms.common.exception.DuplicateDiException;
import cms.common.exception.DuplicateEmailException;
import cms.common.exception.DuplicateUsernameException;
import cms.common.exception.NiceVerificationException;

@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

        private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

        // Handle @Valid and @Validated errors for request body
        @Override
        protected ResponseEntity<Object> handleMethodArgumentNotValid(
                        MethodArgumentNotValidException ex, HttpHeaders headers, HttpStatus status,
                        WebRequest request) {
                log.warn("Validation Error (RequestBody): {}", ex.getMessage());
                Map<String, String> errors = ex.getBindingResult().getFieldErrors().stream()
                                .collect(Collectors.toMap(fieldError -> fieldError.getField(),
                                                fieldError -> fieldError.getDefaultMessage(),
                                                (existingValue, newValue) -> existingValue + "; " + newValue)); // Handle
                                                                                                                // duplicate
                                                                                                                // field
                                                                                                                // errors

                ErrorResponse errorResponse = new ErrorResponse(
                                HttpStatus.BAD_REQUEST.value(),
                                HttpStatus.BAD_REQUEST.getReasonPhrase(),
                                ErrorCode.INVALID_INPUT_VALUE.getDefaultMessage(),
                                request.getDescription(false).replace("uri=", ""),
                                ErrorCode.INVALID_INPUT_VALUE.getCode());
                errorResponse.setValidationErrors(errors);
                return new ResponseEntity<>(errorResponse, HttpStatus.BAD_REQUEST);
        }

        // Handle @ModelAttribute binding/validation errors
        @Override
        protected ResponseEntity<Object> handleBindException(
                        BindException ex, HttpHeaders headers, HttpStatus status, WebRequest request) {
                log.warn("Binding Error (@ModelAttribute): {}", ex.getMessage());
                Map<String, String> errors = ex.getBindingResult().getFieldErrors().stream()
                                .collect(Collectors.toMap(fieldError -> fieldError.getField(),
                                                fieldError -> fieldError.getDefaultMessage(),
                                                (existingValue, newValue) -> existingValue + "; " + newValue));

                ErrorResponse errorResponse = new ErrorResponse(
                                HttpStatus.BAD_REQUEST.value(),
                                HttpStatus.BAD_REQUEST.getReasonPhrase(),
                                ErrorCode.INVALID_INPUT_VALUE.getDefaultMessage(),
                                request.getDescription(false).replace("uri=", ""),
                                ErrorCode.INVALID_INPUT_VALUE.getCode());
                errorResponse.setValidationErrors(errors);
                return new ResponseEntity<>(errorResponse, HttpStatus.BAD_REQUEST);
        }

        // Handle CustomBaseException (our custom exceptions)
        @ExceptionHandler(CustomBaseException.class)
        public ResponseEntity<ErrorResponse> handleCustomBaseException(CustomBaseException ex, WebRequest request) {
                log.warn("Custom Exception [{} - {}]: {}. Details: {}", ex.getErrorCode().getCode(), ex.getHttpStatus(),
                                ex.getMessage(), ex.getDetailMessage(), ex);
                ErrorResponse errorResponse = new ErrorResponse(
                                ex.getHttpStatus().value(),
                                ex.getHttpStatus().getReasonPhrase(),
                                ex.getMessage(), // Message from the exception itself
                                request.getDescription(false).replace("uri=", ""),
                                ex.getErrorCode().getCode());
                return new ResponseEntity<>(errorResponse, ex.getHttpStatus());
        }

        // Handle Spring Security's AccessDeniedException
        @ExceptionHandler(AccessDeniedException.class)
        public ResponseEntity<ErrorResponse> handleAccessDeniedException(AccessDeniedException ex, WebRequest request) {
                log.warn("Access Denied: {}. URI: {}", ex.getMessage(), request.getDescription(false));
                ErrorResponse errorResponse = new ErrorResponse(
                                HttpStatus.FORBIDDEN.value(),
                                HttpStatus.FORBIDDEN.getReasonPhrase(),
                                ErrorCode.ACCESS_DENIED.getDefaultMessage(),
                                request.getDescription(false).replace("uri=", ""),
                                ErrorCode.ACCESS_DENIED.getCode());
                return new ResponseEntity<>(errorResponse, HttpStatus.FORBIDDEN);
        }

        // Handle Spring Security's AuthenticationException (e.g. bad credentials, token
        // issues)
        @ExceptionHandler(AuthenticationException.class)
        public ResponseEntity<ErrorResponse> handleAuthenticationException(AuthenticationException ex,
                        WebRequest request) {
                log.warn("Authentication Failed: {}. URI: {}", ex.getMessage(), request.getDescription(false));
                ErrorResponse errorResponse = new ErrorResponse(
                                HttpStatus.UNAUTHORIZED.value(),
                                HttpStatus.UNAUTHORIZED.getReasonPhrase(),
                                ErrorCode.AUTHENTICATION_FAILED.getDefaultMessage(), // Or ex.getMessage() for more
                                                                                     // specific details from Spring
                                                                                     // Security
                                request.getDescription(false).replace("uri=", ""),
                                ErrorCode.AUTHENTICATION_FAILED.getCode());
                return new ResponseEntity<>(errorResponse, HttpStatus.UNAUTHORIZED);
        }

        // Handle JPA's EntityNotFoundException (can be replaced by our
        // ResourceNotFoundException usage in services)
        @ExceptionHandler(EntityNotFoundException.class)
        public ResponseEntity<ErrorResponse> handleEntityNotFoundException(EntityNotFoundException ex,
                        WebRequest request) {
                log.warn("JPA Entity Not Found: {}. URI: {}", ex.getMessage(), request.getDescription(false));
                // It's often better to throw a custom ResourceNotFoundException from the
                // service layer.
                ErrorResponse errorResponse = new ErrorResponse(
                                HttpStatus.NOT_FOUND.value(),
                                HttpStatus.NOT_FOUND.getReasonPhrase(),
                                ex.getMessage(), // Or ErrorCode.RESOURCE_NOT_FOUND.getDefaultMessage()
                                request.getDescription(false).replace("uri=", ""),
                                ErrorCode.RESOURCE_NOT_FOUND.getCode() // Generic code, specific exception from service
                                                                       // is better
                );
                return new ResponseEntity<>(errorResponse, HttpStatus.NOT_FOUND);
        }

        // Handle general IllegalArgumentException (often indicates bad input not caught
        // by @Valid)
        @ExceptionHandler(IllegalArgumentException.class)
        public ResponseEntity<ErrorResponse> handleIllegalArgumentException(IllegalArgumentException ex,
                        WebRequest request) {
                log.warn("Illegal Argument: {}. URI: {}", ex.getMessage(), request.getDescription(false));
                ErrorResponse errorResponse = new ErrorResponse(
                                HttpStatus.BAD_REQUEST.value(),
                                HttpStatus.BAD_REQUEST.getReasonPhrase(),
                                ex.getMessage() != null && !ex.getMessage().isEmpty() ? ex.getMessage()
                                                : ErrorCode.INVALID_INPUT_VALUE.getDefaultMessage(),
                                request.getDescription(false).replace("uri=", ""),
                                ErrorCode.INVALID_INPUT_VALUE.getCode());
                return new ResponseEntity<>(errorResponse, HttpStatus.BAD_REQUEST);
        }

        // 데이터 무결성 위반 예외 처리 (예: 유니크 제약 조건 위반)
        @ExceptionHandler(DataIntegrityViolationException.class)
        public ResponseEntity<ErrorResponse> handleDataIntegrityViolationException(DataIntegrityViolationException ex,
                        WebRequest request) {
                log.warn("Data Integrity Violation: {}. URI: {}", ex.getMessage(), request.getDescription(false), ex);
                String message = ErrorCode.DATA_INTEGRITY_VIOLATION.getDefaultMessage();
                String errorCode = ErrorCode.DATA_INTEGRITY_VIOLATION.getCode();
                HttpStatus status = HttpStatus.CONFLICT; // Default to 409 for data integrity issues, can be overridden

                // 특정 데이터베이스 오류 메시지를 확인하여 이메일 중복인지 판단 (데이터베이스 종류에 따라 메시지가 다를 수 있음)
                String lowerCaseExMessage = ex.getMostSpecificCause().getMessage().toLowerCase();

                // 데이터베이스 제약 조건 이름 또는 일반적인 키워드를 확인합니다.
                // 예: user_email_uk, uk_user_email, idx_user_email_unique 등 (실제 제약 조건 이름으로 변경
                // 필요)
                if (lowerCaseExMessage.contains("email")
                                && (lowerCaseExMessage.contains("unique") || lowerCaseExMessage.contains("duplicate")
                                                || lowerCaseExMessage.contains("constraint"))) {
                        // Assume it's a duplicate email if the message contains 'email' and typical
                        // unique constraint keywords
                        // Specific constraint names (e.g., 'user_email_uk', 'uk_user_email') are more
                        // reliable if known.
                        message = ErrorCode.DUPLICATE_EMAIL.getDefaultMessage();
                        errorCode = ErrorCode.DUPLICATE_EMAIL.getCode();
                        status = ErrorCode.DUPLICATE_EMAIL.getHttpStatus();
                } else if (lowerCaseExMessage.contains("username")
                                && (lowerCaseExMessage.contains("unique") || lowerCaseExMessage.contains("duplicate")
                                                || lowerCaseExMessage.contains("constraint"))) {
                        // Assume it's a duplicate username if the message contains 'username' and
                        // typical unique constraint keywords
                        // Specific constraint names (e.g., 'user_username_uk', 'uk_user_username') are
                        // more reliable if known.
                        message = ErrorCode.DUPLICATE_USERNAME.getDefaultMessage();
                        errorCode = ErrorCode.DUPLICATE_USERNAME.getCode();
                        status = ErrorCode.DUPLICATE_USERNAME.getHttpStatus();
                }
                // 다른 유니크 제약 조건에 대한 처리도 필요한 경우 여기에 추가합니다.

                ErrorResponse errorResponse = new ErrorResponse(
                                status.value(),
                                status.getReasonPhrase(),
                                message,
                                request.getDescription(false).replace("uri=", ""),
                                errorCode);
                return new ResponseEntity<>(errorResponse, status);
        }

        // Fallback for any other unhandled exceptions: returns a generic 500 Internal
        // Server Error
        @ExceptionHandler(Exception.class)
        public ResponseEntity<ErrorResponse> handleAllUncaughtException(Exception ex, WebRequest request) {
                log.error("Unhandled Internal Server Error. URI: {}", request.getDescription(false), ex);
                ErrorResponse errorResponse = new ErrorResponse(
                                HttpStatus.INTERNAL_SERVER_ERROR.value(),
                                HttpStatus.INTERNAL_SERVER_ERROR.getReasonPhrase(),
                                ErrorCode.INTERNAL_SERVER_ERROR.getDefaultMessage(),
                                request.getDescription(false).replace("uri=", ""),
                                ErrorCode.INTERNAL_SERVER_ERROR.getCode());
                return new ResponseEntity<>(errorResponse, HttpStatus.INTERNAL_SERVER_ERROR);
        }

        // --- Start of Merged Handlers from the old GlobalExceptionHandler ---

        @ExceptionHandler(ConstraintViolationException.class)
        public ResponseEntity<ErrorResponse> handleConstraintViolation(ConstraintViolationException ex,
                        WebRequest request) {
                log.warn("Constraint Violation: {}", ex.getMessage());
                Map<String, String> errors = ex.getConstraintViolations().stream()
                                .collect(Collectors.toMap(
                                                violation -> violation.getPropertyPath().toString(),
                                                violation -> violation.getMessage()));
                ErrorResponse errorResponse = new ErrorResponse(
                                HttpStatus.BAD_REQUEST.value(),
                                HttpStatus.BAD_REQUEST.getReasonPhrase(),
                                "입력 값에 제약 조건 위반이 있습니다.",
                                request.getDescription(false).replace("uri=", ""),
                                ErrorCode.INVALID_INPUT_VALUE.getCode());
                errorResponse.setValidationErrors(errors);
                return new ResponseEntity<>(errorResponse, HttpStatus.BAD_REQUEST);
        }

        @ExceptionHandler(TemplateNotFoundException.class)
        public ResponseEntity<ErrorResponse> handleTemplateNotFoundException(TemplateNotFoundException ex,
                        WebRequest request) {
                log.warn("Template Not Found: {}. URI: {}", ex.getMessage(), request.getDescription(false));
                ErrorCode ec = ErrorCode.TEMPLATE_NOT_FOUND; // Assuming this error code exists
                ErrorResponse errorResponse = new ErrorResponse(
                                ec.getHttpStatus().value(),
                                ec.getHttpStatus().getReasonPhrase(),
                                ex.getMessage(),
                                request.getDescription(false).replace("uri=", ""),
                                ec.getCode());
                return new ResponseEntity<>(errorResponse, ec.getHttpStatus());
        }

        @ExceptionHandler(CannotDeleteFixedTemplateException.class)
        public ResponseEntity<ErrorResponse> handleCannotDeleteFixedTemplateException(
                        CannotDeleteFixedTemplateException ex, WebRequest request) {
                log.warn("Cannot Delete Fixed Template: {}. URI: {}", ex.getMessage(), request.getDescription(false));
                ErrorCode ec = ErrorCode.INVALID_REQUEST; // Using a generic code as a fallback
                ErrorResponse errorResponse = new ErrorResponse(
                                ec.getHttpStatus().value(),
                                ec.getHttpStatus().getReasonPhrase(),
                                ex.getMessage(),
                                request.getDescription(false).replace("uri=", ""),
                                ec.getCode());
                return new ResponseEntity<>(errorResponse, ec.getHttpStatus());
        }

        @ExceptionHandler(DuplicateDiException.class)
        public ResponseEntity<ErrorResponse> handleDuplicateDiException(DuplicateDiException ex, WebRequest request) {
                log.warn("Duplicate DI: {}. URI: {}", ex.getMessage(), request.getDescription(false));
                ErrorCode ec = ErrorCode.DUPLICATE_DI; // Assuming this error code exists
                ErrorResponse errorResponse = new ErrorResponse(
                                ec.getHttpStatus().value(),
                                ec.getHttpStatus().getReasonPhrase(),
                                ex.getMessage(),
                                request.getDescription(false).replace("uri=", ""),
                                ec.getCode());
                return new ResponseEntity<>(errorResponse, ec.getHttpStatus());
        }

        @ExceptionHandler(DuplicateEmailException.class)
        public ResponseEntity<ErrorResponse> handleDuplicateEmailException(DuplicateEmailException ex,
                        WebRequest request) {
                log.warn("Duplicate Email Exception: {}. URI: {}", ex.getMessage(), request.getDescription(false));
                ErrorCode ec = ErrorCode.DUPLICATE_EMAIL;
                ErrorResponse errorResponse = new ErrorResponse(
                                ec.getHttpStatus().value(),
                                ec.getHttpStatus().getReasonPhrase(),
                                ex.getMessage(),
                                request.getDescription(false).replace("uri=", ""),
                                ec.getCode());
                return new ResponseEntity<>(errorResponse, ec.getHttpStatus());
        }

        @ExceptionHandler(DuplicateUsernameException.class)
        public ResponseEntity<ErrorResponse> handleDuplicateUsernameException(DuplicateUsernameException ex,
                        WebRequest request) {
                log.warn("Duplicate Username Exception: {}. URI: {}", ex.getMessage(), request.getDescription(false));
                ErrorCode ec = ErrorCode.DUPLICATE_USERNAME;
                ErrorResponse errorResponse = new ErrorResponse(
                                ec.getHttpStatus().value(),
                                ec.getHttpStatus().getReasonPhrase(),
                                ex.getMessage(),
                                request.getDescription(false).replace("uri=", ""),
                                ec.getCode());
                return new ResponseEntity<>(errorResponse, ec.getHttpStatus());
        }

        @ExceptionHandler(NiceVerificationException.class)
        public ResponseEntity<ErrorResponse> handleNiceVerificationException(NiceVerificationException ex,
                        WebRequest request) {
                log.warn("NICE Verification Failed: {}. URI: {}", ex.getMessage(), request.getDescription(false));
                ErrorCode ec = ErrorCode.NICE_VERIFICATION_FAILED; // Assuming this error code exists
                ErrorResponse errorResponse = new ErrorResponse(
                                ec.getHttpStatus().value(),
                                ec.getHttpStatus().getReasonPhrase(),
                                ex.getMessage(),
                                request.getDescription(false).replace("uri=", ""),
                                ec.getCode());
                return new ResponseEntity<>(errorResponse, ec.getHttpStatus());
        }
}