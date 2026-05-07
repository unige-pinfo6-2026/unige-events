package ch.unige.events.exception.mapper;

import ch.unige.events.dto.ApiErrorResponse;
import ch.unige.events.exception.RateLimitExceededException;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

/**
 * Translates {@link RateLimitExceededException} into a {@code 429 Too Many
 * Requests} response with the {@code Retry-After} header (RFC 6585 §4) and the
 * project-standard error envelope.
 */
@Provider
public class RateLimitExceptionMapper implements ExceptionMapper<RateLimitExceededException> {

    @Override
    public Response toResponse(RateLimitExceededException exception) {
        return Response.status(Response.Status.TOO_MANY_REQUESTS)
                .header(HttpHeaders.RETRY_AFTER, Long.toString(exception.getRetryAfterSeconds()))
                .type(MediaType.APPLICATION_JSON)
                .entity(new ApiErrorResponse("rate_limited", exception.getMessage()))
                .build();
    }
}
