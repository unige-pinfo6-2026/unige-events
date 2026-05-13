package ch.unige.events.shared.domain.projections;

import ch.unige.events.shared.client.UserMeClient;
import ch.unige.events.shared.domain.dto.UserPublicResponse;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.WebApplicationException;
import org.eclipse.microprofile.rest.client.inject.RestClient;

import java.util.UUID;

/**
 * Request-scoped bean that resolves the current caller's internal UUID by
 * calling {@code GET /users/me} on user-service. The result is cached for
 * the lifetime of the request so at most one remote call is made per request.
 */
@RequestScoped
public class CallerIdentity {

    @Inject
    @RestClient
    UserMeClient userMeClient;

    private UUID cachedUuid;

    /**
     * Returns the caller's UUID, calling user-service if not yet resolved.
     * Throws {@link NotFoundException} if the caller has no valid JWT or
     * user-service cannot identify them.
     */
    public UUID requireUuid() {
        if (cachedUuid != null) return cachedUuid;
        try {
            UserPublicResponse me = userMeClient.getMe();
            if (me == null || me.id() == null) {
                throw new NotFoundException("User profile not found");
            }
            cachedUuid = me.id();
            return cachedUuid;
        } catch (WebApplicationException e) {
            throw new NotFoundException("User profile not found");
        }
    }

    /**
     * Like {@link #requireUuid()} but returns {@code null} instead of
     * throwing. Use on {@code @PermitAll} endpoints where the caller UUID
     * is optional.
     */
    public UUID getUuid() {
        try {
            return requireUuid();
        } catch (Exception e) {
            return null;
        }
    }
}
