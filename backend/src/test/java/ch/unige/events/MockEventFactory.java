package ch.unige.events;

import ch.unige.events.entity.Event;
import ch.unige.events.entity.EventCategory;
import ch.unige.events.entity.EventStatus;
import ch.unige.events.entity.User;

import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Utilitaire de construction d'Event en mémoire pour les mocks de test.
 * Centralise la logique commune à FavoriteServiceMock et ShareServiceMock
 * afin d'éviter la duplication de code.
 */
public final class MockEventFactory {

    private MockEventFactory() {}

    public static Event build(String title, AtomicLong idSequence) {
        User creator = new User();
        creator.id = UUID.randomUUID();
        creator.auth0Id = "auth0|seed-creator";

        Event event = new Event();
        event.id = idSequence.getAndIncrement();
        event.title = title;
        event.location = "Uni Mail";
        event.startDate = LocalDateTime.now().plusDays(1);
        event.endDate = LocalDateTime.now().plusDays(2);
        event.category = EventCategory.ACADEMIC;
        event.status = EventStatus.PUBLISHED;
        event.creator = creator;
        event.createdAt = LocalDateTime.now();
        event.updatedAt = LocalDateTime.now();

        return event;
    }
}
