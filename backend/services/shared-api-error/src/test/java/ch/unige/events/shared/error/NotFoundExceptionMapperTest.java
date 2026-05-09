package ch.unige.events.shared.error;

import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

class NotFoundExceptionMapperTest {

    @Test
    void mapsToCanonicalEnvelope() {
        NotFoundExceptionMapper mapper = new NotFoundExceptionMapper();
        Response r = mapper.toResponse(new NotFoundException("Event 42 not found"));
        assertEquals(404, r.getStatus());
        assertInstanceOf(ApiErrorResponse.class, r.getEntity());
        ApiErrorResponse body = (ApiErrorResponse) r.getEntity();
        assertEquals("not_found", body.error());
        assertEquals("Event 42 not found", body.message());
    }

    @Test
    void blankMessageFallsBackToDefault() {
        Response r = new NotFoundExceptionMapper().toResponse(new NotFoundException(""));
        ApiErrorResponse body = (ApiErrorResponse) r.getEntity();
        assertEquals("not_found", body.error());
        assertEquals("Resource not found", body.message());
    }

    @Test
    void nullMessageFallsBackToDefault() {
        NotFoundException ex = new NotFoundException((String) null);
        Response r = new NotFoundExceptionMapper().toResponse(ex);
        ApiErrorResponse body = (ApiErrorResponse) r.getEntity();
        assertEquals("not_found", body.error());
        assertEquals("Resource not found", body.message());
    }
}
