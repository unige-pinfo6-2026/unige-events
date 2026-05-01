package ch.unige.events.dto;

import ch.unige.events.dto.event.EventDTO;
import ch.unige.events.entity.Event;
import ch.unige.events.entity.Faculty;
import ch.unige.events.entity.User;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class EventDTOTest {

    @Test
    void from_withCreator_mapsCreatorIdAsUUID() {
        User organizer = new User();
        organizer.id = UUID.randomUUID();

        Event event = new Event();
        event.creator = organizer;

        EventDTO dto = EventDTO.from(event, 0L, event.capacity == null ? null : (long) event.capacity, 0L);

        assertEquals(organizer.id, dto.creatorId());
    }

    @Test
    void from_withNullCreator_returnsNullCreatorId() {
        Event event = new Event();
        event.creator = null;

        EventDTO dto = EventDTO.from(event, 0L, event.capacity == null ? null : (long) event.capacity, 0L);

        assertNull(dto.creatorId());
    }

    @Test
    void from_withFaculty_mapsFaculty() {
        Event event = new Event();
        event.faculty = Faculty.SCIENCES;

        EventDTO dto = EventDTO.from(event, 0L, event.capacity == null ? null : (long) event.capacity, 0L);

        assertEquals(Faculty.SCIENCES, dto.faculty());
    }

    @Test
    void from_withNullFaculty_returnsNullFaculty() {
        Event event = new Event();

        EventDTO dto = EventDTO.from(event, 0L, event.capacity == null ? null : (long) event.capacity, 0L);

        assertNull(dto.faculty());
    }

    @Test
    void from_withAllDayTrue_mapsAllDay() {
        Event event = new Event();
        event.allDay = true;

        EventDTO dto = EventDTO.from(event, 0L, event.capacity == null ? null : (long) event.capacity, 0L);

        assertTrue(dto.allDay());
    }

    @Test
    void from_withAllDayFalse_mapsAllDay() {
        Event event = new Event();

        EventDTO dto = EventDTO.from(event, 0L, event.capacity == null ? null : (long) event.capacity, 0L);

        assertFalse(dto.allDay());
    }

    @Test
    void from_withAttendingCount_mapsAttendingCount() {
        Event event = new Event();

        EventDTO dto = EventDTO.from(event, 42L, event.capacity == null ? null : (long) event.capacity, 0L);

        assertEquals(42L, dto.attendingCount());
    }

    // --- SCRUM-126 — champs additionnels ---

    @Test
    void from_withAllNewFields_mapsAllFields() {
        LocalDateTime deadline = LocalDateTime.now().plusDays(1);
        Event event = new Event();
        event.websiteUrl = "https://example.com/e/42";
        event.contactEmail = "orga@example.com";
        event.registrationDeadline = deadline;
        event.tags = new ArrayList<>(Arrays.asList("cinema", "plein-air"));

        EventDTO dto = EventDTO.from(event, 0L, null, 0L);

        assertEquals("https://example.com/e/42", dto.websiteUrl());
        assertEquals("orga@example.com", dto.contactEmail());
        assertEquals(deadline, dto.registrationDeadline());
        assertEquals(2, dto.tags().size());
        assertTrue(dto.tags().contains("cinema"));
        assertTrue(dto.tags().contains("plein-air"));
    }

    @Test
    void from_withNullTags_returnsEmptyList() {
        Event event = new Event();
        event.tags = null;

        EventDTO dto = EventDTO.from(event, 0L, null, 0L);

        assertNotNull(dto.tags());
        assertTrue(dto.tags().isEmpty());
    }

    @Test
    void from_returnsImmutableTagsCopy_mutationDoesNotAffectDTO() {
        Event event = new Event();
        event.tags = new ArrayList<>(Arrays.asList("a", "b"));

        EventDTO dto = EventDTO.from(event, 0L, null, 0L);
        event.tags.add("c");

        assertEquals(2, dto.tags().size());
        assertThrows(UnsupportedOperationException.class, () -> dto.tags().add("x"));
    }

    // --- featured field ---

    @Test
    void from_withFeaturedTrue_mapsFeaturedTrue() {
        Event event = new Event();
        event.featured = true;

        EventDTO dto = EventDTO.from(event, 0L, null, 0L);

        assertTrue(dto.featured());
    }

    @Test
    void from_withFeaturedFalse_mapsFeaturedFalse() {
        Event event = new Event();
        event.featured = false;

        EventDTO dto = EventDTO.from(event, 0L, null, 0L);

        assertFalse(dto.featured());
    }

    // --- SCRUM-129 — availableSpots & waitlistedCount ---

    @Test
    void from_withNullAvailableSpots_returnsNull() {
        Event event = new Event();
        event.capacity = null;

        EventDTO dto = EventDTO.from(event, 0L, null, 0L);

        assertNull(dto.availableSpots());
    }

    @Test
    void from_withAvailableSpots_mapsValue() {
        Event event = new Event();
        event.capacity = 10;

        EventDTO dto = EventDTO.from(event, 3L, 7L, 0L);

        assertEquals(7L, dto.availableSpots());
    }

    @Test
    void from_withWaitlistedCount_mapsValue() {
        Event event = new Event();

        EventDTO dto = EventDTO.from(event, 0L, null, 5L);

        assertEquals(5L, dto.waitlistedCount());
    }

    @Test
    void from_withZeroAvailableSpots_mapsZero() {
        Event event = new Event();
        event.capacity = 3;

        EventDTO dto = EventDTO.from(event, 3L, 0L, 2L);

        assertEquals(0L, dto.availableSpots());
        assertEquals(2L, dto.waitlistedCount());
    }
}
