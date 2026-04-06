package ch.unige.events.service;

import ch.unige.events.dto.calendar.CalendarTokenResponse;
import ch.unige.events.entity.Event;
import ch.unige.events.entity.EventCategory;
import ch.unige.events.entity.EventStatus;
import ch.unige.events.entity.Attendance;
import ch.unige.events.entity.AttendanceStatus;
import ch.unige.events.entity.User;
import io.quarkus.test.TestTransaction;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.ws.rs.NotFoundException;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
@TestProfile(CalendarServiceCoverageProfile.class)
class CalendarServiceCoverageTest {

    @Inject
    CalendarService calendarService;

    @Inject
    EntityManager entityManager;

    // =========================================================
    // getOrCreateToken
    // =========================================================

    @Test
    @TestTransaction
    void getOrCreateToken_firstCall_generatesToken() {
        persistUser("auth0|cal1", "cal1@example.com");

        CalendarTokenResponse response = calendarService.getOrCreateToken("auth0|cal1");

        assertNotNull(response.calendarToken());
    }

    @Test
    @TestTransaction
    void getOrCreateToken_secondCall_returnsSameToken() {
        persistUser("auth0|cal2", "cal2@example.com");

        CalendarTokenResponse first = calendarService.getOrCreateToken("auth0|cal2");
        CalendarTokenResponse second = calendarService.getOrCreateToken("auth0|cal2");

        assertEquals(first.calendarToken(), second.calendarToken());
    }

    @Test
    @TestTransaction
    void getOrCreateToken_urlsAreWellFormed() {
        persistUser("auth0|cal3", "cal3@example.com");

        CalendarTokenResponse response = calendarService.getOrCreateToken("auth0|cal3");

        assertTrue(response.webcalUrl().startsWith("webcal://"));
        assertTrue(response.httpsUrl().startsWith("https://"));
    }

    @Test
    @TestTransaction
    void getOrCreateToken_unknownUser_throwsNotFound() {
        assertThrows(NotFoundException.class,
                () -> calendarService.getOrCreateToken("auth0|nobody"));
    }

    // =========================================================
    // regenerateToken
    // =========================================================

    @Test
    @TestTransaction
    void regenerateToken_returnsNewToken() {
        persistUser("auth0|cal4", "cal4@example.com");

        CalendarTokenResponse first = calendarService.getOrCreateToken("auth0|cal4");
        CalendarTokenResponse second = calendarService.regenerateToken("auth0|cal4");

        assertNotEquals(first.calendarToken(), second.calendarToken());
    }

    @Test
    @TestTransaction
    void regenerateToken_unknownUser_throwsNotFound() {
        assertThrows(NotFoundException.class,
                () -> calendarService.regenerateToken("auth0|nobody"));
    }

    // =========================================================
    // generateIcsFeed
    // =========================================================

    @Test
    @TestTransaction
    void generateIcsFeed_withAttendance_containsVevent() {
        User user = persistUser("auth0|cal5", "cal5@example.com");
        user.calendarToken = UUID.randomUUID();
        entityManager.flush();

        Event event = persistEvent("Conférence UNIGE", user, EventStatus.PUBLISHED);
        persistAttendance(user.id, event.id, AttendanceStatus.ATTENDING);

        String ics = calendarService.generateIcsFeed(user.calendarToken);

        assertTrue(ics.contains("BEGIN:VEVENT"));
        assertTrue(ics.contains("SUMMARY:Conférence UNIGE"));
    }

    @Test
    @TestTransaction
    void generateIcsFeed_noAttendance_noVevent() {
        User user = persistUser("auth0|cal6", "cal6@example.com");
        user.calendarToken = UUID.randomUUID();
        entityManager.flush();

        String ics = calendarService.generateIcsFeed(user.calendarToken);

        assertTrue(ics.contains("BEGIN:VCALENDAR"));
        assertFalse(ics.contains("BEGIN:VEVENT"));
    }

    @Test
    @TestTransaction
    void generateIcsFeed_draftEventExcluded() {
        User user = persistUser("auth0|cal7", "cal7@example.com");
        user.calendarToken = UUID.randomUUID();
        entityManager.flush();

        Event event = persistEvent("Brouillon", user, EventStatus.DRAFT);
        persistAttendance(user.id, event.id, AttendanceStatus.ATTENDING);

        String ics = calendarService.generateIcsFeed(user.calendarToken);

        assertFalse(ics.contains("BEGIN:VEVENT"));
    }

    @Test
    @TestTransaction
    void generateIcsFeed_interestedStatusIncluded() {
        User user = persistUser("auth0|cal8", "cal8@example.com");
        user.calendarToken = UUID.randomUUID();
        entityManager.flush();

        Event event = persistEvent("Conférence Interested", user, EventStatus.PUBLISHED);
        persistAttendance(user.id, event.id, AttendanceStatus.INTERESTED);

        String ics = calendarService.generateIcsFeed(user.calendarToken);

        assertTrue(ics.contains("BEGIN:VEVENT"));
        assertTrue(ics.contains("SUMMARY:Conférence Interested"));
    }

    @Test
    @TestTransaction
    void generateIcsFeed_unknownToken_throwsNotFound() {
        assertThrows(NotFoundException.class,
                () -> calendarService.generateIcsFeed(UUID.randomUUID()));
    }

    // =========================================================
    // buildIcsContent (package-private, unit tests sans DB)
    // =========================================================

    @Test
    void buildIcsContent_emptyList_returnsValidCalendar() {
        String ics = calendarService.buildIcsContent(List.of());

        assertTrue(ics.contains("BEGIN:VCALENDAR"));
        assertTrue(ics.contains("END:VCALENDAR"));
        assertFalse(ics.contains("BEGIN:VEVENT"));
    }

    @Test
    void escapeIcs_specialChars_areEscaped() {
        String result = calendarService.escapeIcs("a,b;c\nd");

        assertEquals("a\\,b\\;c\\nd", result);
    }

    // =========================================================
    // Helpers
    // =========================================================

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

    private Attendance persistAttendance(UUID userId, Long eventId, AttendanceStatus status) {
        Attendance a = new Attendance();
        a.userId = userId;
        a.eventId = eventId;
        a.status = status;
        entityManager.persist(a);
        entityManager.flush();
        return a;
    }
}
