package ch.unige.events.user.calendar.service;

import ch.unige.events.shared.client.EngagementServiceClient;
import ch.unige.events.shared.client.EventServiceClient;
import ch.unige.events.shared.domain.dto.AttendanceDTO;
import ch.unige.events.shared.domain.dto.EventDTO;
import ch.unige.events.shared.domain.enums.AttendanceStatus;
import ch.unige.events.shared.domain.enums.EventStatus;
import ch.unige.events.user.calendar.dto.CalendarTokenResponse;
import ch.unige.events.user.entity.User;

import io.quarkus.test.InjectMock;
import io.quarkus.test.TestTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.ws.rs.NotFoundException;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * Coverage suite for {@link CalendarService}. The user record is
 * persisted to the dev-services Postgres ; cross-service REST clients
 * are mocked via {@link InjectMock} + {@link RestClient}.
 */
@QuarkusTest
class CalendarServiceTest {

    @Inject CalendarService calendarService;
    @Inject EntityManager entityManager;

    @InjectMock @RestClient EngagementServiceClient engagementClient;
    @InjectMock @RestClient EventServiceClient eventClient;

    @Test
    @TestTransaction
    void getOrCreateToken_firstCall_generatesToken() {
        persistUser("auth0|cs-1", "cs-1@example.com");

        CalendarTokenResponse response = calendarService.getOrCreateToken("auth0|cs-1");

        assertNotNull(response.calendarToken());
        assertTrue(response.webcalUrl().startsWith("webcal://"));
        assertTrue(response.httpsUrl().startsWith("http"));
    }

    @Test
    @TestTransaction
    void getOrCreateToken_secondCall_returnsSameToken() {
        persistUser("auth0|cs-2", "cs-2@example.com");

        CalendarTokenResponse first = calendarService.getOrCreateToken("auth0|cs-2");
        CalendarTokenResponse second = calendarService.getOrCreateToken("auth0|cs-2");

        assertEquals(first.calendarToken(), second.calendarToken());
    }

    @Test
    @TestTransaction
    void getOrCreateToken_unknownUser_throwsNotFound() {
        assertThrows(NotFoundException.class,
                () -> calendarService.getOrCreateToken("auth0|cs-no-user"));
    }

    @Test
    @TestTransaction
    void regenerateToken_returnsNewToken() {
        persistUser("auth0|cs-3", "cs-3@example.com");

        CalendarTokenResponse first = calendarService.getOrCreateToken("auth0|cs-3");
        CalendarTokenResponse second = calendarService.regenerateToken("auth0|cs-3");

        assertNotEquals(first.calendarToken(), second.calendarToken());
    }

    @Test
    @TestTransaction
    void regenerateToken_unknownUser_throwsNotFound() {
        assertThrows(NotFoundException.class,
                () -> calendarService.regenerateToken("auth0|cs-no-user"));
    }

    @Test
    @TestTransaction
    void generateIcsFeed_unknownToken_throwsNotFound() {
        assertThrows(NotFoundException.class,
                () -> calendarService.generateIcsFeed(UUID.randomUUID()));
    }

    @Test
    @TestTransaction
    void generateIcsFeed_noAttendances_returnsEmptyCalendar() {
        User user = persistUser("auth0|cs-empty", "cs-empty@example.com");
        UUID token = UUID.randomUUID();
        user.calendarToken = token;
        entityManager.flush();

        when(engagementClient.getUserAttendances(eq(user.id), anyString()))
                .thenReturn(List.of());

        String ics = calendarService.generateIcsFeed(token);

        assertTrue(ics.contains("BEGIN:VCALENDAR"));
        assertFalse(ics.contains("BEGIN:VEVENT"));
    }

    @Test
    @TestTransaction
    void generateIcsFeed_attendingEventIdsAllNull_returnsEmptyCalendar() {
        User user = persistUser("auth0|cs-null-ids", "cs-null-ids@example.com");
        UUID token = UUID.randomUUID();
        user.calendarToken = token;
        entityManager.flush();

        AttendanceDTO orphan = new AttendanceDTO(1L, user.id, null, AttendanceStatus.ATTENDING,
                LocalDateTime.now(), "U", null);
        when(engagementClient.getUserAttendances(eq(user.id), anyString()))
                .thenReturn(List.of(orphan));

        String ics = calendarService.generateIcsFeed(token);
        assertFalse(ics.contains("BEGIN:VEVENT"), "null eventIds must yield no VEVENT");
    }

    @Test
    @TestTransaction
    void generateIcsFeed_eventsClientReturnsNull_returnsEmptyCalendar() {
        User user = persistUser("auth0|cs-null-events", "cs-null-events@example.com");
        UUID token = UUID.randomUUID();
        user.calendarToken = token;
        entityManager.flush();

        AttendanceDTO att = new AttendanceDTO(1L, user.id, 42L, AttendanceStatus.ATTENDING,
                LocalDateTime.now(), "U", null);
        when(engagementClient.getUserAttendances(eq(user.id), anyString()))
                .thenReturn(List.of(att));
        when(eventClient.findByIds(anyList(), eq("PUBLISHED"))).thenReturn(null);

        String ics = calendarService.generateIcsFeed(token);
        assertTrue(ics.contains("BEGIN:VCALENDAR"));
        assertFalse(ics.contains("BEGIN:VEVENT"));
    }

    @Test
    @TestTransaction
    void generateIcsFeed_publishedEvent_isProjectedToVevent() {
        User user = persistUser("auth0|cs-event", "cs-event@example.com");
        UUID token = UUID.randomUUID();
        user.calendarToken = token;
        entityManager.flush();

        AttendanceDTO att = new AttendanceDTO(1L, user.id, 99L, AttendanceStatus.ATTENDING,
                LocalDateTime.now(), "U", null);
        when(engagementClient.getUserAttendances(eq(user.id), anyString()))
                .thenReturn(List.of(att));

        EventDTO ev = new EventDTO(99L, "Conf", "desc", "loc",
                LocalDateTime.of(2026, 6, 15, 7, 0),
                LocalDateTime.of(2026, 6, 15, 9, 0),
                null, null, null, null,
                EventStatus.PUBLISHED, null, false, false, null,
                0L, null, 0L, 0L, 0L,
                null, null, null,
                List.of(),
                LocalDateTime.now(), LocalDateTime.now(),
                null, null, null);
        when(eventClient.findByIds(anyList(), eq("PUBLISHED"))).thenReturn(List.of(ev));

        String ics = calendarService.generateIcsFeed(token);

        assertTrue(ics.contains("BEGIN:VEVENT"));
        assertTrue(ics.contains("SUMMARY:Conf"));
        assertTrue(ics.contains("UID:99@unige-events"));
    }

    @Test
    @TestTransaction
    void generateIcsFeed_attendancesNullCollection_returnsEmptyCalendar() {
        User user = persistUser("auth0|cs-attnull", "cs-attnull@example.com");
        UUID token = UUID.randomUUID();
        user.calendarToken = token;
        entityManager.flush();

        when(engagementClient.getUserAttendances(eq(user.id), anyString()))
                .thenReturn(null);

        String ics = calendarService.generateIcsFeed(token);
        assertTrue(ics.contains("BEGIN:VCALENDAR"));
        assertFalse(ics.contains("BEGIN:VEVENT"));
    }

    private User persistUser(String auth0Id, String email) {
        User user = new User();
        user.auth0Id = auth0Id;
        user.email = email;
        user.profilePublic = true;
        user.createdAt = LocalDateTime.now();
        entityManager.persist(user);
        entityManager.flush();
        return user;
    }
}
