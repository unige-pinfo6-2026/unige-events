package ch.unige.events.service;

import ch.unige.events.dto.calendar.CalendarTokenResponse;
import ch.unige.events.entity.Attendance;
import ch.unige.events.entity.AttendanceStatus;
import ch.unige.events.entity.Event;
import ch.unige.events.entity.EventStatus;
import ch.unige.events.entity.User;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.NotFoundException;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
public class CalendarService {

    private static final DateTimeFormatter ICS_DT = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss");

    @ConfigProperty(name = "app.frontend.url", defaultValue = "https://10.25.10.136.nip.io")
    String frontendUrl;

    // ── Token management ──────────────────────────────────────────────────────

    @Transactional
    public CalendarTokenResponse getOrCreateToken(String auth0Id) {
        User user = resolveUser(auth0Id);
        if (user.calendarToken == null) {
            user.calendarToken = UUID.randomUUID();
        }
        return buildTokenResponse(user.calendarToken);
    }

    @Transactional
    public CalendarTokenResponse regenerateToken(String auth0Id) {
        User user = resolveUser(auth0Id);
        user.calendarToken = UUID.randomUUID();
        return buildTokenResponse(user.calendarToken);
    }

    // ── ICS feed ─────────────────────────────────────────────────────────────

    @Transactional
    public String generateIcsFeed(UUID calendarToken) {
        User user = User.<User>find("calendarToken", calendarToken)
                .firstResultOptional()
                .orElseThrow(() -> new NotFoundException("Calendar token not found"));

        List<Event> events = Attendance.findAllByUser(user.id).stream()
                .filter(a -> a.status == AttendanceStatus.INTERESTED
                          || a.status == AttendanceStatus.ATTENDING)
                .map(a -> Event.<Event>findByIdOptional(a.eventId))
                .flatMap(Optional::stream)
                .filter(e -> e.status == EventStatus.PUBLISHED)
                .toList();

        return buildIcsContent(events);
    }

    // ── ICS builder ───────────────────────────────────────────────────────────

    String buildIcsContent(List<Event> events) {
        StringBuilder sb = new StringBuilder();
        sb.append("BEGIN:VCALENDAR\r\n");
        sb.append("VERSION:2.0\r\n");
        sb.append("PRODID:-//UNIGE Events//UNIGE Events API//FR\r\n");
        sb.append("CALSCALE:GREGORIAN\r\n");
        sb.append("METHOD:PUBLISH\r\n");
        sb.append("X-WR-CALNAME:Mes \u00e9v\u00e9nements UNIGE\r\n");
        sb.append("X-WR-TIMEZONE:Europe/Zurich\r\n");

        for (Event event : events) {
            sb.append("BEGIN:VEVENT\r\n");
            sb.append("UID:").append(event.id).append("@unige-events\r\n");
            sb.append(foldLine("SUMMARY:" + escapeIcs(event.title)));
            sb.append("DTSTART;TZID=Europe/Zurich:").append(event.startDate.format(ICS_DT)).append("\r\n");
            sb.append("DTEND;TZID=Europe/Zurich:").append(event.endDate.format(ICS_DT)).append("\r\n");
            if (event.location != null) {
                sb.append(foldLine("LOCATION:" + escapeIcs(event.location)));
            }
            if (event.description != null) {
                sb.append(foldLine("DESCRIPTION:" + escapeIcs(event.description)));
            }
            sb.append(foldLine("URL:" + frontendUrl + "/events/" + event.id));
            sb.append("END:VEVENT\r\n");
        }

        sb.append("END:VCALENDAR\r\n");
        return sb.toString();
    }

    // RFC 5545 §3.1 — fold lines longer than 75 characters
    String foldLine(String line) {
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

    String escapeIcs(String value) {
        if (value == null) return "";
        return value
                .replace("\\", "\\\\")
                .replace(";", "\\;")
                .replace(",", "\\,")
                .replace("\n", "\\n")
                .replace("\r", "");
    }

    private CalendarTokenResponse buildTokenResponse(UUID token) {
        String httpsUrl = frontendUrl + "/api/calendar/" + token + ".ics";
        String webcalUrl = httpsUrl.replaceFirst("^https?://", "webcal://");
        return new CalendarTokenResponse(token, webcalUrl, httpsUrl);
    }

    private User resolveUser(String auth0Id) {
        return User.<User>find("auth0Id", auth0Id)
                .firstResultOptional()
                .orElseThrow(() -> new NotFoundException("User profile not found"));
    }
}
