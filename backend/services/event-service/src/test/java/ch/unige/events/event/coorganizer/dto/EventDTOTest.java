package ch.unige.events.event.coorganizer.dto;

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
                21L, "Co-org", "D", "Geneva", start, end,
                EventCategory.CONFERENCE, Faculty.GSEM, null,
                creatorId, EventStatus.PUBLISHED, 50,
                false, false, null,
                10L, 40L, 0L, null, null,
                null, null, null, List.of("a"), start, end, null, null, null);

        assertEquals(21L, dto.id());
        assertEquals("Co-org", dto.title());
        assertEquals(EventCategory.CONFERENCE, dto.category());
        assertEquals(Faculty.GSEM, dto.faculty());
        assertEquals(50, dto.capacity());
        assertEquals(10L, dto.attendingCount());
        assertEquals(40L, dto.availableSpots());
    }

    @Test
    void from_buildsFromEntity() {
        Event event = newEvent();
        EventDTO dto = EventDTO.from(event, 2L, 8L, 1L, 30L, 4L);
        assertEquals(event.id, dto.id());
        assertEquals(event.title, dto.title());
        assertEquals(event.creatorId, dto.creatorId());
        assertEquals(2L, dto.attendingCount());
        assertEquals(8L, dto.availableSpots());
        assertEquals(1L, dto.waitlistedCount());
        assertEquals(List.of("k"), dto.tags());
        assertNull(dto.coOrganizerOf());
    }

    @Test
    void from_withCoOrganizerOfFalse_propagatesValue() {
        Event event = newEvent();
        EventDTO dto = EventDTO.from(event, 2L, 8L, 1L, 30L, 4L, false);
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
        EventDTO c = new EventDTO(1L, "T", null, "L", t, t, EventCategory.OTHER, null, null,
                id, EventStatus.DRAFT, null, false, false, null,
                99L, null, 0L, null, null, null, null, null, List.of(), t, t, null, null, null);
        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
        assertNotEquals(a, c);
    }

    private static Event newEvent() {
        Event e = new Event();
        e.id = 22L;
        e.title = "Co-org";
        e.description = "D";
        e.location = "L";
        e.startDate = LocalDateTime.of(2026, Month.JUNE, 1, 10, 0);
        e.endDate = LocalDateTime.of(2026, Month.JUNE, 1, 12, 0);
        e.category = EventCategory.CONFERENCE;
        e.faculty = Faculty.GSEM;
        e.creatorId = UUID.randomUUID();
        e.status = EventStatus.PUBLISHED;
        e.capacity = 10;
        e.tags = List.of("k");
        e.createdAt = LocalDateTime.of(2026, Month.MAY, 1, 0, 0);
        e.updatedAt = LocalDateTime.of(2026, Month.MAY, 1, 0, 0);
        return e;
    }
}
