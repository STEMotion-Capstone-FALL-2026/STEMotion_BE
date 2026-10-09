package com.stemotion.core.exception;

import com.fasterxml.jackson.databind.exc.InvalidFormatException;
import com.stemotion.core.enums.StemEnums.StemSubject;
import com.stemotion.core.exception.GlobalExceptionHandler.ErrorResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.mock.http.MockHttpInputMessage;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A malformed request must never look like a server crash: the front end
 * decides between "tell the user to fix this" and "retry later" purely from
 * the status code, so every framework exception that means "bad input" is
 * pinned to its status here.
 */
class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    private static int status(ResponseEntity<ErrorResponse> r) {
        return r.getStatusCode().value();
    }

    @Test
    @DisplayName("an ApiException keeps its own status and code")
    void apiExceptionPassesThrough() {
        ResponseEntity<ErrorResponse> r =
            handler.handleApiException(ApiException.conflict("already exists"));

        assertThat(status(r)).isEqualTo(409);
        assertThat(r.getBody().code()).isEqualTo("CONFLICT");
        assertThat(r.getBody().message()).isEqualTo("already exists");
    }

    @Test
    @DisplayName("overlapping saves of the same script are a 409 conflict, not a 500")
    void concurrentUpdateIs409() {
        ResponseEntity<ErrorResponse> r = handler.handleConcurrentUpdate(
            new org.springframework.orm.ObjectOptimisticLockingFailureException(
                "com.stemotion.domain.script.entity.Scene", "f7c27ead"));

        assertThat(status(r)).isEqualTo(409);
        assertThat(r.getBody().code()).isEqualTo("CONCURRENT_UPDATE");
    }

    @Test
    @DisplayName("a denied role is 403, not 500")
    void accessDeniedIs403() {
        ResponseEntity<ErrorResponse> r =
            handler.handleAccessDenied(new AccessDeniedException("nope"));

        assertThat(status(r)).isEqualTo(403);
        assertThat(r.getBody().code()).isEqualTo("ACCESS_DENIED");
    }

    @Test
    @DisplayName("an unparseable body is 400")
    void unreadableBodyIs400() {
        ResponseEntity<ErrorResponse> r = handler.handleUnreadableBody(
            new HttpMessageNotReadableException("boom", new MockHttpInputMessage(new byte[0])));

        assertThat(status(r)).isEqualTo(400);
        assertThat(r.getBody().code()).isEqualTo("MALFORMED_REQUEST");
    }

    @Test
    @DisplayName("a bad enum in the body lists the accepted values")
    void badEnumInBodyListsAllowedValues() {
        InvalidFormatException cause =
            InvalidFormatException.from(null, "bad", "Lichsu", StemSubject.class);
        ResponseEntity<ErrorResponse> r = handler.handleUnreadableBody(
            new HttpMessageNotReadableException("boom", cause, new MockHttpInputMessage(new byte[0])));

        assertThat(status(r)).isEqualTo(400);
        assertThat(r.getBody().message())
            .contains("Lichsu")
            .contains("Math")
            .contains("Physics");
    }

    @Test
    @DisplayName("a bad enum in a query parameter is 400 and lists the values")
    void badEnumInQueryIs400() {
        ResponseEntity<ErrorResponse> r = handler.handleTypeMismatch(
            new MethodArgumentTypeMismatchException("Lichsu", StemSubject.class, "subject", null, null));

        assertThat(status(r)).isEqualTo(400);
        assertThat(r.getBody().code()).isEqualTo("INVALID_PARAMETER");
        assertThat(r.getBody().message()).contains("subject").contains("Math");
    }

    @Test
    @DisplayName("a missing query parameter is 400")
    void missingParameterIs400() {
        ResponseEntity<ErrorResponse> r = handler.handleMissingParam(
            new MissingServletRequestParameterException("left", "String"));

        assertThat(status(r)).isEqualTo(400);
        assertThat(r.getBody().message()).contains("left");
    }

    @Test
    @DisplayName("a missing header is 400, naming the header")
    void missingHeaderIs400() throws Exception {
        MissingRequestHeaderException ex = new MissingRequestHeaderException(
            "X-Render-Api-Key",
            new org.springframework.core.MethodParameter(
                GlobalExceptionHandlerTest.class.getDeclaredMethod("sample", String.class), 0));

        ResponseEntity<ErrorResponse> r = handler.handleMissingHeader(ex);

        assertThat(status(r)).isEqualTo(400);
        assertThat(r.getBody().message()).contains("X-Render-Api-Key");
    }

    @Test
    @DisplayName("a wrong HTTP method is 405")
    void wrongMethodIs405() {
        ResponseEntity<ErrorResponse> r = handler.handleMethodNotAllowed(
            new HttpRequestMethodNotSupportedException("GET"));

        assertThat(status(r)).isEqualTo(405);
        assertThat(r.getBody().message()).contains("GET");
    }

    @Test
    @DisplayName("a wrong content type is 415")
    void wrongContentTypeIs415() {
        ResponseEntity<ErrorResponse> r = handler.handleUnsupportedMedia(
            new HttpMediaTypeNotSupportedException(MediaType.TEXT_PLAIN, java.util.List.of()));

        assertThat(status(r)).isEqualTo(415);
    }

    @Test
    @DisplayName("an unknown URL is 404, not 500")
    void unknownUrlIs404() {
        ResponseEntity<ErrorResponse> r = handler.handleNotFound(
            new NoResourceFoundException(HttpMethod.GET, "/api/v1/nope"));

        assertThat(status(r)).isEqualTo(404);
        assertThat(r.getBody().code()).isEqualTo("ENDPOINT_NOT_FOUND");
    }

    @Test
    @DisplayName("anything genuinely unexpected stays a 500 and hides its detail")
    void unexpectedStays500() {
        ResponseEntity<ErrorResponse> r =
            handler.handleGenericException(new IllegalStateException("secret internal detail"));

        assertThat(status(r)).isEqualTo(500);
        assertThat(r.getBody().code()).isEqualTo("INTERNAL_ERROR");
        assertThat(r.getBody().message()).doesNotContain("secret internal detail");
    }

    @Test
    @DisplayName("every error carries status, code, message and timestamp")
    void envelopeIsComplete() {
        ErrorResponse body = handler.handleApiException(
            ApiException.badRequest("nope")).getBody();

        assertThat(body.status()).isEqualTo(HttpStatus.BAD_REQUEST.value());
        assertThat(body.code()).isNotBlank();
        assertThat(body.message()).isNotBlank();
        assertThat(body.timestamp()).isNotNull();
    }

    /** Only exists so a MethodParameter can be constructed in the test above. */
    @SuppressWarnings("unused")
    private void sample(String header) {
    }
}
