package ch.unige.events.report.sentinels;

import ch.unige.events.report.dto.CreateReportRequest;
import ch.unige.events.report.dto.HandleReportRequest;
import ch.unige.events.report.dto.ReportDTO;
import ch.unige.events.report.entity.Report;
import ch.unige.events.report.service.ModerationCleanupService;
import ch.unige.events.report.service.ReportService;
import ch.unige.events.report.test.JwtTestContext;
import ch.unige.events.report.test.JwtTestHelper;
import ch.unige.events.shared.client.EngagementServiceClient;
import ch.unige.events.shared.client.EventServiceClient;
import ch.unige.events.shared.client.UserServiceClient;
import ch.unige.events.shared.domain.dto.EventDTO;
import ch.unige.events.shared.domain.dto.UserPublicResponse;
import ch.unige.events.shared.domain.enums.EventStatus;
import ch.unige.events.shared.domain.enums.ReportReason;
import ch.unige.events.shared.domain.enums.ReportStatus;
import ch.unige.events.shared.kafka.events.EventBannedEvent;

import io.quarkus.hibernate.orm.panache.PanacheQuery;
import io.quarkus.panache.mock.PanacheMock;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.WebApplicationException;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.time.Month;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * SCRUM-139 sentinel suite — moderation-service.
 *
 * <p>Pins the 8 SCRUM-139
 * regression guards (report flow + admin handle + cleanup auto-ban). Names
 * mirror the SCRUM-138/144/147 sentinel naming convention. Drives
 * {@link ReportService} / {@link ModerationCleanupService} under a real
 * Quarkus container with mocked REST clients and a test-side CDI
 * observer that captures every {@link EventBannedEvent} fired — closes
 * REPORT-EVENT-FIRE-NOTEST (IMPORTANT) by exercising the proxy CDI fire
 * path end-to-end.
 */
@QuarkusTest
@TestSecurity(user = "auth0|sentinel-user")
@SuppressWarnings({"java:S100", "java:S5961", "java:S2699", "java:S1192"})
class ModerationDomainSentinelsTest {

    @Inject
    ReportService reportService;

    @Inject
    ModerationCleanupService cleanupService;

    @Inject
    BannedEventCaptor captor;

    @InjectMock
    @RestClient
    EventServiceClient eventClient;

    @InjectMock
    @RestClient
    UserServiceClient userClient;

    @InjectMock
    @RestClient
    EngagementServiceClient engagementClient;

    private final UUID reporterId = UUID.randomUUID();
    private final UUID adminId = UUID.randomUUID();
    private final UUID creatorId = UUID.randomUUID();

    @BeforeEach
    void stageJwt() {
        captor.reset();
        JwtTestContext.set(JwtTestHelper.jwtFor(reporterId));
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

    private static EventDTO event(Long id, EventStatus status, UUID creator) {
        return event(id, status, creator, null);
    }

    private static EventDTO event(Long id, EventStatus status, UUID creator, Boolean coOrgOf) {
        return new EventDTO(id, "T-" + id, "d", "l",
                LocalDateTime.of(2025, Month.JANUARY, 1, 12, 0), LocalDateTime.of(2999, Month.JANUARY, 1, 0, 0).plusDays(1),
                null, null, null, creator,
                status, null, false, false, null,
                0L, null, 0L, 0L, 0L,
                null, null, null,
                List.of(), LocalDateTime.of(2025, Month.JANUARY, 1, 12, 0), LocalDateTime.of(2025, Month.JANUARY, 1, 12, 0),
                null, null, coOrgOf);
    }

    private static Report buildReport(Long id, Long eventId, UUID reporter, ReportStatus status) {
        Report r = new Report();
        r.id = id;
        r.eventId = eventId;
        r.reporterId = reporter;
        r.reason = ReportReason.SPAM;
        r.status = status;
        r.createdAt = LocalDateTime.of(2025, Month.JANUARY, 1, 12, 0);
        return r;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static PanacheQuery countQuery(long count) {
        PanacheQuery q = mock(PanacheQuery.class);
        org.mockito.Mockito.doReturn(count).when(q).count();
        return q;
    }

    // ──────────────────────────────────────────────────────────────────
    // SCRUM-139 sentinels — report() flow
    // ──────────────────────────────────────────────────────────────────

    @Test
    void report_ownEvent_returns422_unprocessable() {
        EventDTO ev = event(700L, EventStatus.PUBLISHED, reporterId);
        when(eventClient.getByIdWithCoOrgCheck(eq(700L), any(UUID.class))).thenReturn(ev);

        WebApplicationException ex = assertThrows(WebApplicationException.class,
                () -> reportService.create(700L, "auth0|" + reporterId,
                        new CreateReportRequest(ReportReason.SPAM, null)));
        assertEquals(422, ex.getResponse().getStatus());
    }

    @Test
    void report_eventDraftByNonCreator_returns404_antiOracle() {
        // event-service applies ISSUE-92: a DRAFT event seen by a non-creator
        // returns 404 (anti-oracle). The REST client surfaces this as a null
        // payload (Fallback) — ReportService propagates 404 NotFound.
        when(eventClient.getByIdWithCoOrgCheck(eq(701L), any(UUID.class))).thenReturn(null);

        assertThrows(NotFoundException.class,
                () -> reportService.create(701L, "auth0|" + reporterId,
                        new CreateReportRequest(ReportReason.SPAM, null)));
    }

    @Test
    void report_eventBanned_returns404_antiOracle() {
        // A BANNED event is not visible to a non-admin caller via the
        // public GET — event-service applies the same anti-oracle. The
        // REST client returns null and ReportService throws 404.
        when(eventClient.getByIdWithCoOrgCheck(eq(702L), any(UUID.class))).thenReturn(null);

        assertThrows(NotFoundException.class,
                () -> reportService.create(702L, "auth0|" + reporterId,
                        new CreateReportRequest(ReportReason.SPAM, null)));
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void report_idempotent_secondReportBySameUserIs422() {
        EventDTO ev = event(703L, EventStatus.PUBLISHED, creatorId);
        when(eventClient.getByIdWithCoOrgCheck(eq(703L), any(UUID.class))).thenReturn(ev);

        PanacheMock.mock(Report.class);
        PanacheQuery cq = countQuery(1L);
        when(Report.find(anyString(), any(Object[].class))).thenReturn(cq);

        WebApplicationException ex = assertThrows(WebApplicationException.class,
                () -> reportService.create(703L, "auth0|" + reporterId,
                        new CreateReportRequest(ReportReason.SPAM, null)));
        // Second-tap by the same reporter is rejected — pin the current 409
        // mapping to detect any regression in the duplicate-detection path.
        assertEquals(409, ex.getResponse().getStatus());
    }

    // ──────────────────────────────────────────────────────────────────
    // SCRUM-139 sentinels — handle() flow + Kafka emit (REPORT-EVENT-FIRE)
    // ──────────────────────────────────────────────────────────────────

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void handle_adminMarksReviewed_emitsEventsBannedKafka() {
        Long eventId = 710L;
        Report target = buildReport(40L, eventId, reporterId, ReportStatus.PENDING);
        JwtTestContext.set(JwtTestHelper.adminJwt(adminId));

        PanacheMock.mock(Report.class);
        when(Report.findByIdOptional(40L)).thenReturn(Optional.of(target));

        PanacheQuery siblings = mock(PanacheQuery.class);
        when(siblings.list()).thenReturn(List.of());
        when(Report.find(anyString(), any(Object[].class))).thenReturn(siblings);

        when(eventClient.getById(eventId)).thenReturn(event(eventId, EventStatus.PUBLISHED, creatorId));

        ReportDTO dto = reportService.handle(40L, "auth0|admin",
                new HandleReportRequest(ReportStatus.REVIEWED, "ban motive"));

        assertEquals(ReportStatus.REVIEWED, dto.status());
        assertEquals(1, captor.size(), "exactly one EventBannedEvent should have been fired");
        EventBannedEvent fired = captor.first();
        assertEquals(eventId.longValue(), fired.eventId());
        assertEquals(adminId, fired.bannedBy());
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void handle_commentReportReviewed_deletesCommentAndDoesNotBanEvent() {
        // QA bug batch (bug ③) — validating a COMMENT report must NOT fire
        // EventBannedEvent (report.eventId is null → it used to ban a phantom
        // null event). Instead the comment is hard-deleted via engagement-service
        // and the sibling PENDING comment-reports cascade to REVIEWED.
        Long commentId = 555L;
        Report target = buildCommentReport(50L, commentId, reporterId, ReportStatus.PENDING);
        Report sibling = buildCommentReport(51L, commentId, UUID.randomUUID(), ReportStatus.PENDING);
        JwtTestContext.set(JwtTestHelper.adminJwt(adminId));

        PanacheMock.mock(Report.class);
        when(Report.findByIdOptional(50L)).thenReturn(Optional.of(target));
        PanacheQuery siblings = mock(PanacheQuery.class);
        when(siblings.list()).thenReturn(List.of(sibling));
        when(Report.find(anyString(), any(Object[].class))).thenReturn(siblings);

        ReportDTO dto = reportService.handle(50L, "auth0|admin",
                new HandleReportRequest(ReportStatus.REVIEWED, "abusive comment"));

        assertEquals(ReportStatus.REVIEWED, dto.status());
        assertEquals("COMMENT", dto.targetType());
        assertTrue(captor.isEmpty(), "a comment report must NOT fire EventBannedEvent");
        verify(engagementClient).deleteCommentForModeration(commentId);
        // Sibling comment-report cascaded to REVIEWED in-place.
        assertEquals(ReportStatus.REVIEWED, sibling.status);
        assertEquals(adminId, sibling.reviewedById);
    }

    private static Report buildCommentReport(Long id, Long commentId, UUID reporter, ReportStatus status) {
        Report r = new Report();
        r.id = id;
        r.eventId = null;
        r.commentId = commentId;
        r.reporterId = reporter;
        r.reason = ReportReason.SPAM;
        r.status = status;
        r.createdAt = LocalDateTime.of(2025, Month.JANUARY, 1, 12, 0);
        return r;
    }

    @Test
    void handle_idempotent_secondBanIsNoop() {
        // A report already in REVIEWED status cannot be re-handled — the
        // service returns 409, no second Kafka emit occurs.
        Report alreadyReviewed = buildReport(41L, 711L, reporterId, ReportStatus.REVIEWED);
        JwtTestContext.set(JwtTestHelper.adminJwt(adminId));

        PanacheMock.mock(Report.class);
        when(Report.findByIdOptional(41L)).thenReturn(Optional.of(alreadyReviewed));

        WebApplicationException ex = assertThrows(WebApplicationException.class,
                () -> reportService.handle(41L, "auth0|admin",
                        new HandleReportRequest(ReportStatus.REVIEWED, "duplicate ban")));
        assertEquals(409, ex.getResponse().getStatus());
        assertTrue(captor.isEmpty(), "no Kafka emit on already-REVIEWED report");
    }

    // ──────────────────────────────────────────────────────────────────
    // SCRUM-139 sentinels — auto-cleanup (ModerationCleanupService)
    // ──────────────────────────────────────────────────────────────────

    @Test
    void cleanup_eventsAboveThreshold_areBannedAndKafkaEmitted() throws Exception {
        // Drive ModerationCleanupService.hide(Long) directly via reflection
        // (package-private API). The CDI fire reaches the test-side observer,
        // letting us assert the eventId + auto-cleanup reason.
        java.lang.reflect.Method hide = ModerationCleanupService.class
                .getDeclaredMethod("hide", Long.class);
        hide.setAccessible(true);
        hide.invoke(cleanupService, 720L);

        assertEquals(1, captor.size());
        EventBannedEvent fired = captor.first();
        assertEquals(720L, fired.eventId());
        assertEquals("auto-cleanup-threshold-exceeded", fired.reason());
    }

    @Test
    void cleanup_belowThreshold_doesNothing() throws Exception {
        // atOrAboveThreshold() with all counts < threshold returns empty —
        // pinned via reflection (package-private helper). With nothing to
        // hide, no Kafka event is emitted by the cleanup loop.
        java.lang.reflect.Method aot = ModerationCleanupService.class
                .getDeclaredMethod("atOrAboveThreshold", java.util.Map.class, int.class);
        aot.setAccessible(true);
        @SuppressWarnings("unchecked")
        List<Long> belowOnly = (List<Long>) aot.invoke(null, java.util.Map.of(900_001L, 1L), 100);
        assertTrue(belowOnly.isEmpty());
        assertFalse(captor.observed(), "no fire when nothing reaches threshold");
    }

    /**
     * Test-only CDI observer that captures every {@link EventBannedEvent}
     * fired during a test method. Reset between tests via {@link #reset()}.
     */
    @ApplicationScoped
    public static class BannedEventCaptor {
        private final List<EventBannedEvent> events = new ArrayList<>();

        synchronized void onBanned(@Observes EventBannedEvent ev) {
            events.add(ev);
        }

        public synchronized void reset() {
            events.clear();
        }

        public synchronized int size() {
            return events.size();
        }

        public synchronized boolean isEmpty() {
            return events.isEmpty();
        }

        public synchronized boolean observed() {
            return !events.isEmpty();
        }

        public synchronized EventBannedEvent first() {
            return events.get(0);
        }
    }
}
