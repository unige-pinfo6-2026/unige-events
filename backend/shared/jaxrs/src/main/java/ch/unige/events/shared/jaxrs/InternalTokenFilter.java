package ch.unige.events.shared.jaxrs;

import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.ext.Provider;
import org.eclipse.microprofile.config.ConfigProvider;

/**
 * Validates the {@code X-Internal-Token} header on every request hitting
 * a resource (or method) annotated with {@link Internal}. Returns 404
 * (envelope produced by {@code NotFoundExceptionMapper}) on missing or
 * mismatched header so the resource cannot be probed externally —
 * absence is indistinguishable from "endpoint does not exist".
 *
 * <p>Provided through {@link Internal} as a JAX-RS {@code @NameBinding}
 * so the filter only fires on annotated paths and never blocks public
 * endpoints sharing the same JAX-RS application.
 *
 * <p>Décision C finalization-complete — closes SEC-002-bis (BLOQUANT).
 */
@Provider
@Internal
public class InternalTokenFilter implements ContainerRequestFilter {

    public static final String HEADER = "X-Internal-Token";
    public static final String CONFIG_KEY = "unige.internal-token";

    /** Package-private setter used by tests to short-circuit ConfigProvider. */
    String expectedOverride;

    @Override
    public void filter(ContainerRequestContext ctx) {
        String expected = (expectedOverride != null) ? expectedOverride : resolveExpected();
        String got = ctx.getHeaderString(HEADER);
        if (expected == null || expected.isEmpty() || got == null || !got.equals(expected)) {
            // Same envelope as a missing route — no oracle leak.
            throw new NotFoundException();
        }
    }

    private static String resolveExpected() {
        return ConfigProvider.getConfig()
                .getOptionalValue(CONFIG_KEY, String.class)
                .orElse("");
    }
}
