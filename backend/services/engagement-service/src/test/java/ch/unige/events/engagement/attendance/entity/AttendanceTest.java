package ch.unige.events.engagement.attendance.entity;

import ch.unige.events.shared.domain.enums.AttendanceStatus;
import io.quarkus.test.TestTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.time.Month;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Lifecycle + finder coverage for {@link Attendance}. The static finders
 * ({@code findByEvent}, {@code findAllByUser}, {@code countGroupedByStatus})
 * are exercised against the DevServices PostgreSQL inside
 * {@code @TestTransaction} blocks that roll back at the end of each test.
 */
@QuarkusTest
class AttendanceTest {

    @Inject
    EntityManager em;

    @Test
    void prePersist_setsCreatedAt() {
        Attendance a = new Attendance();
        a.userId = UUID.randomUUID();
        a.eventId = 1L;
        a.status = AttendanceStatus.ATTENDING;
        a.prePersist();
        assertNotNull(a.createdAt);
        assertTrue(a.createdAt.isAfter(LocalDateTime.of(2000, Month.JANUARY, 1, 0, 0).minusSeconds(2)));
    }

    @Test
    void publicFields_areAssignable() {
        UUID user = UUID.randomUUID();
        Attendance a = new Attendance();
        a.userId = user;
        a.eventId = 42L;
        a.status = AttendanceStatus.WAITLISTED;
        a.createdAt = LocalDateTime.of(2026, Month.MAY, 1, 12, 0);
        assertEquals(user, a.userId);
        assertEquals(42L, a.eventId);
        assertEquals(AttendanceStatus.WAITLISTED, a.status);
    }

    @Test
    @TestTransaction
    void findByEvent_returnsRowsForEventId() {
        Long eventId = 100_000L + (long) (Math.random() * 10_000);
        UUID user1 = UUID.randomUUID();
        UUID user2 = UUID.randomUUID();
        persist(user1, eventId, AttendanceStatus.ATTENDING);
        persist(user2, eventId, AttendanceStatus.WAITLISTED);
        // unrelated row for noise
        persist(UUID.randomUUID(), eventId + 1, AttendanceStatus.ATTENDING);

        List<Attendance> rows = Attendance.findByEvent(eventId, 0, 20);
        assertEquals(2, rows.size());
    }

    @Test
    @TestTransaction
    void findAllByUser_returnsRowsForUser() {
        UUID user = UUID.randomUUID();
        Long e1 = 100_001L;
        Long e2 = 100_002L;
        persist(user, e1, AttendanceStatus.ATTENDING);
        persist(user, e2, AttendanceStatus.WAITLISTED);
        persist(UUID.randomUUID(), e1, AttendanceStatus.ATTENDING);

        List<Attendance> rows = Attendance.findAllByUser(user);
        assertEquals(2, rows.size());
    }

    @Test
    @TestTransaction
    void countGroupedByStatus_groupsByEvent() {
        Long e1 = 200_001L;
        Long e2 = 200_002L;
        persist(UUID.randomUUID(), e1, AttendanceStatus.ATTENDING);
        persist(UUID.randomUUID(), e1, AttendanceStatus.ATTENDING);
        persist(UUID.randomUUID(), e1, AttendanceStatus.WAITLISTED);
        persist(UUID.randomUUID(), e2, AttendanceStatus.ATTENDING);

        Map<Long, Long> attending = Attendance.countGroupedByStatus(
                List.of(e1, e2), AttendanceStatus.ATTENDING, em);
        assertEquals(Long.valueOf(2L), attending.get(e1));
        assertEquals(Long.valueOf(1L), attending.get(e2));

        Map<Long, Long> waitlisted = Attendance.countGroupedByStatus(
                List.of(e1, e2), AttendanceStatus.WAITLISTED, em);
        assertEquals(Long.valueOf(1L), waitlisted.get(e1));
        assertTrue(waitlisted.get(e2) == null || waitlisted.get(e2) == 0L);
    }

    @Test
    void countGroupedByStatus_emptyIds_returnsEmptyMap() {
        Map<Long, Long> result = Attendance.countGroupedByStatus(
                List.of(), AttendanceStatus.ATTENDING, em);
        assertTrue(result.isEmpty());
    }

    private static void persist(UUID userId, Long eventId, AttendanceStatus status) {
        Attendance a = new Attendance();
        a.userId = userId;
        a.eventId = eventId;
        a.status = status;
        a.persist();
    }
}
