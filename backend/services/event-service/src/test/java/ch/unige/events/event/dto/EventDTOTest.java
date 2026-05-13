package ch.unige.events.event.dto;

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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SuppressWarnings("java:S100")
class EventDTOTest {

    @Test
    void canonicalConstructor_keepsAllFields() {
        UUID creatorId = UUID.randomUUID();
        LocalDateTime start = LocalDateTime.of(2026, 6, 1, 10, 0);
        LocalDateTime end = LocalDateTime.of(2026, 6, 1, 12, 0);
        EventDTO dto = new EventDTO(
                42L, "Title", "Desc", "Geneva", start, end,
                EventCategory.ACADEMIC, Faculty.SCIENCES, "https://banner",
                creatorId, EventStatus.PUBLISHED, 100,
                false, true, start,
                7L, 50L, 3L, 200L, 5L,
                "https://site", "contact@unige.ch", end,
                List.of("tag1", "tag2"), start, end,
                null, "FREQ=WEEKLY", Boolean.TRUE);

        assertEquals(42L, dto.id());
        assertEquals("Title", dto.title());
        assertEquals(creatorId, dto.creatorId());
        assertEquals(EventCategory.ACADEMIC, dto.category());
        assertEquals(Faculty.SCIENCES, dto.faculty());
        assertEquals(EventStatus.PUBLISHED, dto.status());
        assertEquals(7L, dto.attendingCount());
        assertEquals(3L, dto.waitlistedCount());
        assertEquals(List.of("tag1", "tag2"), dto.tags());
        assertEquals(Boolean.TRUE, dto.coOrganizerOf());
        assertTrue(dto.featured());
    }

    @Test
    void from_withNullTags_yieldsEmptyList() {
        Event event = newEvent();
        event.tags = null;
        EventDTO dto = EventDTO.from(event, 1L, 10L, 0L, 0L, 0L);
        assertEquals(List.of(), dto.tags());
        assertNull(dto.coOrganizerOf());
    }

    @Test
    void from_withCoOrganizerFlag_propagatesIt() {
        Event event = newEvent();
        EventDTO dto = EventDTO.from(event, 5L, 95L, 2L, 100L, 3L, Boolean.TRUE);
        assertEquals(Boolean.TRUE, dto.coOrganizerOf());
        assertEquals(5L, dto.attendingCount());
        assertEquals(95L, dto.availableSpots());
        assertEquals(2L, dto.waitlistedCount());
        assertEquals(event.creatorId, dto.creatorId());
        assertEquals(List.of("a", "b"), dto.tags());
    }

    @Test
    void recordEqualsAndHashCode_canonicalContract() {
        UUID id = UUID.randomUUID();
        LocalDateTime t = LocalDateTime.of(2026, 6, 1, 10, 0);
        EventDTO a = new EventDTO(1L, "T", null, "Loc", t, t, EventCategory.OTHER,
                null, null, id, EventStatus.DRAFT, null, false, false, null,
                0L, null, 0L, null, null, null, null, null, List.of(), t, t, null, null, null);
        EventDTO b = new EventDTO(1L, "T", null, "Loc", t, t, EventCategory.OTHER,
                null, null, id, EventStatus.DRAFT, null, false, false, null,
                0L, null, 0L, null, null, null, null, null, List.of(), t, t, null, null, null);
        EventDTO c = new EventDTO(2L, "T", null, "Loc", t, t, EventCategory.OTHER,
                null, null, id, EventStatus.DRAFT, null, false, false, null,
                0L, null, 0L, null, null, null, null, null, List.of(), t, t, null, null, null);
        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
        assertNotEquals(a, c);
    }

    private static Event newEvent() {
        Event e = new Event();
        e.id = 1L;
        e.title = "T";
        e.description = "D";
        e.location = "L";
        e.startDate = LocalDateTime.of(2026, 6, 1, 10, 0);
        e.endDate = LocalDateTime.of(2026, 6, 1, 12, 0);
        e.category = EventCategory.SPORTS;
        e.faculty = Faculty.MEDICINE;
        e.bannerUrl = null;
        e.creatorId = UUID.randomUUID();
        e.status = EventStatus.PUBLISHED;
        e.capacity = 100;
        e.allDay = false;
        e.featured = false;
        e.featuredAt = null;
        e.websiteUrl = null;
        e.contactEmail = null;
        e.registrationDeadline = null;
        e.tags = List.of("a", "b");
        e.createdAt = LocalDateTime.of(2026, 5, 1, 0, 0);
        e.updatedAt = LocalDateTime.of(2026, 5, 1, 0, 0);
        e.parentEventId = null;
        e.recurrenceRule = null;
        return e;
    }
}
