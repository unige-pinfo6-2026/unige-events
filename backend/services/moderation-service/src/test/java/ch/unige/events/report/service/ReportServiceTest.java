package ch.unige.events.report.service;

import ch.unige.events.report.dto.CreateReportRequest;
import ch.unige.events.report.dto.HandleReportRequest;
import ch.unige.events.report.dto.ReportDTO;
import ch.unige.events.report.entity.Report;
import ch.unige.events.report.test.JwtTestContext;
import ch.unige.events.report.test.JwtTestHelper;
import ch.unige.events.shared.client.EngagementServiceClient;
import ch.unige.events.shared.client.EventServiceClient;
import ch.unige.events.shared.client.UserServiceClient;
import ch.unige.events.shared.domain.dto.CommentContentProjection;
import ch.unige.events.shared.domain.dto.EventDTO;
import ch.unige.events.shared.domain.dto.UserPublicResponse;
import ch.unige.events.shared.domain.enums.EventStatus;
import ch.unige.events.shared.domain.enums.ReportReason;
import ch.unige.events.shared.domain.enums.ReportStatus;

import io.quarkus.hibernate.orm.panache.PanacheQuery;
import io.quarkus.panache.mock.PanacheMock;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.WebApplicationException;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.time.Month;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit-test coverage for {@link ReportService}. Uses {@link PanacheMock}
 * for the static {@link Report} finders, {@link InjectMock} for cross
 * service REST clients, and {@link JwtTestContext} for JWT staging.
 */
@QuarkusTest
class ReportServiceTest {

    @Inject
    ReportService service;

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

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static PanacheQuery makeCountQuery(long count) {
        // Build the mock OUTSIDE any when(Report.find(...)) call to avoid
        // unfinished-stubbing issues from chained mock interactions.
        PanacheQuery q = mock(PanacheQuery.class);
        org.mockito.Mockito.doReturn(count).when(q).count();
        return q;
    }

    private static Report buildReport(Long id, Long eventId, UUID reporterId,
                                      ReportStatus status) {
        Report r = new Report();
        r.id = id;
        r.eventId = eventId;
        r.reporterId = reporterId;
        r.reason = ReportReason.SPAM;
        r.status = status;
        r.createdAt = LocalDateTime.of(2025, Month.JANUARY, 1, 12, 0);
        return r;
    }

    // ──────────────────────────────────────────────────────────────────
    // create(...)
    // ──────────────────────────────────────────────────────────────────

    @Test
    void create_publishedEvent_persistsAndReturnsDTO() {
        EventDTO ev = event(101L, EventStatus.PUBLISHED, creatorId);
        when(eventClient.getByIdWithCoOrgCheck(eq(101L), any(UUID.class))).thenReturn(ev);

        PanacheMock.mock(Report.class);
        PanacheQuery cq0 = makeCountQuery(0L);
        when(Report.find(anyString(), any(Object[].class))).thenReturn(cq0);

        ReportDTO dto = service.create(101L, "auth0|" + reporterId,
                new CreateReportRequest(ReportReason.SPAM, "spam!"));

        assertNotNull(dto);
        assertEquals(101L, dto.eventId());
        assertEquals(reporterId, dto.reporterId());
        assertEquals(ReportReason.SPAM, dto.reason());
        assertEquals("spam!", dto.description());
        assertEquals(ReportStatus.PENDING, dto.status());
    }

    @Test
    void create_unknownEvent_throwsNotFound() {
        when(eventClient.getByIdWithCoOrgCheck(eq(102L), any(UUID.class))).thenReturn(null);
        assertThrows(NotFoundException.class,
                () -> service.create(102L, "auth0|" + reporterId,
                        new CreateReportRequest(ReportReason.SPAM, null)));
    }

    @Test
    void create_anonymousJwt_throwsNotFound() {
        JwtTestContext.set(JwtTestHelper.anonymous());
        assertThrows(NotFoundException.class,
                () -> service.create(103L, "auth0|ghost",
                        new CreateReportRequest(ReportReason.SPAM, null)));
    }

    @Test
    void create_draftEvent_throwsBadRequest() {
        EventDTO ev = event(104L, EventStatus.DRAFT, creatorId);
        when(eventClient.getByIdWithCoOrgCheck(eq(104L), any(UUID.class))).thenReturn(ev);
        WebApplicationException ex = assertThrows(WebApplicationException.class,
                () -> service.create(104L, "auth0|" + reporterId,
                        new CreateReportRequest(ReportReason.SPAM, null)));
        assertEquals(400, ex.getResponse().getStatus());
    }

    @Test
    void create_cancelledEvent_throwsBadRequest() {
        EventDTO ev = event(105L, EventStatus.CANCELLED, creatorId);
        when(eventClient.getByIdWithCoOrgCheck(eq(105L), any(UUID.class))).thenReturn(ev);
        WebApplicationException ex = assertThrows(WebApplicationException.class,
                () -> service.create(105L, "auth0|" + reporterId,
                        new CreateReportRequest(ReportReason.SPAM, null)));
        assertEquals(400, ex.getResponse().getStatus());
    }

    @Test
    void create_bannedEvent_throwsBadRequest() {
        EventDTO ev = event(106L, EventStatus.BANNED, creatorId);
        when(eventClient.getByIdWithCoOrgCheck(eq(106L), any(UUID.class))).thenReturn(ev);
        WebApplicationException ex = assertThrows(WebApplicationException.class,
                () -> service.create(106L, "auth0|" + reporterId,
                        new CreateReportRequest(ReportReason.SPAM, null)));
        assertEquals(400, ex.getResponse().getStatus());
    }

    @Test
    void create_ownEventByCreator_throws422() {
        EventDTO ev = event(107L, EventStatus.PUBLISHED, reporterId);
        when(eventClient.getByIdWithCoOrgCheck(eq(107L), any(UUID.class))).thenReturn(ev);
        WebApplicationException ex = assertThrows(WebApplicationException.class,
                () -> service.create(107L, "auth0|" + reporterId,
                        new CreateReportRequest(ReportReason.SPAM, null)));
        assertEquals(422, ex.getResponse().getStatus());
    }

    @Test
    void create_byCoOrganizer_throws422() {
        EventDTO ev = event(108L, EventStatus.PUBLISHED, creatorId, Boolean.TRUE);
        when(eventClient.getByIdWithCoOrgCheck(eq(108L), any(UUID.class))).thenReturn(ev);
        WebApplicationException ex = assertThrows(WebApplicationException.class,
                () -> service.create(108L, "auth0|" + reporterId,
                        new CreateReportRequest(ReportReason.SPAM, null)));
        assertEquals(422, ex.getResponse().getStatus());
    }

    @Test
    void create_alreadyReported_throwsConflict() {
        EventDTO ev = event(109L, EventStatus.PUBLISHED, creatorId);
        when(eventClient.getByIdWithCoOrgCheck(eq(109L), any(UUID.class))).thenReturn(ev);
        PanacheMock.mock(Report.class);
        PanacheQuery cq1 = makeCountQuery(1L);
        when(Report.find(anyString(), any(Object[].class))).thenReturn(cq1);

        WebApplicationException ex = assertThrows(WebApplicationException.class,
                () -> service.create(109L, "auth0|" + reporterId,
                        new CreateReportRequest(ReportReason.SPAM, null)));
        assertEquals(409, ex.getResponse().getStatus());
    }

    @Test
    void create_userClientThrows_swallowed_dtoStillReturned() {
        EventDTO ev = event(110L, EventStatus.PUBLISHED, creatorId);
        when(eventClient.getByIdWithCoOrgCheck(eq(110L), any(UUID.class))).thenReturn(ev);
        when(userClient.getById(any(UUID.class))).thenThrow(new RuntimeException("user-svc down"));

        PanacheMock.mock(Report.class);
        PanacheQuery cq0 = makeCountQuery(0L);
        when(Report.find(anyString(), any(Object[].class))).thenReturn(cq0);

        ReportDTO dto = service.create(110L, "auth0|" + reporterId,
                new CreateReportRequest(ReportReason.OTHER, null));
        assertNotNull(dto);
        // Reporter display name falls back to null when user-svc errors.
        assertNull(dto.reporterDisplayName());
    }

    // ──────────────────────────────────────────────────────────────────
    // listByStatus(...)
    // ──────────────────────────────────────────────────────────────────

    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    void listByStatus_pendingDefault_returnsEnrichedDTOs() {
        Report r1 = buildReport(1L, 200L, reporterId, ReportStatus.PENDING);
        Report r2 = buildReport(2L, 201L, reporterId, ReportStatus.PENDING);

        PanacheMock.mock(Report.class);
        PanacheQuery q = mock(PanacheQuery.class);
        when(q.page(0, 20)).thenReturn(q);
        when(q.list()).thenReturn(List.of(r1, r2));
        when(Report.find(anyString(), any(Object[].class))).thenReturn(q);

        when(eventClient.findByIds(any(List.class), eq(null))).thenReturn(List.of(
                event(200L, EventStatus.PUBLISHED, creatorId),
                event(201L, EventStatus.PUBLISHED, creatorId)
        ));

        List<ReportDTO> result = service.listByStatus(null, 0, 20);
        assertEquals(2, result.size());
        assertNotNull(result.get(0).eventTitle());
        assertNotNull(result.get(0).reporterDisplayName());
    }

    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    void listByStatus_explicitStatus_works() {
        Report r1 = buildReport(3L, 202L, reporterId, ReportStatus.REVIEWED);

        PanacheMock.mock(Report.class);
        PanacheQuery q = mock(PanacheQuery.class);
        when(q.page(0, 20)).thenReturn(q);
        when(q.list()).thenReturn(List.of(r1));
        when(Report.find(anyString(), any(Object[].class))).thenReturn(q);

        when(eventClient.findByIds(any(List.class), eq(null))).thenReturn(List.of(
                event(202L, EventStatus.PUBLISHED, creatorId)
        ));

        List<ReportDTO> result = service.listByStatus(ReportStatus.REVIEWED, 0, 20);
        assertEquals(1, result.size());
    }

    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    void listByStatus_emptyResult_returnsEmptyList() {
        PanacheMock.mock(Report.class);
        PanacheQuery q = mock(PanacheQuery.class);
        when(q.page(0, 20)).thenReturn(q);
        when(q.list()).thenReturn(List.of());
        when(Report.find(anyString(), any(Object[].class))).thenReturn(q);

        List<ReportDTO> result = service.listByStatus(ReportStatus.PENDING, 0, 20);
        assertTrue(result.isEmpty());
    }

    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    void listByStatus_eventClientReturnsNull_noNpe() {
        Report r1 = buildReport(4L, 203L, reporterId, ReportStatus.PENDING);
        PanacheMock.mock(Report.class);
        PanacheQuery q = mock(PanacheQuery.class);
        when(q.page(0, 20)).thenReturn(q);
        when(q.list()).thenReturn(List.of(r1));
        when(Report.find(anyString(), any(Object[].class))).thenReturn(q);

        when(eventClient.findByIds(any(List.class), eq(null))).thenReturn(null);

        List<ReportDTO> result = service.listByStatus(ReportStatus.PENDING, 0, 20);
        assertEquals(1, result.size());
        assertNull(result.get(0).eventTitle());
    }

    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    void listByStatus_allCommentOnlyReports_returnsListWithoutNpe() {
        // Regression for the bulkFetchEvents null-key NPE: a page consisting
        // solely of comment-only reports (eventId == null) yields an empty
        // eventIds set, so bulkFetchEvents returns an EMPTY MAP and the
        // enrichment map() then does eventsById.get(null) — which must NOT
        // throw (it did on the immutable Map.of(), giving the admin a 500).
        // Covers the empty-set guard (308/309) and the eventId==null branch
        // of the id-collection loop (236), without calling event-service.
        Report c1 = buildReport(5L, null, reporterId, ReportStatus.PENDING);
        Report c2 = buildReport(6L, null, reporterId, ReportStatus.PENDING);
        PanacheMock.mock(Report.class);
        PanacheQuery q = mock(PanacheQuery.class);
        when(q.page(0, 20)).thenReturn(q);
        when(q.list()).thenReturn(List.of(c1, c2));
        when(Report.find(anyString(), any(Object[].class))).thenReturn(q);

        List<ReportDTO> result = service.listByStatus(ReportStatus.PENDING, 0, 20);

        assertEquals(2, result.size());
        assertNull(result.get(0).eventTitle());
        verify(eventClient, never()).findByIds(any(), any());
    }

    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    void listByStatus_commentReport_enrichesCommentContentAndTargetType() {
        // QA bug batch (bug ③) — a comment-bound report (commentId set, eventId
        // null) is enriched with its content via the engagement internal batch
        // endpoint, and exposes targetType=COMMENT so the admin UI renders it
        // distinctly from an event report.
        Report c = buildCommentReport(8L, 808L, reporterId, ReportStatus.PENDING);
        PanacheMock.mock(Report.class);
        PanacheQuery q = mock(PanacheQuery.class);
        when(q.page(0, 20)).thenReturn(q);
        when(q.list()).thenReturn(List.of(c));
        when(Report.find(anyString(), any(Object[].class))).thenReturn(q);

        when(engagementClient.getCommentsByIds(any(List.class))).thenReturn(List.of(
                new CommentContentProjection(808L, 700L, "this is the reported body")
        ));

        List<ReportDTO> result = service.listByStatus(ReportStatus.PENDING, 0, 20);

        assertEquals(1, result.size());
        ReportDTO dto = result.get(0);
        assertEquals("COMMENT", dto.targetType());
        assertEquals(808L, dto.commentId());
        assertNull(dto.eventId());
        assertNull(dto.eventTitle());
        assertEquals("this is the reported body", dto.commentContent());
        // No event enrichment for a comment-only page.
        verify(eventClient, never()).findByIds(any(), any());
    }

    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    void listByStatus_commentReport_nullClientResponse_rendersWithoutContent() {
        // Resilience (bulkFetchCommentContent null-guard): if engagement-service's
        // batch returns null instead of its fallback's empty list, the enrichment
        // degrades to an empty map — the comment report still lists, just without
        // its body — rather than NPE-ing the whole admin page.
        Report c = buildCommentReport(9L, 818L, reporterId, ReportStatus.PENDING);
        PanacheMock.mock(Report.class);
        PanacheQuery q = mock(PanacheQuery.class);
        when(q.page(0, 20)).thenReturn(q);
        when(q.list()).thenReturn(List.of(c));
        when(Report.find(anyString(), any(Object[].class))).thenReturn(q);

        when(engagementClient.getCommentsByIds(any(List.class))).thenReturn(null);

        List<ReportDTO> result = service.listByStatus(ReportStatus.PENDING, 0, 20);

        assertEquals(1, result.size());
        ReportDTO dto = result.get(0);
        assertEquals("COMMENT", dto.targetType());
        assertEquals(818L, dto.commentId());
        assertNull(dto.commentContent());
    }

    @Test
    void handle_commentReportReviewed_deletesCommentSkipsEventFetch() {
        // QA bug batch (bug ③) — REVIEWED on a comment report deletes the comment
        // via engagement-service and never fetches/bans an event (eventId null).
        Report r = buildCommentReport(60L, 909L, reporterId, ReportStatus.PENDING);
        JwtTestContext.set(JwtTestHelper.adminJwt(adminId));

        PanacheMock.mock(Report.class);
        when(Report.findByIdOptional(60L)).thenReturn(Optional.of(r));
        PanacheQuery siblingQ = mock(PanacheQuery.class);
        when(siblingQ.list()).thenReturn(List.of());
        when(Report.find(anyString(), any(Object[].class))).thenReturn(siblingQ);

        ReportDTO dto = service.handle(60L, "auth0|admin",
                new HandleReportRequest(ReportStatus.REVIEWED, "abusive"));

        assertEquals(ReportStatus.REVIEWED, dto.status());
        assertEquals("COMMENT", dto.targetType());
        verify(engagementClient).deleteCommentForModeration(909L);
        verify(eventClient, never()).getById(anyLong());
    }

    @Test
    void handle_commentReportAlreadyDeleted_swallows404() {
        // Idempotency: the comment was already gone (404) — deleteReportedComment
        // swallows the NotFoundException, the report still transitions REVIEWED.
        Report r = buildCommentReport(61L, 910L, reporterId, ReportStatus.PENDING);
        JwtTestContext.set(JwtTestHelper.adminJwt(adminId));

        PanacheMock.mock(Report.class);
        when(Report.findByIdOptional(61L)).thenReturn(Optional.of(r));
        PanacheQuery siblingQ = mock(PanacheQuery.class);
        when(siblingQ.list()).thenReturn(List.of());
        when(Report.find(anyString(), any(Object[].class))).thenReturn(siblingQ);
        org.mockito.Mockito.doThrow(new NotFoundException("already gone"))
                .when(engagementClient).deleteCommentForModeration(910L);

        ReportDTO dto = service.handle(61L, "auth0|admin",
                new HandleReportRequest(ReportStatus.REVIEWED, null));

        assertEquals(ReportStatus.REVIEWED, dto.status());
    }

    private static Report buildCommentReport(Long id, Long commentId, UUID reporter,
                                             ReportStatus status) {
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
    @SuppressWarnings({"rawtypes", "unchecked"})
    void listByStatus_nullReporterId_skipsUserEnrichment() {
        // Legacy row with a null reporter_id: the userIds set stays empty so the
        // enrichment loop never queries user-service. Covers the
        // reporterId==null branch of the id-collection loop.
        Report r1 = buildReport(6L, 204L, null, ReportStatus.PENDING);
        PanacheMock.mock(Report.class);
        PanacheQuery q = mock(PanacheQuery.class);
        when(q.page(0, 20)).thenReturn(q);
        when(q.list()).thenReturn(List.of(r1));
        when(Report.find(anyString(), any(Object[].class))).thenReturn(q);

        when(eventClient.findByIds(any(List.class), eq(null))).thenReturn(List.of(
                event(204L, EventStatus.PUBLISHED, creatorId)
        ));

        List<ReportDTO> result = service.listByStatus(ReportStatus.PENDING, 0, 20);

        assertEquals(1, result.size());
        assertNotNull(result.get(0).eventTitle());
        assertNull(result.get(0).reporterDisplayName());
        verify(userClient, never()).getById(any(UUID.class));
    }

    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    void listByStatus_reporterHardDeleted_skipsUsersByIdEntry() {
        // reporterId is set (so safeGetUser is queried) but user-service 404s,
        // so safeGetUser returns null and usersById gets no entry. Covers the
        // u==null branch of the enrichment loop.
        Report r1 = buildReport(7L, 205L, reporterId, ReportStatus.PENDING);
        PanacheMock.mock(Report.class);
        PanacheQuery q = mock(PanacheQuery.class);
        when(q.page(0, 20)).thenReturn(q);
        when(q.list()).thenReturn(List.of(r1));
        when(Report.find(anyString(), any(Object[].class))).thenReturn(q);

        when(eventClient.findByIds(any(List.class), eq(null))).thenReturn(List.of(
                event(205L, EventStatus.PUBLISHED, creatorId)
        ));
        when(userClient.getById(reporterId)).thenThrow(new NotFoundException("gone"));

        List<ReportDTO> result = service.listByStatus(ReportStatus.PENDING, 0, 20);

        assertEquals(1, result.size());
        assertNotNull(result.get(0).eventTitle());
        assertNull(result.get(0).reporterDisplayName());
    }


    // ──────────────────────────────────────────────────────────────────
    // handle(...)
    // ──────────────────────────────────────────────────────────────────

    @Test
    void handle_invalidStatus_throwsBadRequest() {
        WebApplicationException ex = assertThrows(WebApplicationException.class,
                () -> service.handle(1L, "auth0|admin",
                        new HandleReportRequest(ReportStatus.PENDING, null)));
        assertEquals(400, ex.getResponse().getStatus());
    }

    @Test
    void handle_unknownReport_throwsNotFound() {
        PanacheMock.mock(Report.class);
        when(Report.findByIdOptional(anyLong())).thenReturn(Optional.empty());
        assertThrows(NotFoundException.class,
                () -> service.handle(999L, "auth0|admin",
                        new HandleReportRequest(ReportStatus.DISMISSED, null)));
    }

    @Test
    void handle_alreadyReviewed_throws409() {
        Report r = buildReport(10L, 300L, reporterId, ReportStatus.REVIEWED);
        PanacheMock.mock(Report.class);
        when(Report.findByIdOptional(10L)).thenReturn(Optional.of(r));

        WebApplicationException ex = assertThrows(WebApplicationException.class,
                () -> service.handle(10L, "auth0|admin",
                        new HandleReportRequest(ReportStatus.REVIEWED, null)));
        assertEquals(409, ex.getResponse().getStatus());
    }

    @Test
    void handle_anonymousJwt_throwsNotFound() {
        Report r = buildReport(11L, 301L, reporterId, ReportStatus.PENDING);
        PanacheMock.mock(Report.class);
        when(Report.findByIdOptional(11L)).thenReturn(Optional.of(r));

        JwtTestContext.set(JwtTestHelper.anonymous());

        assertThrows(NotFoundException.class,
                () -> service.handle(11L, null,
                        new HandleReportRequest(ReportStatus.DISMISSED, null)));
    }

    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    void handle_dismissed_setsStatusAndReturnsDTO() {
        Report r = buildReport(12L, 302L, reporterId, ReportStatus.PENDING);
        JwtTestContext.set(JwtTestHelper.adminJwt(adminId));

        PanacheMock.mock(Report.class);
        when(Report.findByIdOptional(12L)).thenReturn(Optional.of(r));

        when(eventClient.getById(302L)).thenReturn(event(302L, EventStatus.PUBLISHED, creatorId));

        ReportDTO dto = service.handle(12L, "auth0|admin",
                new HandleReportRequest(ReportStatus.DISMISSED, "false alarm"));

        assertEquals(ReportStatus.DISMISSED, dto.status());
        assertEquals("false alarm", dto.moderationNote());
        assertEquals(adminId, dto.reviewedBy());
    }

    @Test
    void handle_reporterHardDeleted_userClientNotFound_reporterNull() {
        // safeGetUser NotFoundException catch: the reporter was hard-deleted
        // (or never existed) — enrichment degrades silently to null, the DTO
        // is still returned without propagating the 404.
        Report r = buildReport(40L, 320L, reporterId, ReportStatus.PENDING);
        JwtTestContext.set(JwtTestHelper.adminJwt(adminId));

        PanacheMock.mock(Report.class);
        when(Report.findByIdOptional(40L)).thenReturn(Optional.of(r));
        when(eventClient.getById(320L)).thenReturn(event(320L, EventStatus.PUBLISHED, creatorId));
        when(userClient.getById(reporterId)).thenThrow(new NotFoundException("user gone"));

        ReportDTO dto = service.handle(40L, "auth0|admin",
                new HandleReportRequest(ReportStatus.DISMISSED, null));
        assertEquals(ReportStatus.DISMISSED, dto.status());
        assertNull(dto.reporterDisplayName());
    }

    @Test
    void handle_nullReporterId_safeGetUserShortCircuits() {
        // safeGetUser(null) returns null up-front without touching user-service
        // (a defensive guard for legacy rows with a null reporter_id).
        Report r = buildReport(41L, 321L, null, ReportStatus.PENDING);
        JwtTestContext.set(JwtTestHelper.adminJwt(adminId));

        PanacheMock.mock(Report.class);
        when(Report.findByIdOptional(41L)).thenReturn(Optional.of(r));
        when(eventClient.getById(321L)).thenReturn(event(321L, EventStatus.PUBLISHED, creatorId));

        ReportDTO dto = service.handle(41L, "auth0|admin",
                new HandleReportRequest(ReportStatus.DISMISSED, null));
        assertNull(dto.reporterDisplayName());
        verify(userClient, never()).getById(any(UUID.class));
    }

    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    void handle_reviewed_setsStatusAndCascadesSiblings() {
        Long eventId = 303L;
        Report target = buildReport(20L, eventId, reporterId, ReportStatus.PENDING);
        Report sibling = buildReport(21L, eventId, UUID.randomUUID(), ReportStatus.PENDING);

        JwtTestContext.set(JwtTestHelper.adminJwt(adminId));

        PanacheMock.mock(Report.class);
        when(Report.findByIdOptional(20L)).thenReturn(Optional.of(target));

        // Cascade: Report.find("eventId = ?1 and ...", args).list()
        PanacheQuery siblingQ = mock(PanacheQuery.class);
        when(siblingQ.list()).thenReturn(List.of(sibling));
        when(Report.find(anyString(), any(Object[].class))).thenReturn(siblingQ);

        when(eventClient.getById(eventId)).thenReturn(event(eventId, EventStatus.PUBLISHED, creatorId));

        ReportDTO dto = service.handle(20L, "auth0|admin",
                new HandleReportRequest(ReportStatus.REVIEWED, "spam confirmed"));

        assertEquals(ReportStatus.REVIEWED, dto.status());
        assertEquals("spam confirmed", dto.moderationNote());

        // Sibling has been updated in-place by cascadeSiblingReports.
        assertEquals(ReportStatus.REVIEWED, sibling.status);
        assertEquals(adminId, sibling.reviewedById);
    }

    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    void handle_reviewed_nullModerationNote_works() {
        Long eventId = 304L;
        Report target = buildReport(30L, eventId, reporterId, ReportStatus.PENDING);

        JwtTestContext.set(JwtTestHelper.adminJwt(adminId));

        PanacheMock.mock(Report.class);
        when(Report.findByIdOptional(30L)).thenReturn(Optional.of(target));

        PanacheQuery siblingQ = mock(PanacheQuery.class);
        when(siblingQ.list()).thenReturn(List.of());
        when(Report.find(anyString(), any(Object[].class))).thenReturn(siblingQ);

        when(eventClient.getById(eventId)).thenReturn(event(eventId, EventStatus.PUBLISHED, creatorId));

        ReportDTO dto = service.handle(30L, "auth0|admin",
                new HandleReportRequest(ReportStatus.REVIEWED, null));
        assertEquals(ReportStatus.REVIEWED, dto.status());
        // moderationNote is null in the response — the service used the
        // "admin-ban" default reason for the Kafka event downstream.
        assertNull(dto.moderationNote());
    }
}
