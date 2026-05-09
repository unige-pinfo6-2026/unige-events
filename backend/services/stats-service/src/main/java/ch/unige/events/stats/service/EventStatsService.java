package ch.unige.events.stats.service;

import ch.unige.events.stats.dto.EventStatsDTO;
import ch.unige.events.stats.entity.AttendanceStatus;
import ch.unige.events.stats.entity.AttendanceStub;
import ch.unige.events.stats.entity.EventCoOrganizerStub;
import ch.unige.events.stats.entity.EventStub;
import ch.unige.events.stats.entity.EventViewStub;
import ch.unige.events.stats.entity.FavoriteStub;
import ch.unige.events.stats.entity.UserStub;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.ForbiddenException;
import jakarta.ws.rs.NotFoundException;

@ApplicationScoped
public class EventStatsService {

    @Transactional
    public EventStatsDTO getStats(String auth0Id, Long eventId) {
        EventStub event = EventStub.<EventStub>findByIdOptional(eventId)
                .orElseThrow(() -> new NotFoundException("Event not found"));

        UserStub caller = UserStub.findByAuth0Id(auth0Id)
                .orElseThrow(() -> new NotFoundException("User profile not found"));

        if (!isCreatorOrAcceptedCoOrganizer(event, caller)) {
            throw new ForbiddenException("Only the event creator or an accepted co-organizer can view stats");
        }

        long attendingCount = AttendanceStub.count("eventId = ?1 and status = ?2",
                eventId, AttendanceStatus.ATTENDING);
        long interestedCount = FavoriteStub.count("eventId = ?1", eventId);
        long viewCount = EventViewStub.count("eventId = ?1", eventId);

        return new EventStatsDTO(attendingCount, interestedCount, viewCount);
    }

    private static boolean isCreatorOrAcceptedCoOrganizer(EventStub event, UserStub caller) {
        if (event == null || caller == null) {
            return false;
        }
        if (caller.id.equals(event.creatorId)) {
            return true;
        }
        return EventCoOrganizerStub.isAcceptedFor(event.id, caller.id);
    }
}
