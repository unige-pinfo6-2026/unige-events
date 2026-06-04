package ch.unige.events.event.favorite.dto;

import ch.unige.events.event.entity.Event;
import ch.unige.events.shared.domain.enums.EventCategory;
import ch.unige.events.shared.domain.enums.EventStatus;
import ch.unige.events.shared.domain.enums.Faculty;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.time.Month;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

@SuppressWarnings("java:S100")
class EventDTOTest {

    @Test
    void canonicalConstructor_keepsAllFields() {
        UUID creatorId = UUID.randomUUID();
        LocalDateTime start = LocalDateTime.of(2026, Month.JUNE, 1, 10, 0);
        LocalDateTime end = LocalDateTime.of(2026, Month.JUNE, 1, 12, 0);
        EventDTO dto = new EventDTO(
                33L, "Fav", "D", "Geneva", start, end,
                EventCategory.CULTURAL, Faculty.PSYCHOLOGY, null,
                creatorId, EventStatus.PUBLISHED, null,
                false, false, null,
                0L, null, 0L, null, null,
                null, null, null, List.of(), start, end, null, null, null);

        assertEquals(33L, dto.id());
        assertEquals(EventCategory.CULTURAL, dto.category());
        assertEquals(Faculty.PSYCHOLOGY, dto.faculty());
        // viewCount/interestedCount are nulled per event-service favorite contract (co-located post-finalization)
        assertNull(dto.viewCount());
        assertNull(dto.interestedCount());
    }

    @Test
    void from_buildsFromEntity_andHonoursContract() {
        Event event = newEvent();
        EventDTO dto = EventDTO.from(event, 5L, 5L, 0L, null, null);
        assertEquals(event.id, dto.id());
        assertEquals(event.title, dto.title());
        assertEquals(event.creatorId, dto.creatorId());
        assertEquals(5L, dto.attendingCount());
        assertEquals(5L, dto.availableSpots());
        assertNull(dto.viewCount());
        assertNull(dto.interestedCount());
        assertEquals(List.of("m"), dto.tags());
        assertNull(dto.coOrganizerOf());
    }

    @Test
    void from_withCoOrganizerOfFalse_propagatesValue() {
        Event event = newEvent();
        EventDTO dto = EventDTO.from(event, 5L, 5L, 0L, null, null, false);
        assertEquals(false, dto.coOrganizerOf());
    }

    @Test
    void recordEqualsAndHashCode_canonicalContract() {
        UUID id = UUID.randomUUID();
        LocalDateTime t = LocalDateTime.of(2026, Month.JUNE, 1, 10, 0);
        EventDTO a = new EventDTO(1L, "T", null, "L", t, t, EventCategory.OTHER, null, null,
                id, EventStatus.DRAFT, null, false, false, null,
                0L, null, 0L, null, null, null, null, null, List.of(), t, t, null, null, null);
        EventDTO b = new EventDTO(1L, "T", null, "L", t, t, EventCategory.OTHER, null, null,
                id, EventStatus.DRAFT, null, false, false, null,
                0L, null, 0L, null, null, null, null, null, List.of(), t, t, null, null, null);
        EventDTO c = new EventDTO(1L, "T", null, "DIFF_LOC", t, t, EventCategory.OTHER, null, null,
                id, EventStatus.DRAFT, null, false, false, null,
                0L, null, 0L, null, null, null, null, null, List.of(), t, t, null, null, null);
        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
        assertNotEquals(a, c);
    }

    private static Event newEvent() {
        Event e = new Event();
        e.id = 44L;
        e.title = "Fav";
        e.description = "D";
        e.location = "L";
        e.startDate = LocalDateTime.of(2026, Month.JUNE, 1, 10, 0);
        e.endDate = LocalDateTime.of(2026, Month.JUNE, 1, 12, 0);
        e.category = EventCategory.CULTURAL;
        e.faculty = Faculty.PSYCHOLOGY;
        e.creatorId = UUID.randomUUID();
        e.status = EventStatus.PUBLISHED;
        e.tags = List.of("m");
        e.createdAt = LocalDateTime.of(2026, Month.MAY, 1, 0, 0);
        e.updatedAt = LocalDateTime.of(2026, Month.MAY, 1, 0, 0);
        return e;
    }
}
