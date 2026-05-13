package ch.unige.events.report.dto;

import ch.unige.events.shared.domain.enums.ReportReason;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateReportRequest(
        @NotNull ReportReason reason,
        @Size(max = 2000) String description
) {}
