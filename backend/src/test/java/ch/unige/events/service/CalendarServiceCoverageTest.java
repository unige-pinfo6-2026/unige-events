package ch.unige.events.service;

import ch.unige.events.dto.calendar.CalendarTokenResponse;
import ch.unige.events.entity.Attendance;
import ch.unige.events.entity.AttendanceStatus;
import ch.unige.events.entity.Event;
import ch.unige.events.entity.EventStatus;
import ch.unige.events.entity.User;
import ch.unige.events.util.IcsBuilder;
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
@TestProfile(SharedServiceCoverageProfile.class)
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
        assertTrue(response.httpsUrl().startsWith("http"));
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
    // buildIcsContent / foldLine / escapeIcs (via IcsBuilder)
    // =========================================================

    @Test
    void buildIcsContent_eventWithDescription_includesDescriptionLine() {
        Event event = buildInMemoryEvent("Conférence", "Uni Mail", "Une super description");

        String ics = IcsBuilder.buildIcsContent(List.of(event), "http://localhost:5173");

        assertTrue(ics.contains("DESCRIPTION:Une super description"));
    }

    @Test
    void buildIcsContent_eventWithoutLocation_noLocationLine() {
        Event event = buildInMemoryEvent("Conférence sans lieu", null, null);

        String ics = IcsBuilder.buildIcsContent(List.of(event), "http://localhost:5173");

        assertTrue(ics.contains("BEGIN:VEVENT"));
        assertFalse(ics.contains("LOCATION:"));
    }

    private Event buildInMemoryEvent(String title, String location, String description) {
        Event event = new Event();
        event.id = 99L;
        event.title = title;
        event.location = location;
        event.description = description;
        event.startDate = LocalDateTime.now().plusDays(1);
        event.endDate = LocalDateTime.now().plusDays(2);
        return event;
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

    private Attendance persistAttendance(UUID userId, Long eventId, AttendanceStatus status) {
        return ServiceCoverageTestHelper.persistAttendance(entityManager, userId, eventId, status);
    }
}
