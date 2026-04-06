package ch.unige.events.dto.calendar;

import java.util.UUID;

public record CalendarTokenResponse(
        UUID calendarToken,
        String webcalUrl,
        String httpsUrl
) {}
