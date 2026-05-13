package ch.unige.events.user.calendar.util;

import ch.unige.events.shared.domain.dto.EventDTO;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * RFC 5545 ICS feed builder. Reads from the cross-service
 * {@link EventDTO} (shared record) projected by event-service via the
 * {@code GET /events?ids=...} bulk endpoint.
 *
 * <p>STUB-001 / Décision F: the legacy {@code EventStub} navigation has
 * been replaced by the shared EventDTO record. Field accessors are now
 * method calls (e.g. {@code event.title()} instead of {@code event.title}).
 */
public final class IcsBuilder {

    private static final ZoneId ZURICH = ZoneId.of("Europe/Zurich");
    private static final ZoneId UTC = ZoneId.of("UTC");
    private static final DateTimeFormatter ICS_DT = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss");

    private IcsBuilder() {}

    public static String buildIcsContent(List<EventDTO> events, String frontendUrl) {
        StringBuilder sb = new StringBuilder();
        sb.append("BEGIN:VCALENDAR\r\n");
        sb.append("VERSION:2.0\r\n");
        sb.append("PRODID:-//UNIGE Events//UNIGE Events API//FR\r\n");
        sb.append("CALSCALE:GREGORIAN\r\n");
        sb.append("METHOD:PUBLISH\r\n");
        sb.append("X-WR-CALNAME:Mes événements UNIGE\r\n");
        sb.append("X-WR-TIMEZONE:Europe/Zurich\r\n");

        for (EventDTO event : events) {
            if (event.startDate() == null || event.endDate() == null) {
                continue;
            }
            ZonedDateTime startZurich = event.startDate().atZone(UTC).withZoneSameInstant(ZURICH);
            ZonedDateTime endZurich   = event.endDate().atZone(UTC).withZoneSameInstant(ZURICH);

            sb.append("BEGIN:VEVENT\r\n");
            sb.append("UID:").append(event.id()).append("@unige-events\r\n");
            sb.append(foldLine("SUMMARY:" + escapeIcs(event.title())));
            sb.append("DTSTART;TZID=Europe/Zurich:").append(startZurich.format(ICS_DT)).append("\r\n");
            sb.append("DTEND;TZID=Europe/Zurich:").append(endZurich.format(ICS_DT)).append("\r\n");
            if (event.location() != null) {
                sb.append(foldLine("LOCATION:" + escapeIcs(event.location())));
            }
            if (event.description() != null) {
                sb.append(foldLine("DESCRIPTION:" + escapeIcs(event.description())));
            }
            sb.append(foldLine("URL:" + frontendUrl + "/events/" + event.id()));
            sb.append("END:VEVENT\r\n");
        }

        sb.append("END:VCALENDAR\r\n");
        return sb.toString();
    }

    /** RFC 5545 §3.1 — fold lines longer than 75 octets. */
    public static String foldLine(String line) {
        if (line.length() <= 75) {
            return line + "\r\n";
        }
        StringBuilder result = new StringBuilder();
        result.append(line, 0, 75).append("\r\n");
        int i = 75;
        while (i < line.length()) {
            int end = Math.min(i + 74, line.length());
            result.append(' ').append(line, i, end).append("\r\n");
            i = end;
        }
        return result.toString();
    }

    /** RFC 5545 §3.3.11 — escape special characters in TEXT values. */
    public static String escapeIcs(String value) {
        if (value == null) return "";
        return value
                .replace("\\", "\\\\")
                .replace(";", "\\;")
                .replace(",", "\\,")
                .replace("\n", "\\n")
                .replace("\r", "");
    }
}
