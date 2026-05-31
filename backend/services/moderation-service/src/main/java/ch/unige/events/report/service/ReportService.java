package ch.unige.events.report.service;

import ch.unige.events.shared.error.ApiErrorResponse;
import ch.unige.events.report.dto.CreateReportRequest;
import ch.unige.events.report.dto.HandleReportRequest;
import ch.unige.events.report.dto.ReportDTO;
import ch.unige.events.report.entity.Report;
import ch.unige.events.shared.domain.enums.ReportStatus;
import ch.unige.events.shared.client.EngagementServiceClient;
import ch.unige.events.shared.client.EventServiceClient;
import ch.unige.events.shared.client.UserServiceClient;
import ch.unige.events.shared.domain.dto.CommentContentProjection;
import ch.unige.events.shared.domain.dto.CommentVisibilityProjection;
import ch.unige.events.shared.domain.dto.UserPublicResponse;
import ch.unige.events.shared.domain.enums.EventStatus;
import ch.unige.events.shared.domain.projections.CallerIdentity;
import ch.unige.events.shared.kafka.events.EventBannedEvent;

import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceException;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.rest.client.inject.RestClient;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Same contract as the legacy ReportService.
 *
 * <p>Stub-001 / Décisions F, H:
 * <ul>
 *   <li>Cross-service navigations (EventStub, UserStub,
 *       EventCoOrganizerStub) are replaced by REST clients to
 *       event-service / user-service.</li>
 *   <li>Décision H: validating a report no longer mutates
 *       {@code event.status = BANNED} cross-schema. The decision is
 *       published via Kafka {@code events.banned} ; event-service is
 *       the schema owner of {@code events} and consumes the topic to
 *       apply the BAN idempotently.</li>
 *   <li>SCRUM-136 cascade ("you can't report your own event") delegated
 *       to event-service via {@code GET /events/{id}?check-co-org-of=}
 *       (self-check authentifié — Décision C).</li>
 * </ul>
 */
@ApplicationScoped
public class ReportService {

    @Inject jakarta.enterprise.event.Event<EventBannedEvent> bannedEvent;
    @Inject CallerIdentity callerIdentity;

    @Inject @RestClient EventServiceClient eventClient;
    @Inject @RestClient UserServiceClient userClient;
    @Inject @RestClient EngagementServiceClient engagementClient;

    @Inject EntityManager entityManager;

    @Transactional
    public ReportDTO create(Long eventId, String reporterAuth0Id, CreateReportRequest request) {
        UUID reporterId = callerIdentity.requireUuid();
        if (reporterId == null) {
            throw new NotFoundException(
                    "User profile not found — call GET /users/me first");
        }
        // The provider applies the SCRUM-136 cascade server-side: if the
        // caller is the creator OR an accepted co-organizer of the event,
        // the response carries coOrganizerOf=true (Décision C self-check).
        ch.unige.events.shared.domain.dto.EventDTO event =
                eventClient.getByIdWithCoOrgCheck(eventId, reporterId);
        if (event == null) {
            throw new NotFoundException("Event not found");
        }

        if (event.status() == EventStatus.DRAFT) {
            throw badRequest("cannot_report_draft",
                    "Cannot report an event in DRAFT status.");
        }
        if (event.status() == EventStatus.CANCELLED) {
            throw badRequest("cannot_report_cancelled",
                    "Cannot report an event in CANCELLED status.");
        }
        if (event.status() == EventStatus.BANNED) {
            throw badRequest("cannot_report_banned",
                    "Cannot report an event that has already been banned by moderation.");
        }

        boolean isCreator = reporterId.equals(event.creatorId());
        boolean isCoOrganizer = Boolean.TRUE.equals(event.coOrganizerOf());
        if (isCreator || isCoOrganizer) {
            throw unprocessable("cannot_report_own_event",
                    "You cannot report an event you organize.");
        }

        if (Report.<Report>find("reporterId = ?1 and eventId = ?2", reporterId, eventId).count() > 0) {
            throw conflict("already_reported", "You have already reported this event.");
        }

        Report report = new Report();
        report.eventId = eventId;
        report.reporterId = reporterId;
        report.reason = request.reason();
        report.description = request.description();
        report.status = ReportStatus.PENDING;
        report.persist();

        return ReportDTO.from(report, event, safeGetUser(reporterId));
    }

    /**
     * Persists a report whose target is a comment (SCRUM-144, Décision N).
     *
     * <p>Pipeline :
     * <ol>
     *   <li>Resolve the caller's internal UUID via {@link CallerIdentity}. The
     *       JWT claims must resolve to a provisioned user — otherwise propagate
     *       {@link NotFoundException} (anti-oracle ISSUE-92 envelope identical
     *       to "comment not found").</li>
     *   <li>Validate the comment visibility via the engagement-service internal
     *       endpoint {@code GET /comments/{commentId}/_internal-visibility}
     *       (Décision L). The cascade ISSUE-92 + SCRUM-136 is enforced on the
     *       engagement side ; here, a {@link NotFoundException} is propagated
     *       as-is — never converted to 403 — to keep the anti-oracle envelope
     *       across the two-hop chain.</li>
     *   <li>Block self-reports (Décision N) with 422
     *       {@code cannot_report_own_comment}.</li>
     *   <li>Persist {@code Report{ commentId, eventId=null, ...}} and force a
     *       {@code flush()} so the partial UK {@code uq_report_comment_partial}
     *       fires here rather than at commit-time. A {@link PersistenceException}
     *       matching the partial UK is converted to 409
     *       {@code already_reported} ; anything else is rethrown (so genuine DB
     *       errors surface as 5xx).</li>
     * </ol>
     *
     * <p>The 503 path (engagement-service down) is owned by the
     * {@link EngagementServiceClient} fallback — it throws a
     * {@link WebApplicationException} with status 503 that propagates as-is.
     */
    @Transactional
    public ReportDTO createForComment(Long commentId, String reporterAuth0Id, CreateReportRequest request) {
        UUID reporterId = callerIdentity.requireUuid();
        if (reporterId == null) {
            throw new NotFoundException(
                    "User profile not found — call GET /users/me first");
        }

        CommentVisibilityProjection visibility =
                engagementClient.getCommentVisibility(commentId, reporterId);
        // Defense-in-depth : the engagement-service contract already throws
        // NotFoundException on token / comment / event mismatch. A null
        // response from a misbehaving downstream is treated identically.
        if (visibility == null) {
            throw new NotFoundException("Comment not found");
        }

        if (reporterId.equals(visibility.authorId())) {
            throw unprocessable("cannot_report_own_comment",
                    "You cannot report your own comment.");
        }

        Report report = new Report();
        report.eventId = null;
        report.commentId = commentId;
        report.reporterId = reporterId;
        report.reason = request.reason();
        report.description = request.description();
        report.status = ReportStatus.PENDING;
        try {
            report.persist();
            // Force the partial UK violation here, not at JTA commit — so we
            // can return a clean 409 envelope. Pattern mirror :
            // CommentLikeService.like (engagement-service phase 2 étape 8).
            entityManager.flush();
        } catch (PersistenceException e) {
            if (!isUniqueReportCommentConflict(e)) {
                throw e;
            }
            Log.debugf("[REPORT_COMMENT_DOUBLE_TAP] reporter=%s comment=%d already-reported", reporterId, commentId);
            throw conflict("already_reported",
                    "You have already reported this comment.");
        }

        return ReportDTO.from(report, null, safeGetUser(reporterId));
    }

    /**
     * Matches the partial unique index {@code uq_report_comment_partial} by
     * constraint name. Scoped narrowly so unrelated UK violations (future
     * migrations on the table) still surface as 5xx — pattern miroir
     * {@code FavoriteService.isUniqueFavoriteConflict}.
     *
     * <p>Package-private so {@code ReportServiceCreateForCommentTest} can
     * exercise the constraint-name discrimination without standing up a real
     * Postgres (Docker indispo localement — piège #14). The matcher is the
     * load-bearing piece for the 409 path ; the {@code flush()} that triggers
     * the wrapped {@link PersistenceException} is covered by the integration
     * tier in CI.
     */
    static boolean isUniqueReportCommentConflict(PersistenceException e) {
        Throwable cause = e;
        while (cause != null) {
            if (cause instanceof org.hibernate.exception.ConstraintViolationException c) {
                String name = c.getConstraintName();
                if (name != null && name.equalsIgnoreCase("uq_report_comment_partial")) {
                    return true;
                }
            }
            cause = cause.getCause();
        }
        return false;
    }

    @Transactional
    public List<ReportDTO> listByStatus(ReportStatus status, int page, int size) {
        ReportStatus effective = status != null ? status : ReportStatus.PENDING;
        List<Report> reports = Report.<Report>find(
                "status = ?1 order by createdAt desc, id desc", effective)
                .page(page, size)
                .list();
        if (reports.isEmpty()) {
            return List.of();
        }

        Set<Long> eventIds = new HashSet<>();
        Set<Long> commentIds = new HashSet<>();
        Set<UUID> userIds = new HashSet<>();
        for (Report r : reports) {
            if (r.eventId != null) eventIds.add(r.eventId);
            if (r.commentId != null) commentIds.add(r.commentId);
            if (r.reporterId != null) userIds.add(r.reporterId);
        }

        Map<Long, ch.unige.events.shared.domain.dto.EventDTO> eventsById = bulkFetchEvents(eventIds);
        Map<Long, String> commentContentById = bulkFetchCommentContent(commentIds);
        Map<UUID, UserPublicResponse> usersById = new HashMap<>();
        for (UUID uid : userIds) {
            UserPublicResponse u = safeGetUser(uid);
            if (u != null) usersById.put(uid, u);
        }

        return reports.stream()
                .map(r -> ReportDTO.from(
                        r,
                        eventsById.get(r.eventId),
                        usersById.get(r.reporterId),
                        r.commentId != null ? commentContentById.get(r.commentId) : null))
                .toList();
    }

    @Transactional
    public ReportDTO handle(Long reportId, String adminAuth0Id, HandleReportRequest request) {
        if (!request.status().isClosed()) {
            throw badRequest("invalid_status",
                    "Only REVIEWED or DISMISSED are accepted as a target status.");
        }

        Report report = Report.<Report>findByIdOptional(reportId)
                .orElseThrow(() -> new NotFoundException("Report not found"));

        if (report.status != ReportStatus.PENDING) {
            throw conflict("invalid_transition",
                    "Report is already in status " + report.status
                            + " — only PENDING reports can be transitioned.");
        }

        UUID adminId = callerIdentity.requireUuid();
        if (adminId == null) {
            throw new NotFoundException(
                    "Admin profile not found — call GET /users/me first");
        }

        LocalDateTime now = LocalDateTime.now();
        report.status = request.status();
        report.moderationNote = request.moderationNote();
        report.reviewedAt = now;
        report.reviewedById = adminId;

        // Validating a report (REVIEWED) takes the moderation action and cascades
        // the sibling PENDING reports against the SAME target to REVIEWED.
        //  • event report  → fire events.banned (event-service applies BANNED
        //    locally, Décision H — no cross-schema mutation) + cascade by eventId.
        //  • comment report → hard-delete the comment via engagement-service
        //    (QA bug batch, bug ③) + cascade by commentId. NEVER fire
        //    EventBannedEvent here: report.eventId is null for a comment report,
        //    which previously banned a phantom null event.
        if (request.status() == ReportStatus.REVIEWED) {
            if (report.commentId != null) {
                deleteReportedComment(report.commentId);
                cascadeSiblingCommentReports(report, adminId, now);
            } else {
                String reason = request.moderationNote() != null ? request.moderationNote() : "admin-ban";
                bannedEvent.fire(EventBannedEvent.banned(report.eventId, adminId, reason));
                cascadeSiblingReports(report, adminId, now);
            }
        }

        // Enrichment for the response — only fetch the event for an event report
        // (eventId is null for a comment report). The comment body is omitted
        // here (it's gone after a REVIEWED delete, and the admin UI re-fetches
        // the listing anyway).
        ch.unige.events.shared.domain.dto.EventDTO event =
                report.eventId != null ? eventClient.getById(report.eventId) : null;
        UserPublicResponse reporter = safeGetUser(report.reporterId);
        return ReportDTO.from(report, event, reporter, null);
    }

    /**
     * Hard-deletes the reported comment via engagement-service when its report is
     * validated (QA bug batch, bug ③). A 404 means the comment is already gone
     * (the author or another admin deleted it meanwhile) — that's an idempotent
     * success from the moderation standpoint, so we swallow it. Infra failures
     * surface as 503 via the {@link EngagementServiceClient} fallback.
     */
    private void deleteReportedComment(Long commentId) {
        try {
            engagementClient.deleteCommentForModeration(commentId);
        } catch (NotFoundException alreadyGone) {
            Log.debugf("[REPORT_COMMENT_ALREADY_GONE] comment=%d already deleted at validate-time", commentId);
        }
    }

    private void cascadeSiblingReports(Report validatedReport, UUID adminId, LocalDateTime now) {
        List<Report> siblings = Report.<Report>find(
                "eventId = ?1 and status = ?2 and id <> ?3",
                validatedReport.eventId, ReportStatus.PENDING, validatedReport.id
        ).list();
        cascadeReviewed(siblings, adminId, now);
    }

    private void cascadeSiblingCommentReports(Report validatedReport, UUID adminId, LocalDateTime now) {
        List<Report> siblings = Report.<Report>find(
                "commentId = ?1 and status = ?2 and id <> ?3",
                validatedReport.commentId, ReportStatus.PENDING, validatedReport.id
        ).list();
        cascadeReviewed(siblings, adminId, now);
    }

    private void cascadeReviewed(List<Report> siblings, UUID adminId, LocalDateTime now) {
        for (Report sibling : siblings) {
            sibling.status = ReportStatus.REVIEWED;
            sibling.reviewedAt = now;
            sibling.reviewedById = adminId;
        }
    }

    private Map<Long, ch.unige.events.shared.domain.dto.EventDTO> bulkFetchEvents(Set<Long> ids) {
        if (ids.isEmpty()) {
            // Must be a HashMap (not the immutable Map.of()): the caller does
            // eventsById.get(r.eventId) where r.eventId is null for comment-only
            // reports, and Map.of().get(null) throws NPE. A page consisting
            // solely of comment-only reports yields an empty id-set here.
            return new HashMap<>();
        }
        List<ch.unige.events.shared.domain.dto.EventDTO> events =
                eventClient.findByIds(List.copyOf(ids), null);
        Map<Long, ch.unige.events.shared.domain.dto.EventDTO> byId = new HashMap<>();
        if (events != null) {
            for (ch.unige.events.shared.domain.dto.EventDTO e : events) {
                byId.put(e.id(), e);
            }
        }
        return byId;
    }

    private Map<Long, String> bulkFetchCommentContent(Set<Long> ids) {
        if (ids.isEmpty()) {
            return new HashMap<>();
        }
        List<CommentContentProjection> projections = engagementClient.getCommentsByIds(List.copyOf(ids));
        Map<Long, String> byId = new HashMap<>();
        if (projections != null) {
            for (CommentContentProjection p : projections) {
                byId.put(p.id(), p.content());
            }
        }
        return byId;
    }

    private UserPublicResponse safeGetUser(UUID userId) {
        if (userId == null) {
            return null;
        }
        try {
            return userClient.getById(userId);
        } catch (jakarta.ws.rs.NotFoundException e) {
            // Semantic absence: user was hard-deleted or never existed. Caller
            // already treats null as "anonymous author" — no log needed.
            return null;
        } catch (RuntimeException e) {
            // Infra failure (timeout, CB open, JSON parse error, etc.). Log so
            // ops can correlate degraded enrichment to a downstream incident.
            Log.warnf(e, "[USER_ENRICHMENT_FAIL] safeGetUser(%s) — returning null (degraded enrichment due to downstream failure)", userId);
            return null;
        }
    }

    static WebApplicationException badRequest(String error, String message) {
        return new WebApplicationException(
                Response.status(Response.Status.BAD_REQUEST)
                        .entity(new ApiErrorResponse(error, message))
                        .type(MediaType.APPLICATION_JSON_TYPE)
                        .build());
    }

    static WebApplicationException conflict(String error, String message) {
        return new WebApplicationException(
                Response.status(Response.Status.CONFLICT)
                        .entity(new ApiErrorResponse(error, message))
                        .type(MediaType.APPLICATION_JSON_TYPE)
                        .build());
    }

    static WebApplicationException unprocessable(String error, String message) {
        return new WebApplicationException(
                Response.status(422)
                        .entity(new ApiErrorResponse(error, message))
                        .type(MediaType.APPLICATION_JSON_TYPE)
                        .build());
    }
}
