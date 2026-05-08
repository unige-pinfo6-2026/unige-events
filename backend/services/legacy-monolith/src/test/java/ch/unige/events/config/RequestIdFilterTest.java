package ch.unige.events.config;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerResponseContext;
import jakarta.ws.rs.core.MultivaluedHashMap;
import jakarta.ws.rs.core.MultivaluedMap;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.matchesPattern;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Covers {@link RequestIdFilter}: id resolution, header echo, MDC discipline.
 *
 * <p>Mixes direct unit tests of the filter (fast, deterministic) with a
 * QuarkusTest pass to confirm the filter is wired into the JAX-RS pipeline.
 */
@QuarkusTest
class RequestIdFilterTest {

    private static final Pattern UUID_PATTERN =
            Pattern.compile("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}");

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    // -------------------------------------------------------------------------
    // Unit tests — direct invocation of the filter with mocked contexts.
    // -------------------------------------------------------------------------

    @Test
    void requestFilter_generatesUuidWhenHeaderAbsent() {
        RequestIdFilter filter = new RequestIdFilter();
        Map<String, Object> properties = new HashMap<>();
        ContainerRequestContext req = mockRequest(null, properties);

        filter.filter(req);

        Object id = properties.get(RequestIdFilter.PROPERTY);
        assertNotNull(id);
        assertTrue(UUID_PATTERN.matcher((String) id).matches(),
                "missing client header → generated id must be a UUID");
        assertEquals(id, MDC.get(RequestIdFilter.MDC_KEY));
    }

    @Test
    void requestFilter_preservesValidClientSuppliedId() {
        RequestIdFilter filter = new RequestIdFilter();
        Map<String, Object> properties = new HashMap<>();
        String suppliedId = "abcdef12-3456-7890-abcdef1234567890";
        ContainerRequestContext req = mockRequest(suppliedId, properties);

        filter.filter(req);

        assertEquals(suppliedId, properties.get(RequestIdFilter.PROPERTY));
        assertEquals(suppliedId, MDC.get(RequestIdFilter.MDC_KEY));
    }

    @Test
    void requestFilter_silentlyReplacesInvalidClientSuppliedId() {
        // A 400 on a diagnostic header would be a footgun — replace, don't reject.
        RequestIdFilter filter = new RequestIdFilter();
        Map<String, Object> properties = new HashMap<>();
        ContainerRequestContext req = mockRequest("not a valid id\n<script>", properties);

        filter.filter(req);

        String resolved = (String) properties.get(RequestIdFilter.PROPERTY);
        assertNotNull(resolved);
        assertNotEquals("not a valid id\n<script>", resolved);
        assertTrue(UUID_PATTERN.matcher(resolved).matches());
    }

    @Test
    void requestFilter_rejectsTooShortAndTooLongIds() {
        RequestIdFilter filter = new RequestIdFilter();

        // 7 chars — below the 8-char minimum.
        Map<String, Object> shortProps = new HashMap<>();
        filter.filter(mockRequest("abc1234", shortProps));
        assertNotEquals("abc1234", shortProps.get(RequestIdFilter.PROPERTY));

        // 65 chars — above the 64-char maximum.
        Map<String, Object> longProps = new HashMap<>();
        String tooLong = "a".repeat(65);
        filter.filter(mockRequest(tooLong, longProps));
        assertNotEquals(tooLong, longProps.get(RequestIdFilter.PROPERTY));
    }

    @Test
    void responseFilter_echoesIdAndClearsMdc() {
        RequestIdFilter filter = new RequestIdFilter();
        String id = UUID.randomUUID().toString();
        ContainerRequestContext req = mock(ContainerRequestContext.class);
        when(req.getProperty(RequestIdFilter.PROPERTY)).thenReturn(id);

        ContainerResponseContext res = mock(ContainerResponseContext.class);
        MultivaluedMap<String, Object> headers = new MultivaluedHashMap<>();
        when(res.getHeaders()).thenReturn(headers);

        MDC.put(RequestIdFilter.MDC_KEY, id);
        filter.filter(req, res);

        assertEquals(id, headers.getFirst(RequestIdFilter.HEADER));
        assertNull(MDC.get(RequestIdFilter.MDC_KEY),
                "MDC must be cleared even on the success path so the slot does not leak across pooled request threads");
    }

    @Test
    void responseFilter_clearsMdcEvenWhenPropertyMissing() {
        // Defensive path: if some upstream blew up before the request filter
        // ran, we still want the MDC scrubbed.
        RequestIdFilter filter = new RequestIdFilter();
        ContainerRequestContext req = mock(ContainerRequestContext.class);
        when(req.getProperty(RequestIdFilter.PROPERTY)).thenReturn(null);

        ContainerResponseContext res = mock(ContainerResponseContext.class);
        MultivaluedMap<String, Object> headers = new MultivaluedHashMap<>();
        when(res.getHeaders()).thenReturn(headers);

        MDC.put(RequestIdFilter.MDC_KEY, "leftover");
        filter.filter(req, res);

        // No header added when the property is missing — caller can detect
        // the broken pipeline rather than seeing a fake id.
        assertTrue(headers.isEmpty());
        assertNull(MDC.get(RequestIdFilter.MDC_KEY));
    }

    // -------------------------------------------------------------------------
    // HTTP integration — confirms the filter is registered.
    // -------------------------------------------------------------------------

    @Test
    void apiResponses_carryGeneratedRequestIdHeader() {
        // Public, no-state endpoint — keeps the test independent of services.
        given()
                .when().get("/events")
                .then()
                .statusCode(200)
                .header(RequestIdFilter.HEADER,
                        matchesPattern("[0-9a-fA-F-]{8,64}"));
    }

    @Test
    void apiResponses_preserveValidClientSuppliedRequestId() {
        String suppliedId = "11111111-2222-3333-4444-555555555555";
        given()
                .header(RequestIdFilter.HEADER, suppliedId)
                .when().get("/events")
                .then()
                .statusCode(200)
                .header(RequestIdFilter.HEADER, suppliedId);
    }

    @Test
    void apiResponses_replaceInvalidClientSuppliedRequestId() {
        String bogus = "<script>alert(1)</script>";
        String returnedId = given()
                .header(RequestIdFilter.HEADER, bogus)
                .when().get("/events")
                .then()
                .statusCode(200)
                .header(RequestIdFilter.HEADER,
                        matchesPattern("[0-9a-fA-F-]{8,64}"))
                .extract().header(RequestIdFilter.HEADER);
        assertNotEquals(bogus, returnedId,
                "invalid client-supplied id must be replaced, not echoed back");
    }

    private static ContainerRequestContext mockRequest(String headerValue,
                                                       Map<String, Object> properties) {
        ContainerRequestContext req = mock(ContainerRequestContext.class);
        when(req.getHeaderString(RequestIdFilter.HEADER)).thenReturn(headerValue);
        org.mockito.stubbing.Answer<Void> setProperty = inv -> {
            properties.put(inv.getArgument(0), inv.getArgument(1));
            return null;
        };
        org.mockito.Mockito.doAnswer(setProperty)
                .when(req).setProperty(any(), any());
        when(req.getProperty(any())).thenAnswer(inv -> properties.get(inv.<String>getArgument(0)));
        return req;
    }
}
