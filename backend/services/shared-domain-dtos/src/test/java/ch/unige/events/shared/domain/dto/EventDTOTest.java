package ch.unige.events.shared.domain.dto;

import ch.unige.events.shared.domain.enums.EventCategory;
import ch.unige.events.shared.domain.enums.EventStatus;
import ch.unige.events.shared.domain.enums.Faculty;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EventDTOTest {

    @Test
    void recordExposesAllFields() {
        UUID creator = UUID.randomUUID();
        LocalDateTime now = LocalDateTime.now();
        EventDTO dto = new EventDTO(
                42L, "Hackathon", "desc", "Uni-Mail", now, now.plusHours(2),
                EventCategory.ACADEMIC, Faculty.SCIENCES, "banner.jpg", creator,
                EventStatus.PUBLISHED, 50, false, true, now,
                10L, 40L, 0L, 100L, 0L,
                "https://x", "a@b", now.plusHours(1),
                List.of("hack"), now, now,
                null, null, null);
        assertEquals(42L, dto.id());
        assertEquals(creator, dto.creatorId());
        assertEquals(EventStatus.PUBLISHED, dto.status());
        assertTrue(dto.featured());
        assertNull(dto.coOrganizerOf());
    }

    @Test
    void coOrganizerOf_canBeBoxedTrueOrFalse() {
        EventDTO dto = new EventDTO(
                1L, null, null, null, null, null, null, null, null, null,
                EventStatus.PUBLISHED, null, false, false, null,
                null, null, null, null, null, null, null, null,
                List.of(), null, null, null, null, Boolean.TRUE);
        assertEquals(Boolean.TRUE, dto.coOrganizerOf());
    }
}
