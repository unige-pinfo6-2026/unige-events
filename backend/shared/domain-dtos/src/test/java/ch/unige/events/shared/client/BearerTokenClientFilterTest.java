package ch.unige.events.shared.client;

import jakarta.enterprise.inject.Instance;
import jakarta.ws.rs.client.ClientRequestContext;
import jakarta.ws.rs.core.MultivaluedHashMap;
import jakarta.ws.rs.core.MultivaluedMap;
import org.eclipse.microprofile.jwt.JsonWebToken;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class BearerTokenClientFilterTest {

    /**
     * Builds a filter whose {@code Instance<JsonWebToken>} resolves (or not)
     * to a token returning {@code rawToken}. {@code resolvable=false} models
     * an anonymous / @PermitAll call ; {@code resolvable=true} + {@code null}
     * token models a resolvable-but-empty container.
     */
    private static BearerTokenClientFilter filter(boolean resolvable, JsonWebToken token) {
        @SuppressWarnings("unchecked")
        Instance<JsonWebToken> inst = mock(Instance.class);
        when(inst.isResolvable()).thenReturn(resolvable);
        lenient().when(inst.get()).thenReturn(token);
        return new BearerTokenClientFilter(inst);
    }

    private static JsonWebToken tokenWithRaw(String raw) {
        JsonWebToken jwt = mock(JsonWebToken.class);
        when(jwt.getRawToken()).thenReturn(raw);
        return jwt;
    }

    private static MultivaluedMap<String, Object> filterAndCaptureHeaders(BearerTokenClientFilter f) {
        ClientRequestContext ctx = mock(ClientRequestContext.class);
        MultivaluedMap<String, Object> headers = new MultivaluedHashMap<>();
        lenient().when(ctx.getHeaders()).thenReturn(headers);
        f.filter(ctx);
        return headers;
    }

    @Test
    void unresolvableJwt_doesNotSetHeader() {
        MultivaluedMap<String, Object> headers = filterAndCaptureHeaders(filter(false, null));
        assertFalse(headers.containsKey("Authorization"));
    }

    @Test
    void resolvableButNullToken_doesNotSetHeader() {
        MultivaluedMap<String, Object> headers = filterAndCaptureHeaders(filter(true, null));
        assertFalse(headers.containsKey("Authorization"));
    }

    @Test
    void nullRawToken_doesNotSetHeader() {
        MultivaluedMap<String, Object> headers = filterAndCaptureHeaders(filter(true, tokenWithRaw(null)));
        assertFalse(headers.containsKey("Authorization"));
    }

    @Test
    void blankRawToken_doesNotSetHeader() {
        MultivaluedMap<String, Object> headers = filterAndCaptureHeaders(filter(true, tokenWithRaw("   ")));
        assertFalse(headers.containsKey("Authorization"));
    }

    @Test
    void presentRawToken_setsBearerHeader() {
        MultivaluedMap<String, Object> headers = filterAndCaptureHeaders(filter(true, tokenWithRaw("abc.def.ghi")));
        assertEquals("Bearer abc.def.ghi", headers.getFirst("Authorization"));
    }
}
