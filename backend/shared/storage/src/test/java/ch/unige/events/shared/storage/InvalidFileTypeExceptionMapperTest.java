package ch.unige.events.shared.storage;

import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InvalidFileTypeExceptionMapperTest {

    private final InvalidFileTypeExceptionMapper mapper = new InvalidFileTypeExceptionMapper();

    @Test
    void mapper_returns400Status() {
        Response response = mapper.toResponse(new InvalidFileTypeException("svg rejected"));
        assertEquals(Response.Status.BAD_REQUEST.getStatusCode(), response.getStatus());
    }

    @Test
    void mapper_emitsApplicationJsonContentType() {
        Response response = mapper.toResponse(new InvalidFileTypeException("svg rejected"));
        assertTrue(MediaType.APPLICATION_JSON_TYPE.isCompatible(response.getMediaType()));
    }

    @Test
    void mapper_bodyHasErrorInvalidFileTypeAndMessage() {
        Response response = mapper.toResponse(
                new InvalidFileTypeException("File must be a JPEG, PNG, WebP or GIF image"));
        StorageErrorBody body = assertInstanceOf(StorageErrorBody.class, response.getEntity());
        assertEquals("invalid_file_type", body.error());
        assertEquals("File must be a JPEG, PNG, WebP or GIF image", body.message());
    }
}
