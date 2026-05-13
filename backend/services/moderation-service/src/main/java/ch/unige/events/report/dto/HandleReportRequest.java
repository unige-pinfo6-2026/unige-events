package ch.unige.events.report.dto;

import ch.unige.events.shared.domain.enums.ReportStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record HandleReportRequest(
        @NotNull ReportStatus status,
        @Size(max = 2000) String moderationNote
) {}
