package ch.unige.events.shared.error;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ApiErrorResponseTest {

    @Test
    void recordExposesErrorAndMessage() {
        ApiErrorResponse r = new ApiErrorResponse("conflict", "Already exists");
        assertEquals("conflict", r.error());
        assertEquals("Already exists", r.message());
    }

    @Test
    void recordEqualityIsValueBased() {
        ApiErrorResponse a = new ApiErrorResponse("e", "m");
        ApiErrorResponse b = new ApiErrorResponse("e", "m");
        ApiErrorResponse c = new ApiErrorResponse("e", "other");
        assertEquals(a, b);
        assertNotEquals(a, c);
        assertEquals(a.hashCode(), b.hashCode());
    }

    @Test
    void recordToStringIncludesFields() {
        String s = new ApiErrorResponse("not_found", "missing").toString();
        // Record toString format: ApiErrorResponse[error=not_found, message=missing]
        // We don't assert exact formatting (JDK-dependent) but require both fields to appear.
        assertTrue(s.contains("not_found"));
        assertTrue(s.contains("missing"));
    }

    @Test
    void constructor_nullError_throws() {
        assertThrows(NullPointerException.class,
                () -> new ApiErrorResponse(null, "msg"));
    }

    @Test
    void constructor_blankError_throws() {
        assertThrows(IllegalArgumentException.class,
                () -> new ApiErrorResponse("   ", "msg"));
    }
}
