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

import java.time.LocalDateTime;

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
    // recordView — second view is idempotent and refreshes viewedAt
    // Regression: duplicate key on uq_event_view_user_event when re-viewing event
    // (commit 2837585 reverted prior fix 3591f37).
    // =========================================================

    @Test
    @TestTransaction
    void recordView_secondTime_isIdempotentAndRefreshesViewedAt() {
        User user = persistUser("auth0|view2", "view2@example.com");
        Event event = persistEvent("Event C", user, EventStatus.PUBLISHED);

        eventViewService.recordView("auth0|view2", event.id);
        entityManager.flush();
        entityManager.clear();

        // Rewind viewed_at to a known past value so the next upsert produces an
        // observably newer timestamp without relying on wall-clock precision or sleeps.
        LocalDateTime pastMarker = LocalDateTime.now().minusDays(1);
        entityManager.createNativeQuery(
                "UPDATE event_views SET viewed_at = :past WHERE event_id = :eventId AND user_id = :userId")
                .setParameter("past", pastMarker)
                .setParameter("eventId", event.id)
                .setParameter("userId", user.id)
                .executeUpdate();
        entityManager.clear();

        eventViewService.recordView("auth0|view2", event.id);
        entityManager.flush();
        entityManager.clear();

        long count = EventView.count("eventId = ?1 AND userId = ?2", event.id, user.id);
        assertEquals(1L, count, "Second call must not create a duplicate row");

        LocalDateTime refreshedViewedAt = readViewedAt(event.id, user.id);
        assertTrue(refreshedViewedAt.isAfter(pastMarker),
                "viewedAt must be refreshed by the upsert (was " + pastMarker + ", now " + refreshedViewedAt + ")");
    }

    // =========================================================
    // recordView — N calls collapse to a single row
    // =========================================================

    @Test
    @TestTransaction
    void recordView_repeatedCalls_keepExactlyOneRow() {
        User user = persistUser("auth0|view-n", "view-n@example.com");
        Event event = persistEvent("Event N", user, EventStatus.PUBLISHED);

        for (int i = 0; i < 5; i++) {
            eventViewService.recordView("auth0|view-n", event.id);
        }
        entityManager.flush();

        long count = EventView.count("eventId = ?1 AND userId = ?2", event.id, user.id);
        assertEquals(1L, count);
    }

    // =========================================================
    // recordView — different users on the same event each get their own row
    // =========================================================

    @Test
    @TestTransaction
    void recordView_differentUsersSameEvent_insertsDistinctRows() {
        User alice = persistUser("auth0|view-alice", "view-alice@example.com");
        User bob = persistUser("auth0|view-bob", "view-bob@example.com");
        Event event = persistEvent("Event Shared", alice, EventStatus.PUBLISHED);

        eventViewService.recordView("auth0|view-alice", event.id);
        eventViewService.recordView("auth0|view-bob", event.id);
        entityManager.flush();

        long total = EventView.count("eventId = ?1", event.id);
        long aliceRows = EventView.count("eventId = ?1 AND userId = ?2", event.id, alice.id);
        long bobRows = EventView.count("eventId = ?1 AND userId = ?2", event.id, bob.id);

        assertEquals(2L, total);
        assertEquals(1L, aliceRows);
        assertEquals(1L, bobRows);
    }

    // =========================================================
    // recordView — same user on different events gets multiple rows
    // =========================================================

    @Test
    @TestTransaction
    void recordView_sameUserDifferentEvents_insertsDistinctRows() {
        User user = persistUser("auth0|view-multi", "view-multi@example.com");
        Event eventX = persistEvent("Event X", user, EventStatus.PUBLISHED);
        Event eventY = persistEvent("Event Y", user, EventStatus.PUBLISHED);

        eventViewService.recordView("auth0|view-multi", eventX.id);
        eventViewService.recordView("auth0|view-multi", eventY.id);
        entityManager.flush();

        long userRows = EventView.count("userId = ?1", user.id);
        long xRows = EventView.count("eventId = ?1 AND userId = ?2", eventX.id, user.id);
        long yRows = EventView.count("eventId = ?1 AND userId = ?2", eventY.id, user.id);

        assertEquals(2L, userRows);
        assertEquals(1L, xRows);
        assertEquals(1L, yRows);
    }

    // =========================================================
    // EventView.prePersist — @PrePersist sets viewedAt on direct Java-level persist
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

    private LocalDateTime readViewedAt(Long eventId, java.util.UUID userId) {
        return EventView.<EventView>find("eventId = ?1 AND userId = ?2", eventId, userId)
                .firstResultOptional()
                .orElseThrow(() -> new AssertionError("EventView row not found"))
                .viewedAt;
    }
}
