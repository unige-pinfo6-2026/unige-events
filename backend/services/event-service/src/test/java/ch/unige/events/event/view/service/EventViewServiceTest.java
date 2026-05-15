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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
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

    // ─── Authenticated branch ─────────────────────────────────────────────

    @Test
    @TestTransaction
    void recordView_authenticated_inserts() {
        Event e = newEvent();
        em.flush();
        service.recordView("auth0|x", e.id, null);
        em.flush();
        long count = EventView.count("eventId = ?1 and userId = ?2", e.id, userId);
        assertEquals(1L, count);
    }

    @Test
    @TestTransaction
    void recordView_authenticatedDuplicate_idempotentUpdates() {
        Event e = newEvent();
        em.flush();

        service.recordView("auth0|x", e.id, null);
        service.recordView("auth0|x", e.id, null);
        em.flush();

        long count = EventView.count("eventId = ?1 and userId = ?2", e.id, userId);
        assertEquals(1L, count);
    }

    @Test
    @TestTransaction
    void recordView_authenticatedIgnoresSessionId() {
        // When auth0Id is present and resolves to a userId, sessionId is ignored.
        Event e = newEvent();
        em.flush();
        UUID anonSession = UUID.randomUUID();

        service.recordView("auth0|x", e.id, anonSession);
        em.flush();

        long userRows = EventView.count("eventId = ?1 and userId = ?2", e.id, userId);
        long sessionRows = EventView.count("eventId = ?1 and sessionId = ?2", e.id, anonSession);
        assertEquals(1L, userRows);
        assertEquals(0L, sessionRows);
    }

    // ─── Anonymous branch (Axe 4 — view anon + dedup session) ────────────

    @Test
    @TestTransaction
    void recordView_anonymousWithSessionId_inserts() {
        Event e = newEvent();
        em.flush();
        UUID anonSession = UUID.randomUUID();

        service.recordView(null, e.id, anonSession);
        em.flush();

        long count = EventView.count("eventId = ?1 and sessionId = ?2", e.id, anonSession);
        assertEquals(1L, count);
        EventView row = EventView.find("eventId = ?1 and sessionId = ?2", e.id, anonSession).firstResult();
        assertNotNull(row);
        assertNull(row.userId);
        assertNotNull(row.viewedAt);
    }

    @Test
    @TestTransaction
    void recordView_anonymousWithSessionIdDuplicate_idempotentUpdates() {
        Event e = newEvent();
        em.flush();
        UUID anonSession = UUID.randomUUID();

        service.recordView(null, e.id, anonSession);
        service.recordView(null, e.id, anonSession);
        em.flush();

        long count = EventView.count("eventId = ?1 and sessionId = ?2", e.id, anonSession);
        assertEquals(1L, count);
    }

    @Test
    @TestTransaction
    void recordView_anonymousWithoutSessionId_silentNoOp() {
        Event e = newEvent();
        em.flush();

        // No JWT, no sessionId — should not throw, should not insert anything.
        service.recordView(null, e.id, null);
        em.flush();

        long count = EventView.count("eventId = ?1", e.id);
        assertEquals(0L, count);
    }

    @Test
    @TestTransaction
    void recordView_twoAnonymousSessions_insertTwoRows() {
        Event e = newEvent();
        em.flush();
        UUID anonA = UUID.randomUUID();
        UUID anonB = UUID.randomUUID();

        service.recordView(null, e.id, anonA);
        service.recordView(null, e.id, anonB);
        em.flush();

        long count = EventView.count("eventId = ?1", e.id);
        assertEquals(2L, count);
    }

    // ─── Event existence check ────────────────────────────────────────────

    @Test
    @TestTransaction
    void recordView_unknownEvent_throws404_authenticated() {
        assertThrows(NotFoundException.class,
                () -> service.recordView("auth0|x", 99999L, null));
    }

    @Test
    @TestTransaction
    void recordView_unknownEvent_throws404_anonymous() {
        UUID anonSession = UUID.randomUUID();
        assertThrows(NotFoundException.class,
                () -> service.recordView(null, 99999L, anonSession));
    }
}
