package ch.unige.events.event.test;

import org.eclipse.microprofile.jwt.Claims;
import org.eclipse.microprofile.jwt.JsonWebToken;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Test-only helper to produce {@link JsonWebToken} instances for
 * {@link TestCallerIdentity}. Pairs with {@link TestJwtProducer} (CDI bean)
 * and {@link JwtTestContext} (static handoff).
 */
public final class JwtTestHelper {

    private JwtTestHelper() {
        // utility class
    }

    public static JsonWebToken jwtFor(UUID userId) {
        return jwtFor(userId, Set.of());
    }

    public static JsonWebToken jwtFor(UUID userId, Set<String> roles) {
        Map<String, Object> claims = new HashMap<>();
        claims.put(Claims.sub.name(), "auth0|" + userId);
        claims.put("uuid", userId.toString());
        return new FakeJsonWebToken("auth0|" + userId, claims, roles);
    }

    public static JsonWebToken adminJwt(UUID userId) {
        return jwtFor(userId, Set.of("ADMIN"));
    }

    public static JsonWebToken anonymous() {
        return new FakeJsonWebToken(null, Map.of(), Set.of());
    }

    public static final class FakeJsonWebToken implements JsonWebToken {

        private final String name;
        private final Map<String, Object> claims;
        private final Set<String> groups;

        public FakeJsonWebToken(String name, Map<String, Object> claims, Set<String> groups) {
            this.name = name;
            this.claims = Map.copyOf(claims);
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
