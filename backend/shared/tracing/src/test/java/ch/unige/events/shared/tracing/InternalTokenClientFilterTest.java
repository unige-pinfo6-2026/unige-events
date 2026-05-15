package ch.unige.events.shared.tracing;

import jakarta.ws.rs.client.ClientRequestContext;
import jakarta.ws.rs.core.MultivaluedHashMap;
import jakarta.ws.rs.core.MultivaluedMap;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class InternalTokenClientFilterTest {

    private static InternalTokenClientFilter filterWithToken(String token) {
        InternalTokenClientFilter f = new InternalTokenClientFilter();
        f.token = token;
        return f;
    }

    private static ClientRequestContext mockCtx(MultivaluedMap<String, Object> headers) {
        ClientRequestContext ctx = mock(ClientRequestContext.class);
        when(ctx.getHeaders()).thenReturn(headers);
        return ctx;
    }

    @Test
    void tokenSet_setsHeader() {
        MultivaluedMap<String, Object> headers = new MultivaluedHashMap<>();
        ClientRequestContext ctx = mockCtx(headers);
        filterWithToken("abc-123").filter(ctx);
        assertEquals("abc-123", headers.getFirst(InternalTokenClientFilter.HEADER));
    }

    @Test
    void tokenEmpty_doesNotSetHeader() {
        MultivaluedMap<String, Object> headers = new MultivaluedHashMap<>();
        ClientRequestContext ctx = mockCtx(headers);
        filterWithToken("").filter(ctx);
        assertFalse(headers.containsKey(InternalTokenClientFilter.HEADER));
    }

    @Test
    void tokenNull_doesNotSetHeader() {
        MultivaluedMap<String, Object> headers = new MultivaluedHashMap<>();
        ClientRequestContext ctx = mockCtx(headers);
        filterWithToken(null).filter(ctx);
        assertFalse(headers.containsKey(InternalTokenClientFilter.HEADER));
    }
}
