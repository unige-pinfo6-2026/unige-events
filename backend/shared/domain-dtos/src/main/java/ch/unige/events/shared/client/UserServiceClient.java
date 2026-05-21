package ch.unige.events.shared.client;

import ch.unige.events.shared.domain.dto.AttendeeProjection;
import ch.unige.events.shared.domain.dto.IdProjection;
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

    /**
     * Resolves an Auth0 subject claim ({@code auth0Id}) to the matching
     * {@link User} UUID. Internal endpoint (SCRUM-99 Décision E) consumed
     * by notification-service to translate {@code SecurityIdentity.getPrincipal().getName()}
     * into the {@code notifications.user_id} stored UUID. Provider :
     * {@code GET /users/_internal-by-auth0-id/{auth0Id}} on user-service,
     * gated {@code @Internal} (X-Internal-Token). Not in {@code openapi.yaml} ;
     * documented in {@code backend/docs/internal-endpoints.md} entry #9.
     *
     * <p>The 404 envelope is shared between "unknown auth0Id" and "bad
     * X-Internal-Token" — no oracle on token validity (cf. InternalTokenFilter).
     *
     * <p>Resilience : a legitimate 404 (user not provisioned) must short-circuit
     * the retry and the circuit breaker — otherwise a wave of new sign-ins
     * (not yet provisioned via {@code GET /users/me}) would trip the CB. The
     * caller surfaces the 404 as a 401 / 404 to the browser depending on
     * context.
     */
    @GET
    @Path("/_internal-by-auth0-id/{auth0Id}")
    @Retry(maxRetries = 3, delay = 200, delayUnit = ChronoUnit.MILLIS, abortOn = NotFoundException.class)
    @Timeout(value = 2, unit = ChronoUnit.SECONDS)
    @CircuitBreaker(failureRatio = 0.5, requestVolumeThreshold = 10, skipOn = NotFoundException.class)
    @Fallback(fallbackMethod = "getInternalByAuth0IdFallback")
    IdProjection getInternalByAuth0Id(@PathParam("auth0Id") String auth0Id);

    default IdProjection getInternalByAuth0IdFallback(String auth0Id) {
        // Identity resolution is critical for notification-service's user-facing
        // endpoints — silently returning null would surface as a NullPointerException
        // in the caller. Throw a 503 envelope that the resource layer can
        // translate to a meaningful error.
        Log.errorf("[REST_FALLBACK_user-service] getInternalByAuth0Id(%s) — downstream unavailable; notification endpoints degraded for this caller", auth0Id);
        throw new jakarta.ws.rs.WebApplicationException(
                "User identity service unavailable",
                jakarta.ws.rs.core.Response.Status.SERVICE_UNAVAILABLE);
    }

    /**
     * Returns the UUIDs of all users that {@code followerId} follows with
     * status ACCEPTED. Consumed by event-service to implement the
     * {@code followedOnly} filter on {@code GET /events} (SCRUM-168).
     *
     * <p>Internal endpoint (cf. internal-endpoints.md entry #11) — not routed
     * by Kong, not in {@code openapi.yaml}. Gated by {@code X-Internal-Token}.
     *
     * <p>The 404 envelope is shared between "unknown followerId" and "bad
     * X-Internal-Token" (anti-oracle). A 404 must not be retried nor count
     * against the circuit breaker — a wrong token is a deployment error that
     * should surface immediately rather than be silently swallowed by retries.
     *
     * <p>Fallback returns an empty list so a genuine transient outage degrades
     * gracefully to an empty {@code followedOnly} feed rather than a 503.
     */
    @GET
    @Path("/_internal-followed-ids")
    @Retry(maxRetries = 3, delay = 200, delayUnit = ChronoUnit.MILLIS, abortOn = NotFoundException.class)
    @Timeout(value = 2, unit = ChronoUnit.SECONDS)
    @CircuitBreaker(failureRatio = 0.5, requestVolumeThreshold = 10, skipOn = NotFoundException.class)
    @Fallback(fallbackMethod = "getFollowedIdsFallback")
    List<UUID> getFollowedIds(@QueryParam("followerId") UUID followerId);

    default List<UUID> getFollowedIdsFallback(UUID followerId) {
        Log.warnf("[REST_FALLBACK_user-service] getFollowedIds(%s) — returning empty list (downstream unavailable, followedOnly feed will be empty)", followerId);
        return List.of();
    }
}
