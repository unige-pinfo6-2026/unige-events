package ch.unige.events.user.calendar.service;

import ch.unige.events.user.calendar.config.AppConfig;
import ch.unige.events.user.calendar.dto.CalendarTokenResponse;
import ch.unige.events.user.calendar.entity.AttendanceStub;
import ch.unige.events.user.calendar.entity.EventStatus;
import ch.unige.events.user.calendar.entity.EventStub;
import ch.unige.events.user.calendar.entity.FavoriteStub;
import ch.unige.events.user.entity.User;
import ch.unige.events.user.calendar.util.IcsBuilder;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.NotFoundException;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Same semantics as the legacy monolith's CalendarService — token rotation
 * (lazy create on first read, regenerate on POST) and ICS feed assembly.
 */
@ApplicationScoped
public class CalendarService {

    @Inject
    AppConfig appConfig;

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

    @Transactional
    public String generateIcsFeed(UUID calendarToken) {
        User user = User.findByCalendarToken(calendarToken)
                .orElseThrow(() -> new NotFoundException("Calendar token not found"));

        List<Long> favoriteIds = FavoriteStub.findAllByUser(user.id).stream()
                .map(f -> f.eventId)
                .collect(Collectors.toList());

        List<Long> attendingIds = AttendanceStub.findAllByUser(user.id).stream()
                .map(a -> a.eventId)
                .collect(Collectors.toList());

        Set<Long> allIds = new HashSet<>();
        allIds.addAll(favoriteIds);
        allIds.addAll(attendingIds);

        if (allIds.isEmpty()) {
            return IcsBuilder.buildIcsContent(List.of(), appConfig.frontendUrl());
        }

        List<EventStub> events = EventStub.<EventStub>find(
                "id IN ?1 AND status = ?2", allIds, EventStatus.PUBLISHED
        ).list();

        return IcsBuilder.buildIcsContent(events, appConfig.frontendUrl());
    }

    private CalendarTokenResponse buildTokenResponse(UUID token) {
        String httpsUrl = appConfig.frontendUrl() + "/api/calendar/" + token + ".ics";
        String webcalUrl = httpsUrl.replaceFirst("^https?://", "webcal://");
        return new CalendarTokenResponse(token, webcalUrl, httpsUrl);
    }

    private User resolveUser(String auth0Id) {
        return User.findByAuth0Id(auth0Id)
                .orElseThrow(() -> new NotFoundException("User profile not found"));
    }
}
