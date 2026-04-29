package ch.unige.events.service;

import ch.unige.events.dto.coorganizer.CoOrganizerDTO;
import ch.unige.events.dto.coorganizer.CoOrganizerInvitationDTO;
import ch.unige.events.entity.CoOrganizerStatus;
import ch.unige.events.entity.Event;
import ch.unige.events.entity.EventCategory;
import ch.unige.events.entity.EventCoOrganizer;
import ch.unige.events.entity.EventStatus;
import ch.unige.events.entity.User;
import io.quarkus.test.TestTransaction;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.ws.rs.ForbiddenException;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.WebApplicationException;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
@TestProfile(ShareServiceCoverageProfile.class)
class EventCoOrganizerServiceCoverageTest {

    @Inject
    EventCoOrganizerService service;

    @Inject
    EntityManager entityManager;

    // -------------------------------------------------------------------------
    // invite()
    // -------------------------------------------------------------------------

    @Test
    @TestTransaction
    void invite_persistsRowWithStatusPending() {
        User creator = persistUser("auth0|inv-creator", "inv-creator@example.com");
        User target = persistUser("auth0|inv-target", "inv-target@example.com");
        Event event = persistEvent("inv-event", creator, EventStatus.PUBLISHED);

        CoOrganizerDTO dto = service.invite(event.id, creator.auth0Id, target.id, false);

        assertNotNull(dto.id());
        assertEquals(CoOrganizerStatus.PENDING, dto.status());
        assertEquals(target.id, dto.userId());
        long count = EventCoOrganizer.count("eventId = ?1 and userId = ?2", event.id, target.id);
        assertEquals(1L, count);
    }

    @Test
    @TestTransaction
    void invite_byNonCreator_throwsForbidden() {
        User creator = persistUser("auth0|f-creator", "f-creator@example.com");
        User intruder = persistUser("auth0|f-intruder", "f-intruder@example.com");
        User target = persistUser("auth0|f-target", "f-target@example.com");
        Event event = persistEvent("f-event", creator, EventStatus.PUBLISHED);

        assertThrows(ForbiddenException.class,
                () -> service.invite(event.id, intruder.auth0Id, target.id, false));
    }

    @Test
    @TestTransaction
    void invite_self_throwsBadRequest() {
        User creator = persistUser("auth0|self-creator", "self-creator@example.com");
        Event event = persistEvent("self-event", creator, EventStatus.PUBLISHED);

        WebApplicationException ex = assertThrows(WebApplicationException.class,
                () -> service.invite(event.id, creator.auth0Id, creator.id, false));
        assertEquals(400, ex.getResponse().getStatus());
    }

    @Test
    @TestTransaction
    void invite_alreadyInvited_throwsConflict() {
        User creator = persistUser("auth0|dup-creator", "dup-creator@example.com");
        User target = persistUser("auth0|dup-target", "dup-target@example.com");
        Event event = persistEvent("dup-event", creator, EventStatus.PUBLISHED);

        service.invite(event.id, creator.auth0Id, target.id, false);

        WebApplicationException ex = assertThrows(WebApplicationException.class,
                () -> service.invite(event.id, creator.auth0Id, target.id, false));
        assertEquals(409, ex.getResponse().getStatus());
    }

    @Test
    @TestTransaction
    void invite_targetUserNotFound_throws404() {
        User creator = persistUser("auth0|nf-creator", "nf-creator@example.com");
        Event event = persistEvent("nf-event", creator, EventStatus.PUBLISHED);

        assertThrows(NotFoundException.class,
                () -> service.invite(event.id, creator.auth0Id, UUID.randomUUID(), false));
    }

    @Test
    @TestTransaction
    void invite_eventNotFound_throws404() {
        persistUser("auth0|ne-creator", "ne-creator@example.com");

        assertThrows(NotFoundException.class,
                () -> service.invite(99999L, "auth0|ne-creator", UUID.randomUUID(), false));
    }

    @Test
    @TestTransaction
    void invite_byAdmin_succeedsWithoutBeingCreator() {
        User creator = persistUser("auth0|adm-creator", "adm-creator@example.com");
        User admin = persistUser("auth0|adm-admin", "adm-admin@example.com");
        User target = persistUser("auth0|adm-target", "adm-target@example.com");
        Event event = persistEvent("adm-event", creator, EventStatus.PUBLISHED);

        CoOrganizerDTO dto = service.invite(event.id, admin.auth0Id, target.id, true);

        assertNotNull(dto);
        assertEquals(CoOrganizerStatus.PENDING, dto.status());
    }

    // -------------------------------------------------------------------------
    // accept()
    // -------------------------------------------------------------------------

    @Test
    @TestTransaction
    void accept_transitionsPendingToAccepted() {
        User creator = persistUser("auth0|acc-creator", "acc-creator@example.com");
        User invited = persistUser("auth0|acc-invited", "acc-invited@example.com");
        Event event = persistEvent("acc-event", creator, EventStatus.PUBLISHED);
        persistInvitation(event.id, invited.id, CoOrganizerStatus.PENDING);

        CoOrganizerDTO dto = service.accept(event.id, invited.auth0Id);

        assertEquals(CoOrganizerStatus.ACCEPTED, dto.status());
        EventCoOrganizer reloaded = EventCoOrganizer.findByEventAndUser(event.id, invited.id).orElseThrow();
        assertEquals(CoOrganizerStatus.ACCEPTED, reloaded.status);
    }

    @Test
    @TestTransaction
    void accept_idempotentOnAccepted() {
        User creator = persistUser("auth0|idem-creator", "idem-creator@example.com");
        User invited = persistUser("auth0|idem-invited", "idem-invited@example.com");
        Event event = persistEvent("idem-event", creator, EventStatus.PUBLISHED);
        persistInvitation(event.id, invited.id, CoOrganizerStatus.ACCEPTED);

        CoOrganizerDTO dto = assertDoesNotThrow(() -> service.accept(event.id, invited.auth0Id));
        assertEquals(CoOrganizerStatus.ACCEPTED, dto.status());
    }

    @Test
    @TestTransaction
    void accept_noInvitation_throws404() {
        User invited = persistUser("auth0|noinv-invited", "noinv-invited@example.com");

        assertThrows(NotFoundException.class,
                () -> service.accept(99999L, invited.auth0Id));
    }

    // -------------------------------------------------------------------------
    // decline()
    // -------------------------------------------------------------------------

    @Test
    @TestTransaction
    void decline_deletesRow() {
        User creator = persistUser("auth0|dec-creator", "dec-creator@example.com");
        User invited = persistUser("auth0|dec-invited", "dec-invited@example.com");
        Event event = persistEvent("dec-event", creator, EventStatus.PUBLISHED);
        persistInvitation(event.id, invited.id, CoOrganizerStatus.PENDING);

        service.decline(event.id, invited.auth0Id);
        entityManager.flush();

        assertTrue(EventCoOrganizer.findByEventAndUser(event.id, invited.id).isEmpty());
    }

    @Test
    @TestTransaction
    void decline_thenReinvite_works() {
        User creator = persistUser("auth0|reinv-creator", "reinv-creator@example.com");
        User invited = persistUser("auth0|reinv-invited", "reinv-invited@example.com");
        Event event = persistEvent("reinv-event", creator, EventStatus.PUBLISHED);
        persistInvitation(event.id, invited.id, CoOrganizerStatus.PENDING);

        service.decline(event.id, invited.auth0Id);
        entityManager.flush();

        CoOrganizerDTO dto = service.invite(event.id, creator.auth0Id, invited.id, false);
        assertEquals(CoOrganizerStatus.PENDING, dto.status());
    }

    @Test
    @TestTransaction
    void decline_noInvitation_throws404() {
        User invited = persistUser("auth0|decnf-invited", "decnf-invited@example.com");

        assertThrows(NotFoundException.class,
                () -> service.decline(99999L, invited.auth0Id));
    }

    // -------------------------------------------------------------------------
    // remove()
    // -------------------------------------------------------------------------

    @Test
    @TestTransaction
    void remove_byCreator_deletesRow() {
        User creator = persistUser("auth0|rm-creator", "rm-creator@example.com");
        User invited = persistUser("auth0|rm-invited", "rm-invited@example.com");
        Event event = persistEvent("rm-event", creator, EventStatus.PUBLISHED);
        persistInvitation(event.id, invited.id, CoOrganizerStatus.PENDING);

        service.remove(event.id, creator.auth0Id, invited.id, false);
        entityManager.flush();

        assertTrue(EventCoOrganizer.findByEventAndUser(event.id, invited.id).isEmpty());
    }

    @Test
    @TestTransaction
    void remove_byAdmin_deletesRow() {
        User creator = persistUser("auth0|rmadm-creator", "rmadm-creator@example.com");
        User admin = persistUser("auth0|rmadm-admin", "rmadm-admin@example.com");
        User invited = persistUser("auth0|rmadm-invited", "rmadm-invited@example.com");
        Event event = persistEvent("rmadm-event", creator, EventStatus.PUBLISHED);
        persistInvitation(event.id, invited.id, CoOrganizerStatus.PENDING);

        service.remove(event.id, admin.auth0Id, invited.id, true);
        entityManager.flush();

        assertTrue(EventCoOrganizer.findByEventAndUser(event.id, invited.id).isEmpty());
    }

    @Test
    @TestTransaction
    void remove_byNonCreator_nonAdmin_throwsForbidden() {
        User creator = persistUser("auth0|rmf-creator", "rmf-creator@example.com");
        User intruder = persistUser("auth0|rmf-intruder", "rmf-intruder@example.com");
        Event event = persistEvent("rmf-event", creator, EventStatus.PUBLISHED);

        assertThrows(ForbiddenException.class,
                () -> service.remove(event.id, intruder.auth0Id, UUID.randomUUID(), false));
    }

    @Test
    @TestTransaction
    void remove_idempotent_noOp() {
        User creator = persistUser("auth0|rmi-creator", "rmi-creator@example.com");
        Event event = persistEvent("rmi-event", creator, EventStatus.PUBLISHED);

        assertDoesNotThrow(() -> service.remove(event.id, creator.auth0Id, UUID.randomUUID(), false));
    }

    // -------------------------------------------------------------------------
    // getCoOrganizers()
    // -------------------------------------------------------------------------

    @Test
    @TestTransaction
    void getCoOrganizers_returnsRowsOrderedByInvitedAt() {
        User creator = persistUser("auth0|lst-creator", "lst-creator@example.com");
        User u1 = persistUser("auth0|lst-u1", "lst-u1@example.com");
        User u2 = persistUser("auth0|lst-u2", "lst-u2@example.com");
        Event event = persistEvent("lst-event", creator, EventStatus.PUBLISHED);
        persistInvitationAt(event.id, u1.id, CoOrganizerStatus.PENDING, LocalDateTime.now().minusHours(2));
        persistInvitationAt(event.id, u2.id, CoOrganizerStatus.PENDING, LocalDateTime.now().minusHours(1));

        List<CoOrganizerDTO> result = service.getCoOrganizers(event.id);

        assertEquals(2, result.size());
        assertEquals(u1.id, result.get(0).userId());
        assertEquals(u2.id, result.get(1).userId());
    }

    @Test
    @TestTransaction
    void getCoOrganizers_includesPendingAndAccepted() {
        User creator = persistUser("auth0|mix-creator", "mix-creator@example.com");
        User pending = persistUser("auth0|mix-pending", "mix-pending@example.com");
        User accepted = persistUser("auth0|mix-accepted", "mix-accepted@example.com");
        Event event = persistEvent("mix-event", creator, EventStatus.PUBLISHED);
        persistInvitation(event.id, pending.id, CoOrganizerStatus.PENDING);
        persistInvitation(event.id, accepted.id, CoOrganizerStatus.ACCEPTED);

        List<CoOrganizerDTO> result = service.getCoOrganizers(event.id);

        assertEquals(2, result.size());
    }

    @Test
    @TestTransaction
    void getCoOrganizers_emptyEvent_returnsEmptyList() {
        User creator = persistUser("auth0|emp-creator", "emp-creator@example.com");
        Event event = persistEvent("emp-event", creator, EventStatus.PUBLISHED);

        List<CoOrganizerDTO> result = service.getCoOrganizers(event.id);
        assertTrue(result.isEmpty());
    }

    @Test
    @TestTransaction
    void getCoOrganizers_eventNotFound_throws404() {
        assertThrows(NotFoundException.class, () -> service.getCoOrganizers(99999L));
    }

    // -------------------------------------------------------------------------
    // getMyInvitations()
    // -------------------------------------------------------------------------

    @Test
    @TestTransaction
    void getMyInvitations_defaultsToPending() {
        User creator = persistUser("auth0|my-creator", "my-creator@example.com");
        User invited = persistUser("auth0|my-invited", "my-invited@example.com");
        Event eventA = persistEvent("my-event-a", creator, EventStatus.PUBLISHED);
        Event eventB = persistEvent("my-event-b", creator, EventStatus.PUBLISHED);
        persistInvitation(eventA.id, invited.id, CoOrganizerStatus.PENDING);
        persistInvitation(eventB.id, invited.id, CoOrganizerStatus.ACCEPTED);

        List<CoOrganizerInvitationDTO> result = service.getMyInvitations(invited.auth0Id, null, 0, 20);

        assertEquals(1, result.size());
        assertEquals(CoOrganizerStatus.PENDING, result.get(0).status());
    }

    @Test
    @TestTransaction
    void getMyInvitations_filterAccepted_returnsAccepted() {
        User creator = persistUser("auth0|fa-creator", "fa-creator@example.com");
        User invited = persistUser("auth0|fa-invited", "fa-invited@example.com");
        Event eventA = persistEvent("fa-event-a", creator, EventStatus.PUBLISHED);
        Event eventB = persistEvent("fa-event-b", creator, EventStatus.PUBLISHED);
        persistInvitation(eventA.id, invited.id, CoOrganizerStatus.PENDING);
        persistInvitation(eventB.id, invited.id, CoOrganizerStatus.ACCEPTED);

        List<CoOrganizerInvitationDTO> result = service.getMyInvitations(
                invited.auth0Id, CoOrganizerStatus.ACCEPTED, 0, 20);

        assertEquals(1, result.size());
        assertEquals(CoOrganizerStatus.ACCEPTED, result.get(0).status());
    }

    @Test
    @TestTransaction
    void getMyInvitations_includesEventDTO() {
        User creator = persistUser("auth0|ev-creator", "ev-creator@example.com");
        User invited = persistUser("auth0|ev-invited", "ev-invited@example.com");
        Event event = persistEvent("ev-event", creator, EventStatus.PUBLISHED);
        persistInvitation(event.id, invited.id, CoOrganizerStatus.PENDING);

        List<CoOrganizerInvitationDTO> result = service.getMyInvitations(invited.auth0Id, null, 0, 20);

        assertEquals(1, result.size());
        assertNotNull(result.get(0).event());
        assertEquals("ev-event", result.get(0).event().title());
    }

    @Test
    @TestTransaction
    void getMyInvitations_pagination() {
        User creator = persistUser("auth0|pg-creator", "pg-creator@example.com");
        User invited = persistUser("auth0|pg-invited", "pg-invited@example.com");
        for (int i = 0; i < 5; i++) {
            Event event = persistEvent("pg-event-" + i, creator, EventStatus.PUBLISHED);
            persistInvitation(event.id, invited.id, CoOrganizerStatus.PENDING);
        }

        List<CoOrganizerInvitationDTO> page0 = service.getMyInvitations(invited.auth0Id, null, 0, 2);
        assertEquals(2, page0.size());
    }

    @Test
    @TestTransaction
    void getMyInvitations_userNotProvisioned_throws404() {
        assertThrows(NotFoundException.class,
                () -> service.getMyInvitations("auth0|ghost-user", null, 0, 20));
    }

    @Test
    @TestTransaction
    void getMyInvitations_emptyResult_returnsEmptyList() {
        User invited = persistUser("auth0|empty-invited", "empty-invited@example.com");

        List<CoOrganizerInvitationDTO> result = service.getMyInvitations(invited.auth0Id, null, 0, 20);
        assertTrue(result.isEmpty());
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private User persistUser(String auth0Id, String email) {
        User user = new User();
        user.auth0Id = auth0Id;
        user.email = email;
        user.profilePublic = false;
        user.createdAt = LocalDateTime.now();
        entityManager.persist(user);
        entityManager.flush();
        return user;
    }

    private Event persistEvent(String title, User creator, EventStatus status) {
        Event event = new Event();
        event.title = title;
        event.location = "Uni Mail";
        event.startDate = LocalDateTime.now().plusDays(1);
        event.endDate = LocalDateTime.now().plusDays(2);
        event.category = EventCategory.ACADEMIC;
        event.status = status;
        event.creator = creator;
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

    private void persistInvitationAt(Long eventId, UUID userId, CoOrganizerStatus status, LocalDateTime invitedAt) {
        EventCoOrganizer e = new EventCoOrganizer();
        e.eventId = eventId;
        e.userId = userId;
        e.status = status;
        // Pose le timestamp déterministe AVANT persist : le @PrePersist null-guard
        // (cf. EventCoOrganizer.prePersist) ne l'écrasera pas, et la colonne
        // @Column(updatable=false) est respectée puisque c'est l'INSERT initial.
        e.invitedAt = invitedAt;
        entityManager.persist(e);
        entityManager.flush();
    }
}
