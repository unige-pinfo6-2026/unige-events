package ch.unige.events.event.stats.service;

import ch.unige.events.event.stats.dto.EventStatsDTO;
import ch.unige.events.event.entity.AttendanceStatus;
import ch.unige.events.event.entity.AttendanceStub;
import ch.unige.events.event.coorganizer.entity.EventCoOrganizer;
import ch.unige.events.event.entity.Event;
import ch.unige.events.event.view.entity.EventView;
import ch.unige.events.event.favorite.entity.Favorite;
import ch.unige.events.event.entity.UserStub;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.ForbiddenException;
import jakarta.ws.rs.NotFoundException;

@ApplicationScoped
public class EventStatsService {

    @Transactional
    public EventStatsDTO getStats(String auth0Id, Long eventId) {
        Event event = Event.<Event>findByIdOptional(eventId)
                .orElseThrow(() -> new NotFoundException("Event not found"));

        UserStub caller = UserStub.findByAuth0Id(auth0Id)
                .orElseThrow(() -> new NotFoundException("User profile not found"));

        if (!isCreatorOrAcceptedCoOrganizer(event, caller)) {
            throw new ForbiddenException("Only the event creator or an accepted co-organizer can view stats");
        }

        long attendingCount = AttendanceStub.count("eventId = ?1 and status = ?2",
                eventId, AttendanceStatus.ATTENDING);
        long interestedCount = Favorite.count("eventId = ?1", eventId);
        long viewCount = EventView.count("eventId = ?1", eventId);

        return new EventStatsDTO(attendingCount, interestedCount, viewCount);
    }

    private static boolean isCreatorOrAcceptedCoOrganizer(Event event, UserStub caller) {
        if (event == null || caller == null) {
            return false;
        }
        if (caller.id.equals((event.creator != null ? event.creator.id : null))) {
            return true;
        }
        return EventCoOrganizer.isAcceptedFor(event.id, caller.id);
    }
}
