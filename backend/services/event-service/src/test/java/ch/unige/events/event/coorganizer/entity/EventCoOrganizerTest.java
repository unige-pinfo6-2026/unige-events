package ch.unige.events.event.coorganizer.entity;

import ch.unige.events.shared.domain.enums.CoOrganizerStatus;

import io.quarkus.test.TestTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.time.Month;

@QuarkusTest
class EventCoOrganizerTest {

    @Inject EntityManager em;

    private EventCoOrganizer create(Long eventId, UUID userId, CoOrganizerStatus status) {
        EventCoOrganizer co = new EventCoOrganizer();
        co.eventId = eventId;
        co.userId = userId;
        co.status = status;
        co.persist();
        return co;
    }

    @Test
    @TestTransaction
    void prePersist_setsInvitedAt() {
        UUID userId = UUID.randomUUID();
        EventCoOrganizer co = create(42L, userId, CoOrganizerStatus.PENDING);
        em.flush();
        assertNotNull(co.invitedAt);
    }

    @Test
    @TestTransaction
    void prePersist_preservesExplicitInvitedAt() {
        // L53 false leg — invitedAt is already set before persist, so
        // @PrePersist must NOT overwrite it.
        UUID userId = UUID.randomUUID();
        EventCoOrganizer co = new EventCoOrganizer();
        co.eventId = 142L;
        co.userId = userId;
        co.status = CoOrganizerStatus.PENDING;
        java.time.LocalDateTime explicit = java.time.LocalDateTime.of(2024, Month.JANUARY, 2, 3, 4, 5);
        co.invitedAt = explicit;
        co.persist();
        em.flush();
        assertEquals(explicit, co.invitedAt);
    }

    @Test
    @TestTransaction
    void isAcceptedFor_accepted_returnsTrue() {
        UUID userId = UUID.randomUUID();
        create(43L, userId, CoOrganizerStatus.ACCEPTED);
        em.flush();
        assertTrue(EventCoOrganizer.isAcceptedFor(43L, userId));
    }

    @Test
    @TestTransaction
    void isAcceptedFor_pending_returnsFalse() {
        UUID userId = UUID.randomUUID();
        create(44L, userId, CoOrganizerStatus.PENDING);
        em.flush();
        assertFalse(EventCoOrganizer.isAcceptedFor(44L, userId));
    }

    @Test
    @TestTransaction
    void findByEventAndUser_match() {
        UUID userId = UUID.randomUUID();
        create(45L, userId, CoOrganizerStatus.PENDING);
        em.flush();
        Optional<EventCoOrganizer> found = EventCoOrganizer.findByEventAndUser(45L, userId);
        assertTrue(found.isPresent());
    }

    @Test
    @TestTransaction
    void findByEventAndUser_noMatch() {
        Optional<EventCoOrganizer> found = EventCoOrganizer.findByEventAndUser(46L, UUID.randomUUID());
        assertFalse(found.isPresent());
    }

    @Test
    @TestTransaction
    void findByEvent_returnsAll() {
        create(47L, UUID.randomUUID(), CoOrganizerStatus.PENDING);
        create(47L, UUID.randomUUID(), CoOrganizerStatus.ACCEPTED);
        em.flush();
        List<EventCoOrganizer> rows = EventCoOrganizer.findByEvent(47L);
        assertEquals(2, rows.size());
    }

    @Test
    @TestTransaction
    void findByUser_filterByStatus() {
        UUID userId = UUID.randomUUID();
        create(48L, userId, CoOrganizerStatus.PENDING);
        create(49L, userId, CoOrganizerStatus.ACCEPTED);
        em.flush();

        List<EventCoOrganizer> pending = EventCoOrganizer.findByUser(userId, CoOrganizerStatus.PENDING, 0, 10);
        assertEquals(1, pending.size());
    }

    @Test
    @TestTransaction
    void findAcceptedUserIdsForEvent_returnsList() {
        UUID userA = UUID.randomUUID();
        UUID userB = UUID.randomUUID();
        create(50L, userA, CoOrganizerStatus.ACCEPTED);
        create(50L, userB, CoOrganizerStatus.PENDING);
        em.flush();

        List<UUID> ids = EventCoOrganizer.findAcceptedUserIdsForEvent(50L);
        assertEquals(1, ids.size());
        assertEquals(userA, ids.get(0));
    }
}
