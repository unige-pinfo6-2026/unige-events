package ch.unige.events.shared.ratelimit;

import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;

/**
 * Thrown by {@link RateLimitInterceptor} when the calling principal has
 * exhausted its per-user budget on the targeted endpoint.
 *
 * <p>{@link #getRetryAfterSeconds()} carries the duration the client must
 * wait before retrying — emitted as the {@code Retry-After} response header
 * by {@link RateLimitExceptionMapper}.
 */
public class RateLimitExceededException extends WebApplicationException {

    private final long retryAfterSeconds;

    public RateLimitExceededException(String message, long retryAfterSeconds) {
        super(message, Response.Status.TOO_MANY_REQUESTS);
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public long getRetryAfterSeconds() {
        return retryAfterSeconds;
    }
}
