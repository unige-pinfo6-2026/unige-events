package ch.unige.events.shared.storage;

import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class InvalidFileTypeExceptionTest {

    @Test
    void exception_isMappedTo400Status() {
        InvalidFileTypeException ex = new InvalidFileTypeException("svg rejected");
        assertEquals(Response.Status.BAD_REQUEST.getStatusCode(),
                ex.getResponse().getStatus());
    }

    @Test
    void exception_preservesMessage() {
        InvalidFileTypeException ex = new InvalidFileTypeException("File must be a JPEG, PNG, WebP or GIF image");
        assertEquals("File must be a JPEG, PNG, WebP or GIF image", ex.getMessage());
    }
}
