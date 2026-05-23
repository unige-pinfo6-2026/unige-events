package ch.unige.events.report.service;

import ch.unige.events.report.dto.CreateReportRequest;
import ch.unige.events.report.dto.ReportDTO;
import ch.unige.events.report.entity.Report;
import ch.unige.events.report.test.JwtTestContext;
import ch.unige.events.report.test.JwtTestHelper;
import ch.unige.events.shared.client.EngagementServiceClient;
import ch.unige.events.shared.client.UserServiceClient;
import ch.unige.events.shared.domain.dto.CommentVisibilityProjection;
import ch.unige.events.shared.domain.dto.UserPublicResponse;
import ch.unige.events.shared.domain.enums.ReportReason;
import ch.unige.events.shared.domain.enums.ReportStatus;

import io.quarkus.panache.mock.PanacheMock;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.PersistenceException;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.WebApplicationException;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.SQLException;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * SCRUM-144 — service-side unit tests for {@link ReportService#createForComment}
 * (Décision N).
 *
 * <p>Uses {@link PanacheMock} so {@code Report.persist()} is a no-op without a
 * live DB. The id of the persisted entity stays null — assertions therefore
 * cover the non-id fields of the resulting {@link ReportDTO}. The 409 path is
 * covered indirectly via the helper {@link ReportService#isUniqueReportCommentConflict}
 * directly tested below (the {@code flush()} that surfaces the constraint
 * violation is exercised by the integration tier in CI, since Docker is not
 * available locally — piège #14).
 */
@QuarkusTest
class ReportServiceCreateForCommentTest {

    @Inject
    ReportService service;

    @InjectMock
    @RestClient
    EngagementServiceClient engagementClient;

    @InjectMock
    @RestClient
    UserServiceClient userClient;

    private final UUID reporterId = UUID.randomUUID();
    private final UUID otherAuthorId = UUID.randomUUID();

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
        PanacheMock.reset();
    }

    private static CommentVisibilityProjection visibilityFor(Long commentId, UUID authorId) {
        return new CommentVisibilityProjection(commentId, 1001L, authorId, true);
    }

    private static CreateReportRequest spamReport() {
        return new CreateReportRequest(ReportReason.SPAM, "spammy content");
    }

    @Test
    void createForComment_happyPath_returnsDtoWithCommentIdAndNullEventId() {
        when(engagementClient.getCommentVisibility(eq(42L), any(UUID.class)))
                .thenReturn(visibilityFor(42L, otherAuthorId));
        PanacheMock.mock(Report.class);

        ReportDTO dto = service.createForComment(42L, "auth0|x", spamReport());

        assertNotNull(dto);
        assertEquals(42L, dto.commentId());
        assertNull(dto.eventId(), "report-comment must carry eventId=null (XOR)");
        assertEquals(reporterId, dto.reporterId());
        assertEquals(ReportReason.SPAM, dto.reason());
        assertEquals("spammy content", dto.description());
        assertEquals(ReportStatus.PENDING, dto.status());
    }

    @Test
    void createForComment_visibilityNotFound_propagates404() {
        when(engagementClient.getCommentVisibility(eq(50L), any(UUID.class)))
                .thenThrow(new NotFoundException("Comment not found"));
        CreateReportRequest req = spamReport();

        assertThrows(NotFoundException.class,
                () -> service.createForComment(50L, "auth0|x", req));
    }

    @Test
    void createForComment_visibilityReturnsNull_propagates404() {
        // Defense-in-depth — a misbehaving downstream returning null instead
        // of throwing must surface as 404 anti-oracle envelope.
        when(engagementClient.getCommentVisibility(eq(51L), any(UUID.class)))
                .thenReturn(null);
        CreateReportRequest req = spamReport();

        assertThrows(NotFoundException.class,
                () -> service.createForComment(51L, "auth0|x", req));
    }

    @Test
    void createForComment_ownComment_throws422() {
        when(engagementClient.getCommentVisibility(eq(52L), any(UUID.class)))
                .thenReturn(visibilityFor(52L, reporterId));
        PanacheMock.mock(Report.class);
        CreateReportRequest req = spamReport();

        WebApplicationException ex = assertThrows(WebApplicationException.class,
                () -> service.createForComment(52L, "auth0|x", req));
        assertEquals(422, ex.getResponse().getStatus());
    }

    @Test
    void createForComment_engagementClientFallback503_propagates() {
        // The REST client fallback throws WebApplicationException(503) — the
        // service does NOT catch it ; it surfaces to the resource layer.
        when(engagementClient.getCommentVisibility(eq(55L), any(UUID.class)))
                .thenThrow(new WebApplicationException(
                        "Comment visibility check unavailable", 503));
        CreateReportRequest req = spamReport();

        WebApplicationException ex = assertThrows(WebApplicationException.class,
                () -> service.createForComment(55L, "auth0|x", req));
        assertEquals(503, ex.getResponse().getStatus());
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Helper isUniqueReportCommentConflict — exercises the constraint-name
    // discrimination that gates the 409 envelope. Tested directly here because
    // the flush() that wraps the PersistenceException requires a real Postgres
    // (Docker indispo local — piège #14) ; CI integration covers the end-to-end
    // path.
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    void isUniqueReportCommentConflict_matchesPartialUkByName() {
        ConstraintViolationException uk = new ConstraintViolationException(
                "duplicate key value violates unique constraint",
                new SQLException("ERROR: duplicate key", "23505"),
                "uq_report_comment_partial");
        assertTrue(ReportService.isUniqueReportCommentConflict(new PersistenceException(uk)),
                "must match the partial UK by name for the 409 path");
    }

    @Test
    void isUniqueReportCommentConflict_ignoresXorCheckViolation() {
        ConstraintViolationException xor = new ConstraintViolationException(
                "violates check constraint",
                new SQLException("violates check constraint", "23514"),
                "report_target_xor");
        assertFalse(ReportService.isUniqueReportCommentConflict(new PersistenceException(xor)),
                "the XOR CHECK is NOT a duplicate-report — must surface as 5xx");
    }

    @Test
    void isUniqueReportCommentConflict_ignoresEventSideUk() {
        // The event-side partial UK is a separate constraint — must not be
        // conflated with the comment-side 409 envelope.
        ConstraintViolationException eventUk = new ConstraintViolationException(
                "duplicate key value violates unique constraint",
                new SQLException("ERROR: duplicate key", "23505"),
                "uq_report_event_partial");
        assertFalse(ReportService.isUniqueReportCommentConflict(new PersistenceException(eventUk)),
                "the event-side UK belongs to ReportService.create — not createForComment");
    }

    @Test
    void isUniqueReportCommentConflict_handlesNullCauseChain() {
        // A defensive guard for malformed exceptions — must not NPE.
        PersistenceException raw = new PersistenceException("no wrapped cause");
        assertFalse(ReportService.isUniqueReportCommentConflict(raw));
    }

    @Test
    void isUniqueReportCommentConflict_nullConstraintName_returnsFalse() {
        // A ConstraintViolationException whose driver did not surface a
        // constraint name: the name==null guard short-circuits before
        // equalsIgnoreCase. Covers the name==null branch of the matcher
        // (distinct from handlesNullCauseChain, which never reaches the
        // instanceof at all).
        ConstraintViolationException noName = new ConstraintViolationException(
                "violates not-null constraint",
                new SQLException("ERROR: null value", "23502"),
                (String) null);
        assertFalse(ReportService.isUniqueReportCommentConflict(new PersistenceException(noName)),
                "a null constraint name cannot be the partial UK — must surface as 5xx");
    }
}
