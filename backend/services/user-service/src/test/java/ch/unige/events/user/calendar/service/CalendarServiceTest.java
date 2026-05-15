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
import jakarta.persistence.OptimisticLockException;
import jakarta.ws.rs.NotFoundException;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

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

    /**
     * SCRUM-169 — simulate the ProfileEditPage race: a concurrent PUT
     * /users/me has bumped {@code users.version} between our read and
     * flush, so the first attempt's commit fails with
     * {@link OptimisticLockException}. The retry loop must recover and
     * return a valid token without surfacing the exception to the caller.
     *
     * <p>The test uses {@link CalendarService#withOptimisticLockRetry}
     * directly with a controlled supplier — we cannot inject a transient
     * version conflict via Mockito on Panache static methods, but the
     * retry contract is the same regardless of where the exception
     * originates (commit-time flush, manual flush, etc.) since the loop
     * unwraps via {@code isOptimisticLockConflict}.
     */
    @Test
    void withOptimisticLockRetry_recoversFromTransientConflict() {
        AtomicInteger attempts = new AtomicInteger();
        UUID expected = UUID.randomUUID();

        CalendarTokenResponse stub = new CalendarTokenResponse(expected,
                "webcal://example/" + expected + ".ics",
                "https://example/" + expected + ".ics");

        CalendarTokenResponse result = calendarService.withOptimisticLockRetry(
                "test", () -> {
                    if (attempts.incrementAndGet() == 1) {
                        throw new OptimisticLockException("simulated race on users.version");
                    }
                    return stub;
                });

        assertEquals(expected, result.calendarToken());
        assertEquals(2, attempts.get(),
                "retry loop must invoke the supplier twice: once failing, once succeeding");
    }

    /**
     * SCRUM-169 — bound the retry. After
     * {@link CalendarService#MAX_TOKEN_WRITE_ATTEMPTS} consecutive
     * {@link OptimisticLockException}s the loop gives up and re-throws
     * the last failure (a useful signal for ops dashboards), rather than
     * spinning forever.
     */
    @Test
    void withOptimisticLockRetry_givesUpAfterMaxAttempts() {
        AtomicInteger attempts = new AtomicInteger();

        OptimisticLockException thrown = assertThrows(OptimisticLockException.class,
                () -> calendarService.withOptimisticLockRetry("test", () -> {
                    attempts.incrementAndGet();
                    throw new OptimisticLockException("persistent contention");
                }));

        assertEquals(CalendarService.MAX_TOKEN_WRITE_ATTEMPTS, attempts.get(),
                "loop must stop at MAX_TOKEN_WRITE_ATTEMPTS");
        assertTrue(thrown.getMessage().contains("persistent contention"),
                "must surface the last underlying exception, not a wrapped placeholder");
    }

    /**
     * SCRUM-169 — non-retryable failures (e.g. the user doesn't exist)
     * propagate immediately without burning the attempt budget. Important
     * so a missing user still returns a clean 404, not a 500 after three
     * slow retries.
     */
    @Test
    void withOptimisticLockRetry_doesNotRetryNotFound() {
        AtomicInteger attempts = new AtomicInteger();

        assertThrows(NotFoundException.class,
                () -> calendarService.withOptimisticLockRetry("test", () -> {
                    attempts.incrementAndGet();
                    throw new NotFoundException("missing user");
                }));

        assertEquals(1, attempts.get(),
                "NotFoundException must short-circuit the retry loop");
    }

    /**
     * SCRUM-169 — unrelated runtime exceptions (NPE, IllegalState, …) are
     * not OptimisticLock conflicts and must propagate on the first
     * attempt — masking them as transient would hide real bugs.
     */
    @Test
    void withOptimisticLockRetry_doesNotRetryUnrelatedException() {
        AtomicInteger attempts = new AtomicInteger();

        assertThrows(IllegalStateException.class,
                () -> calendarService.withOptimisticLockRetry("test", () -> {
                    attempts.incrementAndGet();
                    throw new IllegalStateException("unrelated bug");
                }));

        assertEquals(1, attempts.get(),
                "non-OptimisticLock RuntimeException must short-circuit the retry loop");
    }

    private User persistUser(String auth0Id, String email) {
        User user = new User();
        user.auth0Id = auth0Id;
        user.email = email;
        // SCRUM-169 — derive a unique username so the entity satisfies the
        // NOT NULL + UNIQUE constraint. The local helper mirrors the pattern
        // used in TestFixtures and UserServiceTest.
        user.username = ch.unige.events.user.service.UsernameGenerator.resolveAvailable(
                ch.unige.events.user.service.UsernameGenerator.slugify(null,
                        email.contains("@") ? email.substring(0, email.indexOf('@')) : email,
                        null),
                candidate -> ch.unige.events.user.entity.User.findByUsername(candidate).isPresent()
        );
        user.profilePublic = true;
        user.createdAt = LocalDateTime.now();
        entityManager.persist(user);
        entityManager.flush();
        return user;
    }
}
