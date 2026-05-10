package ch.unige.events.event.view.service;

import ch.unige.events.event.entity.Event;
import ch.unige.events.event.test.JwtTestContext;
import ch.unige.events.event.test.JwtTestHelper;
import ch.unige.events.event.view.entity.EventView;
import ch.unige.events.shared.domain.enums.EventCategory;
import ch.unige.events.shared.domain.enums.EventStatus;

import io.quarkus.test.TestTransaction;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.ws.rs.NotFoundException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@QuarkusTest
@TestSecurity(user = "auth0|view")
class EventViewServiceTest {

    @Inject EventViewService service;
    @Inject EntityManager em;

    private final UUID userId = UUID.randomUUID();

    @BeforeEach
    void setup() {
        JwtTestContext.set(JwtTestHelper.jwtFor(userId));
    }

    @AfterEach
    void clear() {
        JwtTestContext.clear();
    }

    private Event newEvent() {
        Event e = new Event();
        e.title = "T";
        e.description = "d";
        e.location = "l";
        e.startDate = LocalDateTime.now().plusDays(1);
        e.endDate = e.startDate.plusHours(2);
        e.category = EventCategory.ACADEMIC;
        e.creatorId = UUID.randomUUID();
        e.status = EventStatus.PUBLISHED;
        e.persist();
        return e;
    }

    @Test
    @TestTransaction
    void recordView_inserts() {
        Event e = newEvent();
        em.flush();
        service.recordView("auth0|x", e.id);
        em.flush();
        long count = EventView.count("eventId = ?1 and userId = ?2", e.id, userId);
        assertEquals(1L, count);
    }

    @Test
    @TestTransaction
    void recordView_duplicate_idempotentUpdates() {
        Event e = newEvent();
        em.flush();

        service.recordView("auth0|x", e.id);
        service.recordView("auth0|x", e.id);
        em.flush();

        long count = EventView.count("eventId = ?1 and userId = ?2", e.id, userId);
        assertEquals(1L, count);
    }

    @Test
    @TestTransaction
    void recordView_unknownEvent_throws404() {
        assertThrows(NotFoundException.class,
                () -> service.recordView("auth0|x", 99999L));
    }

    @Test
    @TestTransaction
    void recordView_anonymousCaller_throws404() {
        Event e = newEvent();
        em.flush();
        JwtTestContext.clear();
        assertThrows(NotFoundException.class,
                () -> service.recordView("auth0|x", e.id));
    }
}
