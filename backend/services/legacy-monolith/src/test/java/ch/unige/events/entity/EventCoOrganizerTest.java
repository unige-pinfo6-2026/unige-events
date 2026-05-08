package ch.unige.events.entity;

import ch.unige.events.service.ShareServiceCoverageProfile;
import io.quarkus.test.TestTransaction;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceException;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
@TestProfile(ShareServiceCoverageProfile.class)
class EventCoOrganizerTest {

    @Inject
    EntityManager entityManager;

    @Test
    @TestTransaction
    void prePersist_setsInvitedAt() {
        Event event = persistEvent("prePersist-event");
        EventCoOrganizer entity = new EventCoOrganizer();
        entity.eventId = event.id;
        entity.userId = UUID.randomUUID();
        entity.status = CoOrganizerStatus.PENDING;

        entity.persist();
        entityManager.flush();

        assertNotNull(entity.invitedAt);
        assertTrue(entity.invitedAt.isBefore(LocalDateTime.now().plusSeconds(1)));
    }

    @Test
    @TestTransaction
    void isAcceptedFor_acceptedRow_returnsTrue() {
        Event event = persistEvent("accepted-event");
        UUID userId = UUID.randomUUID();
        persistInvitation(event.id, userId, CoOrganizerStatus.ACCEPTED);

        assertTrue(EventCoOrganizer.isAcceptedFor(event.id, userId));
    }

    @Test
    @TestTransaction
    void isAcceptedFor_pendingRow_returnsFalse() {
        Event event = persistEvent("pending-event");
        UUID userId = UUID.randomUUID();
        persistInvitation(event.id, userId, CoOrganizerStatus.PENDING);

        assertFalse(EventCoOrganizer.isAcceptedFor(event.id, userId));
    }

    @Test
    @TestTransaction
    void isAcceptedFor_noRow_returnsFalse() {
        Event event = persistEvent("empty-event");

        assertFalse(EventCoOrganizer.isAcceptedFor(event.id, UUID.randomUUID()));
    }

    @Test
    @TestTransaction
    void uniqueConstraint_blocksDuplicateInvitation() {
        Event event = persistEvent("duplicate-event");
        UUID userId = UUID.randomUUID();
        persistInvitation(event.id, userId, CoOrganizerStatus.PENDING);

        EventCoOrganizer dup = new EventCoOrganizer();
        dup.eventId = event.id;
        dup.userId = userId;
        dup.status = CoOrganizerStatus.PENDING;

        assertThrows(PersistenceException.class, () -> {
            dup.persist();
            entityManager.flush();
        });
    }

    private Event persistEvent(String title) {
        Event event = new Event();
        event.title = title;
        event.location = "Uni Mail";
        event.startDate = LocalDateTime.now().plusDays(1);
        event.endDate = LocalDateTime.now().plusDays(2);
        event.category = EventCategory.ACADEMIC;
        event.status = EventStatus.PUBLISHED;
        entityManager.persist(event);
        entityManager.flush();
        return event;
    }

    private void persistInvitation(Long eventId, UUID userId, CoOrganizerStatus status) {
        EventCoOrganizer e = new EventCoOrganizer();
        e.eventId = eventId;
        e.userId = userId;
        e.status = status;
        entityManager.persist(e);
        entityManager.flush();
    }
}
