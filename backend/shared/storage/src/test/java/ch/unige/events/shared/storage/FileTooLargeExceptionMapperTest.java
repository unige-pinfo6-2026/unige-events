package ch.unige.events.shared.storage;

import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FileTooLargeExceptionMapperTest {

    private final FileTooLargeExceptionMapper mapper = new FileTooLargeExceptionMapper();

    @Test
    void mapper_returns413Status() {
        Response response = mapper.toResponse(new FileTooLargeException("too big"));
        assertEquals(Response.Status.REQUEST_ENTITY_TOO_LARGE.getStatusCode(),
                response.getStatus());
    }

    @Test
    void mapper_emitsApplicationJsonContentType() {
        Response response = mapper.toResponse(new FileTooLargeException("too big"));
        assertTrue(MediaType.APPLICATION_JSON_TYPE.isCompatible(response.getMediaType()));
    }

    @Test
    void mapper_bodyHasErrorPayloadTooLargeAndMessage() {
        Response response = mapper.toResponse(new FileTooLargeException("File exceeds 2 MB limit"));
        StorageErrorBody body = assertInstanceOf(StorageErrorBody.class, response.getEntity());
        assertEquals("payload_too_large", body.error());
        assertEquals("File exceeds 2 MB limit", body.message());
    }
}
