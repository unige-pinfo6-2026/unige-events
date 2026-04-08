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
@TestProfile(AttendanceServiceCoverageProfile.class)
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

        AttendanceDTO dto = attendanceService.attend("auth0|att1", event.id, AttendanceStatus.INTERESTED);

        assertNotNull(dto.id());
        assertEquals(AttendanceStatus.INTERESTED, dto.status());
        assertEquals(event.id, dto.eventId());
    }

    @Test
    @TestTransaction
    void attend_secondTime_updatesStatus() {
        User user = persistUser("auth0|att2", "att2@example.com");
        Event event = persistEvent("Event B", user, EventStatus.PUBLISHED, null);

        attendanceService.attend("auth0|att2", event.id, AttendanceStatus.INTERESTED);
        AttendanceDTO second = attendanceService.attend("auth0|att2", event.id, AttendanceStatus.ATTENDING);

        assertEquals(AttendanceStatus.ATTENDING, second.status());
        // Doit toujours n'y avoir qu'une seule inscription
        long count = Attendance.count("userId = ?1 and eventId = ?2", user.id, event.id);
        assertEquals(1, count);
    }

    @Test
    @TestTransaction
    void attend_unknownEvent_throwsNotFound() {
        persistUser("auth0|att3", "att3@example.com");

        assertThrows(NotFoundException.class,
                () -> attendanceService.attend("auth0|att3", 999999L, AttendanceStatus.INTERESTED));
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
                () -> attendanceService.attend("auth0|nobody", event.id, AttendanceStatus.INTERESTED));
    }

    @Test
    @TestTransaction
    void attend_capacityReached_throwsConflict() {
        User organizer = persistUser("auth0|org1", "org1@example.com");
        Event event = persistEvent("Full Event", organizer, EventStatus.PUBLISHED, 1);

        // Premier utilisateur prend la seule place
        User user1 = persistUser("auth0|u1", "u1@example.com");
        persistAttendance(user1.id, event.id, AttendanceStatus.ATTENDING);

        // Deuxième utilisateur — 409 attendu
        persistUser("auth0|u2", "u2@example.com");
        WebApplicationException ex = assertThrows(WebApplicationException.class,
                () -> attendanceService.attend("auth0|u2", event.id, AttendanceStatus.ATTENDING));
        assertEquals(409, ex.getResponse().getStatus());
    }

    @Test
    @TestTransaction
    void attend_capacityReached_interestedAllowed() {
        User organizer = persistUser("auth0|org2", "org2@example.com");
        Event event = persistEvent("Full Event 2", organizer, EventStatus.PUBLISHED, 1);

        User user1 = persistUser("auth0|u3", "u3@example.com");
        persistAttendance(user1.id, event.id, AttendanceStatus.ATTENDING);

        // INTERESTED n'est pas bloqué par la capacité
        persistUser("auth0|u4", "u4@example.com");
        assertDoesNotThrow(
                () -> attendanceService.attend("auth0|u4", event.id, AttendanceStatus.INTERESTED));
    }

    @Test
    @TestTransaction
    void attend_underCapacity_attending_succeeds() {
        User organizer = persistUser("auth0|org4", "org4@example.com");
        Event event = persistEvent("Event with room", organizer, EventStatus.PUBLISHED, 5);

        // Aucun participant — la capacité n'est pas atteinte
        User user = persistUser("auth0|u6", "u6@example.com");
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
        persistAttendance(user.id, event.id, AttendanceStatus.INTERESTED);

        List<AttendanceDTO> result = attendanceService.getMyAttendances("auth0|my1");

        assertEquals(1, result.size());
        assertEquals(AttendanceStatus.INTERESTED, result.get(0).status());
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
    // attendingCount / interestedCount — persistance des comptes
    // =========================================================

    @Test
    @TestTransaction
    void attendingCount_incrementsAfterAttend_andDecrementsAfterUnattend() {
        User organizer = persistUser("auth0|cnt-org", "cnt-org@example.com");
        Event event = persistEvent("Counted Event", organizer, EventStatus.PUBLISHED, null);
        persistUser("auth0|cnt-user", "cnt-user@example.com");

        // Avant toute inscription — compteurs à 0
        EventDTO before = eventService.getById(event.id);
        assertEquals(0, before.attendingCount());
        assertEquals(0, before.interestedCount());

        // Inscription ATTENDING
        attendanceService.attend("auth0|cnt-user", event.id, AttendanceStatus.ATTENDING);
        entityManager.flush();

        EventDTO afterAttend = eventService.getById(event.id);
        assertEquals(1, afterAttend.attendingCount());
        assertEquals(0, afterAttend.interestedCount());

        // Désinscription
        attendanceService.removeAttendance("auth0|cnt-user", event.id);
        entityManager.flush();

        EventDTO afterUnattend = eventService.getById(event.id);
        assertEquals(0, afterUnattend.attendingCount());
        assertEquals(0, afterUnattend.interestedCount());
    }

    @Test
    @TestTransaction
    void interestedCount_incrementsAfterAttend() {
        User organizer = persistUser("auth0|int-org", "int-org@example.com");
        Event event = persistEvent("Interested Event", organizer, EventStatus.PUBLISHED, null);
        persistUser("auth0|int-user", "int-user@example.com");

        attendanceService.attend("auth0|int-user", event.id, AttendanceStatus.INTERESTED);
        entityManager.flush();

        EventDTO dto = eventService.getById(event.id);
        assertEquals(0, dto.attendingCount());
        assertEquals(1, dto.interestedCount());
    }

    @Test
    @TestTransaction
    void counts_updateCorrectly_whenStatusSwitches() {
        User organizer = persistUser("auth0|sw-org", "sw-org@example.com");
        Event event = persistEvent("Switch Event", organizer, EventStatus.PUBLISHED, null);
        persistUser("auth0|sw-user", "sw-user@example.com");

        // Start INTERESTED
        attendanceService.attend("auth0|sw-user", event.id, AttendanceStatus.INTERESTED);
        entityManager.flush();
        EventDTO afterInterested = eventService.getById(event.id);
        assertEquals(0, afterInterested.attendingCount());
        assertEquals(1, afterInterested.interestedCount());

        // Switch to ATTENDING
        attendanceService.attend("auth0|sw-user", event.id, AttendanceStatus.ATTENDING);
        entityManager.flush();
        EventDTO afterSwitch = eventService.getById(event.id);
        assertEquals(1, afterSwitch.attendingCount());
        assertEquals(0, afterSwitch.interestedCount());
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
        attendanceService.attend("auth0|mul-u3", event.id, AttendanceStatus.INTERESTED);
        entityManager.flush();

        EventDTO dto = eventService.getById(event.id);
        assertEquals(2, dto.attendingCount());
        assertEquals(1, dto.interestedCount());
    }

    // =========================================================
    // AttendanceDTO.from — couverture du factory method
    // =========================================================

    @Test
    void attendanceDTO_from_mapsAllFields() {
        Attendance a = new Attendance();
        a.id = 1L;
        a.userId = UUID.randomUUID();
        a.eventId = 42L;
        a.status = AttendanceStatus.ATTENDING;
        a.createdAt = LocalDateTime.now();

        AttendanceDTO dto = AttendanceDTO.from(a);

        assertEquals(1L, dto.id());
        assertEquals(a.userId, dto.userId());
        assertEquals(42L, dto.eventId());
        assertEquals(AttendanceStatus.ATTENDING, dto.status());
        assertEquals(a.createdAt, dto.createdAt());
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
