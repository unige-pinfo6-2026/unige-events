package ch.unige.events.shared.domain.projections;

import io.quarkus.logging.Log;
import org.eclipse.microprofile.jwt.JsonWebToken;

import java.util.UUID;

/**
 * Minimal helpers for extracting the Auth0 user identifier from a
 * {@link JsonWebToken}. Centralised so a future migration to a
 * different sub claim format only touches one file.
 */
public final class Auth0IdResolver {

    /** Custom JWT claim that carries the application UUID of the user. */
    public static final String UUID_CLAIM = "uuid";

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

    /**
     * Returns the application UUID of the user, read from the custom
     * {@code uuid} claim that Auth0 must include in the JWT post Étape 21
     * finalization-ultimate (devops-handoff item 6 — OIDC config).
     *
     * <p>Returns {@code null} when:
     * <ul>
     *   <li>the JWT itself is null (anonymous caller), or</li>
     *   <li>the claim is absent (legacy JWT minted before the Auth0
     *       config update), or</li>
     *   <li>the claim is present but does not parse as a UUID.</li>
     * </ul>
     *
     * <p>Callers must treat a null return as "caller UUID unknown" — for
     * the cascade SCRUM-136 self-check this means {@code coOrganizerOf}
     * stays null in the response (no leak).
     */
    public static UUID resolveUserUuid(JsonWebToken jwt) {
        if (jwt == null) {
            return null;
        }
        Object raw = jwt.getClaim(UUID_CLAIM);
        if (raw == null) {
            return null;
        }
        try {
            return UUID.fromString(raw.toString());
        } catch (IllegalArgumentException e) {
            Log.warnf(
                "[AUTH0_UUID_MALFORMED] JWT claim 'uuid'=%s does not parse — caller treated as anonymous (sub=%s)",
                raw, jwt.getName()
            );
            return null;
        }
    }
}
