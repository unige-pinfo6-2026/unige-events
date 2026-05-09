package ch.unige.events.shared.ratelimit;

import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RateLimitExceptionMapperTest {

    private final RateLimitExceptionMapper mapper = new RateLimitExceptionMapper();

    @Test
    void mapper_returns429Status() {
        Response response = mapper.toResponse(
                new RateLimitExceededException("slow down", 7L));
        assertEquals(Response.Status.TOO_MANY_REQUESTS.getStatusCode(),
                response.getStatus());
    }

    @Test
    void mapper_setsRetryAfterHeader() {
        Response response = mapper.toResponse(
                new RateLimitExceededException("slow down", 7L));
        assertEquals("7", response.getHeaderString(HttpHeaders.RETRY_AFTER));
    }

    @Test
    void mapper_emitsApplicationJsonContentType() {
        Response response = mapper.toResponse(
                new RateLimitExceededException("slow down", 1L));
        assertTrue(MediaType.APPLICATION_JSON_TYPE.isCompatible(response.getMediaType()),
                "Content-Type must be application/json, got " + response.getMediaType());
    }

    @Test
    void mapper_bodyHasErrorRateLimitedAndMessage() {
        Response response = mapper.toResponse(
                new RateLimitExceededException("budget exhausted", 3L));
        Object entity = response.getEntity();
        RateLimitExceptionMapper.RateLimitErrorBody body =
                assertInstanceOf(RateLimitExceptionMapper.RateLimitErrorBody.class, entity);
        assertEquals("rate_limited", body.error());
        assertEquals("budget exhausted", body.message());
    }

    @Test
    void mapper_largeRetryAfterIsSerializedAsLong() {
        long large = 86_400L;
        Response response = mapper.toResponse(
                new RateLimitExceededException("come back tomorrow", large));
        assertEquals(Long.toString(large), response.getHeaderString(HttpHeaders.RETRY_AFTER));
    }
}
