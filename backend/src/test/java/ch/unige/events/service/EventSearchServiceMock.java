package ch.unige.events.service;

import ch.unige.events.dto.event.EventDTO;
import ch.unige.events.entity.Event;
import ch.unige.events.entity.EventCategory;
import ch.unige.events.entity.EventStatus;
import ch.unige.events.entity.Faculty;
import ch.unige.events.entity.User;
import io.quarkus.test.Mock;
import jakarta.enterprise.context.ApplicationScoped;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@Mock
@ApplicationScoped
public class EventSearchServiceMock extends EventSearchService {

    private final Map<Long, Event> eventsById = new ConcurrentHashMap<>();
    private final AtomicLong idSequence = new AtomicLong(1);

    public void reset() {
        eventsById.clear();
        idSequence.set(1);
    }

    /**
     * Crée un événement en mémoire.
     * title + description sont utilisés pour tester le filtre ILIKE.
     * category et startDate sont optionnels (valeurs par défaut si null).
     */
    public Event seedEvent(String title, String description,
                           EventCategory category, LocalDateTime startDate) {
        User creator = new User();
        creator.id = UUID.randomUUID();
        creator.auth0Id = "auth0|seed-user";

        Event event = new Event();
        event.id = idSequence.getAndIncrement();
        event.title = title;
        event.description = description;
        event.location = "Uni Mail";
        event.startDate = startDate != null ? startDate : LocalDateTime.now().plusDays(1);
        event.endDate = event.startDate.plusHours(2);
        event.category = category != null ? category : EventCategory.ACADEMIC;
        event.status = EventStatus.PUBLISHED;
        event.creator = creator;
        event.createdAt = LocalDateTime.now();
        event.updatedAt = LocalDateTime.now();

        eventsById.put(event.id, event);
        return event;
    }

    @Override
    @SuppressWarnings("java:S107")
    public List<EventDTO> search(String q, EventCategory category, Faculty faculty, Boolean facultyNone,
                                  LocalDate dateFrom, LocalDate dateTo,
                                  int page, int size) {
        return eventsById.values().stream()
                .filter(e -> {
                    if (q == null || q.isBlank()) return true;
                    String lower = q.toLowerCase();
                    return (e.title != null && e.title.toLowerCase().contains(lower))
                            || (e.description != null && e.description.toLowerCase().contains(lower));
                })
                .filter(e -> category == null || e.category == category)
                .filter(e -> {
                    // facultyNone=true has priority over faculty — mutually exclusive filter.
                    if (Boolean.TRUE.equals(facultyNone)) {
                        return e.faculty == null;
                    }
                    return faculty == null || e.faculty == faculty;
                })
                .filter(e -> dateFrom == null || !e.startDate.isBefore(dateFrom.atStartOfDay()))
                .filter(e -> dateTo == null || !e.startDate.isAfter(dateTo.atTime(23, 59, 59)))
                .sorted(Comparator.comparing((Event e) -> e.startDate).thenComparingLong(e -> e.id))
                .skip((long) page * size)
                .limit(size)
                .map(e -> EventDTO.from(e, 0L))
                .toList();
    }
}
