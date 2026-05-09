package ch.unige.events.calendar.dto;

import java.util.UUID;

public record CalendarTokenResponse(
        UUID calendarToken,
        String webcalUrl,
        String httpsUrl
) {}
