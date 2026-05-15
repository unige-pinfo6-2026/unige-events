package ch.unige.events.event.stats.dto;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

@SuppressWarnings("java:S100")
class EventStatsDTOTest {

    @Test
    void canonicalConstructor_keepsAllCounts() {
        EventStatsDTO dto = new EventStatsDTO(10L, 20L, 30L);
        assertEquals(10L, dto.attendingCount());
        assertEquals(20L, dto.interestedCount());
        assertEquals(30L, dto.viewCount());
    }

    @Test
    void canonicalConstructor_supportsZeroes() {
        EventStatsDTO dto = new EventStatsDTO(0L, 0L, 0L);
        assertEquals(0L, dto.attendingCount());
        assertEquals(0L, dto.interestedCount());
        assertEquals(0L, dto.viewCount());
    }

    @Test
    void recordEqualsAndHashCode_canonicalContract() {
        EventStatsDTO a = new EventStatsDTO(1L, 2L, 3L);
        EventStatsDTO b = new EventStatsDTO(1L, 2L, 3L);
        EventStatsDTO c = new EventStatsDTO(1L, 2L, 4L);
        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
        assertNotEquals(a, c);
    }
}
