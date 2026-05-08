package ch.unige.events.service;

import ch.unige.events.dto.stats.EventStatsDTO;
import ch.unige.events.entity.Attendance;
import ch.unige.events.entity.AttendanceStatus;
import ch.unige.events.entity.Event;
import ch.unige.events.entity.EventCategory;
import ch.unige.events.entity.EventStatus;
import ch.unige.events.entity.EventView;
import ch.unige.events.entity.Favorite;
import ch.unige.events.entity.User;
import io.quarkus.test.TestTransaction;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.ws.rs.ForbiddenException;
import jakarta.ws.rs.NotFoundException;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
@TestProfile(ShareServiceCoverageProfile.class)
class EventStatsServiceCoverageTest {

    @Inject
    EventStatsService eventStatsService;

    @Inject
    EntityManager entityManager;

    // -------------------------------------------------------------------------
    // Happy path — tous les compteurs
    // -------------------------------------------------------------------------

    @Test
    @TestTransaction
    void getStats_asCreator_returnsCorrectCounts() {
        User creator = persistUser("auth0|stats-creator", "stats-creator@example.com");
        Event event = persistEvent("Stats Event", creator);

        User attendee1 = persistUser("auth0|stats-a1", "stats-a1@example.com");
        User attendee2 = persistUser("auth0|stats-a2", "stats-a2@example.com");
        persistAttendance(attendee1.id, event.id, AttendanceStatus.ATTENDING);
        persistAttendance(attendee2.id, event.id, AttendanceStatus.ATTENDING);
        // WAITLISTED ne doit pas être compté dans attendingCount
        User waiter = persistUser("auth0|stats-w1", "stats-w1@example.com");
        persistAttendance(waiter.id, event.id, AttendanceStatus.WAITLISTED);

        persistFavorite(attendee1.id, event.id);
        persistFavorite(attendee2.id, event.id);
        persistFavorite(waiter.id, event.id);

        persistEventView(event.id, attendee1.id);
        persistEventView(event.id, attendee2.id);

        entityManager.flush();

        EventStatsDTO dto = eventStatsService.getStats("auth0|stats-creator", event.id);

        assertEquals(2L, dto.attendingCount());
        assertEquals(3L, dto.interestedCount());
        assertEquals(2L, dto.viewCount());
    }

    @Test
    @TestTransaction
    void getStats_zeroCounts_returnsAllZero() {
        User creator = persistUser("auth0|stats-zero", "stats-zero@example.com");
        Event event = persistEvent("Empty Event", creator);
        entityManager.flush();

        EventStatsDTO dto = eventStatsService.getStats("auth0|stats-zero", event.id);

        assertEquals(0L, dto.attendingCount());
        assertEquals(0L, dto.interestedCount());
        assertEquals(0L, dto.viewCount());
    }

    // -------------------------------------------------------------------------
    // Cas d'erreur — NotFoundException
    // -------------------------------------------------------------------------

    @Test
    @TestTransaction
    void getStats_unknownEvent_throwsNotFound() {
        persistUser("auth0|stats-nf", "stats-nf@example.com");

        assertThrows(NotFoundException.class,
                () -> eventStatsService.getStats("auth0|stats-nf", 999999L));
    }

    @Test
    @TestTransaction
    void getStats_unknownUser_throwsNotFound() {
        User creator = persistUser("auth0|stats-owner", "stats-owner@example.com");
        Event event = persistEvent("Event", creator);
        entityManager.flush();

        assertThrows(NotFoundException.class,
                () -> eventStatsService.getStats("auth0|ghost-user", event.id));
    }

    // -------------------------------------------------------------------------
    // Cas d'erreur — ForbiddenException (deux branches du if)
    // -------------------------------------------------------------------------

    @Test
    @TestTransaction
    void getStats_eventWithNullCreator_throwsForbidden() {
        // Branche : event.creator == null
        persistUser("auth0|stats-noowner", "stats-noowner@example.com");
        Event event = persistEvent("No Creator Event", null);
        entityManager.flush();

        assertThrows(ForbiddenException.class,
                () -> eventStatsService.getStats("auth0|stats-noowner", event.id));
    }

    @Test
    @TestTransaction
    void getStats_differentUser_throwsForbidden() {
        // Branche : event.creator.id != caller.id
        User creator = persistUser("auth0|stats-real-owner", "stats-real-owner@example.com");
        persistUser("auth0|stats-intruder", "stats-intruder@example.com");
        Event event = persistEvent("Owner Event", creator);
        entityManager.flush();

        assertThrows(ForbiddenException.class,
                () -> eventStatsService.getStats("auth0|stats-intruder", event.id));
    }

    // -------------------------------------------------------------------------
    // SCRUM-136 — Cascade getStats autorise un co-organisateur ACCEPTED
    // -------------------------------------------------------------------------

    @Test
    @TestTransaction
    void getStats_byAcceptedCoOrganizer_succeeds() {
        User creator = persistUser("auth0|stats-cas-c", "stats-cas-c@example.com");
        User coOrg = persistUser("auth0|stats-cas-co", "stats-cas-co@example.com");
        Event event = persistEvent("Cascade Stats", creator);
        persistCoOrg(event.id, coOrg.id, ch.unige.events.entity.CoOrganizerStatus.ACCEPTED);
        entityManager.flush();

        EventStatsDTO dto = eventStatsService.getStats(coOrg.auth0Id, event.id);

        assertEquals(0L, dto.attendingCount());
    }

    @Test
    @TestTransaction
    void getStats_byPendingCoOrganizer_throwsForbidden() {
        User creator = persistUser("auth0|stats-pen-c", "stats-pen-c@example.com");
        User coOrg = persistUser("auth0|stats-pen-co", "stats-pen-co@example.com");
        Event event = persistEvent("Stats Pending", creator);
        persistCoOrg(event.id, coOrg.id, ch.unige.events.entity.CoOrganizerStatus.PENDING);
        entityManager.flush();

        assertThrows(ForbiddenException.class,
                () -> eventStatsService.getStats(coOrg.auth0Id, event.id));
    }

    private void persistCoOrg(Long eventId, UUID userId, ch.unige.events.entity.CoOrganizerStatus status) {
        ch.unige.events.entity.EventCoOrganizer e = new ch.unige.events.entity.EventCoOrganizer();
        e.eventId = eventId;
        e.userId = userId;
        e.status = status;
        entityManager.persist(e);
        entityManager.flush();
    }

    // -------------------------------------------------------------------------
    // EventView.prePersist() — couverture entité
    // -------------------------------------------------------------------------

    @Test
    @TestTransaction
    void eventView_prePersist_setsViewedAt() {
        User user = persistUser("auth0|stats-vp", "stats-vp@example.com");
        Event event = persistEvent("View Event", user);
        entityManager.flush();

        EventView view = new EventView();
        view.eventId = event.id;
        view.userId = user.id;
        entityManager.persist(view);
        entityManager.flush();

        assertNotNull(view.viewedAt);
        assertTrue(view.viewedAt.isBefore(LocalDateTime.now().plusSeconds(1)));
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

    private Event persistEvent(String title, User creator) {
        Event event = new Event();
        event.title = title;
        event.location = "Uni Mail";
        event.startDate = LocalDateTime.now().plusDays(1);
        event.endDate = LocalDateTime.now().plusDays(2);
        event.category = EventCategory.ACADEMIC;
        event.status = EventStatus.PUBLISHED;
        event.creator = creator;
        entityManager.persist(event);
        entityManager.flush();
        return event;
    }

    private void persistAttendance(UUID userId, Long eventId, AttendanceStatus status) {
        Attendance a = new Attendance();
        a.userId = userId;
        a.eventId = eventId;
        a.status = status;
        entityManager.persist(a);
        entityManager.flush();
    }

    private void persistFavorite(UUID userId, Long eventId) {
        Favorite f = new Favorite();
        f.userId = userId;
        f.eventId = eventId;
        entityManager.persist(f);
        entityManager.flush();
    }

    private void persistEventView(Long eventId, UUID userId) {
        EventView view = new EventView();
        view.eventId = eventId;
        view.userId = userId;
        entityManager.persist(view);
        entityManager.flush();
    }
}
