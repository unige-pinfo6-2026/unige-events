package ch.unige.events.service;

import ch.unige.events.dto.calendar.CalendarTokenResponse;
import io.quarkus.test.Mock;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.UUID;

@Mock
@ApplicationScoped
public class CalendarServiceMock extends CalendarService {

    private static final UUID FIXED_TOKEN = UUID.fromString("11111111-2222-3333-4444-555555555555");

    @Override
    public CalendarTokenResponse getOrCreateToken(String auth0Id) {
        return fakeResponse(FIXED_TOKEN);
    }

    @Override
    public CalendarTokenResponse regenerateToken(String auth0Id) {
        return fakeResponse(UUID.randomUUID());
    }

    @Override
    public String generateIcsFeed(UUID calendarToken) {
        return "BEGIN:VCALENDAR\r\nVERSION:2.0\r\nEND:VCALENDAR\r\n";
    }

    private CalendarTokenResponse fakeResponse(UUID token) {
        String base = "https://10.25.10.136.nip.io/api/calendar/" + token + ".ics";
        return new CalendarTokenResponse(token, base.replace("https://", "webcal://"), base);
    }
}
