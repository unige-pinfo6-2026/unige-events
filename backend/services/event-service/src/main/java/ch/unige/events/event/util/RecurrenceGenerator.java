package ch.unige.events.event.util;

import ch.unige.events.event.entity.RecurrenceFrequency;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Period;
import java.util.ArrayList;
import java.util.List;

/**
 * Generates the (start, end) ranges for a recurrence (SCRUM-147). Pure
 * function — testable outside Quarkus in plain JUnit. Returns occurrences
 * <strong>excluding the parent</strong> — the parent lives at
 * {@code parentStart/parentEnd} and is created separately.
 */
public final class RecurrenceGenerator {

    public static final int MAX_TOTAL_OCCURRENCES = 52;

    private RecurrenceGenerator() {}

    public record DateRange(LocalDateTime start, LocalDateTime end) {}

    public static List<DateRange> generate(
            LocalDateTime parentStart,
            LocalDateTime parentEnd,
            RecurrenceFrequency frequency,
            LocalDate untilDate,
            Integer maxOccurrences
    ) {
        if (untilDate == null && maxOccurrences == null) {
            throw new IllegalArgumentException(
                    "RecurrenceGenerator requires at least one of untilDate or maxOccurrences");
        }

        Period spacing = switch (frequency) {
            case WEEKLY -> Period.ofDays(7);
            case BIWEEKLY -> Period.ofDays(14);
            case MONTHLY -> Period.ofMonths(1);
        };

        int effectiveCap = MAX_TOTAL_OCCURRENCES;
        if (maxOccurrences != null) {
            effectiveCap = Math.min(effectiveCap, maxOccurrences);
        }

        List<DateRange> ranges = new ArrayList<>();
        for (int n = 1; n < effectiveCap; n++) {
            LocalDateTime start = parentStart.plus(spacing.multipliedBy(n));
            LocalDateTime end = parentEnd.plus(spacing.multipliedBy(n));

            if (untilDate != null && start.toLocalDate().isAfter(untilDate)) {
                break;
            }

            ranges.add(new DateRange(start, end));
        }
        return ranges;
    }
}
