package ch.unige.events.shared.domain.projections;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AttendanceCountsTest {

    @Test
    void empty_returnsZeros() {
        AttendanceCounts c = AttendanceCounts.empty();
        assertEquals(0L, c.attending());
        assertEquals(0L, c.waitlisted());
        assertEquals(0L, c.interested());
    }

    @Test
    void recordExposesAllThreeFields() {
        AttendanceCounts c = new AttendanceCounts(10, 3, 7);
        assertEquals(10L, c.attending());
        assertEquals(3L, c.waitlisted());
        assertEquals(7L, c.interested());
    }
}
