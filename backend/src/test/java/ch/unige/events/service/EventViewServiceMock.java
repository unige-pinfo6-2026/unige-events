package ch.unige.events.service;

import io.quarkus.test.Mock;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.NotFoundException;

import java.util.HashSet;
import java.util.Set;

@Mock
@ApplicationScoped
public class EventViewServiceMock extends EventViewService {

    private final Set<Long> viewedEvents = new HashSet<>();

    public static volatile boolean forceNotFound = false;

    public void reset() {
        viewedEvents.clear();
        forceNotFound = false;
    }

    public void seedView(Long eventId) {
        viewedEvents.add(eventId);
    }

    public int getViewCount(Long eventId) {
        return viewedEvents.contains(eventId) ? 1 : 0;
    }

    @Override
    public void recordView(String auth0Id, Long eventId) {
        if (forceNotFound) throw new NotFoundException("Event not found");
        viewedEvents.add(eventId);
    }
}
