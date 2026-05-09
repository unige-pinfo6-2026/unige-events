package ch.unige.events.report.service;

import ch.unige.events.report.dto.ApiErrorResponse;
import ch.unige.events.report.dto.CreateReportRequest;
import ch.unige.events.report.dto.HandleReportRequest;
import ch.unige.events.report.dto.ReportDTO;
import ch.unige.events.report.entity.EventCoOrganizerStub;
import ch.unige.events.report.entity.EventStatus;
import ch.unige.events.report.entity.EventStub;
import ch.unige.events.report.entity.Report;
import ch.unige.events.report.entity.ReportStatus;
import ch.unige.events.report.entity.UserStub;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Same contract as the legacy ReportService.
 *
 * <p>The SCRUM-136 cascade ("you can't report your own event") is inlined
 * locally — once event-service / co-organizer-service expose REST clients
 * this becomes a {@code GET /events/{id}/co-organizers/check?userId=}
 * call. SCRUM-97 BANNED-on-validate writes {@code event.status} directly
 * here ; once event-service is the schema owner, this becomes a Kafka
 * {@code events.banned} producer message that event-service consumes to
 * apply the status flip.
 */
@ApplicationScoped
public class ReportService {

    @Transactional
    public ReportDTO create(Long eventId, String reporterAuth0Id, CreateReportRequest request) {
        EventStub event = EventStub.<EventStub>findByIdOptional(eventId)
                .orElseThrow(() -> new NotFoundException("Event not found"));

        if (event.status == EventStatus.DRAFT) {
            throw badRequest("cannot_report_draft",
                    "Cannot report an event in DRAFT status.");
        }
        if (event.status == EventStatus.CANCELLED) {
            throw badRequest("cannot_report_cancelled",
                    "Cannot report an event in CANCELLED status.");
        }
        if (event.status == EventStatus.BANNED) {
            throw badRequest("cannot_report_banned",
                    "Cannot report an event that has already been banned by moderation.");
        }

        UserStub reporter = UserStub.findByAuth0Id(reporterAuth0Id)
                .orElseThrow(() -> new NotFoundException(
                        "User profile not found — call GET /users/me first"));

        if (isCreatorOrAcceptedCoOrganizer(event, reporter)) {
            throw unprocessable("cannot_report_own_event",
                    "You cannot report an event you organize.");
        }

        if (Report.<Report>find("reporter.id = ?1 and event.id = ?2", reporter.id, eventId).count() > 0) {
            throw conflict("already_reported", "You have already reported this event.");
        }

        Report report = new Report();
        report.event = event;
        report.reporter = reporter;
        report.reason = request.reason();
        report.description = request.description();
        report.status = ReportStatus.PENDING;
        report.persist();

        return ReportDTO.from(report);
    }

    @Transactional
    public List<ReportDTO> listByStatus(ReportStatus status, int page, int size) {
        ReportStatus effective = status != null ? status : ReportStatus.PENDING;
        return Report.<Report>find(
                "status = ?1 order by createdAt desc, id desc", effective)
                .page(page, size)
                .list()
                .stream()
                .map(ReportDTO::from)
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

        UserStub admin = UserStub.findByAuth0Id(adminAuth0Id)
                .orElseThrow(() -> new NotFoundException(
                        "Admin profile not found — call GET /users/me first"));

        LocalDateTime now = LocalDateTime.now();
        report.status = request.status();
        report.moderationNote = request.moderationNote();
        report.reviewedAt = now;
        report.reviewedBy = admin;

        // SCRUM-97: validating a report bans the underlying event and
        // cascades the decision to all sibling PENDING reports on the
        // same event. Soft-extraction caveat: we mutate event.status
        // directly on the shared schema — once event-service ships, the
        // ban migrates to a Kafka events.banned producer message.
        if (request.status() == ReportStatus.REVIEWED) {
            report.event.status = EventStatus.BANNED;
            cascadeSiblingReports(report, admin, now);
        }

        return ReportDTO.from(report);
    }

    private void cascadeSiblingReports(Report validatedReport, UserStub admin, LocalDateTime now) {
        List<Report> siblings = Report.<Report>find(
                "event.id = ?1 and status = ?2 and id <> ?3",
                validatedReport.event.id, ReportStatus.PENDING, validatedReport.id
        ).list();
        for (Report sibling : siblings) {
            sibling.status = ReportStatus.REVIEWED;
            sibling.reviewedAt = now;
            sibling.reviewedBy = admin;
        }
    }

    private static boolean isCreatorOrAcceptedCoOrganizer(EventStub event, UserStub caller) {
        if (event == null || caller == null) {
            return false;
        }
        if (caller.id.equals(event.creatorId)) {
            return true;
        }
        return EventCoOrganizerStub.isAcceptedFor(event.id, caller.id);
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
