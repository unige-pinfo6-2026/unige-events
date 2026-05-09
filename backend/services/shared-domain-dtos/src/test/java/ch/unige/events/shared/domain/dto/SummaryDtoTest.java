package ch.unige.events.shared.domain.dto;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SummaryDtoTest {

    @Test
    void capacitySummary_recordExposesFields() {
        CapacitySummary s = new CapacitySummary(50, 30, 5);
        assertEquals(50, s.capacity());
        assertEquals(30, s.currentAttending());
        assertEquals(5, s.waitlistedCount());
    }

    @Test
    void capacitySummary_unboundedCapacity_null() {
        CapacitySummary s = new CapacitySummary(null, 100, 0);
        assertNull(s.capacity());
    }

    @Test
    void attendanceSummary_of_zeroesInterested() {
        AttendanceSummary s = AttendanceSummary.of(20, 5);
        assertEquals(20, s.attending());
        assertEquals(5, s.waitlisted());
        assertEquals(0, s.interested());
    }

    @Test
    void attendanceSummary_directConstruction_keepsInterested() {
        AttendanceSummary s = new AttendanceSummary(10, 1, 7);
        assertEquals(7, s.interested());
    }

    @Test
    void followCounts_empty_returnsZeros() {
        FollowCounts fc = FollowCounts.empty();
        assertEquals(0L, fc.followers());
        assertEquals(0L, fc.following());
        assertNull(fc.followStatus());
    }

    @Test
    void coOrganizerCheck_yesNo() {
        assertTrue(CoOrganizerCheck.yes().accepted());
        assertFalse(CoOrganizerCheck.no().accepted());
    }
}
