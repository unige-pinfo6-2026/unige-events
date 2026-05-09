package ch.unige.events.shared.tracing;

import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerResponseContext;
import jakarta.ws.rs.core.MultivaluedHashMap;
import jakarta.ws.rs.core.MultivaluedMap;
import org.jboss.logmanager.MDC;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RequestIdFilterTest {

    private final RequestIdFilter filter = new RequestIdFilter();

    @AfterEach
    void clearMdc() {
        MDC.remove(RequestIdFilter.MDC_KEY);
    }

    @Test
    void filter_existingHeader_postsToMdcAndProperty() {
        ContainerRequestContext ctx = mock(ContainerRequestContext.class);
        when(ctx.getHeaderString(RequestIdFilter.HEADER)).thenReturn("abc-123");

        filter.filter(ctx);

        verify(ctx).setProperty(RequestIdFilter.MDC_KEY, "abc-123");
        assertEquals("abc-123", MDC.get(RequestIdFilter.MDC_KEY));
    }

    @Test
    void filter_missingHeader_generatesUuid() {
        ContainerRequestContext ctx = mock(ContainerRequestContext.class);
        when(ctx.getHeaderString(RequestIdFilter.HEADER)).thenReturn(null);

        filter.filter(ctx);

        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(ctx).setProperty(eq(RequestIdFilter.MDC_KEY), captor.capture());
        String generated = captor.getValue();
        assertNotNull(generated);
        // valid UUID
        assertEquals(36, generated.length());
        UUID.fromString(generated); // throws if invalid
        assertEquals(generated, MDC.get(RequestIdFilter.MDC_KEY));
    }

    @Test
    void filter_blankHeader_generatesUuid() {
        ContainerRequestContext ctx = mock(ContainerRequestContext.class);
        when(ctx.getHeaderString(RequestIdFilter.HEADER)).thenReturn("   ");

        filter.filter(ctx);

        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(ctx).setProperty(eq(RequestIdFilter.MDC_KEY), captor.capture());
        UUID.fromString(captor.getValue()); // proves UUID
    }

    @Test
    void responseFilter_echoesIdToHeader() {
        ContainerRequestContext req = mock(ContainerRequestContext.class);
        ContainerResponseContext res = mock(ContainerResponseContext.class);
        MultivaluedMap<String, Object> headers = new MultivaluedHashMap<>();
        when(req.getProperty(RequestIdFilter.MDC_KEY)).thenReturn("xyz");
        when(res.getHeaders()).thenReturn(headers);

        filter.filter(req, res);

        assertTrue(headers.getFirst(RequestIdFilter.HEADER).equals("xyz"));
        // MDC is cleared on the way out
        assertNull(MDC.get(RequestIdFilter.MDC_KEY));
    }

    @Test
    void responseFilter_missingProperty_doesNothing() {
        ContainerRequestContext req = mock(ContainerRequestContext.class);
        ContainerResponseContext res = mock(ContainerResponseContext.class);
        when(req.getProperty(RequestIdFilter.MDC_KEY)).thenReturn(null);

        filter.filter(req, res);

        // No headers added — verify response getHeaders was never called
        verify(res, org.mockito.Mockito.never()).getHeaders();
    }
}
