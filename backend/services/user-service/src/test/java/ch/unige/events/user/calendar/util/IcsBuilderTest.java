package ch.unige.events.user.calendar.util;

import ch.unige.events.shared.domain.dto.EventDTO;
import ch.unige.events.shared.domain.enums.EventStatus;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
class IcsBuilderTest {

    private static EventDTO buildEvent(Long id, String title, String location, String description,
                                       int year, int month, int day, int hour, int minute) {
        return new EventDTO(id, title, description, location,
                LocalDateTime.of(year, month, day, hour, minute),
                LocalDateTime.of(year, month, day, hour, minute).plusHours(2),
                null, null, null, null,
                EventStatus.PUBLISHED, null, false, false, null,
                0L, null, 0L, 0L, 0L,
                null, null, null,
                List.of(), LocalDateTime.now(), LocalDateTime.now(),
                null, null, null);
    }

    @Test
    void buildIcsContent_emptyList_returnsValidCalendar() {
        String ics = IcsBuilder.buildIcsContent(List.of(), "http://localhost:5173");
        assertTrue(ics.contains("BEGIN:VCALENDAR"));
        assertTrue(ics.contains("END:VCALENDAR"));
        assertFalse(ics.contains("BEGIN:VEVENT"));
    }

    @Test
    void buildIcsContent_singleEvent_containsVevent() {
        EventDTO e = buildEvent(1L, "Test Event", null, null, 2025, 6, 15, 7, 0);
        String ics = IcsBuilder.buildIcsContent(List.of(e), "http://localhost:5173");
        assertTrue(ics.contains("BEGIN:VEVENT"));
        assertTrue(ics.contains("SUMMARY:Test Event"));
        assertTrue(ics.contains("DTSTART;TZID=Europe/Zurich:"));
        assertTrue(ics.contains("UID:1@unige-events"));
    }

    @Test
    void buildIcsContent_eventMissingStartOrEnd_skipped() {
        EventDTO partial = new EventDTO(2L, "Bad", "d", "l",
                null, LocalDateTime.now(),
                null, null, null, null,
                EventStatus.PUBLISHED, null, false, false, null,
                0L, null, 0L, 0L, 0L,
                null, null, null,
                List.of(), LocalDateTime.now(), LocalDateTime.now(),
                null, null, null);
        String ics = IcsBuilder.buildIcsContent(List.of(partial), "http://localhost:5173");
        assertFalse(ics.contains("BEGIN:VEVENT"));
    }

    @Test
    void buildIcsContent_timezone_utcToZurichSummer() {
        // UTC+2 (DST) — 7h UTC → 9h Zurich
        EventDTO e = buildEvent(1L, "Summer", null, null, 2025, 6, 15, 7, 0);
        String ics = IcsBuilder.buildIcsContent(List.of(e), "http://localhost:5173");
        assertTrue(ics.contains("DTSTART;TZID=Europe/Zurich:20250615T090000"));
    }

    @Test
    void buildIcsContent_timezone_utcToZurichWinter() {
        // UTC+1 — 7h UTC → 8h Zurich
        EventDTO e = buildEvent(1L, "Winter", null, null, 2025, 1, 15, 7, 0);
        String ics = IcsBuilder.buildIcsContent(List.of(e), "http://localhost:5173");
        assertTrue(ics.contains("DTSTART;TZID=Europe/Zurich:20250115T080000"));
    }

    @Test
    void buildIcsContent_locationAndDescription_areIncluded() {
        EventDTO e = buildEvent(7L, "Conf", "Uni Mail", "A great talk",
                2025, 6, 15, 10, 0);
        String ics = IcsBuilder.buildIcsContent(List.of(e), "http://localhost:5173");
        assertTrue(ics.contains("LOCATION:Uni Mail"));
        assertTrue(ics.contains("DESCRIPTION:A great talk"));
    }

    @Test
    void buildIcsContent_urlContainsFrontendUrl() {
        EventDTO e = buildEvent(42L, "Link", null, null, 2025, 6, 15, 10, 0);
        String ics = IcsBuilder.buildIcsContent(List.of(e), "https://myapp.example.com");
        assertTrue(ics.contains("URL:https://myapp.example.com/events/42"));
    }

    @Test
    void foldLine_shortLine_noFolding() {
        String result = IcsBuilder.foldLine("SUMMARY:Short");
        assertEquals("SUMMARY:Short\r\n", result);
    }

    @Test
    void foldLine_longLine_foldedAt75() {
        String longLine = "X-CUSTOM:" + "A".repeat(100);
        String result = IcsBuilder.foldLine(longLine);
        assertTrue(result.contains("\r\n "), "Folded line must contain CRLF + space continuation");
        String firstLine = result.substring(0, result.indexOf("\r\n"));
        assertEquals(75, firstLine.length());
    }

    @Test
    void escapeIcs_specialChars_areEscaped() {
        assertEquals("a\\,b\\;c\\nd\\\\", IcsBuilder.escapeIcs("a,b;c\nd\\"));
    }

    @Test
    void escapeIcs_null_returnsEmpty() {
        assertEquals("", IcsBuilder.escapeIcs(null));
    }

    @Test
    void escapeIcs_carriageReturnIsStripped() {
        assertEquals("ab", IcsBuilder.escapeIcs("a\rb"));
    }
}
