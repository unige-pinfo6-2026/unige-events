package ch.unige.events.user.calendar.dto;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class CalendarTokenResponseTest {

    @Test
    void canonicalConstructor_populatedFields_keepsAllValues() {
        UUID token = UUID.randomUUID();
        CalendarTokenResponse dto = new CalendarTokenResponse(
            token,
            "webcal://example.org/calendar/" + token + ".ics",
            "https://example.org/calendar/" + token + ".ics");

        assertEquals(token, dto.calendarToken());
        assertEquals("webcal://example.org/calendar/" + token + ".ics", dto.webcalUrl());
        assertEquals("https://example.org/calendar/" + token + ".ics", dto.httpsUrl());
    }

    @Test
    void canonicalConstructor_nullToken_isAllowed() {
        CalendarTokenResponse dto = new CalendarTokenResponse(null, null, null);

        assertNull(dto.calendarToken());
        assertNull(dto.webcalUrl());
        assertNull(dto.httpsUrl());
    }

    @Test
    void recordEqualsAndHashCode_canonicalContract() {
        UUID token = UUID.randomUUID();
        CalendarTokenResponse a = new CalendarTokenResponse(token, "webcal://x", "https://x");
        CalendarTokenResponse b = new CalendarTokenResponse(token, "webcal://x", "https://x");
        CalendarTokenResponse c = new CalendarTokenResponse(token, "webcal://y", "https://x");

        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
        assertNotEquals(a, c);
    }
}
