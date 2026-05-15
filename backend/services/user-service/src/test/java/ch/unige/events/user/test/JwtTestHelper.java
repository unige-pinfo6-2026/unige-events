package ch.unige.events.user.test;

import org.eclipse.microprofile.jwt.Claims;
import org.eclipse.microprofile.jwt.JsonWebToken;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Test-only utilities to drive the {@code @Inject Instance<JsonWebToken>}
 * resolution paths in {@link ch.unige.events.user.service.UserService} and
 * any other user-service component that reads JWT claims.
 *
 * <p>Pairs with {@link TestJwtProducer} (a CDI producer scoped to
 * {@code src/test}) which serves a {@link JsonWebToken} backed by the
 * static state in {@link JwtTestContext}. Tests call
 * {@link #jwtFor(String, Map)} or related factories to seed the context;
 * any service code that resolves the JWT will then read the configured
 * claims.
 */
public final class JwtTestHelper {

    private JwtTestHelper() {
        // utility class
    }

    /** Returns a JWT for the given auth0Id with no email/profile claims. */
    public static JsonWebToken jwtFor(String auth0Id) {
        return jwtFor(auth0Id, Map.of(), Set.of());
    }

    /** Returns a JWT for the given auth0Id and additional claims. */
    public static JsonWebToken jwtFor(String auth0Id, Map<String, Object> extraClaims) {
        return jwtFor(auth0Id, extraClaims, Set.of());
    }

    public static JsonWebToken jwtFor(String auth0Id, Map<String, Object> extraClaims, Set<String> roles) {
        Map<String, Object> claims = new HashMap<>();
        claims.put(Claims.sub.name(), auth0Id);
        claims.putAll(extraClaims);
        return new FakeJsonWebToken(auth0Id, claims, roles);
    }

    /** Convenience: a JWT carrying an email claim. */
    public static JsonWebToken jwtWithEmail(String auth0Id, String email) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("email", email);
        return jwtFor(auth0Id, claims);
    }

    /** Convenience: a JWT carrying email + name claims. */
    public static JsonWebToken jwtWithProfile(String auth0Id, String email, String displayName) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("email", email);
        claims.put("name", displayName);
        return jwtFor(auth0Id, claims);
    }

    public static JsonWebToken anonymous() {
        return new FakeJsonWebToken(null, Map.of(), Set.of());
    }

    public static JsonWebToken adminJwt(String auth0Id, UUID userId) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("uuid", userId.toString());
        return jwtFor(auth0Id, claims, Set.of("ADMIN"));
    }

    /** Plain POJO {@link JsonWebToken} — read-only, request-scoped per test. */
    public static final class FakeJsonWebToken implements JsonWebToken {

        private final String name;
        private final Map<String, Object> claims;
        private final Set<String> groups;

        public FakeJsonWebToken(String name, Map<String, Object> claims, Set<String> groups) {
            this.name = name;
            // Allow null values (e.g. claims.put("email", null)) — Map.copyOf rejects
            // them but tests need that path to verify NotAuthorized handling.
            this.claims = new java.util.HashMap<>(claims);
            this.groups = Set.copyOf(groups);
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public Set<String> getClaimNames() {
            return claims.keySet();
        }

        @SuppressWarnings("unchecked")
        @Override
        public <T> T getClaim(String claimName) {
            if ("groups".equals(claimName)) {
                return (T) groups;
            }
            return (T) claims.get(claimName);
        }

        @Override
        public Set<String> getGroups() {
            return Collections.unmodifiableSet(groups);
        }
    }
}
