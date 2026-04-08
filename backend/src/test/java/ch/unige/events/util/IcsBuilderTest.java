package ch.unige.events.util;

import ch.unige.events.entity.Event;
import ch.unige.events.entity.EventStatus;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class IcsBuilderTest {

    private static Event buildEvent(String title, int yearStart, int monthStart, int dayStart,
                                    int hour, int min) {
        Event e = new Event();
        e.id = 1L;
        e.title = title;
        e.status = EventStatus.PUBLISHED;
        e.startDate = LocalDateTime.of(yearStart, monthStart, dayStart, hour, min);
        e.endDate = e.startDate.plusHours(2);
        return e;
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
        Event e = buildEvent("Test Event", 2025, 6, 15, 7, 0); // 7h UTC, été → 9h Zurich
        String ics = IcsBuilder.buildIcsContent(List.of(e), "http://localhost:5173");
        assertTrue(ics.contains("BEGIN:VEVENT"));
        assertTrue(ics.contains("SUMMARY:Test Event"));
        assertTrue(ics.contains("DTSTART;TZID=Europe/Zurich:"));
    }

    @Test
    void buildIcsContent_timezone_utcToZurichSummer() {
        // Été (UTC+2) : 7h UTC → 9h Zurich
        Event e = buildEvent("Summer Event", 2025, 6, 15, 7, 0);
        String ics = IcsBuilder.buildIcsContent(List.of(e), "http://localhost:5173");
        assertTrue(ics.contains("DTSTART;TZID=Europe/Zurich:20250615T090000"),
                "Expected 09:00 Zurich (summer UTC+2), got: " + ics);
    }

    @Test
    void buildIcsContent_timezone_utcToZurichWinter() {
        // Hiver (UTC+1) : 7h UTC → 8h Zurich
        Event e = buildEvent("Winter Event", 2025, 1, 15, 7, 0);
        String ics = IcsBuilder.buildIcsContent(List.of(e), "http://localhost:5173");
        assertTrue(ics.contains("DTSTART;TZID=Europe/Zurich:20250115T080000"),
                "Expected 08:00 Zurich (winter UTC+1), got: " + ics);
    }

    @Test
    void buildIcsContent_urlContainsFrontendUrl() {
        Event e = buildEvent("Link Event", 2025, 6, 15, 10, 0);
        String ics = IcsBuilder.buildIcsContent(List.of(e), "https://myapp.example.com");
        assertTrue(ics.contains("URL:https://myapp.example.com/events/1"));
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
        // Première ligne : exactement 75 caractères
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
}
