package ch.unige.events.shared.ratelimit;

import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RateLimitExceededExceptionTest {

    @Test
    void exception_carriesRetryAfterSeconds() {
        RateLimitExceededException ex = new RateLimitExceededException("slow down", 42L);
        assertEquals(42L, ex.getRetryAfterSeconds());
    }

    @Test
    void exception_isMappedTo429Status() {
        RateLimitExceededException ex = new RateLimitExceededException("slow down", 1L);
        assertEquals(Response.Status.TOO_MANY_REQUESTS.getStatusCode(),
                ex.getResponse().getStatus());
    }

    @Test
    void exception_preservesMessage() {
        RateLimitExceededException ex = new RateLimitExceededException("budget exhausted", 5L);
        assertEquals("budget exhausted", ex.getMessage());
    }
}
