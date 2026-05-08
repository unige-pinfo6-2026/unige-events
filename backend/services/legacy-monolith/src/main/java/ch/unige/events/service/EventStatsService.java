package ch.unige.events.service;

import ch.unige.events.dto.stats.EventStatsDTO;
import ch.unige.events.entity.Attendance;
import ch.unige.events.entity.AttendanceStatus;
import ch.unige.events.entity.Event;
import ch.unige.events.entity.EventView;
import ch.unige.events.entity.Favorite;
import ch.unige.events.entity.User;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.ForbiddenException;
import jakarta.ws.rs.NotFoundException;

@ApplicationScoped
public class EventStatsService {

    @Inject
    EventService eventService;

    @Transactional
    public EventStatsDTO getStats(String auth0Id, Long eventId) {
        Event event = Event.<Event>findByIdOptional(eventId)
                .orElseThrow(() -> new NotFoundException("Event not found"));

        // Ré-affirme la présence du profil utilisateur (404 explicite pour les tests
        // qui couvrent ce cas, cohérent avec l'historique avant SCRUM-136).
        User.findByAuth0Id(auth0Id)
                .orElseThrow(() -> new NotFoundException("User profile not found"));

        // SCRUM-136 : créateur OU co-organisateur ACCEPTED.
        if (!eventService.isCreatorOrAcceptedCoOrganizerPublic(event, auth0Id)) {
            throw new ForbiddenException("Only the event creator or an accepted co-organizer can view stats");
        }

        long attendingCount = Attendance.count("eventId = ?1 and status = ?2", eventId, AttendanceStatus.ATTENDING);
        long interestedCount = Favorite.count("eventId = ?1", eventId);
        long viewCount = EventView.count("eventId = ?1", eventId);

        return new EventStatsDTO(attendingCount, interestedCount, viewCount);
    }
}
