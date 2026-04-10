package ch.unige.events.service;

import ch.unige.events.dto.calendar.CalendarTokenResponse;
import ch.unige.events.entity.Attendance;
import ch.unige.events.entity.Event;
import ch.unige.events.entity.EventStatus;
import ch.unige.events.entity.Favorite;
import ch.unige.events.entity.User;
import ch.unige.events.config.AppConfig;
import ch.unige.events.util.IcsBuilder;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.NotFoundException;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@ApplicationScoped
public class CalendarService {

    @Inject
    AppConfig appConfig;

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

        // Collect all favorite event IDs
        List<Long> favoriteIds = Favorite.findAllByUser(user.id).stream()
                .map(f -> f.eventId)
                .collect(Collectors.toList());

        // Collect all attending event IDs
        List<Long> attendingIds = Attendance.findAllByUser(user.id).stream()
                .map(a -> a.eventId)
                .collect(Collectors.toList());

        // Combine into a deduplicated set
        Set<Long> allIds = new HashSet<>();
        allIds.addAll(favoriteIds);
        allIds.addAll(attendingIds);

        if (allIds.isEmpty()) {
            return IcsBuilder.buildIcsContent(List.of(), appConfig.frontendUrl());
        }

        // Single bulk query for all PUBLISHED events
        List<Event> events = Event.<Event>find(
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
        return User.<User>find("auth0Id", auth0Id)
                .firstResultOptional()
                .orElseThrow(() -> new NotFoundException("User profile not found"));
    }
}
