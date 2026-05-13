package ch.unige.events.shared.domain.projections;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class EventCapacityTest {

    @Test
    void nullCapacity_returnsNull() {
        assertNull(EventCapacity.computeAvailableSpots(null, 0));
        assertNull(EventCapacity.computeAvailableSpots(null, 100));
    }

    @Test
    void capacityGreaterThanAttending_returnsRemaining() {
        assertEquals(50L, EventCapacity.computeAvailableSpots(50, 0L));
        assertEquals(20L, EventCapacity.computeAvailableSpots(50, 30L));
        assertEquals(1L, EventCapacity.computeAvailableSpots(2, 1L));
    }

    @Test
    void capacityEqualsAttending_returnsZero() {
        assertEquals(0L, EventCapacity.computeAvailableSpots(50, 50L));
    }

    @Test
    void overAttendance_clampsToZero() {
        // Should not happen in practice (WAITLISTED gating prevents it)
        // but the helper must not return a negative.
        assertEquals(0L, EventCapacity.computeAvailableSpots(50, 100L));
    }

    @Test
    void zeroCapacity_returnsZero() {
        assertEquals(0L, EventCapacity.computeAvailableSpots(0, 0L));
        assertEquals(0L, EventCapacity.computeAvailableSpots(0, 5L));
    }
}
