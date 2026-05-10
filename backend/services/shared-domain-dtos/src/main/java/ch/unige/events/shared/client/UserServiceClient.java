package ch.unige.events.shared.client;

import ch.unige.events.shared.domain.dto.UserPublicResponse;
import ch.unige.events.shared.tracing.RequestIdClientFilter;

import io.quarkus.logging.Log;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import org.eclipse.microprofile.faulttolerance.CircuitBreaker;
import org.eclipse.microprofile.faulttolerance.Fallback;
import org.eclipse.microprofile.faulttolerance.Retry;
import org.eclipse.microprofile.faulttolerance.Timeout;
import org.eclipse.microprofile.rest.client.annotation.RegisterProvider;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

import java.time.temporal.ChronoUnit;
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
 * post-finalization (cf. internal-endpoints.md). Consumers resolve the
 * auth0 sub locally via {@link
 * ch.unige.events.shared.domain.projections.Auth0IdResolver} and call
 * {@link #getById(UUID)} with the resolved UUID.
 */
@RegisterRestClient(configKey = "user-service")
@RegisterProvider(RequestIdClientFilter.class)
@RegisterProvider(ch.unige.events.shared.tracing.InternalTokenClientFilter.class)
@Path("/users")
public interface UserServiceClient {

    @GET
    @Path("/{id}")
    @Retry(maxRetries = 3, delay = 200, delayUnit = ChronoUnit.MILLIS)
    @Timeout(value = 2, unit = ChronoUnit.SECONDS)
    @CircuitBreaker(failureRatio = 0.5, requestVolumeThreshold = 10)
    @Fallback(fallbackMethod = "getByIdFallback")
    UserPublicResponse getById(@PathParam("id") UUID id);

    default UserPublicResponse getByIdFallback(UUID id) {
        Log.warnf("[REST_FALLBACK_user-service] getById(%s) — returning null (downstream unavailable, comments/reports will display anonymized author)", id);
        return null;
    }
}
