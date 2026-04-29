package ch.unige.events.service;

import ch.unige.events.dto.stats.EventStatsDTO;
import ch.unige.events.entity.Attendance;
import ch.unige.events.entity.AttendanceStatus;
import ch.unige.events.entity.Event;
import ch.unige.events.entity.EventView;
import ch.unige.events.entity.Favorite;
import ch.unige.events.entity.User;
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

        User caller = User.findByAuth0Id(auth0Id)
                .orElseThrow(() -> new NotFoundException("User profile not found"));

        if (event.creator == null || !event.creator.id.equals(caller.id)) {
            throw new ForbiddenException("Only the event creator can view stats");
        }

        long attendingCount = Attendance.count("eventId = ?1 and status = ?2", eventId, AttendanceStatus.ATTENDING);
        long interestedCount = Favorite.count("eventId = ?1", eventId);
        long viewCount = EventView.count("eventId = ?1", eventId);

        return new EventStatsDTO(attendingCount, interestedCount, viewCount);
    }
}
