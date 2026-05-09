package ch.unige.events.shared.domain.projections;

import org.eclipse.microprofile.jwt.JsonWebToken;

/**
 * Minimal helpers for extracting the Auth0 user identifier from a
 * {@link JsonWebToken}. Centralised so a future migration to a
 * different sub claim format only touches one file.
 */
public final class Auth0IdResolver {

    private Auth0IdResolver() {
    }

    /**
     * Returns the {@code sub} claim of the JWT, or {@code null} if the
     * token is null (e.g. anonymous request in {@code @PermitAll}
     * resources).
     */
    public static String resolveUserId(JsonWebToken jwt) {
        return jwt == null ? null : jwt.getName();
    }
}
