package ch.unige.events.dto.report;

import ch.unige.events.entity.ReportReason;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateReportRequest(
        @NotNull ReportReason reason,
        @Size(max = 2000) String description
) {}
