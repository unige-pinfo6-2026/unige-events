package ch.unige.events.user.test;

import org.eclipse.microprofile.jwt.JsonWebToken;

/**
 * Static context that hands a {@link JsonWebToken} to {@link TestJwtProducer}.
 * Tests typically call {@link #set(JsonWebToken)} in a {@code @BeforeEach}
 * (or inline) and {@link #clear()} in {@code @AfterEach}.
 */
public final class JwtTestContext {

    private static volatile JsonWebToken current;

    private JwtTestContext() {
    }

    public static void set(JsonWebToken jwt) {
        current = jwt;
    }

    public static JsonWebToken get() {
        return current;
    }

    public static void clear() {
        current = null;
    }
}
