package ch.unige.events.report.test;

import org.eclipse.microprofile.jwt.JsonWebToken;

/**
 * Static context that hands a {@link JsonWebToken} to {@link TestJwtProducer}.
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
