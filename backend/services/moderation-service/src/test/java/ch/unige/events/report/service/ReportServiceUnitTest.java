package ch.unige.events.report.service;

import ch.unige.events.report.dto.CreateReportRequest;
import ch.unige.events.report.dto.HandleReportRequest;
import ch.unige.events.report.entity.Report;
import ch.unige.events.shared.client.EngagementServiceClient;
import ch.unige.events.shared.client.EventServiceClient;
import ch.unige.events.shared.client.UserServiceClient;
import ch.unige.events.shared.domain.dto.CommentVisibilityProjection;
import ch.unige.events.shared.domain.dto.EventDTO;
import ch.unige.events.shared.domain.enums.EventStatus;
import ch.unige.events.shared.domain.enums.ReportReason;
import ch.unige.events.shared.domain.enums.ReportStatus;
import ch.unige.events.shared.kafka.events.EventBannedEvent;

import io.quarkus.hibernate.orm.panache.PanacheQuery;
import io.quarkus.panache.mock.PanacheMock;
import io.quarkus.test.TestTransaction;
import io.quarkus.test.junit.QuarkusTest;
import ch.unige.events.shared.domain.projections.CallerIdentity;

import jakarta.enterprise.event.Event;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceException;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.WebApplicationException;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.lang.reflect.Field;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import ch.unige.events.report.dto.ReportDTO;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Plain unit tests (no @QuarkusTest, no CDI proxy) — used to exercise
 * the {@code Event<EventBannedEvent>.fire(...)} branches that cannot be
 * verified through the CDI proxy in {@link ReportServiceTest}. We
 * instantiate the service directly and inject mocked collaborators via
 * reflection.
 *
 * <p>This complements {@link ReportServiceTest} by adding coverage for
 * the Kafka-event firing paths in {@code handle(...)}.
 */
@QuarkusTest
class ReportServiceUnitTest {

    private final UUID reporterId = UUID.randomUUID();
    private final UUID adminId = UUID.randomUUID();
    private final UUID creatorId = UUID.randomUUID();

    private static EventDTO event(Long id, EventStatus status, UUID creator) {
        return new EventDTO(id, "T-" + id, "d", "l",
                LocalDateTime.now(), LocalDateTime.now().plusDays(1),
                null, null, null, creator,
                status, null, false, false, null,
                0L, null, 0L, 0L, 0L,
                null, null, null,
                List.of(), LocalDateTime.now(), LocalDateTime.now(),
                null, null, null);
    }

    private static Report buildReport(Long id, Long eventId, UUID reporterId, ReportStatus status) {
        Report r = new Report();
        r.id = id;
        r.eventId = eventId;
        r.reporterId = reporterId;
        r.reason = ReportReason.SPAM;
        r.status = status;
        r.createdAt = LocalDateTime.now();
        return r;
    }

    private static ReportService buildService(Event<EventBannedEvent> bannedEvent,
                                              EventServiceClient eventClient,
                                              UserServiceClient userClient,
                                              UUID callerUuid) throws Exception {
        ReportService svc = new ReportService();
        setField(svc, "bannedEvent", bannedEvent);
        setField(svc, "eventClient", eventClient);
        setField(svc, "userClient", userClient);
        setField(svc, "callerIdentity", mockCaller(callerUuid));
        return svc;
    }

    private static CallerIdentity mockCaller(UUID callerUuid) {
        CallerIdentity callerIdentity = mock(CallerIdentity.class);
        // The real RequestScoped CallerIdentity throws when no UUID resolves ;
        // here we drive requireUuid()==null to reach the service-side
        // "profile not found" guards (unreachable through the CDI proxy).
        when(callerIdentity.requireUuid()).thenReturn(callerUuid);
        return callerIdentity;
    }

    private static void setField(Object target, String name, Object value) throws Exception {
        Field f = ReportService.class.getDeclaredField(name);
        f.setAccessible(true);
        f.set(target, value);
    }

    @AfterEach
    void resetPanache() {
        PanacheMock.reset();
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    @Test
    void handle_reviewed_firesEventBannedEvent_withModerationNote() throws Exception {
        Long eventId = 5001L;
        Report target = buildReport(50L, eventId, reporterId, ReportStatus.PENDING);
        Report sibling = buildReport(51L, eventId, UUID.randomUUID(), ReportStatus.PENDING);

        Event<EventBannedEvent> bannedEvent = mock(Event.class);
        EventServiceClient eventClient = mock(EventServiceClient.class);
        UserServiceClient userClient = mock(UserServiceClient.class);

        ReportService svc = buildService(bannedEvent, eventClient, userClient, adminId);

        PanacheMock.mock(Report.class);
        when(Report.findByIdOptional(50L)).thenReturn(Optional.of(target));
        PanacheQuery siblingQ = mock(PanacheQuery.class);
        when(siblingQ.list()).thenReturn(List.of(sibling));
        when(Report.find(anyString(), any(Object[].class))).thenReturn(siblingQ);

        when(eventClient.getById(eventId)).thenReturn(event(eventId, EventStatus.PUBLISHED, creatorId));

        svc.handle(50L, "auth0|admin",
                new HandleReportRequest(ReportStatus.REVIEWED, "confirmed spam"));

        ArgumentCaptor<EventBannedEvent> cap = ArgumentCaptor.forClass(EventBannedEvent.class);
        verify(bannedEvent, atLeastOnce()).fire(cap.capture());
        EventBannedEvent fired = cap.getValue();
        assertEquals(eventId.longValue(), fired.eventId());
        assertEquals("confirmed spam", fired.reason());
        assertEquals(adminId, fired.bannedBy());
        // Sibling cascaded.
        assertEquals(ReportStatus.REVIEWED, sibling.status);
        assertEquals(adminId, sibling.reviewedById);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    @Test
    void handle_reviewed_nullModerationNote_usesDefaultReason() throws Exception {
        Long eventId = 5002L;
        Report target = buildReport(60L, eventId, reporterId, ReportStatus.PENDING);

        Event<EventBannedEvent> bannedEvent = mock(Event.class);
        EventServiceClient eventClient = mock(EventServiceClient.class);
        UserServiceClient userClient = mock(UserServiceClient.class);

        ReportService svc = buildService(bannedEvent, eventClient, userClient, adminId);

        PanacheMock.mock(Report.class);
        when(Report.findByIdOptional(60L)).thenReturn(Optional.of(target));
        PanacheQuery siblingQ = mock(PanacheQuery.class);
        when(siblingQ.list()).thenReturn(List.of());
        when(Report.find(anyString(), any(Object[].class))).thenReturn(siblingQ);

        when(eventClient.getById(eventId)).thenReturn(event(eventId, EventStatus.PUBLISHED, creatorId));

        svc.handle(60L, "auth0|admin",
                new HandleReportRequest(ReportStatus.REVIEWED, null));

        ArgumentCaptor<EventBannedEvent> cap = ArgumentCaptor.forClass(EventBannedEvent.class);
        verify(bannedEvent).fire(cap.capture());
        assertEquals("admin-ban", cap.getValue().reason());
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    @Test
    void handle_dismissed_doesNotFireEventBannedEvent() throws Exception {
        Long eventId = 5003L;
        Report target = buildReport(70L, eventId, reporterId, ReportStatus.PENDING);

        Event<EventBannedEvent> bannedEvent = mock(Event.class);
        EventServiceClient eventClient = mock(EventServiceClient.class);
        UserServiceClient userClient = mock(UserServiceClient.class);

        ReportService svc = buildService(bannedEvent, eventClient, userClient, adminId);

        PanacheMock.mock(Report.class);
        when(Report.findByIdOptional(70L)).thenReturn(Optional.of(target));

        when(eventClient.getById(eventId)).thenReturn(event(eventId, EventStatus.PUBLISHED, creatorId));

        svc.handle(70L, "auth0|admin",
                new HandleReportRequest(ReportStatus.DISMISSED, "false alarm"));

        verify(bannedEvent, never()).fire(any(EventBannedEvent.class));
    }

    /** Branches in the static helpers (badRequest / conflict / unprocessable). */
    @Test
    void staticHelpers_buildExpectedResponses() {
        WebApplicationException bad = ReportService.badRequest("e", "m");
        assertEquals(400, bad.getResponse().getStatus());
        WebApplicationException conf = ReportService.conflict("e", "m");
        assertEquals(409, conf.getResponse().getStatus());
        WebApplicationException unp = ReportService.unprocessable("e", "m");
        assertEquals(422, unp.getResponse().getStatus());
    }

    @Test
    void handle_reviewed_unknownReportThrowsBeforeFire() throws Exception {
        Event<EventBannedEvent> bannedEvent = mock(Event.class);
        EventServiceClient eventClient = mock(EventServiceClient.class);
        UserServiceClient userClient = mock(UserServiceClient.class);
        ReportService svc = buildService(bannedEvent, eventClient, userClient, adminId);

        PanacheMock.mock(Report.class);
        when(Report.findByIdOptional(999L)).thenReturn(Optional.empty());

        assertThrows(jakarta.ws.rs.NotFoundException.class,
                () -> svc.handle(999L, "auth0|admin",
                        new HandleReportRequest(ReportStatus.REVIEWED, null)));
        verify(bannedEvent, never()).fire(any(EventBannedEvent.class));
    }

    // ──────────────────────────────────────────────────────────────────
    // requireUuid()==null guards — only reachable by driving CallerIdentity
    // to return null (the RequestScoped bean throws instead). Each guard maps
    // the missing-profile state to a 404 anti-oracle envelope.
    // ──────────────────────────────────────────────────────────────────

    @Test
    void create_callerUuidNull_throwsProfileNotFound() throws Exception {
        ReportService svc = buildService(mock(Event.class), mock(EventServiceClient.class),
                mock(UserServiceClient.class), null);

        assertThrows(NotFoundException.class,
                () -> svc.create(800L, "auth0|ghost",
                        new CreateReportRequest(ReportReason.SPAM, null)));
    }

    @Test
    void handle_callerUuidNull_throwsProfileNotFound() throws Exception {
        Report target = buildReport(80L, 801L, reporterId, ReportStatus.PENDING);
        ReportService svc = buildService(mock(Event.class), mock(EventServiceClient.class),
                mock(UserServiceClient.class), null);

        PanacheMock.mock(Report.class);
        when(Report.findByIdOptional(80L)).thenReturn(Optional.of(target));

        assertThrows(NotFoundException.class,
                () -> svc.handle(80L, "auth0|ghost",
                        new HandleReportRequest(ReportStatus.DISMISSED, null)));
    }

    @Test
    void createForComment_callerUuidNull_throwsProfileNotFound() throws Exception {
        ReportService svc = buildService(mock(Event.class), mock(EventServiceClient.class),
                mock(UserServiceClient.class), null);

        assertThrows(NotFoundException.class,
                () -> svc.createForComment(810L, "auth0|ghost",
                        new CreateReportRequest(ReportReason.SPAM, null)));
    }

    // ──────────────────────────────────────────────────────────────────
    // createForComment double-tap — the flush() PersistenceException branch
    // (183-188). A real Postgres surfaces the partial-UK violation ; here a
    // mocked EntityManager throws so both arms of the catch are covered.
    // ──────────────────────────────────────────────────────────────────

    private ReportService buildCommentService(EntityManager em,
                                              EngagementServiceClient engagementClient,
                                              UUID callerUuid) throws Exception {
        ReportService svc = buildService(mock(Event.class), mock(EventServiceClient.class),
                mock(UserServiceClient.class), callerUuid);
        setField(svc, "engagementClient", engagementClient);
        setField(svc, "entityManager", em);
        return svc;
    }

    private static PersistenceException ukViolation(String constraintName) {
        ConstraintViolationException cve = new ConstraintViolationException(
                "duplicate key value violates unique constraint",
                new SQLException("ERROR: duplicate key", "23505"),
                constraintName);
        return new PersistenceException(cve);
    }

    @Test
    @TestTransaction
    void createForComment_flushPartialUkConflict_throws409() throws Exception {
        EntityManager em = mock(EntityManager.class);
        EngagementServiceClient engagementClient = mock(EngagementServiceClient.class);
        ReportService svc = buildCommentService(em, engagementClient, reporterId);

        when(engagementClient.getCommentVisibility(eq(900L), any(UUID.class)))
                .thenReturn(new CommentVisibilityProjection(900L, 1001L, creatorId, true));
        PanacheMock.mock(Report.class);
        doThrow(ukViolation("uq_report_comment_partial")).when(em).flush();

        WebApplicationException ex = assertThrows(WebApplicationException.class,
                () -> svc.createForComment(900L, "auth0|x",
                        new CreateReportRequest(ReportReason.SPAM, "dup")));
        assertEquals(409, ex.getResponse().getStatus());
    }

    @Test
    @TestTransaction
    void createForComment_flushNonConflict_rethrows() throws Exception {
        EntityManager em = mock(EntityManager.class);
        EngagementServiceClient engagementClient = mock(EngagementServiceClient.class);
        ReportService svc = buildCommentService(em, engagementClient, reporterId);

        when(engagementClient.getCommentVisibility(eq(901L), any(UUID.class)))
                .thenReturn(new CommentVisibilityProjection(901L, 1001L, creatorId, true));
        PanacheMock.mock(Report.class);
        // A genuine DB error (not the partial UK) must surface as-is, not 409.
        PersistenceException raw = ukViolation("some_other_constraint");
        doThrow(raw).when(em).flush();

        PersistenceException thrown = assertThrows(PersistenceException.class,
                () -> svc.createForComment(901L, "auth0|x",
                        new CreateReportRequest(ReportReason.SPAM, "boom")));
        assertSame(raw, thrown);
    }

    // ──────────────────────────────────────────────────────────────────
    // safeGetUser catch arms (328 / 331-336). A @RestClient userClient
    // would be wrapped by Fault Tolerance @Fallback (the throw never reaches
    // the service catch) — so we hand-wire a PLAIN Mockito userClient whose
    // getById(...) throws for real. handle(DISMISSED) is the simplest entry:
    // no Kafka fire / cascade, eventClient.getById is stubbed first (line 290)
    // so we actually reach safeGetUser(report.reporterId) at line 291.
    // ──────────────────────────────────────────────────────────────────

    @SuppressWarnings({"rawtypes", "unchecked"})
    @Test
    void handle_safeGetUser_notFound_returnsNullReporter() throws Exception {
        Long eventId = 5101L;
        Report target = buildReport(110L, eventId, reporterId, ReportStatus.PENDING);

        EventServiceClient eventClient = mock(EventServiceClient.class);
        UserServiceClient userClient = mock(UserServiceClient.class);
        ReportService svc = buildService(mock(Event.class), eventClient, userClient, adminId);

        PanacheMock.mock(Report.class);
        when(Report.findByIdOptional(110L)).thenReturn(Optional.of(target));
        when(eventClient.getById(eventId)).thenReturn(event(eventId, EventStatus.PUBLISHED, creatorId));
        // Semantic absence (328): user hard-deleted / never existed → null.
        when(userClient.getById(reporterId)).thenThrow(new NotFoundException("gone"));

        ReportDTO dto = svc.handle(110L, "auth0|admin",
                new HandleReportRequest(ReportStatus.DISMISSED, "no reporter"));

        assertNull(dto.reporterDisplayName());
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    @Test
    void handle_safeGetUser_infraFailure_returnsNullReporter() throws Exception {
        Long eventId = 5102L;
        Report target = buildReport(120L, eventId, reporterId, ReportStatus.PENDING);

        EventServiceClient eventClient = mock(EventServiceClient.class);
        UserServiceClient userClient = mock(UserServiceClient.class);
        ReportService svc = buildService(mock(Event.class), eventClient, userClient, adminId);

        PanacheMock.mock(Report.class);
        when(Report.findByIdOptional(120L)).thenReturn(Optional.of(target));
        when(eventClient.getById(eventId)).thenReturn(event(eventId, EventStatus.PUBLISHED, creatorId));
        // Infra failure (331-336): CB open / timeout → logged, returns null.
        when(userClient.getById(reporterId)).thenThrow(new RuntimeException("CB open"));

        ReportDTO dto = svc.handle(120L, "auth0|admin",
                new HandleReportRequest(ReportStatus.DISMISSED, "downstream down"));

        assertNull(dto.reporterDisplayName());
    }

    // ──────────────────────────────────────────────────────────────────
    // bulkFetchEvents empty-set short-circuit (309). A comment-only report
    // has eventId==null, so listByStatus never adds it to eventIds → the set
    // stays empty and bulkFetchEvents returns Map.of() WITHOUT calling
    // eventClient. ReportDTO.from tolerates the null event (eventsById.get(null)).
    // ──────────────────────────────────────────────────────────────────

    @SuppressWarnings({"rawtypes", "unchecked"})
    @Test
    void listByStatus_commentOnlyReport_skipsBulkFetchEvents() throws Exception {
        Report commentReport = buildReport(130L, null, reporterId, ReportStatus.PENDING);
        commentReport.commentId = 7001L;

        EventServiceClient eventClient = mock(EventServiceClient.class);
        UserServiceClient userClient = mock(UserServiceClient.class);
        ReportService svc = buildService(mock(Event.class), eventClient, userClient, adminId);

        PanacheMock.mock(Report.class);
        PanacheQuery pageQ = mock(PanacheQuery.class);
        when(pageQ.page(eq(0), eq(20))).thenReturn(pageQ);
        when(pageQ.list()).thenReturn(List.of(commentReport));
        when(Report.find(anyString(), any(Object[].class))).thenReturn(pageQ);
        when(userClient.getById(reporterId)).thenReturn(null);

        List<ReportDTO> result = svc.listByStatus(ReportStatus.PENDING, 0, 20);

        assertEquals(1, result.size());
        assertNull(result.get(0).eventTitle());
        // Empty eventIds → bulkFetchEvents short-circuits, never hits eventClient.
        verify(eventClient, never()).findByIds(any(), any());
    }
}
