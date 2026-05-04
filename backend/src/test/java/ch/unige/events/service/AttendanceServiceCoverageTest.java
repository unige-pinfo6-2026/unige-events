package ch.unige.events.service;

import ch.unige.events.dto.attendance.AttendanceDTO;
import ch.unige.events.dto.event.EventDTO;
import ch.unige.events.entity.Attendance;
import ch.unige.events.entity.AttendanceStatus;
import ch.unige.events.entity.Event;
import ch.unige.events.entity.EventStatus;
import ch.unige.events.entity.User;
import io.quarkus.test.TestTransaction;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.ws.rs.BadRequestException;
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
class AttendanceServiceCoverageTest {

    @Inject
    AttendanceService attendanceService;

    @Inject
    EventService eventService;

    @Inject
    EntityManager entityManager;

    // =========================================================
    // attend — création / upsert
    // =========================================================

    @Test
    @TestTransaction
    void attend_firstTime_createsAttendance() {
        User user = persistUser("auth0|att1", "att1@example.com");
        Event event = persistEvent("Event A", user, EventStatus.PUBLISHED, null);

        AttendanceDTO dto = attendanceService.attend("auth0|att1", event.id, AttendanceStatus.ATTENDING);

        assertNotNull(dto.id());
        assertEquals(AttendanceStatus.ATTENDING, dto.status());
        assertEquals(event.id, dto.eventId());
    }

    @Test
    @TestTransaction
    void attend_secondTime_updatesStatus() {
        User user = persistUser("auth0|att2", "att2@example.com");
        Event event = persistEvent("Event B", user, EventStatus.PUBLISHED, null);

        attendanceService.attend("auth0|att2", event.id, AttendanceStatus.ATTENDING);
        AttendanceDTO second = attendanceService.attend("auth0|att2", event.id, AttendanceStatus.ATTENDING);

        assertEquals(AttendanceStatus.ATTENDING, second.status());
        // La contrainte unique doit toujours n'avoir qu'une seule ligne
        long count = Attendance.count("userId = ?1 and eventId = ?2", user.id, event.id);
        assertEquals(1, count);
    }

    @Test
    @TestTransaction
    void attend_unknownEvent_throwsNotFound() {
        persistUser("auth0|att3", "att3@example.com");

        assertThrows(NotFoundException.class,
                () -> attendanceService.attend("auth0|att3", 999999L, AttendanceStatus.ATTENDING));
    }

    @Test
    @TestTransaction
    void attend_draftEvent_throwsBadRequest() {
        User user = persistUser("auth0|draft1", "draft1@example.com");
        Event event = persistEvent("Draft Event", user, EventStatus.DRAFT, null);

        assertThrows(BadRequestException.class,
                () -> attendanceService.attend("auth0|draft1", event.id, AttendanceStatus.ATTENDING));
    }

    @Test
    @TestTransaction
    void attend_unknownUser_throwsNotFound() {
        User user = persistUser("auth0|owner", "owner@example.com");
        Event event = persistEvent("Event C", user, EventStatus.PUBLISHED, null);

        assertThrows(NotFoundException.class,
                () -> attendanceService.attend("auth0|nobody", event.id, AttendanceStatus.ATTENDING));
    }

    @Test
    @TestTransaction
    void attend_capacityReached_placesInWaitlist() {
        User organizer = persistUser("auth0|org1", "org1@example.com");
        Event event = persistEvent("Full Event", organizer, EventStatus.PUBLISHED, 1);

        // Premier utilisateur prend la seule place
        User user1 = persistUser("auth0|u1", "u1@example.com");
        persistAttendance(user1.id, event.id, AttendanceStatus.ATTENDING);

        // Deuxième utilisateur — doit passer en WAITLISTED (200), plus de 409
        persistUser("auth0|u2", "u2@example.com");
        AttendanceDTO dto = attendanceService.attend("auth0|u2", event.id, AttendanceStatus.ATTENDING);

        assertEquals(AttendanceStatus.WAITLISTED, dto.status());
    }

    @Test
    @TestTransaction
    void attend_underCapacity_attending_succeeds() {
        User organizer = persistUser("auth0|org4", "org4@example.com");
        Event event = persistEvent("Event with room", organizer, EventStatus.PUBLISHED, 5);

        // Aucun participant — la capacité n'est pas atteinte
        persistUser("auth0|u6", "u6@example.com");
        AttendanceDTO dto = attendanceService.attend("auth0|u6", event.id, AttendanceStatus.ATTENDING);

        assertEquals(AttendanceStatus.ATTENDING, dto.status());
    }

    @Test
    @TestTransaction
    void attend_alreadyAttending_resubmitAttending_notBlockedByCapacity() {
        User organizer = persistUser("auth0|org3", "org3@example.com");
        Event event = persistEvent("Full Event 3", organizer, EventStatus.PUBLISHED, 1);

        User user = persistUser("auth0|u5", "u5@example.com");
        persistAttendance(user.id, event.id, AttendanceStatus.ATTENDING);

        // L'utilisateur reconfirme ATTENDING — ne doit pas être bloqué (il n'occupe pas de place supplémentaire)
        assertDoesNotThrow(
                () -> attendanceService.attend("auth0|u5", event.id, AttendanceStatus.ATTENDING));
    }

    // =========================================================
    // removeAttendance
    // =========================================================

    @Test
    @TestTransaction
    void removeAttendance_existingAttendance_deletesIt() {
        User user = persistUser("auth0|rem1", "rem1@example.com");
        Event event = persistEvent("Event D", user, EventStatus.PUBLISHED, null);
        persistAttendance(user.id, event.id, AttendanceStatus.ATTENDING);

        attendanceService.removeAttendance("auth0|rem1", event.id);

        entityManager.flush();
        long count = Attendance.count("userId = ?1 and eventId = ?2", user.id, event.id);
        assertEquals(0, count);
    }

    @Test
    @TestTransaction
    void removeAttendance_notAttending_throwsNotFound() {
        User user = persistUser("auth0|rem2", "rem2@example.com");
        Event event = persistEvent("Event E", user, EventStatus.PUBLISHED, null);

        assertThrows(NotFoundException.class,
                () -> attendanceService.removeAttendance("auth0|rem2", event.id));
    }

    @Test
    @TestTransaction
    void removeAttendance_unknownUser_throwsNotFound() {
        assertThrows(NotFoundException.class,
                () -> attendanceService.removeAttendance("auth0|nobody", 1L));
    }

    // =========================================================
    // getAttendees
    // =========================================================

    @Test
    @TestTransaction
    void getAttendees_byCreator_returnsList() {
        User creator = persistUser("auth0|cr1", "cr1@example.com");
        Event event = persistEvent("Event F", creator, EventStatus.PUBLISHED, null);
        User attendee = persistUser("auth0|at1", "at1@example.com");
        persistAttendance(attendee.id, event.id, AttendanceStatus.ATTENDING);

        List<AttendanceDTO> result = attendanceService.getAttendees("auth0|cr1", event.id, 0, 20);

        assertEquals(1, result.size());
        assertEquals(AttendanceStatus.ATTENDING, result.get(0).status());
    }

    @Test
    @TestTransaction
    void getAttendees_byNonCreator_throwsForbidden() {
        User creator = persistUser("auth0|cr2", "cr2@example.com");
        Event event = persistEvent("Event G", creator, EventStatus.PUBLISHED, null);
        persistUser("auth0|other", "other@example.com");

        assertThrows(ForbiddenException.class,
                () -> attendanceService.getAttendees("auth0|other", event.id, 0, 20));
    }

    @Test
    @TestTransaction
    void getAttendees_unknownEvent_throwsNotFound() {
        persistUser("auth0|cr3", "cr3@example.com");

        assertThrows(NotFoundException.class,
                () -> attendanceService.getAttendees("auth0|cr3", 999999L, 0, 20));
    }

    // =========================================================
    // getMyAttendances
    // =========================================================

    @Test
    @TestTransaction
    void getMyAttendances_withAttendances_returnsList() {
        User user = persistUser("auth0|my1", "my1@example.com");
        Event event = persistEvent("Event H", user, EventStatus.PUBLISHED, null);
        persistAttendance(user.id, event.id, AttendanceStatus.ATTENDING);

        List<AttendanceDTO> result = attendanceService.getMyAttendances("auth0|my1");

        assertEquals(1, result.size());
        assertEquals(AttendanceStatus.ATTENDING, result.get(0).status());
    }

    @Test
    @TestTransaction
    void getMyAttendances_noAttendances_returnsEmpty() {
        persistUser("auth0|my2", "my2@example.com");

        List<AttendanceDTO> result = attendanceService.getMyAttendances("auth0|my2");

        assertTrue(result.isEmpty());
    }

    @Test
    @TestTransaction
    void getMyAttendances_unknownUser_throwsNotFound() {
        assertThrows(NotFoundException.class,
                () -> attendanceService.getMyAttendances("auth0|nobody"));
    }

    // =========================================================
    // attendingCount — persistance des comptes
    // =========================================================

    @Test
    @TestTransaction
    void attendingCount_incrementsAfterAttend_andDecrementsAfterUnattend() {
        User organizer = persistUser("auth0|cnt-org", "cnt-org@example.com");
        Event event = persistEvent("Counted Event", organizer, EventStatus.PUBLISHED, null);
        persistUser("auth0|cnt-user", "cnt-user@example.com");

        // Avant toute inscription — compteur à 0
        EventDTO before = eventService.getById(event.id, null, false);
        assertEquals(0, before.attendingCount());

        // Inscription ATTENDING
        attendanceService.attend("auth0|cnt-user", event.id, AttendanceStatus.ATTENDING);
        entityManager.flush();

        EventDTO afterAttend = eventService.getById(event.id, null, false);
        assertEquals(1, afterAttend.attendingCount());

        // Désinscription
        attendanceService.removeAttendance("auth0|cnt-user", event.id);
        entityManager.flush();

        EventDTO afterUnattend = eventService.getById(event.id, null, false);
        assertEquals(0, afterUnattend.attendingCount());
    }

    @Test
    @TestTransaction
    void counts_multipleUsers_accumulateCorrectly() {
        User organizer = persistUser("auth0|mul-org", "mul-org@example.com");
        Event event = persistEvent("Multi-user Event", organizer, EventStatus.PUBLISHED, null);
        persistUser("auth0|mul-u1", "mul-u1@example.com");
        persistUser("auth0|mul-u2", "mul-u2@example.com");
        persistUser("auth0|mul-u3", "mul-u3@example.com");

        attendanceService.attend("auth0|mul-u1", event.id, AttendanceStatus.ATTENDING);
        attendanceService.attend("auth0|mul-u2", event.id, AttendanceStatus.ATTENDING);
        attendanceService.attend("auth0|mul-u3", event.id, AttendanceStatus.ATTENDING);
        entityManager.flush();

        EventDTO dto = eventService.getById(event.id, null, false);
        assertEquals(3, dto.attendingCount());
    }

    // =========================================================
    // Schema integrity — no INTERESTED rows after startup
    // =========================================================

    @Test
    @TestTransaction
    void noInterestedRowsExistAfterStartup() {
        // The INTERESTED status was removed from AttendanceStatus.
        // The test schema is drop-and-create so no legacy rows survive.
        // A native query verifies the column constraint holds.
        Number count = (Number) entityManager
                .createNativeQuery("SELECT COUNT(*) FROM attendances WHERE status = 'INTERESTED'")
                .getSingleResult();
        assertEquals(0, count.longValue(),
                "No attendance rows with status=INTERESTED should exist after startup");
    }

    // =========================================================
    // AttendanceDTO.from — couverture du factory method
    // =========================================================

    // =========================================================
    // SCRUM-126 — registrationDeadline
    // =========================================================

    @Test
    @TestTransaction
    void attend_afterRegistrationDeadline_throwsConflictRegistrationClosed() {
        User organizer = persistUser("auth0|dl-org", "dl-org@example.com");
        Event event = persistEvent("Deadline passed", organizer, EventStatus.PUBLISHED, null);
        event.registrationDeadline = LocalDateTime.now().minusMinutes(5);
        entityManager.flush();

        persistUser("auth0|dl-user", "dl-user@example.com");

        WebApplicationException ex = assertThrows(WebApplicationException.class,
                () -> attendanceService.attend("auth0|dl-user", event.id, AttendanceStatus.ATTENDING));
        assertEquals(409, ex.getResponse().getStatus());
    }

    @Test
    @TestTransaction
    void attend_beforeRegistrationDeadline_succeeds() {
        User organizer = persistUser("auth0|dl-org2", "dl-org2@example.com");
        Event event = persistEvent("Deadline future", organizer, EventStatus.PUBLISHED, null);
        event.registrationDeadline = LocalDateTime.now().plusDays(2);
        entityManager.flush();

        persistUser("auth0|dl-user2", "dl-user2@example.com");
        AttendanceDTO dto = attendanceService.attend("auth0|dl-user2", event.id, AttendanceStatus.ATTENDING);

        assertEquals(AttendanceStatus.ATTENDING, dto.status());
    }

    @Test
    @TestTransaction
    void attend_withWaitlistedStatusInRequest_throwsBadRequest() {
        User organizer = persistUser("auth0|br-org", "br-org@example.com");
        Event event = persistEvent("BadReq Event", organizer, EventStatus.PUBLISHED, null);
        persistUser("auth0|br-user", "br-user@example.com");

        assertThrows(BadRequestException.class,
                () -> attendanceService.attend("auth0|br-user", event.id, AttendanceStatus.WAITLISTED));
    }

    // =========================================================
    // SCRUM-129 — WAITLISTED + promotion FIFO
    // =========================================================

    @Test
    @TestTransaction
    void attend_capacityExactlyReached_nextUserGoesToWaitlist() {
        User organizer = persistUser("auth0|wl-org", "wl-org@example.com");
        Event event = persistEvent("Waitlist Event", organizer, EventStatus.PUBLISHED, 2);

        User u1 = persistUser("auth0|wl-u1", "wl-u1@example.com");
        User u2 = persistUser("auth0|wl-u2", "wl-u2@example.com");
        persistUser("auth0|wl-u3", "wl-u3@example.com");

        attendanceService.attend("auth0|wl-u1", event.id, AttendanceStatus.ATTENDING);
        attendanceService.attend("auth0|wl-u2", event.id, AttendanceStatus.ATTENDING);
        AttendanceDTO third = attendanceService.attend("auth0|wl-u3", event.id, AttendanceStatus.ATTENDING);

        assertEquals(AttendanceStatus.WAITLISTED, third.status());
        assertEquals(2, Attendance.count("eventId = ?1 and status = ?2", event.id, AttendanceStatus.ATTENDING));
        assertEquals(1, Attendance.count("eventId = ?1 and status = ?2", event.id, AttendanceStatus.WAITLISTED));
        // silence unused warnings
        assertNotNull(u1);
        assertNotNull(u2);
    }

    @Test
    @TestTransaction
    void removeAttendance_promotesFirstWaitlisted_fifo() {
        User organizer = persistUser("auth0|fifo-org", "fifo-org@example.com");
        Event event = persistEvent("FIFO Event", organizer, EventStatus.PUBLISHED, 1);

        User u1 = persistUser("auth0|fifo-u1", "fifo-u1@example.com");
        User u2 = persistUser("auth0|fifo-u2", "fifo-u2@example.com");
        User u3 = persistUser("auth0|fifo-u3", "fifo-u3@example.com");

        // u1 ATTENDING
        attendanceService.attend("auth0|fifo-u1", event.id, AttendanceStatus.ATTENDING);
        // u2 WAITLISTED (créé en premier)
        Attendance waitU2 = persistAttendance(u2.id, event.id, AttendanceStatus.WAITLISTED);
        // Assurer un createdAt plus ancien pour u2
        waitU2.createdAt = LocalDateTime.now().minusMinutes(10);
        entityManager.flush();
        // u3 WAITLISTED (créé en second)
        Attendance waitU3 = persistAttendance(u3.id, event.id, AttendanceStatus.WAITLISTED);
        waitU3.createdAt = LocalDateTime.now().minusMinutes(5);
        entityManager.flush();

        // u1 se désinscrit → u2 doit être promu (plus ancien createdAt)
        attendanceService.removeAttendance("auth0|fifo-u1", event.id);
        entityManager.flush();

        Attendance u2After = Attendance.<Attendance>find(
                "userId = ?1 and eventId = ?2", u2.id, event.id).firstResult();
        Attendance u3After = Attendance.<Attendance>find(
                "userId = ?1 and eventId = ?2", u3.id, event.id).firstResult();

        assertEquals(AttendanceStatus.ATTENDING, u2After.status);
        assertEquals(AttendanceStatus.WAITLISTED, u3After.status);
        // silence unused warning
        assertNotNull(u1);
    }

    @Test
    @TestTransaction
    void removeAttendance_removeWaitlisted_doesNotPromoteAnyone() {
        User organizer = persistUser("auth0|rmw-org", "rmw-org@example.com");
        Event event = persistEvent("Remove waitlisted", organizer, EventStatus.PUBLISHED, 1);

        User u1 = persistUser("auth0|rmw-u1", "rmw-u1@example.com");
        User u2 = persistUser("auth0|rmw-u2", "rmw-u2@example.com");
        User u3 = persistUser("auth0|rmw-u3", "rmw-u3@example.com");

        persistAttendance(u1.id, event.id, AttendanceStatus.ATTENDING);
        persistAttendance(u2.id, event.id, AttendanceStatus.WAITLISTED);
        persistAttendance(u3.id, event.id, AttendanceStatus.WAITLISTED);

        // u2 se désinscrit alors qu'il était seulement WAITLISTED → pas de promotion
        attendanceService.removeAttendance("auth0|rmw-u2", event.id);
        entityManager.flush();

        Attendance u1After = Attendance.<Attendance>find(
                "userId = ?1 and eventId = ?2", u1.id, event.id).firstResult();
        Attendance u3After = Attendance.<Attendance>find(
                "userId = ?1 and eventId = ?2", u3.id, event.id).firstResult();

        assertEquals(AttendanceStatus.ATTENDING, u1After.status);
        assertEquals(AttendanceStatus.WAITLISTED, u3After.status);
    }

    @Test
    @TestTransaction
    void removeAttendance_onCancelledEvent_doesNotPromote() {
        User organizer = persistUser("auth0|cx-org", "cx-org@example.com");
        Event event = persistEvent("Cancelled Event", organizer, EventStatus.PUBLISHED, 1);

        User u1 = persistUser("auth0|cx-u1", "cx-u1@example.com");
        User u2 = persistUser("auth0|cx-u2", "cx-u2@example.com");

        persistAttendance(u1.id, event.id, AttendanceStatus.ATTENDING);
        persistAttendance(u2.id, event.id, AttendanceStatus.WAITLISTED);

        // Annule l'event puis u1 se désinscrit
        event.status = EventStatus.CANCELLED;
        entityManager.flush();
        attendanceService.removeAttendance("auth0|cx-u1", event.id);
        entityManager.flush();

        Attendance u2After = Attendance.<Attendance>find(
                "userId = ?1 and eventId = ?2", u2.id, event.id).firstResult();
        assertEquals(AttendanceStatus.WAITLISTED, u2After.status);
    }

    @Test
    @TestTransaction
    void removeAttendance_onExpiredEvent_doesNotPromote() {
        User organizer = persistUser("auth0|ex-org", "ex-org@example.com");
        Event event = persistEvent("Expired Event", organizer, EventStatus.PUBLISHED, 1);

        User u1 = persistUser("auth0|ex-u1", "ex-u1@example.com");
        User u2 = persistUser("auth0|ex-u2", "ex-u2@example.com");

        persistAttendance(u1.id, event.id, AttendanceStatus.ATTENDING);
        persistAttendance(u2.id, event.id, AttendanceStatus.WAITLISTED);

        event.status = EventStatus.EXPIRED;
        entityManager.flush();
        attendanceService.removeAttendance("auth0|ex-u1", event.id);
        entityManager.flush();

        Attendance u2After = Attendance.<Attendance>find(
                "userId = ?1 and eventId = ?2", u2.id, event.id).firstResult();
        assertEquals(AttendanceStatus.WAITLISTED, u2After.status);
    }

    @Test
    @TestTransaction
    void getById_reflectsAvailableSpotsAndWaitlistedCount() {
        User organizer = persistUser("auth0|spots-org", "spots-org@example.com");
        Event event = persistEvent("Spots Event", organizer, EventStatus.PUBLISHED, 2);

        User u1 = persistUser("auth0|spots-u1", "spots-u1@example.com");
        User u2 = persistUser("auth0|spots-u2", "spots-u2@example.com");
        User u3 = persistUser("auth0|spots-u3", "spots-u3@example.com");
        persistAttendance(u1.id, event.id, AttendanceStatus.ATTENDING);
        persistAttendance(u2.id, event.id, AttendanceStatus.ATTENDING);
        persistAttendance(u3.id, event.id, AttendanceStatus.WAITLISTED);
        entityManager.flush();

        EventDTO dto = eventService.getById(event.id, null, false);

        assertEquals(2, dto.attendingCount());
        assertEquals(0L, dto.availableSpots());
        assertEquals(1, dto.waitlistedCount());
    }

    @Test
    @TestTransaction
    void getById_withoutCapacity_returnsNullAvailableSpots() {
        User organizer = persistUser("auth0|noc-org", "noc-org@example.com");
        Event event = persistEvent("No Capacity Event", organizer, EventStatus.PUBLISHED, null);

        EventDTO dto = eventService.getById(event.id, null, false);

        assertNull(dto.availableSpots());
        assertEquals(0, dto.waitlistedCount());
    }

    @Test
    @TestTransaction
    void attend_alreadyWaitlisted_isIdempotent() {
        User organizer = persistUser("auth0|id-org", "id-org@example.com");
        Event event = persistEvent("Idem Event", organizer, EventStatus.PUBLISHED, 1);

        User u1 = persistUser("auth0|id-u1", "id-u1@example.com");
        User u2 = persistUser("auth0|id-u2", "id-u2@example.com");

        persistAttendance(u1.id, event.id, AttendanceStatus.ATTENDING);
        persistAttendance(u2.id, event.id, AttendanceStatus.WAITLISTED);

        AttendanceDTO second = attendanceService.attend("auth0|id-u2", event.id, AttendanceStatus.ATTENDING);

        assertEquals(AttendanceStatus.WAITLISTED, second.status());
        assertEquals(1, Attendance.count("eventId = ?1 and status = ?2", event.id, AttendanceStatus.WAITLISTED));
        assertEquals(1, Attendance.count("eventId = ?1 and status = ?2", event.id, AttendanceStatus.ATTENDING));
    }

    @Test
    void attendanceDTO_from_mapsAllFields() {
        Attendance a = new Attendance();
        a.id = 1L;
        a.userId = UUID.randomUUID();
        a.eventId = 42L;
        a.status = AttendanceStatus.ATTENDING;
        a.createdAt = LocalDateTime.now();

        User user = new User();
        user.id = a.userId;
        user.displayName = "Alice";
        user.avatarUrl = "https://cdn.example.com/alice.png";

        AttendanceDTO dto = AttendanceDTO.from(a, user);

        assertEquals(1L, dto.id());
        assertEquals(a.userId, dto.userId());
        assertEquals(42L, dto.eventId());
        assertEquals(AttendanceStatus.ATTENDING, dto.status());
        assertEquals(a.createdAt, dto.createdAt());
        assertEquals("Alice", dto.displayName());
        assertEquals("https://cdn.example.com/alice.png", dto.avatarUrl());
    }

    @Test
    void attendanceDTO_from_nullUser_yieldsNullProfileFields() {
        Attendance a = new Attendance();
        a.id = 7L;
        a.userId = UUID.randomUUID();
        a.eventId = 11L;
        a.status = AttendanceStatus.WAITLISTED;
        a.createdAt = LocalDateTime.now();

        AttendanceDTO dto = AttendanceDTO.from(a, null);

        assertEquals(7L, dto.id());
        assertEquals(a.userId, dto.userId());
        assertNull(dto.displayName());
        assertNull(dto.avatarUrl());
    }

    @Test
    @TestTransaction
    void getAttendees_byCreator_exposesPrivateUserDisplayName() {
        // Page stats organisateur : on doit voir le nom des participants même
        // si leur profil est `profilePublic = false`. La route est restreinte
        // au créateur / co-organisateur ACCEPTED, donc exposer ces champs ici
        // est sûr (cf. SCRUM hotfix UUID).
        User creator = persistUser("auth0|priv-c", "priv-c@example.com");
        Event event = persistEvent("Stats with private", creator, EventStatus.PUBLISHED, null);

        User privateUser = persistUser("auth0|priv-att", "priv-att@example.com");
        privateUser.displayName = "Private Attendee";
        privateUser.avatarUrl = "https://cdn.example.com/private.png";
        privateUser.profilePublic = false;
        entityManager.flush();
        persistAttendance(privateUser.id, event.id, AttendanceStatus.ATTENDING);

        List<AttendanceDTO> result = attendanceService.getAttendees("auth0|priv-c", event.id, 0, 20);

        assertEquals(1, result.size());
        assertEquals("Private Attendee", result.get(0).displayName());
        assertEquals("https://cdn.example.com/private.png", result.get(0).avatarUrl());
        assertEquals(privateUser.id, result.get(0).userId());
    }

    @Test
    @TestTransaction
    void getAttendees_orphanAttendance_yieldsNullDisplayName() {
        // Pas de FK contrainte entre Attendance et User : si un user a été supprimé
        // mais que sa ligne d'attendance survit, le DTO doit rester rendable
        // (displayName = null) plutôt que de planter le batch entier.
        User creator = persistUser("auth0|orph-c", "orph-c@example.com");
        Event event = persistEvent("Stats orphan", creator, EventStatus.PUBLISHED, null);
        UUID ghostUserId = UUID.randomUUID();
        persistAttendance(ghostUserId, event.id, AttendanceStatus.ATTENDING);

        List<AttendanceDTO> result = attendanceService.getAttendees("auth0|orph-c", event.id, 0, 20);

        assertEquals(1, result.size());
        assertEquals(ghostUserId, result.get(0).userId());
        assertNull(result.get(0).displayName());
        assertNull(result.get(0).avatarUrl());
    }

    // =========================================================
    // SCRUM-136 — Cascade getAttendees autorise un co-organisateur ACCEPTED
    // =========================================================

    @Test
    @TestTransaction
    void getAttendees_byAcceptedCoOrganizer_succeeds() {
        User creator = persistUser("auth0|cas-att-c", "cas-att-c@example.com");
        User coOrg = persistUser("auth0|cas-att-co", "cas-att-co@example.com");
        Event event = persistEvent("Cascade Att", creator, EventStatus.PUBLISHED, null);
        persistCoOrg(event.id, coOrg.id, ch.unige.events.entity.CoOrganizerStatus.ACCEPTED);
        User attendee = persistUser("auth0|cas-att-x", "cas-att-x@example.com");
        persistAttendance(attendee.id, event.id, AttendanceStatus.ATTENDING);

        List<AttendanceDTO> result = attendanceService.getAttendees(coOrg.auth0Id, event.id, 0, 20);

        assertEquals(1, result.size());
    }

    @Test
    @TestTransaction
    void getAttendees_byNonCoOrganizer_throwsForbidden() {
        User creator = persistUser("auth0|cas-att-nc-c", "cas-att-nc-c@example.com");
        User intruder = persistUser("auth0|cas-att-nc-x", "cas-att-nc-x@example.com");
        Event event = persistEvent("Cascade Att Forbidden", creator, EventStatus.PUBLISHED, null);

        assertThrows(ForbiddenException.class,
                () -> attendanceService.getAttendees(intruder.auth0Id, event.id, 0, 20));
    }

    private void persistCoOrg(Long eventId, UUID userId, ch.unige.events.entity.CoOrganizerStatus status) {
        ch.unige.events.entity.EventCoOrganizer e = new ch.unige.events.entity.EventCoOrganizer();
        e.eventId = eventId;
        e.userId = userId;
        e.status = status;
        entityManager.persist(e);
        entityManager.flush();
    }

    // =========================================================
    // Helpers
    // =========================================================

    private User persistUser(String auth0Id, String email) {
        return ServiceCoverageTestHelper.persistUser(entityManager, auth0Id, email);
    }

    private Event persistEvent(String title, User creator, EventStatus status, Integer capacity) {
        return ServiceCoverageTestHelper.persistEvent(entityManager, title, creator, status, capacity);
    }

    private Attendance persistAttendance(UUID userId, Long eventId, AttendanceStatus status) {
        return ServiceCoverageTestHelper.persistAttendance(entityManager, userId, eventId, status);
    }
}
