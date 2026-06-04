package ch.unige.events.engagement.attendance.service;

import ch.unige.events.engagement.attendance.entity.Attendance;
import ch.unige.events.shared.client.EventServiceClient;
import ch.unige.events.shared.client.UserServiceClient;
import ch.unige.events.shared.domain.dto.AttendanceDTO;
import ch.unige.events.shared.domain.dto.EventDTO;
import ch.unige.events.shared.domain.enums.AttendanceStatus;
import ch.unige.events.shared.domain.enums.EventStatus;
import ch.unige.events.shared.domain.projections.CallerIdentity;

import io.quarkus.panache.mock.PanacheMock;
import io.quarkus.security.identity.SecurityIdentity;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import jakarta.ws.rs.NotFoundException;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Coverage suite for the {@code @RestClient}-driven {@code catch} branches of
 * {@link AttendanceService} that the {@code @InjectMock}-based
 * {@link AttendanceServiceTest} cannot reach.
 *
 * <p>The cross-service clients are wrapped by MicroProfile Fault Tolerance
 * ({@code @Fallback}). An {@code @InjectMock @RestClient} whose stub throws is
 * intercepted by the fallback, which returns the degraded default — so the
 * exception never propagates to the service's own {@code catch}. To exercise
 * {@code safeGetUser} and {@code fetchAttendeeProjections} error handling we
 * hand-wire the service with PLAIN Mockito mocks (no Fault Tolerance proxy) so
 * the {@code thenThrow} is real. The class stays {@code @QuarkusTest} so the
 * executed service bytecode is captured by quarkus-jacoco. Panache statics are
 * stubbed via {@link PanacheMock}; none of the targeted methods persist, so no
 * {@code @TestTransaction} is needed.
 *
 * <p>Model: user-service {@code UserServiceExceptionPathsTest}.
 */
@QuarkusTest
class AttendanceServiceSafeGetUserTest {

    private AttendanceService newService() {
        AttendanceService svc = new AttendanceService();
        svc.userClient = mock(UserServiceClient.class);
        svc.eventClient = mock(EventServiceClient.class);
        svc.callerIdentity = mock(CallerIdentity.class);
        svc.identity = mock(SecurityIdentity.class);
        // Native advisory-lock query (SELECT pg_advisory_xact_lock) — fluent
        // no-op so attend()/removeAttendance() can reach their userId guards.
        EntityManager em = mock(EntityManager.class);
        Query lockQuery = mock(Query.class);
        when(em.createNativeQuery(anyString())).thenReturn(lockQuery);
        when(lockQuery.setParameter(anyInt(), any())).thenReturn(lockQuery);
        when(lockQuery.getSingleResult()).thenReturn(1);
        svc.entityManager = em;
        return svc;
    }

    private static Attendance row(UUID userId, Long eventId, AttendanceStatus status) {
        Attendance a = new Attendance();
        a.id = 1L;
        a.userId = userId;
        a.eventId = eventId;
        a.status = status;
        a.createdAt = LocalDateTime.of(2025, 1, 1, 12, 0);
        return a;
    }

    // ── safeGetUser — NotFoundException catch (lines 447-450) ──────────────

    @Test
    void getMyAttendances_userServiceNotFound_returnsRowWithNullUser() {
        AttendanceService svc = newService();
        UUID userId = UUID.randomUUID();
        when(svc.callerIdentity.getUuid()).thenReturn(userId);
        when(svc.userClient.getById(userId))
                .thenThrow(new NotFoundException("hard-deleted"));

        PanacheMock.mock(Attendance.class);
        when(Attendance.findAllByUser(userId))
                .thenReturn(List.of(row(userId, 7L, AttendanceStatus.ATTENDING)));

        List<AttendanceDTO> dtos = svc.getMyAttendances("auth0|x");

        assertEquals(1, dtos.size());
        assertNull(dtos.get(0).displayName(),
                "NotFound enrichment must yield a null-user DTO");
    }

    // ── safeGetUser — RuntimeException catch (lines 451-455) ───────────────

    @Test
    void getMyAttendances_userServiceRuntimeFailure_returnsRowWithNullUser() {
        AttendanceService svc = newService();
        UUID userId = UUID.randomUUID();
        when(svc.callerIdentity.getUuid()).thenReturn(userId);
        when(svc.userClient.getById(userId))
                .thenThrow(new RuntimeException("CB open / timeout"));

        PanacheMock.mock(Attendance.class);
        when(Attendance.findAllByUser(userId))
                .thenReturn(List.of(row(userId, 9L, AttendanceStatus.WAITLISTED)));

        List<AttendanceDTO> dtos = svc.getMyAttendances("auth0|x");

        assertEquals(1, dtos.size());
        assertNull(dtos.get(0).displayName(),
                "infra-failure enrichment must yield a null-user DTO");
    }

    // ── fetchAttendeeProjections — RuntimeException catch (lines 282-284) ──

    @Test
    void getAttendees_projectionFailure_anonymizesEveryRow() {
        AttendanceService svc = newService();
        UUID callerUuid = UUID.randomUUID();
        UUID attendeeUuid = UUID.randomUUID();
        when(svc.callerIdentity.getUuid()).thenReturn(callerUuid);
        when(svc.identity.hasRole(any())).thenReturn(false);

        // Visible event, caller is neither creator nor co-organizer → non-organizer
        // view, so a failed projection fetch anonymizes every row.
        EventDTO event = new EventDTO(5L, "Title", null, "loc",
                LocalDateTime.of(2999, 1, 1, 0, 0).plusDays(1), LocalDateTime.of(2999, 1, 1, 0, 0).plusDays(2),
                null, null, null, UUID.randomUUID(),
                EventStatus.PUBLISHED, null, false, false, null,
                0L, null, 0L, 0L, 0L,
                null, null, null, List.of(),
                LocalDateTime.of(2025, 1, 1, 12, 0), LocalDateTime.of(2025, 1, 1, 12, 0),
                null, null, Boolean.FALSE);
        when(svc.eventClient.getByIdWithCoOrgCheck(5L, callerUuid)).thenReturn(event);
        when(svc.userClient.getAttendeeProjections(anyList()))
                .thenThrow(new RuntimeException("user-service down"));

        PanacheMock.mock(Attendance.class);
        when(Attendance.findByEvent(5L, 0, 20))
                .thenReturn(List.of(row(attendeeUuid, 5L, AttendanceStatus.ATTENDING)));

        List<AttendanceDTO> dtos = svc.getAttendees("auth0|x", 5L, 0, 20);

        assertEquals(1, dtos.size());
        assertNull(dtos.get(0).userId(),
                "degraded projection fetch must anonymize the row (null userId)");
        assertNull(dtos.get(0).displayName());
    }

    // ── isTargetProfilePublic via getUserParticipationEvents — same catch ──

    @Test
    void getUserParticipationEvents_projectionFailure_treatsTargetAsPrivate() {
        AttendanceService svc = newService();
        UUID callerUuid = UUID.randomUUID();
        UUID targetUuid = UUID.randomUUID();
        // Non-self caller — gate runs isTargetProfilePublic → fetchAttendeeProjections.
        when(svc.callerIdentity.getUuid()).thenReturn(callerUuid);
        when(svc.userClient.getAttendeeProjections(anyList()))
                .thenThrow(new RuntimeException("user-service down"));

        List<EventDTO> events = svc.getUserParticipationEvents(targetUuid, null);

        assertTrue(events.isEmpty(),
                "fail-closed: indeterminate profile yields an empty participation list");
    }

    // ── safeGetUser null-guard (lines 442-443) via getMyAttendances ────────

    @Test
    void getMyAttendances_unprovisionedCaller_returnsEmpty() {
        AttendanceService svc = newService();
        when(svc.callerIdentity.getUuid()).thenReturn(null);

        List<AttendanceDTO> dtos = svc.getMyAttendances("auth0|x");

        assertTrue(dtos.isEmpty());
    }

    // ── attend — userId null guard (line 104) ─────────────────────────────

    @Test
    void attend_unprovisionedCaller_throwsNotFound() {
        AttendanceService svc = newService();
        // Event resolves & passes the published/deadline gates, but the caller
        // has no resolvable UUID → requireUuid() null → 104.
        EventDTO event = new EventDTO(3L, "Title", null, "loc",
                LocalDateTime.of(2999, 1, 1, 0, 0).plusDays(1), LocalDateTime.of(2999, 1, 1, 0, 0).plusDays(2),
                null, null, null, UUID.randomUUID(),
                EventStatus.PUBLISHED, null, false, false, null,
                0L, null, 0L, 0L, 0L,
                null, null, null, List.of(),
                LocalDateTime.of(2025, 1, 1, 12, 0), LocalDateTime.of(2025, 1, 1, 12, 0),
                null, null, null);
        when(svc.eventClient.getById(3L)).thenReturn(event);
        when(svc.callerIdentity.requireUuid()).thenReturn(null);

        assertThrows(NotFoundException.class,
                () -> svc.attend("auth0|x", 3L, AttendanceStatus.ATTENDING));
    }

    // ── removeAttendance — userId null guard (line 154) ───────────────────

    @Test
    void removeAttendance_unprovisionedCaller_throwsNotFound() {
        AttendanceService svc = newService();
        // Advisory lock acquired (native query no-op), then requireUuid() null → 154.
        when(svc.callerIdentity.requireUuid()).thenReturn(null);

        assertThrows(NotFoundException.class,
                () -> svc.removeAttendance("auth0|x", 4L));
    }

    // ── getUserParticipationEvents — empty eventIds short-circuit (line 377) ─

    @Test
    void getUserParticipationEvents_selfNoAttendingRows_returnsEmpty() {
        AttendanceService svc = newService();
        UUID self = UUID.randomUUID();
        when(svc.callerIdentity.getUuid()).thenReturn(self);

        PanacheMock.mock(Attendance.class);
        // Self caller skips the privacy gate; the only row is WAITLISTED so the
        // ATTENDING filter empties eventIds → line 377.
        when(Attendance.findAllByUser(self))
                .thenReturn(List.of(row(self, 8L, AttendanceStatus.WAITLISTED)));

        List<EventDTO> events = svc.getUserParticipationEvents(self, null);

        assertTrue(events.isEmpty());
    }

    // ── matchesTimeframe — null endDate guard (line 414) ──────────────────

    @Test
    void getMyParticipationEvents_eventWithNullEndDate_filteredOutByTimeframe() {
        AttendanceService svc = newService();
        UUID self = UUID.randomUUID();
        when(svc.callerIdentity.getUuid()).thenReturn(self);

        PanacheMock.mock(Attendance.class);
        when(Attendance.findAllByUser(self))
                .thenReturn(List.of(row(self, 5L, AttendanceStatus.ATTENDING)));
        when(Attendance.countGroupedByStatus(anyList(), any(), any()))
                .thenReturn(new java.util.HashMap<>());

        // Event has a null endDate → matchesTimeframe returns false (line 414)
        // under a non-null timeframe filter, so it is filtered out.
        EventDTO event = new EventDTO(5L, "Title", null, "loc",
                LocalDateTime.of(2999, 1, 1, 0, 0).plusDays(1), null,
                null, null, null, UUID.randomUUID(),
                EventStatus.PUBLISHED, null, false, false, null,
                0L, null, 0L, 0L, 0L,
                null, null, null, List.of(),
                LocalDateTime.of(2025, 1, 1, 12, 0), LocalDateTime.of(2025, 1, 1, 12, 0),
                null, null, null);
        when(svc.eventClient.findByIds(anyList(), any())).thenReturn(List.of(event));

        List<EventDTO> events = svc.getMyParticipationEvents(
                "auth0|x", null, ch.unige.events.shared.jaxrs.Timeframe.UPCOMING);

        assertTrue(events.isEmpty(),
                "event with null endDate must be excluded under a timeframe filter");
    }
}
