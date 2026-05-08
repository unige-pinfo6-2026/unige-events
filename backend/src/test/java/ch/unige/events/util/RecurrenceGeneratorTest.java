package ch.unige.events.util;

import ch.unige.events.entity.RecurrenceFrequency;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pure JUnit (no @QuarkusTest) — RecurrenceGenerator est une fonction stateless.
 * SCRUM-147 décision 29.
 */
class RecurrenceGeneratorTest {

    private static final LocalDateTime BASE_START = LocalDateTime.of(2026, 6, 1, 18, 0);
    private static final LocalDateTime BASE_END = LocalDateTime.of(2026, 6, 1, 20, 0);

    @Test
    void weekly_4Occurrences_returns3DatesSpacedBy7Days() {
        var ranges = RecurrenceGenerator.generate(
                BASE_START, BASE_END, RecurrenceFrequency.WEEKLY, null, 4);

        assertEquals(3, ranges.size());
        assertEquals(BASE_START.plusDays(7), ranges.get(0).start());
        assertEquals(BASE_END.plusDays(7), ranges.get(0).end());
        assertEquals(BASE_START.plusDays(14), ranges.get(1).start());
        assertEquals(BASE_START.plusDays(21), ranges.get(2).start());
    }

    @Test
    void biweekly_6Occurrences_returns5DatesSpacedBy14Days() {
        var ranges = RecurrenceGenerator.generate(
                BASE_START, BASE_END, RecurrenceFrequency.BIWEEKLY, null, 6);

        assertEquals(5, ranges.size());
        assertEquals(BASE_START.plusDays(14), ranges.get(0).start());
        assertEquals(BASE_START.plusDays(28), ranges.get(1).start());
        assertEquals(BASE_START.plusDays(56), ranges.get(3).start());
        assertEquals(BASE_START.plusDays(70), ranges.get(4).start());
    }

    @Test
    void monthly_3Occurrences_returns2DatesSpacedByOneMonth() {
        LocalDateTime start = LocalDateTime.of(2026, 1, 15, 18, 0);
        LocalDateTime end = LocalDateTime.of(2026, 1, 15, 20, 0);

        var ranges = RecurrenceGenerator.generate(
                start, end, RecurrenceFrequency.MONTHLY, null, 3);

        assertEquals(2, ranges.size());
        assertEquals(LocalDateTime.of(2026, 2, 15, 18, 0), ranges.get(0).start());
        assertEquals(LocalDateTime.of(2026, 3, 15, 18, 0), ranges.get(1).start());
    }

    @Test
    void monthly_handlesShortFebruaryFromJanuary31() {
        LocalDateTime start = LocalDateTime.of(2026, 1, 31, 18, 0);
        LocalDateTime end = LocalDateTime.of(2026, 1, 31, 20, 0);

        var ranges = RecurrenceGenerator.generate(
                start, end, RecurrenceFrequency.MONTHLY, null, 3);

        // Period rolling: 2026-01-31 + 1 month -> 2026-02-28 (Feb 2026 has 28 days)
        assertEquals(2, ranges.size());
        assertEquals(LocalDateTime.of(2026, 2, 28, 18, 0), ranges.get(0).start());
        // 2026-01-31 + 2 months -> 2026-03-31
        assertEquals(LocalDateTime.of(2026, 3, 31, 18, 0), ranges.get(1).start());
    }

    @Test
    void untilBeforeMaxOccurrences_stopsAtUntil() {
        LocalDate until = BASE_START.toLocalDate().plusDays(10); // covers only +7d
        var ranges = RecurrenceGenerator.generate(
                BASE_START, BASE_END, RecurrenceFrequency.WEEKLY, until, 10);

        assertEquals(1, ranges.size());
        assertEquals(BASE_START.plusDays(7), ranges.get(0).start());
    }

    @Test
    void maxOccurrencesBeforeUntil_stopsAtCount() {
        LocalDate until = BASE_START.toLocalDate().plusYears(1);
        var ranges = RecurrenceGenerator.generate(
                BASE_START, BASE_END, RecurrenceFrequency.WEEKLY, until, 3);

        assertEquals(2, ranges.size());
        assertEquals(BASE_START.plusDays(7), ranges.get(0).start());
        assertEquals(BASE_START.plusDays(14), ranges.get(1).start());
    }

    @Test
    void bothNull_throwsIllegalArgumentException() {
        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> RecurrenceGenerator.generate(
                        BASE_START, BASE_END, RecurrenceFrequency.WEEKLY, null, null));
        assertTrue(ex.getMessage().contains("untilDate"));
    }

    @Test
    void maxOccurrencesAbove52_cappedTo52() {
        // maxOccurrences=100 should be silently capped to MAX_TOTAL_OCCURRENCES=52
        // -> at most 51 children returned (parent counts for 1).
        var ranges = RecurrenceGenerator.generate(
                BASE_START, BASE_END, RecurrenceFrequency.WEEKLY, null, 100);

        assertEquals(51, ranges.size());
        // last child: parent + 51 weeks
        assertEquals(BASE_START.plusDays(7L * 51), ranges.get(50).start());
    }

    @Test
    void maxOccurrences1_returnsEmptyList() {
        // maxOccurrences=1 -> only the parent, no children.
        var ranges = RecurrenceGenerator.generate(
                BASE_START, BASE_END, RecurrenceFrequency.WEEKLY, null, 1);

        assertTrue(ranges.isEmpty());
    }

    @Test
    void maxOccurrences2_returnsExactlyOneChild() {
        var ranges = RecurrenceGenerator.generate(
                BASE_START, BASE_END, RecurrenceFrequency.WEEKLY, null, 2);

        assertEquals(1, ranges.size());
        assertEquals(BASE_START.plusDays(7), ranges.get(0).start());
    }

    @Test
    void untilOnly_neverThrows_andRespectsBound() {
        LocalDate until = BASE_START.toLocalDate().plusDays(28);
        var ranges = RecurrenceGenerator.generate(
                BASE_START, BASE_END, RecurrenceFrequency.WEEKLY, until, null);

        // +7, +14, +21, +28 — all before or on until
        assertEquals(4, ranges.size());
    }

    @Test
    void untilOnly_capStillEnforcedAt52() {
        LocalDate until = BASE_START.toLocalDate().plusYears(10);
        var ranges = RecurrenceGenerator.generate(
                BASE_START, BASE_END, RecurrenceFrequency.WEEKLY, until, null);

        assertEquals(51, ranges.size());
    }

    @Test
    void preservesEndOffsetForEachOccurrence() {
        LocalDateTime start = LocalDateTime.of(2026, 6, 1, 18, 0);
        LocalDateTime end = LocalDateTime.of(2026, 6, 1, 21, 30); // 3h30 duration

        List<RecurrenceGenerator.DateRange> ranges = RecurrenceGenerator.generate(
                start, end, RecurrenceFrequency.WEEKLY, null, 4);

        for (var r : ranges) {
            assertEquals(java.time.Duration.ofHours(3).plusMinutes(30),
                    java.time.Duration.between(r.start(), r.end()));
        }
    }
}
