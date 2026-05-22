package ch.unige.events.engagement.attendance.service;

import ch.unige.events.engagement.attendance.entity.Attendance;
import ch.unige.events.engagement.test.JwtTestContext;
import ch.unige.events.engagement.test.JwtTestHelper;
import ch.unige.events.shared.client.EventServiceClient;
import ch.unige.events.shared.client.UserServiceClient;
import ch.unige.events.shared.domain.dto.AttendanceDTO;
import ch.unige.events.shared.domain.dto.AttendeeProjection;
import ch.unige.events.shared.domain.dto.EventDTO;
import ch.unige.events.shared.domain.dto.UserPublicResponse;
import ch.unige.events.shared.domain.enums.AttendanceStatus;
import ch.unige.events.shared.domain.enums.EventStatus;
import ch.unige.events.shared.jaxrs.Timeframe;

import io.quarkus.hibernate.orm.panache.PanacheQuery;
import io.quarkus.panache.mock.PanacheMock;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import jakarta.inject.Inject;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.WebApplicationException;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

/**
 * Unit-test coverage for {@link AttendanceService} using
 * {@link PanacheMock} for the static finders and {@link InjectMock} for
 * the cross-service REST clients. The {@link io.quarkus.test.security.TestSecurity}
 * annotation drives the {@code SecurityIdentity} principal that
 * {@code AttendanceResource} would normally pass via
 * {@code identity.getPrincipal().getName()} — but since these tests call
 * the service layer directly, the {@code auth0Id} parameter is
 * synthesized and the JWT is staged via {@link JwtTestContext}.
 */
@QuarkusTest
@TestSecurity(user = "auth0|test-att-user")
class AttendanceServiceTest {

    @Inject
    AttendanceService service;

    @InjectMock
    @RestClient
    EventServiceClient eventClient;

    @InjectMock
    @RestClient
    UserServiceClient userClient;

    private final UUID userId = UUID.randomUUID();
    private final UUID otherUserId = UUID.randomUUID();
    private final UUID creatorId = UUID.randomUUID();

    @BeforeEach
    void stageJwt() {
        JwtTestContext.set(JwtTestHelper.jwtFor(userId));
        // Default user-enrichment stub.
        lenient().when(userClient.getById(any(UUID.class))).thenAnswer(inv -> {
            UUID id = inv.getArgument(0);
            return new UserPublicResponse(id, "User-" + id, null, null, null,
                    null, null, null, 0L, 0L, null);
        });
    }

    @AfterEach
    void clearJwt() {
        JwtTestContext.clear();
    }

    private static EventDTO event(Long id, EventStatus status, Integer capacity) {
        return event(id, status, capacity, null, null);
    }

    private static EventDTO event(Long id, EventStatus status, Integer capacity,
                                  UUID creatorId, LocalDateTime registrationDeadline) {
        return new EventDTO(id, "Title-" + id, "desc", "loc",
                LocalDateTime.now().plusDays(1), LocalDateTime.now().plusDays(2),
                null, null, null,
                creatorId,
                status, capacity, false, false, null,
                0L, capacity != null ? capacity.longValue() : null, 0L,
                0L, 0L,
                null, null, registrationDeadline,
                List.of(),
                LocalDateTime.now(), LocalDateTime.now(),
                null, null, null);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static PanacheQuery queryWithFirst(Optional<? extends io.quarkus.hibernate.orm.panache.PanacheEntityBase> first) {
        PanacheQuery q = mock(PanacheQuery.class);
        // doReturn keeps the stubbing self-contained — using when(...)thenReturn here
        // would interleave with an outer when(...) chain at the call site.
        org.mockito.Mockito.doReturn(first).when(q).firstResultOptional();
        return q;
    }

    /**
     * Wraps an {@link Attendance} in a spy that suppresses the JPA-bound
     * {@link Attendance#delete()} call. Without this, the service's
     * {@code attendance.delete()} would invoke the real Hibernate session
     * and throw {@code EntityNotFound} for our transient test instance.
     */
    private static Attendance spyDeletable(Attendance a) {
        Attendance s = spy(a);
        doNothing().when(s).delete();
        return s;
    }

    // ──────────────────────────────────────────────────────────────────
    // attend(...)
    // ──────────────────────────────────────────────────────────────────

    @Test
    void attend_published_capacityAvailable_persistsAttending() {
        when(eventClient.getById(7L)).thenReturn(event(7L, EventStatus.PUBLISHED, 5));

        PanacheMock.mock(Attendance.class);
        PanacheQuery q1__ = queryWithFirst(Optional.empty());
        when(Attendance.find(anyString(), any(Object[].class)))
                .thenReturn(q1__);
        when(Attendance.count(anyString(), any(Object[].class)))
                .thenReturn(0L);

        AttendanceDTO dto = service.attend("auth0|test-att-user", 7L, AttendanceStatus.ATTENDING);

        assertEquals(AttendanceStatus.ATTENDING, dto.status());
        assertEquals(7L, dto.eventId());
        assertEquals(userId, dto.userId());
    }

    @Test
    void attend_capacityFull_returnsWaitlisted() {
        when(eventClient.getById(8L)).thenReturn(event(8L, EventStatus.PUBLISHED, 2));

        PanacheMock.mock(Attendance.class);
        PanacheQuery q2__ = queryWithFirst(Optional.empty());
        when(Attendance.find(anyString(), any(Object[].class)))
                .thenReturn(q2__);
        when(Attendance.count(anyString(), any(Object[].class)))
                .thenReturn(2L);

        AttendanceDTO dto = service.attend("auth0|test-att-user", 8L, AttendanceStatus.ATTENDING);
        assertEquals(AttendanceStatus.WAITLISTED, dto.status());
    }

    @Test
    void attend_unlimitedCapacity_returnsAttending() {
        when(eventClient.getById(9L)).thenReturn(event(9L, EventStatus.PUBLISHED, null));

        PanacheMock.mock(Attendance.class);
        PanacheQuery q3__ = queryWithFirst(Optional.empty());
        when(Attendance.find(anyString(), any(Object[].class)))
                .thenReturn(q3__);

        AttendanceDTO dto = service.attend("auth0|test-att-user", 9L, AttendanceStatus.ATTENDING);
        assertEquals(AttendanceStatus.ATTENDING, dto.status());
    }

    @Test
    void attend_unknownEvent_throwsNotFound() {
        when(eventClient.getById(404L)).thenReturn(null);
        assertThrows(NotFoundException.class,
                () -> service.attend("auth0|test-att-user", 404L, AttendanceStatus.ATTENDING));
    }

    @Test
    void attend_draftEvent_throwsBadRequest() {
        when(eventClient.getById(10L)).thenReturn(event(10L, EventStatus.DRAFT, null));
        assertThrows(BadRequestException.class,
                () -> service.attend("auth0|test-att-user", 10L, AttendanceStatus.ATTENDING));
    }

    @Test
    void attend_bannedEvent_throwsBadRequest() {
        when(eventClient.getById(11L)).thenReturn(event(11L, EventStatus.BANNED, null));
        assertThrows(BadRequestException.class,
                () -> service.attend("auth0|test-att-user", 11L, AttendanceStatus.ATTENDING));
    }

    @Test
    void attend_nonAttendingStatus_throwsBadRequest() {
        assertThrows(BadRequestException.class,
                () -> service.attend("auth0|test-att-user", 1L, AttendanceStatus.WAITLISTED));
    }

    @Test
    void attend_registrationDeadlinePassed_throws409() {
        EventDTO past = event(12L, EventStatus.PUBLISHED, 5, creatorId,
                LocalDateTime.now().minusDays(1));
        when(eventClient.getById(12L)).thenReturn(past);

        WebApplicationException ex = assertThrows(WebApplicationException.class,
                () -> service.attend("auth0|test-att-user", 12L, AttendanceStatus.ATTENDING));
        assertEquals(409, ex.getResponse().getStatus());
    }

    @Test
    void attend_anonymousJwt_throwsNotFound() {
        JwtTestContext.set(JwtTestHelper.anonymous());
        when(eventClient.getById(13L)).thenReturn(event(13L, EventStatus.PUBLISHED, 5));

        assertThrows(NotFoundException.class,
                () -> service.attend("auth0|test-att-user", 13L, AttendanceStatus.ATTENDING));
    }

    @Test
    void attend_existingAttendance_isIdempotent() {
        when(eventClient.getById(14L)).thenReturn(event(14L, EventStatus.PUBLISHED, 5));

        Attendance existing = new Attendance();
        existing.id = 100L;
        existing.userId = userId;
        existing.eventId = 14L;
        existing.status = AttendanceStatus.ATTENDING;
        existing.createdAt = LocalDateTime.now();

        PanacheMock.mock(Attendance.class);
        PanacheQuery q4__ = queryWithFirst(Optional.of(existing));
        when(Attendance.find(anyString(), any(Object[].class)))
                .thenReturn(q4__);

        AttendanceDTO dto = service.attend("auth0|test-att-user", 14L, AttendanceStatus.ATTENDING);
        assertEquals(AttendanceStatus.ATTENDING, dto.status());
        assertEquals(100L, dto.id());
    }

    // ──────────────────────────────────────────────────────────────────
    // removeAttendance(...)
    // ──────────────────────────────────────────────────────────────────

    @Test
    void removeAttendance_attending_promotesNextWaitlisted() {
        Attendance attendingRaw = new Attendance();
        attendingRaw.id = 200L;
        attendingRaw.userId = userId;
        attendingRaw.eventId = 20L;
        attendingRaw.status = AttendanceStatus.ATTENDING;
        Attendance attending = spyDeletable(attendingRaw);

        Attendance waitlisted = new Attendance();
        waitlisted.id = 201L;
        waitlisted.userId = otherUserId;
        waitlisted.eventId = 20L;
        waitlisted.status = AttendanceStatus.WAITLISTED;

        PanacheMock.mock(Attendance.class);
        PanacheQuery q5__ = queryWithFirst(Optional.of(attending));
        when(Attendance.find(argThat((String s) -> s != null && s.startsWith("userId =")),
                any(Object[].class)))
                .thenReturn(q5__);
        PanacheQuery q6__ = queryWithFirst(Optional.of(waitlisted));
        when(Attendance.find(argThat((String s) -> s != null && s.startsWith("eventId =")),
                any(Object[].class)))
                .thenReturn(q6__);

        when(eventClient.getById(20L)).thenReturn(event(20L, EventStatus.PUBLISHED, 5));

        service.removeAttendance("auth0|test-att-user", 20L);
        assertEquals(AttendanceStatus.ATTENDING, waitlisted.status);
    }

    @Test
    void removeAttendance_attending_noWaitlist_doesNotPromote() {
        Attendance attendingRaw = new Attendance();
        attendingRaw.id = 210L;
        attendingRaw.userId = userId;
        attendingRaw.eventId = 21L;
        attendingRaw.status = AttendanceStatus.ATTENDING;
        Attendance attending = spyDeletable(attendingRaw);

        PanacheMock.mock(Attendance.class);
        PanacheQuery q7__ = queryWithFirst(Optional.of(attending));
        when(Attendance.find(argThat((String s) -> s != null && s.startsWith("userId =")),
                any(Object[].class)))
                .thenReturn(q7__);
        PanacheQuery q8__ = queryWithFirst(Optional.empty());
        when(Attendance.find(argThat((String s) -> s != null && s.startsWith("eventId =")),
                any(Object[].class)))
                .thenReturn(q8__);

        when(eventClient.getById(21L)).thenReturn(event(21L, EventStatus.PUBLISHED, 5));

        service.removeAttendance("auth0|test-att-user", 21L);
    }

    @Test
    void removeAttendance_waitlisted_doesNotTriggerPromotion() {
        Attendance waitlistedRaw = new Attendance();
        waitlistedRaw.id = 220L;
        waitlistedRaw.userId = userId;
        waitlistedRaw.eventId = 22L;
        waitlistedRaw.status = AttendanceStatus.WAITLISTED;
        Attendance waitlisted = spyDeletable(waitlistedRaw);

        PanacheMock.mock(Attendance.class);
        PanacheQuery q9__ = queryWithFirst(Optional.of(waitlisted));
        when(Attendance.find(anyString(), any(Object[].class)))
                .thenReturn(q9__);

        when(eventClient.getById(22L)).thenReturn(event(22L, EventStatus.PUBLISHED, 5));

        service.removeAttendance("auth0|test-att-user", 22L);
    }

    @Test
    void removeAttendance_eventCancelled_doesNotPromote() {
        Attendance attendingRaw = new Attendance();
        attendingRaw.id = 230L;
        attendingRaw.userId = userId;
        attendingRaw.eventId = 23L;
        attendingRaw.status = AttendanceStatus.ATTENDING;
        Attendance attending = spyDeletable(attendingRaw);

        PanacheMock.mock(Attendance.class);
        PanacheQuery q10__ = queryWithFirst(Optional.of(attending));
        when(Attendance.find(anyString(), any(Object[].class)))
                .thenReturn(q10__);

        when(eventClient.getById(23L)).thenReturn(event(23L, EventStatus.CANCELLED, 5));

        service.removeAttendance("auth0|test-att-user", 23L);
    }

    @Test
    void removeAttendance_unknownAttendance_throwsNotFound() {
        PanacheMock.mock(Attendance.class);
        PanacheQuery q11__ = queryWithFirst(Optional.empty());
        when(Attendance.find(anyString(), any(Object[].class)))
                .thenReturn(q11__);

        assertThrows(NotFoundException.class,
                () -> service.removeAttendance("auth0|test-att-user", 24L));
    }

    @Test
    void removeAttendance_anonymousJwt_throwsNotFound() {
        JwtTestContext.set(JwtTestHelper.anonymous());
        assertThrows(NotFoundException.class,
                () -> service.removeAttendance("auth0|test-att-user", 25L));
    }

    @Test
    void removeAttendance_eventClientReturnsNull_logsAndSkipsPromotion() {
        // [WAITLIST_PROMOTION_SKIPPED]: when event-service
        // is unreachable the fallback returns null; the attendance row must
        // still be deleted but waitlist promotion is deferred and a WARN is logged.
        Attendance attendingRaw = new Attendance();
        attendingRaw.id = 260L;
        attendingRaw.userId = userId;
        attendingRaw.eventId = 26L;
        attendingRaw.status = AttendanceStatus.ATTENDING;
        Attendance attending = spyDeletable(attendingRaw);

        Attendance waitlisted = new Attendance();
        waitlisted.id = 261L;
        waitlisted.userId = otherUserId;
        waitlisted.eventId = 26L;
        waitlisted.status = AttendanceStatus.WAITLISTED;

        PanacheMock.mock(Attendance.class);
        PanacheQuery qFirst = queryWithFirst(Optional.of(attending));
        when(Attendance.find(argThat((String s) -> s != null && s.startsWith("userId =")),
                any(Object[].class)))
                .thenReturn(qFirst);
        PanacheQuery qSecond = queryWithFirst(Optional.of(waitlisted));
        when(Attendance.find(argThat((String s) -> s != null && s.startsWith("eventId =")),
                any(Object[].class)))
                .thenReturn(qSecond);

        when(eventClient.getById(26L)).thenReturn(null);

        Logger jul = Logger.getLogger(AttendanceService.class.getName());
        Level originalLevel = jul.getLevel();
        List<LogRecord> captured = new ArrayList<>();
        Handler handler = new Handler() {
            @Override public void publish(LogRecord r) { captured.add(r); }
            @Override public void flush() {}
            @Override public void close() {}
        };
        jul.addHandler(handler);
        jul.setLevel(Level.ALL);
        try {
            service.removeAttendance("auth0|test-att-user", 26L);

            assertEquals(AttendanceStatus.WAITLISTED, waitlisted.status,
                    "waitlisted attendance must NOT be promoted when event-service is unreachable");
            assertTrue(
                captured.stream().anyMatch(r -> {
                    String msg = r.getMessage();
                    return msg != null && msg.contains("[WAITLIST_PROMOTION_SKIPPED]")
                            && r.getLevel().intValue() >= Level.WARNING.intValue();
                }),
                "expected a WARN log record containing [WAITLIST_PROMOTION_SKIPPED] but captured=" + captured);
        } finally {
            jul.removeHandler(handler);
            jul.setLevel(originalLevel);
        }
    }

    // ──────────────────────────────────────────────────────────────────
    // getAttendees(...) — privacy projection (SCRUM-S7)
    // ──────────────────────────────────────────────────────────────────

    /**
     * Helper: stages two attendee rows on event {@code eventId} — one
     * public-profile user and one private-profile user — and stubs the
     * matching {@code getAttendeeProjections} call so the service can
     * apply the privacy filter.
     */
    private List<AttendanceDTO> stageTwoAttendeesAndCall(
            Long eventId, EventDTO event, UUID publicUserId, UUID privateUserId) {
        when(eventClient.getByIdWithCoOrgCheck(eq(eventId), any(UUID.class))).thenReturn(event);

        Attendance pub = new Attendance();
        pub.id = 300L;
        pub.userId = publicUserId;
        pub.eventId = eventId;
        pub.status = AttendanceStatus.ATTENDING;
        pub.createdAt = LocalDateTime.now();

        Attendance priv = new Attendance();
        priv.id = 301L;
        priv.userId = privateUserId;
        priv.eventId = eventId;
        priv.status = AttendanceStatus.ATTENDING;
        priv.createdAt = LocalDateTime.now();

        PanacheMock.mock(Attendance.class);
        when(Attendance.findByEvent(eventId, 0, 20)).thenReturn(List.of(pub, priv));

        when(userClient.getAttendeeProjections(argThat(ids ->
                ids != null && ids.containsAll(List.of(publicUserId, privateUserId)))))
                .thenReturn(List.of(
                        new AttendeeProjection(publicUserId, "Public-User", "/avatars/p.png", true),
                        new AttendeeProjection(privateUserId, "Private-User", "/avatars/q.png", false)
                ));

        return service.getAttendees("auth0|test-att-user", eventId, 0, 20);
    }

    @Test
    void getAttendees_byCreator_returnsRealIdentityForPublicAndPrivate() {
        UUID caller = userId;
        UUID privateUserId = UUID.randomUUID();
        EventDTO ev = new EventDTO(30L, "T", "d", "l",
                LocalDateTime.now(), LocalDateTime.now().plusDays(1),
                null, null, null,
                caller, EventStatus.PUBLISHED, 5,
                false, false, null,
                0L, 5L, 0L, 0L, 0L,
                null, null, null,
                List.of(),
                LocalDateTime.now(), LocalDateTime.now(),
                null, null, true);

        List<AttendanceDTO> result = stageTwoAttendeesAndCall(30L, ev, caller, privateUserId);

        assertEquals(2, result.size());
        // Public row — real identity
        AttendanceDTO pub = result.stream().filter(d -> d.id() == 300L).findFirst().orElseThrow();
        assertEquals("Public-User", pub.displayName());
        assertEquals(caller, pub.userId());
        // Private row — organizer sees real identity
        AttendanceDTO priv = result.stream().filter(d -> d.id() == 301L).findFirst().orElseThrow();
        assertEquals("Private-User", priv.displayName());
        assertEquals("/avatars/q.png", priv.avatarUrl());
        assertEquals(privateUserId, priv.userId());
    }

    @Test
    void getAttendees_byCoOrganizer_returnsRealIdentityForPublicAndPrivate() {
        UUID privateUserId = UUID.randomUUID();
        // coOrganizerOf=true → caller is an ACCEPTED co-organizer
        EventDTO ev = new EventDTO(34L, "T", "d", "l",
                LocalDateTime.now(), LocalDateTime.now().plusDays(1),
                null, null, null,
                creatorId, EventStatus.PUBLISHED, 5,
                false, false, null,
                0L, 5L, 0L, 0L, 0L,
                null, null, null,
                List.of(),
                LocalDateTime.now(), LocalDateTime.now(),
                null, null, true);

        List<AttendanceDTO> result = stageTwoAttendeesAndCall(34L, ev, otherUserId, privateUserId);

        AttendanceDTO priv = result.stream().filter(d -> d.id() == 301L).findFirst().orElseThrow();
        assertEquals("Private-User", priv.displayName());
        assertEquals(privateUserId, priv.userId());
    }

    @Test
    void getAttendees_byNonOrganizer_anonymizesPrivateRowsAndKeepsPublic() {
        UUID privateUserId = UUID.randomUUID();
        EventDTO ev = new EventDTO(32L, "T", "d", "l",
                LocalDateTime.now(), LocalDateTime.now().plusDays(1),
                null, null, null,
                creatorId, EventStatus.PUBLISHED, 5,
                false, false, null,
                0L, 5L, 0L, 0L, 0L,
                null, null, null,
                List.of(),
                LocalDateTime.now(), LocalDateTime.now(),
                null, null, false);
        when(eventClient.getOrganizerUuids(32L)).thenReturn(List.of(creatorId));

        List<AttendanceDTO> result = stageTwoAttendeesAndCall(32L, ev, otherUserId, privateUserId);

        // Public row — real identity (and userId preserved)
        AttendanceDTO pub = result.stream().filter(d -> d.id() == 300L).findFirst().orElseThrow();
        assertEquals("Public-User", pub.displayName());
        assertEquals("/avatars/p.png", pub.avatarUrl());
        assertEquals(otherUserId, pub.userId());

        // Private row — anonymized: displayName, avatarUrl, AND userId nulled.
        AttendanceDTO priv = result.stream().filter(d -> d.id() == 301L).findFirst().orElseThrow();
        assertNull(priv.displayName());
        assertNull(priv.avatarUrl());
        assertNull(priv.userId(), "UUID must be nulled for non-organizer view to prevent /users/{id} probing");
        // Status + eventId + attendance.id remain (needed for rendering & key)
        assertEquals(AttendanceStatus.ATTENDING, priv.status());
        assertEquals(32L, priv.eventId());
        assertNotNull(priv.createdAt());
    }

    @Test
    @TestSecurity(user = "auth0|test-att-admin", roles = "ADMIN")
    void getAttendees_byAdmin_returnsRealIdentityForPrivateRows() {
        UUID privateUserId = UUID.randomUUID();
        EventDTO ev = new EventDTO(35L, "T", "d", "l",
                LocalDateTime.now(), LocalDateTime.now().plusDays(1),
                null, null, null,
                creatorId, EventStatus.PUBLISHED, 5,
                false, false, null,
                0L, 5L, 0L, 0L, 0L,
                null, null, null,
                List.of(),
                LocalDateTime.now(), LocalDateTime.now(),
                null, null, false);
        when(eventClient.getOrganizerUuids(35L)).thenReturn(List.of(creatorId));

        List<AttendanceDTO> result = stageTwoAttendeesAndCall(35L, ev, otherUserId, privateUserId);

        AttendanceDTO priv = result.stream().filter(d -> d.id() == 301L).findFirst().orElseThrow();
        assertEquals("Private-User", priv.displayName());
        assertEquals(privateUserId, priv.userId());
    }

    @Test
    void getAttendees_deletedUser_anonymizedRegardlessOfRole() {
        UUID caller = userId;
        UUID ghostId = UUID.randomUUID();
        EventDTO ev = new EventDTO(36L, "T", "d", "l",
                LocalDateTime.now(), LocalDateTime.now().plusDays(1),
                null, null, null,
                caller, EventStatus.PUBLISHED, 5,
                false, false, null,
                0L, 5L, 0L, 0L, 0L,
                null, null, null,
                List.of(),
                LocalDateTime.now(), LocalDateTime.now(),
                null, null, true);
        when(eventClient.getByIdWithCoOrgCheck(eq(36L), any(UUID.class))).thenReturn(ev);

        Attendance orphan = new Attendance();
        orphan.id = 600L;
        orphan.userId = ghostId;
        orphan.eventId = 36L;
        orphan.status = AttendanceStatus.ATTENDING;
        orphan.createdAt = LocalDateTime.now();

        PanacheMock.mock(Attendance.class);
        when(Attendance.findByEvent(36L, 0, 20)).thenReturn(List.of(orphan));
        when(userClient.getAttendeeProjections(any())).thenReturn(List.of()); // ghost — no projection

        List<AttendanceDTO> result = service.getAttendees("auth0|test-att-user", 36L, 0, 20);

        assertEquals(1, result.size());
        AttendanceDTO dto = result.get(0);
        assertNull(dto.displayName());
        assertNull(dto.avatarUrl());
        assertNull(dto.userId(), "deleted user surfaces with null userId — there's no real identity to expose");
    }

    @Test
    void getAttendees_userClientFailure_returnsAllAnonymizedRows() {
        UUID caller = userId;
        UUID someUser = UUID.randomUUID();
        EventDTO ev = new EventDTO(37L, "T", "d", "l",
                LocalDateTime.now(), LocalDateTime.now().plusDays(1),
                null, null, null,
                caller, EventStatus.PUBLISHED, 5,
                false, false, null,
                0L, 5L, 0L, 0L, 0L,
                null, null, null,
                List.of(),
                LocalDateTime.now(), LocalDateTime.now(),
                null, null, true);
        when(eventClient.getByIdWithCoOrgCheck(eq(37L), any(UUID.class))).thenReturn(ev);

        Attendance a = new Attendance();
        a.id = 700L;
        a.userId = someUser;
        a.eventId = 37L;
        a.status = AttendanceStatus.ATTENDING;
        a.createdAt = LocalDateTime.now();
        PanacheMock.mock(Attendance.class);
        when(Attendance.findByEvent(37L, 0, 20)).thenReturn(List.of(a));

        when(userClient.getAttendeeProjections(any()))
                .thenThrow(new RuntimeException("user-service down"));

        List<AttendanceDTO> result = service.getAttendees("auth0|test-att-user", 37L, 0, 20);
        assertEquals(1, result.size());
        // Degraded enrichment — row stays in the list but with no identity exposed.
        assertNull(result.get(0).displayName());
        assertNull(result.get(0).userId());
    }

    @Test
    void getAttendees_unknownEvent_throwsNotFound() {
        when(eventClient.getByIdWithCoOrgCheck(eq(31L), any(UUID.class))).thenReturn(null);
        assertThrows(NotFoundException.class,
                () -> service.getAttendees("auth0|test-att-user", 31L, 0, 20));
    }

    @Test
    void getAttendees_anonymousJwt_publicEvent_returnsAnonymizedList() {
        JwtTestContext.set(JwtTestHelper.anonymous());
        UUID someUser = UUID.randomUUID();
        EventDTO ev = new EventDTO(33L, "T", "d", "l",
                LocalDateTime.now(), LocalDateTime.now().plusDays(1),
                null, null, null,
                creatorId,
                EventStatus.PUBLISHED, 5,
                false, false, null,
                0L, 5L, 0L, 0L, 0L,
                null, null, null,
                List.of(),
                LocalDateTime.now(), LocalDateTime.now(),
                null, null, false);
        // No callerUuid → falls back to getById (no co-org self-check possible)
        when(eventClient.getById(33L)).thenReturn(ev);

        Attendance a = new Attendance();
        a.id = 900L;
        a.userId = someUser;
        a.eventId = 33L;
        a.status = AttendanceStatus.ATTENDING;
        a.createdAt = LocalDateTime.now();
        PanacheMock.mock(Attendance.class);
        when(Attendance.findByEvent(33L, 0, 20)).thenReturn(List.of(a));
        when(userClient.getAttendeeProjections(any())).thenReturn(List.of(
                new AttendeeProjection(someUser, "Public-User", null, true)
        ));

        // Anonymous caller is not an organizer view — but the resource layer
        // requires @Authenticated, so this path is only reachable via tests.
        // The service itself does not 403; it returns the privacy projection.
        List<AttendanceDTO> result = service.getAttendees("auth0|test-att-user", 33L, 0, 20);
        assertEquals(1, result.size());
        // Public profile row keeps real identity even for non-organizer view.
        assertEquals("Public-User", result.get(0).displayName());
    }

    // ──────────────────────────────────────────────────────────────────
    // getMyAttendances + findByUser
    // ──────────────────────────────────────────────────────────────────

    @Test
    void getMyAttendances_returnsMappedList() {
        Attendance a = new Attendance();
        a.id = 400L;
        a.userId = userId;
        a.eventId = 40L;
        a.status = AttendanceStatus.ATTENDING;
        a.createdAt = LocalDateTime.now();
        PanacheMock.mock(Attendance.class);
        when(Attendance.findAllByUser(userId)).thenReturn(List.of(a));

        List<AttendanceDTO> mine = service.getMyAttendances("auth0|test-att-user");
        assertEquals(1, mine.size());
        assertEquals(40L, mine.get(0).eventId());
    }

    @Test
    void getMyAttendances_anonymousJwt_returnsEmptyList() {
        JwtTestContext.set(JwtTestHelper.anonymous());
        List<AttendanceDTO> mine = service.getMyAttendances("auth0|test-att-user");
        assertTrue(mine.isEmpty());
    }

    @Test
    void findByUser_withStatusFilter_returnsMatches() {
        UUID someUser = UUID.randomUUID();
        Attendance a = new Attendance();
        a.id = 500L;
        a.userId = someUser;
        a.eventId = 50L;
        a.status = AttendanceStatus.WAITLISTED;
        a.createdAt = LocalDateTime.now();

        PanacheMock.mock(Attendance.class);
        when(Attendance.list(anyString(), any(Object[].class)))
                .thenReturn(List.of(a));

        List<AttendanceDTO> result = service.findByUser(someUser, AttendanceStatus.WAITLISTED);
        assertEquals(1, result.size());
        assertEquals(AttendanceStatus.WAITLISTED, result.get(0).status());
    }

    @Test
    void findByUser_nullStatus_returnsAll() {
        UUID someUser = UUID.randomUUID();
        Attendance a = new Attendance();
        a.id = 510L;
        a.userId = someUser;
        a.eventId = 51L;
        a.status = AttendanceStatus.ATTENDING;
        a.createdAt = LocalDateTime.now();

        PanacheMock.mock(Attendance.class);
        when(Attendance.list(anyString(), any(Object[].class)))
                .thenReturn(List.of(a));

        List<AttendanceDTO> result = service.findByUser(someUser, null);
        assertEquals(1, result.size());
    }

    // ──────────────────────────────────────────────────────────────────
    // getMyParticipationEvents
    // ──────────────────────────────────────────────────────────────────

    @Test
    void getMyParticipationEvents_anonymousJwt_returnsEmptyList() {
        JwtTestContext.set(JwtTestHelper.anonymous());
        List<EventDTO> result = service.getMyParticipationEvents("auth0|test-att-user", null, null);
        assertTrue(result.isEmpty());
    }

    @Test
    void getMyParticipationEvents_noAttendances_returnsEmptyList() {
        PanacheMock.mock(Attendance.class);
        when(Attendance.findAllByUser(userId)).thenReturn(List.of());
        List<EventDTO> result = service.getMyParticipationEvents("auth0|test-att-user", null, null);
        assertTrue(result.isEmpty());
    }

    @SuppressWarnings("unchecked")
    @Test
    void getMyParticipationEvents_nullStatusFilter_returnsAllUpcoming() {
        Attendance a = new Attendance();
        a.id = 600L;
        a.userId = userId;
        a.eventId = 60L;
        a.status = AttendanceStatus.ATTENDING;
        a.createdAt = LocalDateTime.now();
        PanacheMock.mock(Attendance.class);
        when(Attendance.findAllByUser(userId)).thenReturn(List.of(a));
        when(Attendance.countGroupedByStatus(any(List.class),
                eq(AttendanceStatus.ATTENDING), any())).thenReturn(Map.of(60L, 7L));
        when(Attendance.countGroupedByStatus(any(List.class),
                eq(AttendanceStatus.WAITLISTED), any())).thenReturn(Map.of(60L, 2L));

        EventDTO ev = event(60L, EventStatus.PUBLISHED, 10, creatorId, null);
        when(eventClient.findByIds(any(List.class), eq(null))).thenReturn(List.of(ev));

        List<EventDTO> result = service.getMyParticipationEvents("auth0|test-att-user", null, null);
        assertEquals(1, result.size());
        assertEquals(60L, result.get(0).id());
        assertEquals(7L, result.get(0).attendingCount());
        assertEquals(2L, result.get(0).waitlistedCount());
    }

    @SuppressWarnings("unchecked")
    @Test
    void getMyParticipationEvents_pastTimeframe_filtersByEndDate() {
        Attendance a = new Attendance();
        a.id = 610L;
        a.userId = userId;
        a.eventId = 61L;
        a.status = AttendanceStatus.ATTENDING;
        a.createdAt = LocalDateTime.now().minusDays(10);
        PanacheMock.mock(Attendance.class);
        when(Attendance.findAllByUser(userId)).thenReturn(List.of(a));
        when(Attendance.countGroupedByStatus(any(List.class), any(), any()))
                .thenReturn(Map.of());

        EventDTO past = new EventDTO(61L, "T", "d", "l",
                LocalDateTime.now().minusDays(5), LocalDateTime.now().minusDays(4),
                null, null, null, creatorId,
                EventStatus.PUBLISHED, 10, false, false, null,
                0L, 10L, 0L, 0L, 0L, null, null, null,
                List.of(), LocalDateTime.now(), LocalDateTime.now(),
                null, null, null);
        when(eventClient.findByIds(any(List.class), eq(null))).thenReturn(List.of(past));

        List<EventDTO> result = service.getMyParticipationEvents(
                "auth0|test-att-user", null, Timeframe.PAST);
        assertEquals(1, result.size());
    }

    @SuppressWarnings("unchecked")
    @Test
    void getMyParticipationEvents_upcomingTimeframe_skipsPastEvents() {
        Attendance a = new Attendance();
        a.id = 620L;
        a.userId = userId;
        a.eventId = 62L;
        a.status = AttendanceStatus.ATTENDING;
        a.createdAt = LocalDateTime.now().minusDays(10);
        PanacheMock.mock(Attendance.class);
        when(Attendance.findAllByUser(userId)).thenReturn(List.of(a));
        when(Attendance.countGroupedByStatus(any(List.class), any(), any()))
                .thenReturn(Map.of());

        EventDTO past = new EventDTO(62L, "T", "d", "l",
                LocalDateTime.now().minusDays(5), LocalDateTime.now().minusDays(4),
                null, null, null, creatorId,
                EventStatus.PUBLISHED, 10, false, false, null,
                0L, 10L, 0L, 0L, 0L, null, null, null,
                List.of(), LocalDateTime.now(), LocalDateTime.now(),
                null, null, null);
        when(eventClient.findByIds(any(List.class), eq(null))).thenReturn(List.of(past));

        List<EventDTO> result = service.getMyParticipationEvents(
                "auth0|test-att-user", null, Timeframe.UPCOMING);
        assertTrue(result.isEmpty());
    }

    @SuppressWarnings("unchecked")
    @Test
    void getMyParticipationEvents_statusFilter_keepsOnlyMatching() {
        Attendance attending = new Attendance();
        attending.id = 700L;
        attending.userId = userId;
        attending.eventId = 70L;
        attending.status = AttendanceStatus.ATTENDING;
        attending.createdAt = LocalDateTime.now();

        Attendance waitlisted = new Attendance();
        waitlisted.id = 701L;
        waitlisted.userId = userId;
        waitlisted.eventId = 71L;
        waitlisted.status = AttendanceStatus.WAITLISTED;
        waitlisted.createdAt = LocalDateTime.now();

        PanacheMock.mock(Attendance.class);
        when(Attendance.findAllByUser(userId)).thenReturn(List.of(attending, waitlisted));
        when(Attendance.countGroupedByStatus(any(List.class), any(), any()))
                .thenReturn(Map.of());

        EventDTO ev = event(70L, EventStatus.PUBLISHED, 10, creatorId, null);
        when(eventClient.findByIds(any(List.class), eq(null))).thenReturn(List.of(ev));

        List<EventDTO> result = service.getMyParticipationEvents(
                "auth0|test-att-user", AttendanceStatus.ATTENDING, null);
        assertEquals(1, result.size());
        assertEquals(70L, result.get(0).id());
    }

    /**
     * Sentinel for {@code acquireAdvisoryLock(null)}: must fail fast with
     * an {@link IllegalStateException} rather than
     * silently {@code return} (the previous behaviour). A null eventId
     * indicates a programming error in the calling path — the REST entry
     * points always resolve event-id from the path parameter — and the
     * old early-return would have bypassed the {@code pg_advisory_xact_lock}
     * that gates concurrent capacity insertions, allowing two parallel
     * inserts to race past {@code max_attendees}.
     *
     * <p>The method is {@code private}; reflection is the same pattern
     * used by {@code ModerationDomainSentinelsTest} elsewhere in this
     * codebase to exercise package-private invariants without leaking
     * visibility for test-only access.
     */
    @Test
    void acquireAdvisoryLock_nullEventId_throwsIllegalState() throws Exception {
        Method method = AttendanceService.class.getDeclaredMethod("acquireAdvisoryLock", Long.class);
        method.setAccessible(true);

        InvocationTargetException wrapper = assertThrows(
                InvocationTargetException.class,
                () -> method.invoke(service, (Long) null));

        Throwable cause = wrapper.getCause();
        assertTrue(cause instanceof IllegalStateException,
                "expected IllegalStateException, got " + (cause == null ? "null" : cause.getClass().getName()));
        assertTrue(cause.getMessage().contains("acquireAdvisoryLock called with null eventId"),
                "message must surface the null-eventId reason: " + cause.getMessage());
    }

    // ──────────────────────────────────────────────────────────────────
    // getUserParticipationEvents (public profile — SCRUM-141 follow-up)
    // ──────────────────────────────────────────────────────────────────

    @Test
    void getUserParticipationEvents_nullTarget_returnsEmptyList() {
        List<EventDTO> result = service.getUserParticipationEvents(null, null);
        assertTrue(result.isEmpty());
    }

    @SuppressWarnings("unchecked")
    @Test
    void getUserParticipationEvents_self_returnsPublishedAttendingWithCounts() {
        // caller == target (userId): the profilePublic gate does not apply.
        Attendance a = new Attendance();
        a.id = 800L; a.userId = userId; a.eventId = 80L;
        a.status = AttendanceStatus.ATTENDING; a.createdAt = LocalDateTime.now();
        PanacheMock.mock(Attendance.class);
        when(Attendance.findAllByUser(userId)).thenReturn(List.of(a));
        when(Attendance.countGroupedByStatus(any(List.class),
                eq(AttendanceStatus.ATTENDING), any())).thenReturn(Map.of(80L, 5L));
        when(Attendance.countGroupedByStatus(any(List.class),
                eq(AttendanceStatus.WAITLISTED), any())).thenReturn(Map.of(80L, 1L));
        // PUBLISHED-only enrichment: the service must pass "PUBLISHED" (an
        // unmatched stub would return null and NPE — so this asserts the filter).
        when(eventClient.findByIds(any(List.class), eq("PUBLISHED")))
                .thenReturn(List.of(event(80L, EventStatus.PUBLISHED, 10, creatorId, null)));

        List<EventDTO> result = service.getUserParticipationEvents(userId, null);
        assertEquals(1, result.size());
        assertEquals(80L, result.get(0).id());
        assertEquals(5L, result.get(0).attendingCount());
        assertEquals(1L, result.get(0).waitlistedCount());
    }

    @SuppressWarnings("unchecked")
    @Test
    void getUserParticipationEvents_publicTarget_returnsList() {
        Attendance a = new Attendance();
        a.id = 810L; a.userId = otherUserId; a.eventId = 81L;
        a.status = AttendanceStatus.ATTENDING; a.createdAt = LocalDateTime.now();
        PanacheMock.mock(Attendance.class);
        when(Attendance.findAllByUser(otherUserId)).thenReturn(List.of(a));
        when(Attendance.countGroupedByStatus(any(List.class), any(), any())).thenReturn(Map.of());
        when(eventClient.findByIds(any(List.class), eq("PUBLISHED")))
                .thenReturn(List.of(event(81L, EventStatus.PUBLISHED, 10, creatorId, null)));
        when(userClient.getAttendeeProjections(any())).thenReturn(List.of(
                new AttendeeProjection(otherUserId, "Other", null, true)));

        List<EventDTO> result = service.getUserParticipationEvents(otherUserId, null);
        assertEquals(1, result.size());
        assertEquals(81L, result.get(0).id());
    }

    @Test
    void getUserParticipationEvents_privateTarget_returnsEmptyList() {
        PanacheMock.mock(Attendance.class);
        when(userClient.getAttendeeProjections(any())).thenReturn(List.of(
                new AttendeeProjection(otherUserId, "Other", null, false)));

        List<EventDTO> result = service.getUserParticipationEvents(otherUserId, null);
        assertTrue(result.isEmpty());
    }

    @Test
    void getUserParticipationEvents_projectionMissing_failsClosedEmpty() {
        PanacheMock.mock(Attendance.class);
        // user-service returns no projection for the target → treat as private.
        when(userClient.getAttendeeProjections(any())).thenReturn(List.of());

        List<EventDTO> result = service.getUserParticipationEvents(otherUserId, null);
        assertTrue(result.isEmpty());
    }

    @SuppressWarnings("unchecked")
    @Test
    void getUserParticipationEvents_excludesWaitlistedRows() {
        // Self view with one ATTENDING (82) + one WAITLISTED (83) row. Only the
        // ATTENDING event id must reach the enrichment call.
        Attendance attending = new Attendance();
        attending.id = 820L; attending.userId = userId; attending.eventId = 82L;
        attending.status = AttendanceStatus.ATTENDING; attending.createdAt = LocalDateTime.now();
        Attendance waitlisted = new Attendance();
        waitlisted.id = 821L; waitlisted.userId = userId; waitlisted.eventId = 83L;
        waitlisted.status = AttendanceStatus.WAITLISTED; waitlisted.createdAt = LocalDateTime.now();
        PanacheMock.mock(Attendance.class);
        when(Attendance.findAllByUser(userId)).thenReturn(List.of(attending, waitlisted));
        when(Attendance.countGroupedByStatus(any(List.class), any(), any())).thenReturn(Map.of());
        // Echo back only the ids actually requested → proves 83 (WAITLISTED) was filtered out.
        when(eventClient.findByIds(any(List.class), eq("PUBLISHED"))).thenAnswer(inv -> {
            List<Long> ids = inv.getArgument(0);
            List<EventDTO> out = new ArrayList<>();
            if (ids.contains(82L)) out.add(event(82L, EventStatus.PUBLISHED, 10, creatorId, null));
            if (ids.contains(83L)) out.add(event(83L, EventStatus.PUBLISHED, 10, creatorId, null));
            return out;
        });

        List<EventDTO> result = service.getUserParticipationEvents(userId, null);
        assertEquals(1, result.size());
        assertEquals(82L, result.get(0).id());
    }
}
