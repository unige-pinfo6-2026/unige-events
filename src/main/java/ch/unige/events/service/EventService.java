package ch.unige.events.service;

import ch.unige.events.entity.Event;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;

import java.util.List;

@ApplicationScoped
public class EventService {

    public List<Event> getAll() {
        return Event.listAll();
    }

    @Transactional
    public Event create(Event event) {
        event.persist();
        return event;
    }
}