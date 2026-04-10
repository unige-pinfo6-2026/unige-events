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

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

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

        Set<Long> eventIds = new HashSet<>();
        List<Event> events = new ArrayList<>();

        // Événements favoris (PUBLISHED)
        Favorite.findAllByUser(user.id).stream()
                .map(f -> Event.<Event>findByIdOptional(f.eventId))
                .flatMap(Optional::stream)
                .filter(e -> e.status == EventStatus.PUBLISHED)
                .forEach(e -> {
                    if (eventIds.add(e.id)) events.add(e);
                });

        // Événements ATTENDING (PUBLISHED, dédupliqués)
        Attendance.findAllByUser(user.id).stream()
                .map(a -> Event.<Event>findByIdOptional(a.eventId))
                .flatMap(Optional::stream)
                .filter(e -> e.status == EventStatus.PUBLISHED)
                .forEach(e -> {
                    if (eventIds.add(e.id)) events.add(e);
                });

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
