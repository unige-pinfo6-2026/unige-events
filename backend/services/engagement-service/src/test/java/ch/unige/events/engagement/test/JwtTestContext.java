package ch.unige.events.engagement.test;

import org.eclipse.microprofile.jwt.JsonWebToken;

/**
 * Static context that hands a {@link JsonWebToken} to {@link TestJwtProducer}.
 * Tests typically call {@link #set(JsonWebToken)} in a {@code @BeforeEach}
 * (or inline) and {@link #clear()} in {@code @AfterEach}.
 *
 * <p>Uses a {@code volatile} static field rather than a {@link ThreadLocal}
 * so the staged JWT survives the JUnit thread → vertx worker thread
 * handoff that REST-Assured triggers when invoking HTTP endpoints. Surefire
 * runs each test method sequentially on the same forked JVM so the static
 * field is safe across test methods provided each test sets/clears it.
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
