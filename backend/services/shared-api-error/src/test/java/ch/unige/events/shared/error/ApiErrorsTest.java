package ch.unige.events.shared.error;

import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ApiErrorsTest {

    @Test
    void badRequest_buildsJson400WithErrorAndMessage() {
        WebApplicationException ex = ApiErrors.badRequest("validation_failed", "Field x missing");
        Response r = ex.getResponse();
        assertEquals(400, r.getStatus());
        assertEquals(MediaType.APPLICATION_JSON_TYPE, r.getMediaType());
        ApiErrorResponse body = assertInstanceOf(ApiErrorResponse.class, r.getEntity());
        assertEquals("validation_failed", body.error());
        assertEquals("Field x missing", body.message());
    }

    @Test
    void conflict_returns409() {
        Response r = ApiErrors.conflict("conflict", "Already exists").getResponse();
        assertEquals(409, r.getStatus());
        assertEquals("conflict", ((ApiErrorResponse) r.getEntity()).error());
    }

    @Test
    void unprocessable_returns422() {
        Response r = ApiErrors.unprocessable("recurrence_unbounded", "Need endDate or maxOccurrences").getResponse();
        assertEquals(422, r.getStatus());
        assertEquals("recurrence_unbounded", ((ApiErrorResponse) r.getEntity()).error());
    }

    @Test
    void forbidden_returns403() {
        Response r = ApiErrors.forbidden("forbidden", "Not your event").getResponse();
        assertEquals(403, r.getStatus());
        assertEquals("Not your event", ((ApiErrorResponse) r.getEntity()).message());
    }

    @Test
    void notFound_returns404() {
        Response r = ApiErrors.notFound("not_found", "Event 42 does not exist").getResponse();
        assertEquals(404, r.getStatus());
        assertEquals("not_found", ((ApiErrorResponse) r.getEntity()).error());
    }

    @Test
    void allHelpersThrowableThroughout() {
        // Sentinel: each helper produces a throwable WebApplicationException.
        assertThrows(WebApplicationException.class, () -> { throw ApiErrors.badRequest("a", "b"); });
        assertThrows(WebApplicationException.class, () -> { throw ApiErrors.conflict("a", "b"); });
        assertThrows(WebApplicationException.class, () -> { throw ApiErrors.unprocessable("a", "b"); });
        assertThrows(WebApplicationException.class, () -> { throw ApiErrors.forbidden("a", "b"); });
        assertThrows(WebApplicationException.class, () -> { throw ApiErrors.notFound("a", "b"); });
    }
}
