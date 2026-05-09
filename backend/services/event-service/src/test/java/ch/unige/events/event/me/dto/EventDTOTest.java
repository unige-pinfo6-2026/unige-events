package ch.unige.events.event.me.dto;

import ch.unige.events.event.entity.Event;
import ch.unige.events.shared.domain.enums.EventCategory;
import ch.unige.events.shared.domain.enums.EventStatus;
import ch.unige.events.shared.domain.enums.Faculty;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

@SuppressWarnings("java:S100")
class EventDTOTest {

    @Test
    void canonicalConstructor_keepsAllFields() {
        UUID creatorId = UUID.randomUUID();
        LocalDateTime start = LocalDateTime.of(2026, 6, 1, 10, 0);
        LocalDateTime end = LocalDateTime.of(2026, 6, 1, 12, 0);
        EventDTO dto = new EventDTO(
                10L, "MyEvent", "D", "Geneva", start, end,
                EventCategory.SOCIAL, Faculty.LETTERS, null,
                creatorId, EventStatus.DRAFT, null,
                true, false, null,
                4L, 96L, 1L, 50L, 2L,
                null, null, null, List.of(), start, end, null, null);

        assertEquals(10L, dto.id());
        assertEquals("MyEvent", dto.title());
        assertEquals(EventCategory.SOCIAL, dto.category());
        assertEquals(Faculty.LETTERS, dto.faculty());
        assertEquals(EventStatus.DRAFT, dto.status());
        assertEquals(4L, dto.attendingCount());
        assertEquals(96L, dto.availableSpots());
    }

    @Test
    void from_buildsFromEntity_andCopiesTags() {
        Event event = newEvent();
        EventDTO dto = EventDTO.from(event, 7L, 93L, 3L, 100L, 4L);
        assertEquals(event.id, dto.id());
        assertEquals(event.title, dto.title());
        assertEquals(7L, dto.attendingCount());
        assertEquals(93L, dto.availableSpots());
        assertEquals(3L, dto.waitlistedCount());
        assertEquals(100L, dto.viewCount());
        assertEquals(4L, dto.interestedCount());
        assertEquals(List.of("x", "y"), dto.tags());
    }

    @Test
    void recordEqualsAndHashCode_canonicalContract() {
        UUID id = UUID.randomUUID();
        LocalDateTime t = LocalDateTime.of(2026, 6, 1, 10, 0);
        EventDTO a = new EventDTO(1L, "T", null, "L", t, t, EventCategory.OTHER, null, null,
                id, EventStatus.DRAFT, null, false, false, null,
                0L, null, 0L, null, null, null, null, null, List.of(), t, t, null, null);
        EventDTO b = new EventDTO(1L, "T", null, "L", t, t, EventCategory.OTHER, null, null,
                id, EventStatus.DRAFT, null, false, false, null,
                0L, null, 0L, null, null, null, null, null, List.of(), t, t, null, null);
        EventDTO c = new EventDTO(1L, "OTHER_TITLE", null, "L", t, t, EventCategory.OTHER, null, null,
                id, EventStatus.DRAFT, null, false, false, null,
                0L, null, 0L, null, null, null, null, null, List.of(), t, t, null, null);
        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
        assertNotEquals(a, c);
    }

    private static Event newEvent() {
        Event e = new Event();
        e.id = 11L;
        e.title = "MyEvent";
        e.description = "D";
        e.location = "L";
        e.startDate = LocalDateTime.of(2026, 6, 1, 10, 0);
        e.endDate = LocalDateTime.of(2026, 6, 1, 12, 0);
        e.category = EventCategory.SOCIAL;
        e.faculty = Faculty.LETTERS;
        e.creatorId = UUID.randomUUID();
        e.status = EventStatus.PUBLISHED;
        e.capacity = 100;
        e.tags = List.of("x", "y");
        e.createdAt = LocalDateTime.of(2026, 5, 1, 0, 0);
        e.updatedAt = LocalDateTime.of(2026, 5, 1, 0, 0);
        return e;
    }
}
