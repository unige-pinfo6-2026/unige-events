package ch.unige.events.event.service;

import ch.unige.events.event.entity.Event;
import ch.unige.events.shared.domain.enums.EventCategory;
import ch.unige.events.shared.domain.enums.EventStatus;

import io.quarkus.test.TestTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

@QuarkusTest
class EventExpirationServiceTest {

    @Inject EventExpirationService service;
    @Inject EntityManager em;

    private Event create(EventStatus status, LocalDateTime end) {
        Event e = new Event();
        e.title = "T";
        e.description = "d";
        e.location = "l";
        e.startDate = LocalDateTime.of(2000, 1, 1, 0, 0).minusDays(2);
        e.endDate = end;
        e.category = EventCategory.ACADEMIC;
        e.creatorId = UUID.randomUUID();
        e.status = status;
        e.persist();
        return e;
    }

    @Test
    @TestTransaction
    void expireEvents_publishedPastEnd_flippedToExpired() {
        Event e = create(EventStatus.PUBLISHED, LocalDateTime.of(2000, 1, 1, 0, 0).minusHours(1));
        em.flush();
        Long id = e.id;
        int count = service.expireEvents();
        em.flush();
        em.clear();
        Event reloaded = Event.findById(id);
        assertEquals(1, count);
        assertEquals(EventStatus.EXPIRED, reloaded.status);
    }

    @Test
    @TestTransaction
    void expireEvents_publishedFuture_unchanged() {
        Event e = create(EventStatus.PUBLISHED, LocalDateTime.of(2999, 1, 1, 0, 0).plusDays(1));
        em.flush();
        int count = service.expireEvents();
        assertEquals(0, count);
        em.refresh(e);
        assertEquals(EventStatus.PUBLISHED, e.status);
    }

    @Test
    @TestTransaction
    void expireEvents_draftPastEnd_unchanged() {
        Event e = create(EventStatus.DRAFT, LocalDateTime.of(2000, 1, 1, 0, 0).minusHours(1));
        em.flush();
        int count = service.expireEvents();
        assertEquals(0, count);
        em.refresh(e);
        assertEquals(EventStatus.DRAFT, e.status);
    }

    @Test
    @TestTransaction
    void expireEvents_multiple_processedAll() {
        create(EventStatus.PUBLISHED, LocalDateTime.of(2000, 1, 1, 0, 0).minusHours(1));
        create(EventStatus.PUBLISHED, LocalDateTime.of(2000, 1, 1, 0, 0).minusDays(2));
        em.flush();
        int count = service.expireEvents();
        assertEquals(2, count);
    }

    @Test
    @TestTransaction
    void expireEvents_noCandidates_returnsZero() {
        assertEquals(0, service.expireEvents());
    }
}
