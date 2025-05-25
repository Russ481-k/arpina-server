package cms.common.exception.handler;

import cms.common.dto.ErrorResponse;
import cms.common.exception.CustomBaseException;
import cms.common.exception.ErrorCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
import java.util.Map;
import java.util.stream.Collectors;

@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    // Handle @Valid and @Validated errors for request body
    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex, HttpHeaders headers, HttpStatus status, WebRequest request) {
        log.warn("Validation Error (RequestBody): {}", ex.getMessage());
        Map<String, String> errors = ex.getBindingResult().getFieldErrors().stream()
                .collect(Collectors.toMap(fieldError -> fieldError.getField(), fieldError -> fieldError.getDefaultMessage(), (existingValue, newValue) -> existingValue + "; " + newValue)); // Handle duplicate field errors
        
        ErrorResponse errorResponse = new ErrorResponse(
                HttpStatus.BAD_REQUEST.value(),
                HttpStatus.BAD_REQUEST.getReasonPhrase(),
                ErrorCode.INVALID_INPUT_VALUE.getDefaultMessage(),
                request.getDescription(false).replace("uri=", ""),
                ErrorCode.INVALID_INPUT_VALUE.getCode()
        );
        errorResponse.setValidationErrors(errors);
        return new ResponseEntity<>(errorResponse, HttpStatus.BAD_REQUEST);
    }
    
    // Handle @ModelAttribute binding/validation errors
    @Override
    protected ResponseEntity<Object> handleBindException(
            BindException ex, HttpHeaders headers, HttpStatus status, WebRequest request) {
        log.warn("Binding Error (@ModelAttribute): {}", ex.getMessage());
        Map<String, String> errors = ex.getBindingResult().getFieldErrors().stream()
            .collect(Collectors.toMap(fieldError -> fieldError.getField(), fieldError -> fieldError.getDefaultMessage(), (existingValue, newValue) -> existingValue + "; " + newValue));

        ErrorResponse errorResponse = new ErrorResponse(
                HttpStatus.BAD_REQUEST.value(),
                HttpStatus.BAD_REQUEST.getReasonPhrase(),
                ErrorCode.INVALID_INPUT_VALUE.getDefaultMessage(),
                request.getDescription(false).replace("uri=", ""),
                ErrorCode.INVALID_INPUT_VALUE.getCode()
        );
        errorResponse.setValidationErrors(errors);
        return new ResponseEntity<>(errorResponse, HttpStatus.BAD_REQUEST);
    }

    // Handle CustomBaseException (our custom exceptions)
    @ExceptionHandler(CustomBaseException.class)
    public ResponseEntity<ErrorResponse> handleCustomBaseException(CustomBaseException ex, WebRequest request) {
        log.warn("Custom Exception [{} - {}]: {}. Details: {}", ex.getErrorCode().getCode(), ex.getHttpStatus(), ex.getMessage(), ex.getDetailMessage(), ex);
        ErrorResponse errorResponse = new ErrorResponse(
                ex.getHttpStatus().value(),
                ex.getHttpStatus().getReasonPhrase(),
                ex.getMessage(), // Message from the exception itself
                request.getDescription(false).replace("uri=", ""),
                ex.getErrorCode().getCode()
        );
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
                ErrorCode.ACCESS_DENIED.getCode()
        );
        return new ResponseEntity<>(errorResponse, HttpStatus.FORBIDDEN);
    }
    
    // Handle Spring Security's AuthenticationException (e.g. bad credentials, token issues)
    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ErrorResponse> handleAuthenticationException(AuthenticationException ex, WebRequest request) {
        log.warn("Authentication Failed: {}. URI: {}", ex.getMessage(), request.getDescription(false));
        ErrorResponse errorResponse = new ErrorResponse(
                HttpStatus.UNAUTHORIZED.value(),
                HttpStatus.UNAUTHORIZED.getReasonPhrase(),
                ErrorCode.AUTHENTICATION_FAILED.getDefaultMessage(), // Or ex.getMessage() for more specific details from Spring Security
                request.getDescription(false).replace("uri=", ""),
                ErrorCode.AUTHENTICATION_FAILED.getCode()
        );
        return new ResponseEntity<>(errorResponse, HttpStatus.UNAUTHORIZED);
    }
    
    // Handle JPA's EntityNotFoundException (can be replaced by our ResourceNotFoundException usage in services)
    @ExceptionHandler(EntityNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleEntityNotFoundException(EntityNotFoundException ex, WebRequest request) {
        log.warn("JPA Entity Not Found: {}. URI: {}", ex.getMessage(), request.getDescription(false));
        // It's often better to throw a custom ResourceNotFoundException from the service layer.
        ErrorResponse errorResponse = new ErrorResponse(
                HttpStatus.NOT_FOUND.value(),
                HttpStatus.NOT_FOUND.getReasonPhrase(),
                ex.getMessage(), // Or ErrorCode.RESOURCE_NOT_FOUND.getDefaultMessage()
                request.getDescription(false).replace("uri=", ""),
                ErrorCode.RESOURCE_NOT_FOUND.getCode() // Generic code, specific exception from service is better
        );
        return new ResponseEntity<>(errorResponse, HttpStatus.NOT_FOUND);
    }
    
    // Handle general IllegalArgumentException (often indicates bad input not caught by @Valid)
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgumentException(IllegalArgumentException ex, WebRequest request) {
        log.warn("Illegal Argument: {}. URI: {}", ex.getMessage(), request.getDescription(false));
        ErrorResponse errorResponse = new ErrorResponse(
                HttpStatus.BAD_REQUEST.value(),
                HttpStatus.BAD_REQUEST.getReasonPhrase(),
                ex.getMessage() != null && !ex.getMessage().isEmpty() ? ex.getMessage() : ErrorCode.INVALID_INPUT_VALUE.getDefaultMessage(),
                request.getDescription(false).replace("uri=", ""),
                ErrorCode.INVALID_INPUT_VALUE.getCode() 
        );
        return new ResponseEntity<>(errorResponse, HttpStatus.BAD_REQUEST);
    }

    // Fallback for any other unhandled exceptions: returns a generic 500 Internal Server Error
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleAllUncaughtException(Exception ex, WebRequest request) {
        log.error("Unhandled Internal Server Error. URI: {}", request.getDescription(false), ex);
        ErrorResponse errorResponse = new ErrorResponse(
                HttpStatus.INTERNAL_SERVER_ERROR.value(),
                HttpStatus.INTERNAL_SERVER_ERROR.getReasonPhrase(),
                ErrorCode.INTERNAL_SERVER_ERROR.getDefaultMessage(),
                request.getDescription(false).replace("uri=", ""),
                ErrorCode.INTERNAL_SERVER_ERROR.getCode()
        );
        return new ResponseEntity<>(errorResponse, HttpStatus.INTERNAL_SERVER_ERROR);
    }
} 