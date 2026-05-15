package ch.unige.events.report.test;

import org.eclipse.microprofile.jwt.Claims;
import org.eclipse.microprofile.jwt.JsonWebToken;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Test-only utilities to drive the {@code @Inject Instance<JsonWebToken>}
 * resolution paths in {@link ch.unige.events.report.service.ReportService}.
 *
 * <p>Pairs with {@link TestJwtProducer} (CDI alternative producer scoped
 * to {@code src/test}) which serves a {@link JsonWebToken} backed by the
 * per-thread state in {@link JwtTestContext}.
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
