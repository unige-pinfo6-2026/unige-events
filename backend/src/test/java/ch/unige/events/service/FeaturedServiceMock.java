package ch.unige.events.service;

import ch.unige.events.dto.event.EventDTO;
import ch.unige.events.entity.*;
import io.quarkus.test.Mock;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.NotFoundException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@Mock
@ApplicationScoped
public class FeaturedServiceMock extends FeaturedService {

    private final Map<Long, Event> store = new ConcurrentHashMap<>();
    private final AtomicLong seq = new AtomicLong(1);
    public static volatile boolean forceNotFound = false;

    public void reset() {
        store.clear();
        seq.set(1);
        forceNotFound = false;
    }

    public Event seedEvent(String title, boolean featured) {
        User creator = new User();
        creator.id = UUID.randomUUID();

        Event e = new Event();
        e.id = seq.getAndIncrement();
        e.title = title;
        e.location = "Uni Mail";
        e.startDate = LocalDateTime.now().plusDays(1);
        e.endDate = LocalDateTime.now().plusDays(2);
        e.status = EventStatus.PUBLISHED;
        e.featured = featured;
        e.featuredAt = featured ? LocalDateTime.now() : null;
        e.creator = creator;
        e.createdAt = LocalDateTime.now();
        e.updatedAt = LocalDateTime.now();
        store.put(e.id, e);
        return e;
    }

    @Override
    public List<EventDTO> getFeatured(int limit) {
        return store.values().stream()
                .filter(e -> e.featured)
                .map(e -> EventDTO.from(e, 0L, null, 0L))
                .toList();
    }

    @Override
    public EventDTO feature(Long id) {
        if (forceNotFound) throw new NotFoundException();
        Event e = store.get(id);
        if (e == null) throw new NotFoundException();
        e.featured = true;
        e.featuredAt = LocalDateTime.now();
        return EventDTO.from(e, 0L, null, 0L);
    }

    @Override
    public EventDTO unfeature(Long id) {
        if (forceNotFound) throw new NotFoundException();
        Event e = store.get(id);
        if (e == null) throw new NotFoundException();
        e.featured = false;
        e.featuredAt = null;
        return EventDTO.from(e, 0L, null, 0L);
    }
}
