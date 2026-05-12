package ch.unige.events.shared.storage;

import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FileTooLargeExceptionTest {

    @Test
    void exception_isMappedTo413Status() {
        FileTooLargeException ex = new FileTooLargeException("too big");
        assertEquals(Response.Status.REQUEST_ENTITY_TOO_LARGE.getStatusCode(),
                ex.getResponse().getStatus());
    }

    @Test
    void exception_preservesMessage() {
        FileTooLargeException ex = new FileTooLargeException("File exceeds 2 MB limit");
        assertEquals("File exceeds 2 MB limit", ex.getMessage());
    }
}
