package ch.unige.events.dto.report;

import ch.unige.events.entity.ReportStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record HandleReportRequest(
        @NotNull ReportStatus status,
        @Size(max = 2000) String moderationNote
) {}
