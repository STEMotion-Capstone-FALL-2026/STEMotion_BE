package com.stemotion.core.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public class ApiException extends RuntimeException {

    private final HttpStatus status;
    private final String code;
    /** Machine-readable facts for the client (e.g. the current revision); null for most errors. */
    private final java.util.Map<String, Object> details;

    public ApiException(HttpStatus status, String code, String message) {
        this(status, code, message, null);
    }

    public ApiException(HttpStatus status, String code, String message, java.util.Map<String, Object> details) {
        super(message);
        this.status = status;
        this.code = code;
        this.details = details;
    }

    /**
     * 409 for an edit or a sync based on an older revision. The client reads {@code details}
     * (resource, currentRevision, baseRevision) to reload instead of parsing the message.
     * {@code currentRevision} is null when another first save won a race and the number is unknown.
     */
    public static ApiException staleRevision(String resource, Integer currentRevision, Integer baseRevision,
                                             String message) {
        java.util.Map<String, Object> details = new java.util.LinkedHashMap<>();
        details.put("resource", resource);
        details.put("currentRevision", currentRevision);
        details.put("baseRevision", baseRevision);
        return new ApiException(HttpStatus.CONFLICT, "STALE_REVISION", message, details);
    }

    public static ApiException notFound(String resource, String id) {
        return new ApiException(
            HttpStatus.NOT_FOUND,
            "RESOURCE_NOT_FOUND",
            String.format("%s with id '%s' not found", resource, id)
        );
    }

    public static ApiException badRequest(String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, "BAD_REQUEST", message);
    }

    public static ApiException conflict(String message) {
        return new ApiException(HttpStatus.CONFLICT, "CONFLICT", message);
    }

    public static ApiException unauthorized(String message) {
        return new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", message);
    }

    public static ApiException forbidden(String message) {
        return new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", message);
    }
}
