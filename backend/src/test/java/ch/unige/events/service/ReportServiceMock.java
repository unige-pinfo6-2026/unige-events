package ch.unige.events.service;

import ch.unige.events.dto.report.CreateReportRequest;
import ch.unige.events.dto.report.HandleReportRequest;
import ch.unige.events.dto.report.ReportDTO;
import ch.unige.events.entity.ReportReason;
import ch.unige.events.entity.ReportStatus;
import io.quarkus.test.Mock;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.NotFoundException;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Mock
@ApplicationScoped
public class ReportServiceMock extends ReportService {

    public static volatile boolean forceNotFoundOnCreate = false;
    public static volatile boolean forceCannotReportDraft = false;
    public static volatile boolean forceCannotReportCancelled = false;
    public static volatile boolean forceCannotReportBanned = false;
    public static volatile boolean forceCannotReportOwn = false;
    public static volatile boolean forceAlreadyReported = false;
    public static volatile boolean forceNotFoundOnHandle = false;
    public static volatile boolean forceInvalidTransition = false;
    public static volatile boolean forceInvalidStatus = false;

    private final List<ReportDTO> reportsFixture = new ArrayList<>();

    public void reset() {
        forceNotFoundOnCreate = false;
        forceCannotReportDraft = false;
        forceCannotReportCancelled = false;
        forceCannotReportBanned = false;
        forceCannotReportOwn = false;
        forceAlreadyReported = false;
        forceNotFoundOnHandle = false;
        forceInvalidTransition = false;
        forceInvalidStatus = false;
        reportsFixture.clear();
    }

    public void seedReport(ReportDTO dto) {
        reportsFixture.add(dto);
    }

    @Override
    public ReportDTO create(Long eventId, String reporterAuth0Id, CreateReportRequest request) {
        if (forceNotFoundOnCreate) throw new NotFoundException();
        if (forceCannotReportDraft) throw badRequest("cannot_report_draft", "Cannot report DRAFT");
        if (forceCannotReportCancelled) throw badRequest("cannot_report_cancelled", "Cannot report CANCELLED");
        if (forceCannotReportBanned) throw badRequest("cannot_report_banned", "Cannot report BANNED");
        if (forceCannotReportOwn) throw unprocessable("cannot_report_own_event", "Cannot report own event");
        if (forceAlreadyReported) throw conflict("already_reported", "Already reported");
        return new ReportDTO(
                1L,
                eventId,
                "Mock event " + eventId,
                UUID.randomUUID(),
                "Mock reporter",
                request.reason() != null ? request.reason() : ReportReason.OTHER,
                request.description(),
                ReportStatus.PENDING,
                null,
                LocalDateTime.now(),
                null,
                null);
    }

    @Override
    public List<ReportDTO> listByStatus(ReportStatus status, int page, int size) {
        return List.copyOf(reportsFixture);
    }

    @Override
    public ReportDTO handle(Long reportId, String adminAuth0Id, HandleReportRequest request) {
        if (forceInvalidStatus) throw badRequest("invalid_status", "Only REVIEWED or DISMISSED");
        if (forceNotFoundOnHandle) throw new NotFoundException();
        if (forceInvalidTransition) throw conflict("invalid_transition", "Already handled");
        return new ReportDTO(
                reportId,
                1L,
                "Mock event 1",
                UUID.randomUUID(),
                "Mock reporter",
                ReportReason.SPAM,
                null,
                request.status(),
                request.moderationNote(),
                LocalDateTime.now().minusHours(1),
                LocalDateTime.now(),
                UUID.randomUUID());
    }
}
