package ch.unige.events.event.util;

import ch.unige.events.shared.domain.enums.RecurrenceFrequency;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * SCRUM-147 sentinels for the pure recurrence helper. Tests the canonical
 * legacy assertions ported into event-service. Annotated {@code @QuarkusTest}
 * so the executed helper bytecode is captured by quarkus-jacoco (plain unit
 * tests are not instrumented by the Quarkus JaCoCo extension).
 */
@QuarkusTest
@SuppressWarnings("java:S100")
class RecurrenceGeneratorTest {

    @Test
    void weekly_4Occurrences_returns3DatesSpacedBy7Days() {
        LocalDateTime start = LocalDateTime.of(2026, 6, 1, 10, 0);
        LocalDateTime end = LocalDateTime.of(2026, 6, 1, 12, 0);

        List<RecurrenceGenerator.DateRange> ranges = RecurrenceGenerator.generate(
                start, end, RecurrenceFrequency.WEEKLY, null, 4);

        // Parent is excluded — caller persists it separately. So 4 occurrences
        // total → 3 children. Spacing = 7 days.
        assertEquals(3, ranges.size());
        assertEquals(start.plusDays(7),  ranges.get(0).start());
        assertEquals(start.plusDays(14), ranges.get(1).start());
        assertEquals(start.plusDays(21), ranges.get(2).start());
    }

    @Test
    void monthly_handlesShortFebruaryFromJanuary31() {
        LocalDateTime start = LocalDateTime.of(2026, 1, 31, 10, 0);
        LocalDateTime end = LocalDateTime.of(2026, 1, 31, 12, 0);

        List<RecurrenceGenerator.DateRange> ranges = RecurrenceGenerator.generate(
                start, end, RecurrenceFrequency.MONTHLY, null, 3);

        // Adding 1 month to Jan 31 lands on Feb 28 (or 29 in leap years) —
        // java.time.Period handles the short-month adjustment without
        // throwing.
        assertEquals(2, ranges.size());
        assertEquals(2026, ranges.get(0).start().getYear());
    }

    @Test
    void bothNull_throwsIllegalArgumentException() {
        LocalDateTime start = LocalDateTime.of(2026, 6, 1, 10, 0);
        LocalDateTime end = LocalDateTime.of(2026, 6, 1, 12, 0);

        assertThrows(IllegalArgumentException.class,
                () -> RecurrenceGenerator.generate(start, end, RecurrenceFrequency.WEEKLY, null, null));
    }

    @Test
    void maxOccurrencesAbove52_cappedTo52() {
        LocalDateTime start = LocalDateTime.of(2026, 6, 1, 10, 0);
        LocalDateTime end = LocalDateTime.of(2026, 6, 1, 12, 0);
        // 200 requested, 52 is the global cap → 51 children (parent excluded).
        List<RecurrenceGenerator.DateRange> ranges = RecurrenceGenerator.generate(
                start, end, RecurrenceFrequency.WEEKLY, LocalDate.of(2099, 1, 1), 200);
        assertEquals(51, ranges.size());
    }
}
