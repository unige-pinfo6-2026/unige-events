package ch.unige.events.resource;

import ch.unige.events.config.OpenApiSecurityConfig;
import ch.unige.events.dto.ApiErrorResponse;
import ch.unige.events.dto.ValidationErrorResponse;
import ch.unige.events.exception.mapper.*;
import com.fasterxml.jackson.core.JsonProcessingException;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.persistence.OptimisticLockException;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.ForbiddenException;
import jakarta.ws.rs.NotAuthorizedException;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@QuarkusTest
class ResourceMappersCoverageTest {

    @Test
    void badRequestMapperUsesJsonProcessingOriginalMessage() {
        BadRequestExceptionMapper mapper = new BadRequestExceptionMapper();
        JsonProcessingException jsonCause = new JsonProcessingException("Malformed JSON payload") {
        };

        Response response = mapper.toResponse(new BadRequestException(jsonCause));

        assertEquals(400, response.getStatus());
        ValidationErrorResponse payload = (ValidationErrorResponse) response.getEntity();
        assertEquals("validation_error", payload.error());
        assertEquals("Request validation failed", payload.message());
        assertEquals(1, payload.details().size());
        assertEquals("Malformed JSON payload", payload.details().get(0).message());
    }

    @Test
    void badRequestMapperUsesCauseMessageWhenNotJsonProcessing() {
        BadRequestExceptionMapper mapper = new BadRequestExceptionMapper();
        RuntimeException cause = new RuntimeException("Detailed cause message");

        Response response = mapper.toResponse(new BadRequestException(cause));

        ValidationErrorResponse payload = (ValidationErrorResponse) response.getEntity();
        assertEquals("Detailed cause message", payload.details().get(0).message());
    }

    @Test
    void badRequestMapperUsesExceptionMessageWhenCauseMissing() {
        BadRequestExceptionMapper mapper = new BadRequestExceptionMapper();

        Response response = mapper.toResponse(new BadRequestException("Explicit bad request message"));

        ValidationErrorResponse payload = (ValidationErrorResponse) response.getEntity();
        assertEquals("Explicit bad request message", payload.details().get(0).message());
    }

    @Test
    void badRequestMapperUsesFallbackWhenNoMessagesExist() {
        BadRequestExceptionMapper mapper = new BadRequestExceptionMapper();
        BadRequestException exception = new BadRequestException((String) null) {
            @Override
            public String getMessage() {
                return null;
            }
        };

        Response response = mapper.toResponse(exception);

        ValidationErrorResponse payload = (ValidationErrorResponse) response.getEntity();
        assertEquals("Invalid request payload.", payload.details().get(0).message());
    }

    @Test
    void badRequestMapperUsesExceptionMessageWhenCauseMessageBlank() {
        BadRequestExceptionMapper mapper = new BadRequestExceptionMapper();
        RuntimeException cause = new RuntimeException("   ");
        BadRequestException exception = new BadRequestException(cause) {
            @Override
            public String getMessage() {
                return "Fallback from exception message";
            }
        };

        Response response = mapper.toResponse(exception);

        ValidationErrorResponse payload = (ValidationErrorResponse) response.getEntity();
        assertEquals("Fallback from exception message", payload.details().get(0).message());
    }

    @Test
    void badRequestMapperUsesFallbackWhenCauseExistsButMessagesUnavailable() {
        BadRequestExceptionMapper mapper = new BadRequestExceptionMapper();
        RuntimeException cause = new RuntimeException((String) null);
        BadRequestException exception = new BadRequestException(cause) {
            @Override
            public String getMessage() {
                return "   ";
            }
        };

        Response response = mapper.toResponse(exception);

        ValidationErrorResponse payload = (ValidationErrorResponse) response.getEntity();
        assertEquals("Invalid request payload.", payload.details().get(0).message());
    }

    @Test
    void conflictMapperUsesFallbackMessageWhenMessageMissingOrBlank() {
        ConflictExceptionMapper mapper = new ConflictExceptionMapper();

        Response nullMessageResponse = mapper.toResponse(new OptimisticLockException((String) null));
        Response blankMessageResponse = mapper.toResponse(new OptimisticLockException("   "));

        ApiErrorResponse nullPayload = (ApiErrorResponse) nullMessageResponse.getEntity();
        ApiErrorResponse blankPayload = (ApiErrorResponse) blankMessageResponse.getEntity();
        assertEquals(409, nullMessageResponse.getStatus());
        assertEquals(409, blankMessageResponse.getStatus());
        assertEquals("conflict", nullPayload.error());
        assertEquals("conflict", blankPayload.error());
        assertEquals("Profile update conflict. Please retry.", nullPayload.message());
        assertEquals("Profile update conflict. Please retry.", blankPayload.message());
    }

    @Test
    void conflictMapperUsesProvidedMessage() {
        ConflictExceptionMapper mapper = new ConflictExceptionMapper();

        Response response = mapper.toResponse(new OptimisticLockException("Custom conflict message"));

        ApiErrorResponse payload = (ApiErrorResponse) response.getEntity();
        assertEquals("Custom conflict message", payload.message());
    }

    @Test
    void forbiddenMapperCoversFallbackAndProvidedMessage() {
        ForbiddenExceptionMapper mapper = new ForbiddenExceptionMapper();

        Response fallback = mapper.toResponse(new ForbiddenException((String) null));
        Response provided = mapper.toResponse(new ForbiddenException("Forbidden details"));
        Response blank = mapper.toResponse(new ForbiddenException("   "));

        ApiErrorResponse fallbackPayload = (ApiErrorResponse) fallback.getEntity();
        ApiErrorResponse providedPayload = (ApiErrorResponse) provided.getEntity();
        ApiErrorResponse blankPayload = (ApiErrorResponse) blank.getEntity();
        assertEquals(403, fallback.getStatus());
        assertEquals(403, provided.getStatus());
        assertEquals(403, blank.getStatus());
        assertEquals("forbidden", fallbackPayload.error());
        assertEquals("forbidden", providedPayload.error());
        assertEquals("forbidden", blankPayload.error());
        assertEquals("Forbidden", fallbackPayload.message());
        assertEquals("Forbidden details", providedPayload.message());
        assertEquals("Forbidden", blankPayload.message());
    }

    @Test
    void notFoundMapperCoversFallbackAndProvidedMessage() {
        NotFoundExceptionMapper mapper = new NotFoundExceptionMapper();

        Response fallback = mapper.toResponse(new NotFoundException((String) null));
        Response provided = mapper.toResponse(new NotFoundException("Not found details"));
        Response blank = mapper.toResponse(new NotFoundException("   "));

        ApiErrorResponse fallbackPayload = (ApiErrorResponse) fallback.getEntity();
        ApiErrorResponse providedPayload = (ApiErrorResponse) provided.getEntity();
        ApiErrorResponse blankPayload = (ApiErrorResponse) blank.getEntity();
        assertEquals(404, fallback.getStatus());
        assertEquals(404, provided.getStatus());
        assertEquals(404, blank.getStatus());
        assertEquals("not_found", fallbackPayload.error());
        assertEquals("not_found", providedPayload.error());
        assertEquals("not_found", blankPayload.error());
        assertEquals("Profile not found", fallbackPayload.message());
        assertEquals("Not found details", providedPayload.message());
        assertEquals("Profile not found", blankPayload.message());
    }

    @Test
    void unauthorizedMapperCoversFallbackAndProvidedMessage() {
        UnauthorizedExceptionMapper mapper = new UnauthorizedExceptionMapper();

        NotAuthorizedException fallbackException = new NotAuthorizedException("Bearer") {
            @Override
            public String getMessage() {
                return null;
            }
        };
        Response fallback = mapper.toResponse(fallbackException);
        NotAuthorizedException blankException = new NotAuthorizedException("Bearer") {
            @Override
            public String getMessage() {
                return "   ";
            }
        };
        Response blank = mapper.toResponse(blankException);
        Response provided = mapper.toResponse(new NotAuthorizedException("Authentication details", "Bearer"));

        ApiErrorResponse fallbackPayload = (ApiErrorResponse) fallback.getEntity();
        ApiErrorResponse blankPayload = (ApiErrorResponse) blank.getEntity();
        ApiErrorResponse providedPayload = (ApiErrorResponse) provided.getEntity();
        assertEquals(401, fallback.getStatus());
        assertEquals(401, blank.getStatus());
        assertEquals(401, provided.getStatus());
        assertEquals("unauthorized", fallbackPayload.error());
        assertEquals("unauthorized", blankPayload.error());
        assertEquals("unauthorized", providedPayload.error());
        assertEquals("Authentication is required.", fallbackPayload.message());
        assertEquals("Authentication is required.", blankPayload.message());
        assertEquals("Authentication details", providedPayload.message());
    }

    @Test
    void openApiSecurityConfigClassIsInstantiable() {
        OpenApiSecurityConfig config = new OpenApiSecurityConfig();
        assertNotNull(config);
    }
}
