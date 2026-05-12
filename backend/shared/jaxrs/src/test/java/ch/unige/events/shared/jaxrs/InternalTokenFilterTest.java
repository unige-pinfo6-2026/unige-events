package ch.unige.events.shared.jaxrs;

import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.container.ContainerRequestContext;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class InternalTokenFilterTest {

    private InternalTokenFilter filterWithExpected(String expected) {
        InternalTokenFilter f = new InternalTokenFilter();
        f.expectedOverride = expected;
        return f;
    }

    private static ContainerRequestContext requestWithHeader(String header) {
        ContainerRequestContext ctx = mock(ContainerRequestContext.class);
        when(ctx.getHeaderString(InternalTokenFilter.HEADER)).thenReturn(header);
        return ctx;
    }

    @Test
    void matchingToken_passesThrough() {
        InternalTokenFilter f = filterWithExpected("secret-xyz");
        ContainerRequestContext ctx = requestWithHeader("secret-xyz");
        assertDoesNotThrow(() -> f.filter(ctx));
    }

    @Test
    void missingHeader_throws404() {
        InternalTokenFilter f = filterWithExpected("secret-xyz");
        ContainerRequestContext ctx = requestWithHeader(null);
        assertThrows(NotFoundException.class, () -> f.filter(ctx));
    }

    @Test
    void mismatchedHeader_throws404() {
        InternalTokenFilter f = filterWithExpected("secret-xyz");
        ContainerRequestContext ctx = requestWithHeader("nope");
        assertThrows(NotFoundException.class, () -> f.filter(ctx));
    }

    @Test
    void emptyExpected_throws404_eveni_request_carries_anything() {
        // Misconfigured service (no unige.internal-token set) must fail closed
        // — the filter cannot validate, so it rejects every request.
        InternalTokenFilter f = filterWithExpected("");
        ContainerRequestContext ctx = requestWithHeader("anything");
        assertThrows(NotFoundException.class, () -> f.filter(ctx));
    }
}
