package ch.unige.events.shared.client;

import ch.unige.events.shared.domain.dto.AttendeeProjection;
import ch.unige.events.shared.domain.dto.UserPublicResponse;
import ch.unige.events.shared.tracing.RequestIdClientFilter;

import io.quarkus.logging.Log;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.QueryParam;
import org.eclipse.microprofile.faulttolerance.CircuitBreaker;
import org.eclipse.microprofile.faulttolerance.Fallback;
import org.eclipse.microprofile.faulttolerance.Retry;
import org.eclipse.microprofile.faulttolerance.Timeout;
import org.eclipse.microprofile.rest.client.annotation.RegisterProvider;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

/**
 * REST client for cross-service reads of User entities.
 *
 * <p>Producer: user-service ({@code GET /users/{id}}). The endpoint
 * applies the ISSUE-93 anti-oracle: 404 if {@code profilePublic == false}
 * and the caller is neither the user themselves nor an admin.
 *
 * <p>Consumers: event-service, engagement-service, moderation-service.
 *
 * <p>URL: configured per-consumer via
 * {@code quarkus.rest-client.user-service.url=
 *  ${USER_SERVICE_URL:http://user-service:8080}}.
 *
 * <p>Note: there is no {@code /users/by-auth0/{auth0Id}} endpoint
 * post-finalization (cf. internal-endpoints.md). Consumers use
 * {@code GET /users/me} when they need the current caller UUID, then call
 * {@link #getById(UUID)} for profile enrichment.
 */
@RegisterRestClient(configKey = "user-service")
@RegisterProvider(RequestIdClientFilter.class)
@RegisterProvider(ch.unige.events.shared.tracing.InternalTokenClientFilter.class)
@Path("/users")
public interface UserServiceClient {

    @GET
    @Path("/{id}")
    // Legitimate 404s (user hard-deleted / unknown UUID) must not count
    // against the circuit-breaker volume nor trigger a Retry — otherwise
    // a handful of 404s in a row trip the CB and every subsequent call
    // silently degrades to the @Fallback null payload.
    @Retry(maxRetries = 3, delay = 200, delayUnit = ChronoUnit.MILLIS, abortOn = NotFoundException.class)
    @Timeout(value = 2, unit = ChronoUnit.SECONDS)
    @CircuitBreaker(failureRatio = 0.5, requestVolumeThreshold = 10, skipOn = NotFoundException.class)
    @Fallback(fallbackMethod = "getByIdFallback")
    UserPublicResponse getById(@PathParam("id") UUID id);

    default UserPublicResponse getByIdFallback(UUID id) {
        Log.warnf("[REST_FALLBACK_user-service] getById(%s) — returning null (downstream unavailable, comments/reports will display anonymized author)", id);
        return null;
    }

    /**
     * Bulk projection used by engagement-service to feed the attendees-list
     * privacy filter on {@code GET /events/{id}/attendees}.
     *
     * <p>Internal-only endpoint (cf. internal-endpoints.md entry #7) — bypasses
     * the ISSUE-93 anti-oracle so the consumer can decide whether to anonymize
     * each row based on {@link AttendeeProjection#profilePublic}. Missing ids
     * (deleted users) are silently dropped from the response; the consumer is
     * responsible for surfacing them as anonymous rows.
     */
    @GET
    @Path("/_internal-attendee-projections")
    @Retry(maxRetries = 3, delay = 200, delayUnit = ChronoUnit.MILLIS)
    @Timeout(value = 2, unit = ChronoUnit.SECONDS)
    @CircuitBreaker(failureRatio = 0.5, requestVolumeThreshold = 10)
    @Fallback(fallbackMethod = "getAttendeeProjectionsFallback")
    List<AttendeeProjection> getAttendeeProjections(@QueryParam("ids") List<UUID> ids);

    default List<AttendeeProjection> getAttendeeProjectionsFallback(List<UUID> ids) {
        Log.warnf("[REST_FALLBACK_user-service] getAttendeeProjections(size=%d) — returning empty list (downstream unavailable, attendees rendered anonymous)", ids == null ? 0 : ids.size());
        return List.of();
    }
}
