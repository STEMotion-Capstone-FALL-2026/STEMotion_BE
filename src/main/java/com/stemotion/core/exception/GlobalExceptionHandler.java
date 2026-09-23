package com.stemotion.core.exception;

import com.fasterxml.jackson.databind.exc.InvalidFormatException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Turns every failure into the same error envelope.
 *
 * <p>The catch-all at the bottom reports 500, so any framework exception that
 * really means "the caller sent something wrong" must be handled explicitly
 * above it. Otherwise a typo in a request looks identical to a server crash,
 * and the front end cannot tell the user what to fix.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static ResponseEntity<ErrorResponse> build(HttpStatus status, String code, String message) {
        return ResponseEntity.status(status)
            .body(new ErrorResponse(status.value(), code, message, LocalDateTime.now()));
    }

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ErrorResponse> handleApiException(ApiException ex) {
        log.warn("API Exception: [{}] {}", ex.getCode(), ex.getMessage());
        return build(ex.getStatus(), ex.getCode(), ex.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationException(MethodArgumentNotValidException ex) {
        Map<String, String> errors = new HashMap<>();
        ex.getBindingResult().getAllErrors().forEach(error -> {
            String field = error instanceof FieldError fe ? fe.getField() : error.getObjectName();
            errors.put(field, error.getDefaultMessage());
        });

        log.warn("Validation failed: {}", errors);
        return build(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", errors.toString());
    }

    /**
     * Raised by @PreAuthorize when the caller's role is not allowed. Without
     * this the catch-all below would turn a legitimate denial into a 500.
     */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDenied(AccessDeniedException ex) {
        log.warn("Access denied: {}", ex.getMessage());
        return build(HttpStatus.FORBIDDEN, "ACCESS_DENIED",
            "You do not have permission to perform this action");
    }

    /**
     * Unparseable body, or a value that does not fit the target type — most
     * often an enum the caller spelled wrongly. The accepted values are echoed
     * back so the caller can correct the request without reading the source.
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleUnreadableBody(HttpMessageNotReadableException ex) {
        String message = "Request body is missing or not valid JSON";

        if (ex.getCause() instanceof InvalidFormatException ife) {
            Class<?> target = ife.getTargetType();
            String field = ife.getPath().isEmpty()
                ? "value"
                : ife.getPath().get(ife.getPath().size() - 1).getFieldName();

            if (target != null && target.isEnum()) {
                String allowed = java.util.Arrays.stream(target.getEnumConstants())
                    .map(String::valueOf)
                    .collect(Collectors.joining(", "));
                message = "Field '%s' has invalid value '%s'. Accepted values: %s"
                    .formatted(field, ife.getValue(), allowed);
            } else {
                message = "Field '%s' has invalid value '%s'".formatted(field, ife.getValue());
            }
        }

        log.warn("Unreadable request body: {}", message);
        return build(HttpStatus.BAD_REQUEST, "MALFORMED_REQUEST", message);
    }

    /** A query or path parameter that does not fit its declared type. */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        Class<?> required = ex.getRequiredType();
        String message;

        if (required != null && required.isEnum()) {
            String allowed = java.util.Arrays.stream(required.getEnumConstants())
                .map(String::valueOf)
                .collect(Collectors.joining(", "));
            message = "Parameter '%s' has invalid value '%s'. Accepted values: %s"
                .formatted(ex.getName(), ex.getValue(), allowed);
        } else {
            message = "Parameter '%s' has invalid value '%s'".formatted(ex.getName(), ex.getValue());
        }

        log.warn("Parameter type mismatch: {}", message);
        return build(HttpStatus.BAD_REQUEST, "INVALID_PARAMETER", message);
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ErrorResponse> handleMissingParam(MissingServletRequestParameterException ex) {
        return build(HttpStatus.BAD_REQUEST, "MISSING_PARAMETER",
            "Required parameter '%s' is missing".formatted(ex.getParameterName()));
    }

    @ExceptionHandler(MissingRequestHeaderException.class)
    public ResponseEntity<ErrorResponse> handleMissingHeader(MissingRequestHeaderException ex) {
        return build(HttpStatus.BAD_REQUEST, "MISSING_HEADER",
            "Required header '%s' is missing".formatted(ex.getHeaderName()));
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleMethodNotAllowed(HttpRequestMethodNotSupportedException ex) {
        return build(HttpStatus.METHOD_NOT_ALLOWED, "METHOD_NOT_ALLOWED",
            "Method %s is not supported on this endpoint".formatted(ex.getMethod()));
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleUnsupportedMedia(HttpMediaTypeNotSupportedException ex) {
        return build(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "UNSUPPORTED_MEDIA_TYPE",
            "Content type %s is not supported; use application/json".formatted(ex.getContentType()));
    }

    /** An unknown URL is the caller's mistake, not a server fault. */
    @ExceptionHandler({NoHandlerFoundException.class, NoResourceFoundException.class})
    public ResponseEntity<ErrorResponse> handleNotFound(Exception ex) {
        log.warn("No handler: {}", ex.getMessage());
        return build(HttpStatus.NOT_FOUND, "ENDPOINT_NOT_FOUND", "No endpoint matches this request");
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGenericException(Exception ex) {
        log.error("Unhandled exception", ex);
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR",
            "An unexpected error occurred");
    }

    public record ErrorResponse(
        int status,
        String code,
        String message,
        LocalDateTime timestamp
    ) {}
}
