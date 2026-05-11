package ch.unige.events.shared.tracing;

import ch.unige.events.shared.jaxrs.InternalTokenFilter;
import jakarta.inject.Inject;
import jakarta.ws.rs.client.ClientRequestContext;
import jakarta.ws.rs.client.ClientRequestFilter;
import jakarta.ws.rs.ext.Provider;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/**
 * Sets the {@code X-Internal-Token} header on every outgoing
 * service-to-service REST call. Pairs with
 * {@code shared-jaxrs.InternalTokenFilter} on the provider side which
 * accepts only requests carrying the matching token.
 *
 * <p>Reads the token from {@code unige.internal-token} which is sourced
 * from {@code INTERNAL_TOKEN} env var (Doppler in prod). When the
 * config is missing or empty (e.g. tests without the property set),
 * the filter is a no-op so unit tests don't have to stage the value.
 *
 * <p>Décision C finalization-complete — closes SEC-002-bis (BLOQUANT)
 * on the consumer side.
 */
@Provider
public class InternalTokenClientFilter implements ClientRequestFilter {

    // Re-use the canonical constant from shared-jaxrs.InternalTokenFilter to avoid drift.
    public static final String HEADER = InternalTokenFilter.HEADER;

    @Inject
    @ConfigProperty(name = "unige.internal-token", defaultValue = "")
    String token;

    @Override
    public void filter(ClientRequestContext ctx) {
        if (token != null && !token.isEmpty()) {
            ctx.getHeaders().putSingle(HEADER, token);
        }
    }
}
