package ch.unige.events.shared.tracing;

import jakarta.ws.rs.client.ClientRequestContext;
import jakarta.ws.rs.core.MultivaluedHashMap;
import jakarta.ws.rs.core.MultivaluedMap;
import org.jboss.logmanager.MDC;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RequestIdClientFilterTest {

    private final RequestIdClientFilter filter = new RequestIdClientFilter();

    @AfterEach
    void clearMdc() {
        MDC.remove(RequestIdFilter.MDC_KEY);
    }

    @Test
    void mdcSet_propagatesToOutgoingHeader() {
        MDC.put(RequestIdFilter.MDC_KEY, "req-42");
        ClientRequestContext ctx = mock(ClientRequestContext.class);
        MultivaluedMap<String, Object> headers = new MultivaluedHashMap<>();
        when(ctx.getHeaders()).thenReturn(headers);

        filter.filter(ctx);

        assertEquals("req-42", headers.getFirst(RequestIdFilter.HEADER));
    }

    @Test
    void mdcUnset_doesNothing() {
        ClientRequestContext ctx = mock(ClientRequestContext.class);

        filter.filter(ctx);

        // no headers added → never asked for them
        verify(ctx, never()).getHeaders();
    }

    @Test
    void constants_areStable() {
        // sentinel against accidental rename
        assertTrue("X-Request-ID".equals(RequestIdFilter.HEADER));
        assertTrue("requestId".equals(RequestIdFilter.MDC_KEY));
    }
}
