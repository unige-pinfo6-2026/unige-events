package ch.unige.events.dto;

import ch.unige.events.dto.event.EventDTO;
import ch.unige.events.entity.Event;
import ch.unige.events.entity.Faculty;
import ch.unige.events.entity.User;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class EventDTOTest {

    @Test
    void from_withCreator_mapsCreatorIdAsUUID() {
        User organizer = new User();
        organizer.id = UUID.randomUUID();

        Event event = new Event();
        event.creator = organizer;

        EventDTO dto = EventDTO.from(event, 0L);

        assertEquals(organizer.id, dto.creatorId());
    }

    @Test
    void from_withNullCreator_returnsNullCreatorId() {
        Event event = new Event();
        event.creator = null;

        EventDTO dto = EventDTO.from(event, 0L);

        assertNull(dto.creatorId());
    }

    @Test
    void from_withFaculty_mapsFaculties() {
        Event event = new Event();
        event.faculties.add(Faculty.SCIENCES);

        EventDTO dto = EventDTO.from(event);

        assertTrue(dto.faculties().contains(Faculty.SCIENCES));
    }

    @Test
    void from_withNoFaculty_returnsEmptyList() {
        Event event = new Event();

        EventDTO dto = EventDTO.from(event);

        assertTrue(dto.faculties().isEmpty());
    }

    @Test
    void from_withMultipleFaculties_mapsAll() {
        Event event = new Event();
        event.faculties.add(Faculty.SCIENCES);
        event.faculties.add(Faculty.MEDECINE);

        EventDTO dto = EventDTO.from(event);

        assertEquals(2, dto.faculties().size());
        assertTrue(dto.faculties().containsAll(List.of(Faculty.SCIENCES, Faculty.MEDECINE)));
    }
}
