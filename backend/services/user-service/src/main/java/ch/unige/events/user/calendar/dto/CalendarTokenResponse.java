package ch.unige.events.user.calendar.dto;

import java.util.UUID;

public record CalendarTokenResponse(
        UUID calendarToken,
        String webcalUrl,
        String httpsUrl
) {}
