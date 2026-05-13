package ch.unige.events.event.dto;

import ch.unige.events.shared.domain.enums.RecurrenceFrequency;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

public record RecurrenceRequest(
        @NotNull RecurrenceFrequency frequency,
        LocalDate endDate,
        @Min(1) @Max(52) Integer maxOccurrences
) {}
