package ch.unige.events.service;

import ch.unige.events.dto.stats.EventStatsDTO;
import io.quarkus.test.Mock;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.ForbiddenException;
import jakarta.ws.rs.NotFoundException;

@Mock
@ApplicationScoped
public class EventStatsServiceMock extends EventStatsService {

    private long attendingCount = 0L;
    private long interestedCount = 0L;
    private long viewCount = 0L;

    public static volatile boolean forceNotFound = false;
    public static volatile boolean forceForbidden = false;

    public void reset() {
        attendingCount = 0L;
        interestedCount = 0L;
        viewCount = 0L;
        forceNotFound = false;
        forceForbidden = false;
    }

    public void seed(long attending, long interested, long views) {
        this.attendingCount = attending;
        this.interestedCount = interested;
        this.viewCount = views;
    }

    @Override
    public EventStatsDTO getStats(String auth0Id, Long eventId) {
        if (forceNotFound) throw new NotFoundException("Event not found");
        if (forceForbidden) throw new ForbiddenException();
        return new EventStatsDTO(attendingCount, interestedCount, viewCount);
    }
}
