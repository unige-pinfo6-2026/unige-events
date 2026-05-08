package ch.unige.events.dto.event;

import ch.unige.events.entity.RecurrenceFrequency;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

/**
 * Bloc optionnel de {@link CreateEventRequest} qui matérialise une récurrence
 * (SCRUM-147). Au moins un de {@code endDate} ou {@code maxOccurrences} doit être
 * fourni — sinon le service jette {@code 400 recurrence_unbounded}.
 */
public record RecurrenceRequest(
        @NotNull
        RecurrenceFrequency frequency,

        LocalDate endDate,

        @Min(1)
        @Max(52)
        Integer maxOccurrences
) {}
