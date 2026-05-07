package ch.unige.events.service;

import ch.unige.events.dto.ApiErrorResponse;
import ch.unige.events.dto.report.CreateReportRequest;
import ch.unige.events.dto.report.HandleReportRequest;
import ch.unige.events.dto.report.ReportDTO;
import ch.unige.events.entity.Event;
import ch.unige.events.entity.EventStatus;
import ch.unige.events.entity.Report;
import ch.unige.events.entity.ReportStatus;
import ch.unige.events.entity.User;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.time.LocalDateTime;
import java.util.List;

@ApplicationScoped
public class ReportService {

    @Inject
    EventService eventService;

    @Transactional
    public ReportDTO create(Long eventId, String reporterAuth0Id, CreateReportRequest request) {
        Event event = Event.<Event>findByIdOptional(eventId)
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

        User reporter = User.findByAuth0Id(reporterAuth0Id)
                .orElseThrow(() -> new NotFoundException(
                        "User profile not found — call GET /users/me first"));

        // Cascade SCRUM-136 : créateur OU co-organisateur ACCEPTED ne peut pas signaler
        // son propre event.
        if (eventService.isCreatorOrAcceptedCoOrganizerPublic(event, reporterAuth0Id)) {
            throw unprocessable("cannot_report_own_event",
                    "You cannot report an event you organize.");
        }

        // Doublon : la unique constraint (reporter_id, event_id) bloque déjà au persist,
        // mais on check au préalable pour produire une envelope `error=already_reported` propre.
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

        User admin = User.findByAuth0Id(adminAuth0Id)
                .orElseThrow(() -> new NotFoundException(
                        "Admin profile not found — call GET /users/me first"));

        LocalDateTime now = LocalDateTime.now();
        report.status = request.status();
        report.moderationNote = request.moderationNote();
        report.reviewedAt = now;
        report.reviewedBy = admin;

        // SCRUM-97: validating a report bans the underlying event and cascades
        // the decision to all sibling PENDING reports on the same event so the
        // moderation queue is cleared in one click. Dismiss is neutral — no ban,
        // no cascade — for cases where the report was bogus.
        // `report.event` is NOT NULL by FK constraint (cf. Report.java
        // @JoinColumn(nullable = false)), so no defensive null check is needed.
        if (request.status() == ReportStatus.REVIEWED) {
            report.event.status = EventStatus.BANNED;
            cascadeSiblingReports(report, admin, now);
        }

        return ReportDTO.from(report);
    }

    private void cascadeSiblingReports(Report validatedReport, User admin, LocalDateTime now) {
        List<Report> siblings = Report.<Report>find(
                "event.id = ?1 and status = ?2 and id <> ?3",
                validatedReport.event.id, ReportStatus.PENDING, validatedReport.id
        ).list();
        for (Report sibling : siblings) {
            sibling.status = ReportStatus.REVIEWED;
            sibling.reviewedAt = now;
            sibling.reviewedBy = admin;
            // moderationNote left null — only the explicit handle() call carries
            // the admin's note; cascaded siblings inherit the decision, not the prose.
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
