package ch.unige.events.shared.client;

import ch.unige.events.shared.domain.dto.UserPublicResponse;
import ch.unige.events.shared.client.BearerTokenClientFilter;
import ch.unige.events.shared.tracing.RequestIdClientFilter;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import org.eclipse.microprofile.faulttolerance.Retry;
import org.eclipse.microprofile.faulttolerance.Timeout;
import org.eclipse.microprofile.rest.client.annotation.RegisterProvider;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

import java.time.temporal.ChronoUnit;

/**
 * REST client to call {@code GET /users/me} on user-service using the
 * caller's own Bearer token (propagated by {@link BearerTokenClientFilter}).
 *
 * <p>Used by {@link ch.unige.events.shared.domain.projections.CallerIdentity}
 * to resolve the caller's internal UUID without requiring a custom JWT claim
 * injected by an Auth0 Action.
 *
 * <p>Uses the same {@code user-service} config key as {@link UserServiceClient}.
 */
@RegisterRestClient(configKey = "user-service")
@RegisterProvider(RequestIdClientFilter.class)
@RegisterProvider(BearerTokenClientFilter.class)
@Path("/users")
public interface UserMeClient {

    @GET
    @Path("/me")
    @Retry(maxRetries = 2, delay = 100, delayUnit = ChronoUnit.MILLIS)
    @Timeout(value = 2, unit = ChronoUnit.SECONDS)
    UserPublicResponse getMe();
}
