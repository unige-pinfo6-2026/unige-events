package ch.unige.events.user.calendar.service;

import ch.unige.events.shared.client.EngagementServiceClient;
import ch.unige.events.shared.client.EventServiceClient;
import ch.unige.events.shared.domain.dto.AttendanceDTO;
import ch.unige.events.shared.domain.dto.EventDTO;
import ch.unige.events.shared.domain.enums.AttendanceStatus;
import ch.unige.events.user.config.AppConfig;
import ch.unige.events.user.calendar.dto.CalendarTokenResponse;
import ch.unige.events.user.calendar.util.IcsBuilder;
import ch.unige.events.user.entity.User;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.NotFoundException;
import org.eclipse.microprofile.rest.client.inject.RestClient;

import java.util.List;
import java.util.UUID;

/**
 * Same semantics as the legacy monolith's CalendarService — token rotation
 * (lazy create on first read, regenerate on POST) and ICS feed assembly.
 *
 * <p>Étape 3.3 finalization-ultimate (STUB-001 / Décisions A, B, F):
 * the legacy {@code FavoriteStub} / {@code AttendanceStub} /
 * {@code EventStub} navigations are replaced by REST clients to
 * engagement-service ({@code GET /users/{id}/attendances?status=ATTENDING}
 * — Décision B) and event-service ({@code GET /events?ids=...}). The
 * favorites projection is dropped from the ICS feed: the favorites
 * table moved into event-service in 2.2.3 with no internal endpoint
 * for "user favorite event ids" (deferred to S9 — cf.
 * internal-endpoints.md "endpoints à ajouter S9"). For S8 the ICS
 * feed only includes events the user is ATTENDING — minor functional
 * regression, acted in commit message.
 */
@ApplicationScoped
public class CalendarService {

    @Inject
    AppConfig appConfig;

    @Inject @RestClient EventServiceClient eventClient;
    @Inject @RestClient EngagementServiceClient engagementClient;

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

    public String generateIcsFeed(UUID calendarToken) {
        User user = User.findByCalendarToken(calendarToken)
                .orElseThrow(() -> new NotFoundException("Calendar token not found"));

        List<AttendanceDTO> attendances = engagementClient.getUserAttendances(
                user.id, AttendanceStatus.ATTENDING.name());
        if (attendances == null || attendances.isEmpty()) {
            return IcsBuilder.buildIcsContent(List.of(), appConfig.frontendUrl());
        }

        List<Long> eventIds = attendances.stream()
                .map(AttendanceDTO::eventId)
                .filter(java.util.Objects::nonNull)
                .distinct()
                .toList();
        if (eventIds.isEmpty()) {
            return IcsBuilder.buildIcsContent(List.of(), appConfig.frontendUrl());
        }

        List<EventDTO> events = eventClient.findByIds(eventIds, "PUBLISHED");
        if (events == null) {
            events = List.of();
        }
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
