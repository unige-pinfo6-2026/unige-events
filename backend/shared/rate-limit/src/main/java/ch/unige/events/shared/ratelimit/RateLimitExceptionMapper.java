package ch.unige.events.shared.ratelimit;

import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

/**
 * Translates {@link RateLimitExceededException} into a {@code 429 Too Many
 * Requests} response with the {@code Retry-After} header (RFC 6585 §4) and
 * the project-standard {@code {error, message}} envelope.
 *
 * <p>Each microservice has its own {@code ApiErrorResponse} record in its
 * own package; replicating that record here would couple this lib to the
 * wrong layer. Instead, this mapper emits its own {@link RateLimitErrorBody}
 * record with the same field shape — the JSON wire-format is identical, so
 * the frontend doesn't see a difference.
 */
@Provider
public class RateLimitExceptionMapper implements ExceptionMapper<RateLimitExceededException> {

    @Override
    public Response toResponse(RateLimitExceededException exception) {
        return Response.status(Response.Status.TOO_MANY_REQUESTS)
                .header(HttpHeaders.RETRY_AFTER, Long.toString(exception.getRetryAfterSeconds()))
                .type(MediaType.APPLICATION_JSON)
                .entity(new RateLimitErrorBody("rate_limited", exception.getMessage()))
                .build();
    }

    /** {@code {error, message}} envelope — wire-compatible with each service's
     *  {@code ApiErrorResponse} record. Not exported as part of the API. */
    public record RateLimitErrorBody(String error, String message) {}
}
