package ch.unige.events.report.service;

import ch.unige.events.shared.error.ApiErrorResponse;
import ch.unige.events.report.dto.CreateReportRequest;
import ch.unige.events.report.dto.HandleReportRequest;
import ch.unige.events.report.dto.ReportDTO;
import ch.unige.events.report.entity.Report;
import ch.unige.events.shared.domain.enums.ReportStatus;
import ch.unige.events.shared.client.EventServiceClient;
import ch.unige.events.shared.client.UserServiceClient;
import ch.unige.events.shared.domain.dto.UserPublicResponse;
import ch.unige.events.shared.domain.enums.EventStatus;
import ch.unige.events.shared.domain.projections.Auth0IdResolver;
import ch.unige.events.shared.kafka.events.EventBannedEvent;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.jwt.JsonWebToken;
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
 * <p>Étape 3.2 finalization-ultimate (STUB-001 / Décisions F, H):
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
    @Inject Instance<JsonWebToken> jwt;

    @Inject @RestClient EventServiceClient eventClient;
    @Inject @RestClient UserServiceClient userClient;

    private JsonWebToken jwt() {
        return jwt.isResolvable() ? jwt.get() : null;
    }

    @Transactional
    public ReportDTO create(Long eventId, String reporterAuth0Id, CreateReportRequest request) {
        UUID reporterId = Auth0IdResolver.resolveUserUuid(jwt());
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
        Set<UUID> userIds = new HashSet<>();
        for (Report r : reports) {
            if (r.eventId != null) eventIds.add(r.eventId);
            if (r.reporterId != null) userIds.add(r.reporterId);
        }

        Map<Long, ch.unige.events.shared.domain.dto.EventDTO> eventsById = bulkFetchEvents(eventIds);
        Map<UUID, UserPublicResponse> usersById = new HashMap<>();
        for (UUID uid : userIds) {
            UserPublicResponse u = safeGetUser(uid);
            if (u != null) usersById.put(uid, u);
        }

        return reports.stream()
                .map(r -> ReportDTO.from(r, eventsById.get(r.eventId), usersById.get(r.reporterId)))
                .toList();
    }

    @Transactional
    public ReportDTO handle(Long reportId, String adminAuth0Id, HandleReportRequest request) {
        if (request.status() != ReportStatus.REVIEWED && request.status() != ReportStatus.DISMISSED) {
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

        UUID adminId = Auth0IdResolver.resolveUserUuid(jwt());
        if (adminId == null) {
            throw new NotFoundException(
                    "Admin profile not found — call GET /users/me first");
        }

        LocalDateTime now = LocalDateTime.now();
        report.status = request.status();
        report.moderationNote = request.moderationNote();
        report.reviewedAt = now;
        report.reviewedById = adminId;

        // Décision H: validating a report fires the events.banned Kafka
        // event ; event-service consumer applies status=BANNED locally
        // (no more cross-schema mutation). The CDI fire is delivered
        // AFTER_SUCCESS so a rollback aborts the propagation.
        if (request.status() == ReportStatus.REVIEWED) {
            String reason = request.moderationNote() != null ? request.moderationNote() : "admin-ban";
            bannedEvent.fire(EventBannedEvent.banned(report.eventId, adminId, reason));
            cascadeSiblingReports(report, adminId, now);
        }

        ch.unige.events.shared.domain.dto.EventDTO event = eventClient.getById(report.eventId);
        UserPublicResponse reporter = safeGetUser(report.reporterId);
        return ReportDTO.from(report, event, reporter);
    }

    private void cascadeSiblingReports(Report validatedReport, UUID adminId, LocalDateTime now) {
        List<Report> siblings = Report.<Report>find(
                "eventId = ?1 and status = ?2 and id <> ?3",
                validatedReport.eventId, ReportStatus.PENDING, validatedReport.id
        ).list();
        for (Report sibling : siblings) {
            sibling.status = ReportStatus.REVIEWED;
            sibling.reviewedAt = now;
            sibling.reviewedById = adminId;
        }
    }

    private Map<Long, ch.unige.events.shared.domain.dto.EventDTO> bulkFetchEvents(Set<Long> ids) {
        if (ids.isEmpty()) {
            return Map.of();
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

    private UserPublicResponse safeGetUser(UUID userId) {
        if (userId == null) {
            return null;
        }
        try {
            return userClient.getById(userId);
        } catch (RuntimeException e) {
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
