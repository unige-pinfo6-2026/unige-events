package ch.unige.events.shared.client;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import jakarta.ws.rs.client.ClientRequestContext;
import jakarta.ws.rs.client.ClientRequestFilter;
import jakarta.ws.rs.ext.Provider;
import org.eclipse.microprofile.jwt.JsonWebToken;

/**
 * Propagates the caller's JWT as {@code Authorization: Bearer <token>} on
 * outgoing service-to-service calls. Used by {@code UserMeClient} to call
 * {@code GET /users/me} on behalf of the current user, allowing services to
 * resolve the caller's internal UUID without an Auth0 custom claim.
 *
 * <p>No-op when no JWT is present (anonymous / @PermitAll requests).
 *
 * <p>{@code @ApplicationScoped} is required so Quarkus instantiates this
 * class via CDI (enabling {@code @Inject}) rather than via reflection
 * ({@code new BearerTokenClientFilter()}), which would leave {@code jwt} null.
 */
@Provider
@ApplicationScoped
public class BearerTokenClientFilter implements ClientRequestFilter {

    private final Instance<JsonWebToken> jwt;

    @Inject
    public BearerTokenClientFilter(Instance<JsonWebToken> jwt) {
        this.jwt = jwt;
    }

    @Override
    public void filter(ClientRequestContext ctx) {
        JsonWebToken token = jwt.isResolvable() ? jwt.get() : null;
        if (token == null) return;
        String raw = token.getRawToken();
        if (raw != null && !raw.isBlank()) {
            ctx.getHeaders().putSingle("Authorization", "Bearer " + raw);
        }
    }
}
