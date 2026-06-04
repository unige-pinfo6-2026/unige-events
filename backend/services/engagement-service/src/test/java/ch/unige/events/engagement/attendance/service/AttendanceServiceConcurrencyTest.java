package ch.unige.events.engagement.attendance.service;

import ch.unige.events.engagement.attendance.entity.Attendance;
import ch.unige.events.engagement.test.JwtTestContext;
import ch.unige.events.engagement.test.JwtTestHelper;
import ch.unige.events.shared.client.EventServiceClient;
import ch.unige.events.shared.client.UserServiceClient;
import ch.unige.events.shared.domain.dto.EventDTO;
import ch.unige.events.shared.domain.enums.AttendanceStatus;
import ch.unige.events.shared.domain.enums.EventStatus;

import io.quarkus.arc.Arc;
import io.quarkus.arc.ManagedContext;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import jakarta.inject.Inject;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.time.Month;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.lenient;

/**
 * BUG-005-bis sentinel — advisory lock + capacity gating.
 *
 * <p>FR — Pin l'invariant que sur un burst concurrent de N attend() avec
 * capacity=K, exactement K placements ATTENDING et N-K placements WAITLISTED
 * sont créés. Sans {@code pg_advisory_xact_lock}, les checks de capacité
 * racent et peuvent persister K+1 ATTENDING.
 *
 * <p>EN — Pins that on a concurrent burst of N attend() with capacity=K,
 * exactly K ATTENDING and N-K WAITLISTED rows are persisted. Without
 * {@code pg_advisory_xact_lock}, the capacity checks race and may persist
 * K+1 ATTENDING.
 *
 * <p>Tests use DevServices Postgres (real DB, real lock); each worker thread
 * activates its own CDI request context and stages a per-thread test
 * identity via {@link JwtTestContext#setForThread}.
 */
@QuarkusTest
@TestSecurity(user = "auth0|concurrency-test")
@SuppressWarnings("java:S100")
class AttendanceServiceConcurrencyTest {

    @Inject AttendanceService attendanceService;

    @InjectMock @RestClient EventServiceClient eventClient;
    @InjectMock @RestClient UserServiceClient userClient;

    private Long eventId;

    @BeforeEach
    void setup() {
        // Use a fresh eventId for each test run to avoid cross-test contamination.
        eventId = System.currentTimeMillis() % 1_000_000L + 90_000_000L;
        EventDTO publishedEventCap3 = new EventDTO(
                eventId, "Cap3", "desc", "loc",
                LocalDateTime.of(2999, Month.JANUARY, 1, 0, 0).plusDays(1), LocalDateTime.of(2999, Month.JANUARY, 1, 0, 0).plusDays(2),
                null, null, null,
                UUID.randomUUID(),
                EventStatus.PUBLISHED, 3, false, false, null,
                0L, 3L, 0L,
                0L, 0L,
                null, null, null,
                List.of(),
                LocalDateTime.of(2025, Month.JANUARY, 1, 12, 0), LocalDateTime.of(2025, Month.JANUARY, 1, 12, 0),
                null, null, null);
        lenient().when(eventClient.getById(anyLong())).thenReturn(publishedEventCap3);
        lenient().when(userClient.getById(any(UUID.class))).thenReturn(null);
    }

    @AfterEach
    void cleanup() {
        // Purge any rows the test inserted; the test runs outside @TestTransaction
        // so cleanup must be explicit.
        final Long evId = eventId;
        QuarkusTransaction.requiringNew().run(() -> {
            Attendance.delete("eventId = ?1", evId);
        });
        JwtTestContext.clear();
    }

    @Test
    @DisplayName("BUG-005-bis: concurrent burst of 10 attend() on capacity=3 yields exactly 3 ATTENDING + 7 WAITLISTED")
    void attend_concurrentBurst_respectsCapacity() throws Exception {
        int n = 10;
        ExecutorService pool = Executors.newFixedThreadPool(n);
        try {
            CountDownLatch start = new CountDownLatch(1);
            CountDownLatch done = new CountDownLatch(n);
            AtomicInteger errors = new AtomicInteger();

            for (int i = 0; i < n; i++) {
                UUID workerUserId = UUID.randomUUID();
                final Long evId = eventId;
                pool.submit(() -> {
                    ManagedContext requestContext = Arc.container().requestContext();
                    requestContext.activate();
                    try {
                        JwtTestContext.setForThread(JwtTestHelper.jwtFor(workerUserId));
                        start.await();
                        attendanceService.attend(
                                "auth0|" + workerUserId, evId, AttendanceStatus.ATTENDING);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        errors.incrementAndGet();
                    } catch (Exception e) {
                        errors.incrementAndGet();
                    } finally {
                        JwtTestContext.clearForThread();
                        requestContext.terminate();
                        done.countDown();
                    }
                });
            }
            start.countDown();
            assertEquals(true, done.await(30, TimeUnit.SECONDS),
                    "All 10 attend() calls must complete within 30s");
            assertEquals(0, errors.get(),
                    "No errors expected on concurrent attend(); got " + errors.get());

            long attending = Attendance.count(
                    "eventId = ?1 AND status = ?2", eventId, AttendanceStatus.ATTENDING);
            long waitlisted = Attendance.count(
                    "eventId = ?1 AND status = ?2", eventId, AttendanceStatus.WAITLISTED);
            assertEquals(3L, attending,
                    "Expected exactly 3 ATTENDING (capacity); got " + attending);
            assertEquals(7L, waitlisted,
                    "Expected exactly 7 WAITLISTED (10 minus capacity); got " + waitlisted);
        } finally {
            pool.shutdown();
        }
    }
}
