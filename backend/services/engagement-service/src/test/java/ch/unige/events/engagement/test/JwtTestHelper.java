package ch.unige.events.engagement.test;

import org.eclipse.microprofile.jwt.Claims;
import org.eclipse.microprofile.jwt.JsonWebToken;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Test-only utilities to drive the {@code @Inject Instance<JsonWebToken>}
 * resolution paths in {@link ch.unige.events.engagement.attendance.service.AttendanceService}
 * and {@link ch.unige.events.engagement.comment.service.CommentService}.
 *
 * <p>Pairs with {@link TestJwtProducer} (a CDI alternative producer
 * scoped to {@code src/test}) which serves a {@link JsonWebToken} backed by
 * the per-thread state in {@link JwtTestContext}. Tests call
 * {@link #jwtFor(UUID)} or {@link #adminJwt(UUID)} to seed the context;
 * any service code that resolves the JWT will then read the configured
 * claims.
 *
 * <p>The actual JWT object is a lightweight {@link FakeJsonWebToken}
 * implementation — no signing, no verification, no Quarkus runtime needed.
 * Using a real signed token would require introducing public/private RSA
 * keypairs + flipping {@code quarkus.oidc.enabled=true} for tests, which
 * would conflict with the existing test profile.
 */
public final class JwtTestHelper {

    private JwtTestHelper() {
        // utility class
    }

    /**
     * Returns a {@link JsonWebToken} carrying the test caller UUID and
     * {@code sub} (the auth0Id) wired to {@code "auth0|<uuid>"}. Roles
     * are empty.
     */
    public static JsonWebToken jwtFor(UUID userId) {
        return jwtFor(userId, Set.of());
    }

    /**
     * Returns a {@link JsonWebToken} for a caller in the given roles (e.g.
     * {@code "ADMIN"}). The {@code sub} / {@code uuid} claims are
     * populated from {@code userId}.
     */
    public static JsonWebToken jwtFor(UUID userId, Set<String> roles) {
        Map<String, Object> claims = new HashMap<>();
        claims.put(Claims.sub.name(), "auth0|" + userId);
        claims.put("uuid", userId.toString());
        return new FakeJsonWebToken("auth0|" + userId, claims, roles);
    }

    /** Convenience: a JWT carrying the {@code ADMIN} role. */
    public static JsonWebToken adminJwt(UUID userId) {
        return jwtFor(userId, Set.of("ADMIN"));
    }

    /**
     * Anonymous (returned when the test wants to clear the per-thread
     * context). Useful in {@link JwtTestContext#clear()} call chains.
     */
    public static JsonWebToken anonymous() {
        return new FakeJsonWebToken(null, Map.of(), Set.of());
    }

    /** Plain POJO {@link JsonWebToken} — read-only, request-scoped per test. */
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
