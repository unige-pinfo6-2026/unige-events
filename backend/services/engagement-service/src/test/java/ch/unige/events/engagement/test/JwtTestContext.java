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
 *
 * <p>For concurrent multi-thread tests (BUG-005-bis advisory lock sentinel),
 * {@link #setForThread(JsonWebToken)} stages a per-thread JWT that overrides
 * the global static for the current thread only. The producer reads the
 * thread-local first and falls back to the static when none is set —
 * preserving the legacy single-thread semantics.
 */
public final class JwtTestContext {

    private static volatile JsonWebToken current;
    private static final ThreadLocal<JsonWebToken> PER_THREAD = new ThreadLocal<>();

    private JwtTestContext() {
    }

    public static void set(JsonWebToken jwt) {
        current = jwt;
    }

    public static JsonWebToken get() {
        JsonWebToken local = PER_THREAD.get();
        return local != null ? local : current;
    }

    public static void clear() {
        current = null;
        PER_THREAD.remove();
    }

    /** Stages a JWT visible only to the current thread. Pair with {@link #clearForThread()}. */
    public static void setForThread(JsonWebToken jwt) {
        PER_THREAD.set(jwt);
    }

    /** Removes any JWT staged for the current thread (does not touch the global). */
    public static void clearForThread() {
        PER_THREAD.remove();
    }
}
