package ch.unige.events.dto;

import ch.unige.events.dto.event.EventDTO;
import ch.unige.events.entity.Event;
import ch.unige.events.entity.User;
import org.junit.jupiter.api.Test;

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
}
