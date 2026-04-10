package ch.unige.events.service;

import ch.unige.events.MockEventFactory;
import ch.unige.events.dto.event.EventDTO;
import ch.unige.events.entity.Event;
import io.quarkus.test.Mock;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.NotFoundException;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@Mock
@ApplicationScoped
public class FavoriteServiceMock extends FavoriteService {

    /** Map eventId → Event seeded */
    private final Map<Long, Event> eventsById = new ConcurrentHashMap<>();
    /** Set de (userId_mock, eventId) représentant les favoris en mémoire */
    private final Set<Long> favoritedEventIds = ConcurrentHashMap.newKeySet();
    private final AtomicLong idSequence = new AtomicLong(1);

    public static volatile boolean forceNotFoundOnAdd = false;
    public static volatile boolean forceNotFoundOnRemove = false;

    public void reset() {
        eventsById.clear();
        favoritedEventIds.clear();
        idSequence.set(1);
        forceNotFoundOnAdd = false;
        forceNotFoundOnRemove = false;
    }

    /**
     * Seed un événement en mémoire pour les tests.
     * Retourne l'Event pour permettre à l'appelant d'accéder à son id.
     */
    public Event seedEvent(String title) {
        Event event = MockEventFactory.build(title, idSequence);
        eventsById.put(event.id, event);
        return event;
    }

    /** Ajoute un eventId directement dans la liste des favoris (pour les tests de GET favorites) */
    public void seedFavorite(Long eventId) {
        favoritedEventIds.add(eventId);
    }

    @Override
    public void addFavorite(String auth0Id, Long eventId) {
        if (forceNotFoundOnAdd) throw new NotFoundException();
        if (!eventsById.containsKey(eventId)) throw new NotFoundException();
        // Idempotent — pas d'exception si déjà présent
        favoritedEventIds.add(eventId);
    }

    @Override
    public void removeFavorite(String auth0Id, Long eventId) {
        if (forceNotFoundOnRemove) throw new NotFoundException();
        if (!favoritedEventIds.remove(eventId)) throw new NotFoundException();
    }

    @Override
    public List<EventDTO> getFavorites(String auth0Id, int page, int size) {
        return favoritedEventIds.stream()
                .map(eventsById::get)
                .filter(Objects::nonNull)
                .skip((long) page * size)
                .limit(size)
                .map(e -> EventDTO.from(e, 0L))
                .toList();
    }
}
