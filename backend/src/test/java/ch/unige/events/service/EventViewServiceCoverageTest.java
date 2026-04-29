package ch.unige.events.service;

import ch.unige.events.entity.Event;
import ch.unige.events.entity.EventStatus;
import ch.unige.events.entity.EventView;
import ch.unige.events.entity.User;
import io.quarkus.test.TestTransaction;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.ws.rs.NotFoundException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
@TestProfile(ShareServiceCoverageProfile.class)
class EventViewServiceCoverageTest {

    @Inject
    EventViewService eventViewService;

    @Inject
    EntityManager entityManager;

    // =========================================================
    // recordView — event not found
    // =========================================================

    @Test
    @TestTransaction
    void recordView_unknownEvent_throwsNotFound() {
        persistUser("auth0|view-noevent", "view-noevent@example.com");

        assertThrows(NotFoundException.class,
                () -> eventViewService.recordView("auth0|view-noevent", 999999L));
    }

    // =========================================================
    // recordView — user not found
    // =========================================================

    @Test
    @TestTransaction
    void recordView_unknownUser_throwsNotFound() {
        User creator = persistUser("auth0|view-creator1", "view-creator1@example.com");
        Event event = persistEvent("Event A", creator, EventStatus.PUBLISHED);

        assertThrows(NotFoundException.class,
                () -> eventViewService.recordView("auth0|nobody-view", event.id));
    }

    // =========================================================
    // recordView — first view inserts a row
    // =========================================================

    @Test
    @TestTransaction
    void recordView_firstTime_insertsViewRow() {
        User user = persistUser("auth0|view1", "view1@example.com");
        Event event = persistEvent("Event B", user, EventStatus.PUBLISHED);

        eventViewService.recordView("auth0|view1", event.id);
        entityManager.flush();

        long count = EventView.count("eventId = ?1 AND userId = ?2", event.id, user.id);
        assertEquals(1L, count);
    }

    // =========================================================
    // recordView — second view is idempotent (ON CONFLICT DO NOTHING)
    // =========================================================

    @Test
    @TestTransaction
    void recordView_secondTime_isIdempotent() {
        User user = persistUser("auth0|view2", "view2@example.com");
        Event event = persistEvent("Event C", user, EventStatus.PUBLISHED);

        eventViewService.recordView("auth0|view2", event.id);
        entityManager.flush();
        entityManager.clear();

        // Second call must not throw and must not create a duplicate row.
        eventViewService.recordView("auth0|view2", event.id);
        entityManager.flush();

        long count = EventView.count("eventId = ?1 AND userId = ?2", event.id, user.id);
        assertEquals(1L, count);
    }

    // =========================================================
    // EventView.prePersist — @PrePersist sets viewedAt
    // =========================================================

    @Test
    @TestTransaction
    void eventView_prePersist_setsViewedAt() {
        User user = persistUser("auth0|view-pp", "view-pp@example.com");
        Event event = persistEvent("Event PP", user, EventStatus.PUBLISHED);

        EventView view = new EventView();
        view.eventId = event.id;
        view.userId = user.id;
        entityManager.persist(view);
        entityManager.flush();

        assertNotNull(view.viewedAt, "@PrePersist must have set viewedAt");
    }

    // =========================================================
    // Helpers
    // =========================================================

    private User persistUser(String auth0Id, String email) {
        return ServiceCoverageTestHelper.persistUser(entityManager, auth0Id, email);
    }

    private Event persistEvent(String title, User creator, EventStatus status) {
        return ServiceCoverageTestHelper.persistEvent(entityManager, title, creator, status, null);
    }
}
