package ch.unige.events.event.dto;

import ch.unige.events.shared.domain.enums.EventCategory;
import ch.unige.events.shared.domain.enums.EventStatus;
import ch.unige.events.shared.domain.enums.Faculty;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

@SuppressWarnings("java:S100")
class UpdateEventRequestTest {

    @Test
    void defaults_statusNull_inheritedFieldsClean() {
        UpdateEventRequest req = new UpdateEventRequest();
        assertNull(req.status);
        assertNull(req.title);
        assertNull(req.startDate);
    }

    @Test
    void publicFields_canBeSetDirectly() {
        UpdateEventRequest req = new UpdateEventRequest();
        req.title = "Updated";
        req.location = "Geneva";
        req.startDate = LocalDateTime.of(2099, 2, 1, 10, 0);
        req.endDate = LocalDateTime.of(2099, 2, 1, 12, 0);
        req.category = EventCategory.SPORTS;
        req.faculty = Faculty.MEDICINE;
        req.status = EventStatus.CANCELLED;

        assertEquals("Updated", req.title);
        assertEquals(EventCategory.SPORTS, req.category);
        assertEquals(Faculty.MEDICINE, req.faculty);
        assertEquals(EventStatus.CANCELLED, req.status);
    }

    @Test
    void status_acceptsAllEnumValues() {
        UpdateEventRequest req = new UpdateEventRequest();
        for (EventStatus s : EventStatus.values()) {
            req.status = s;
            assertEquals(s, req.status);
        }
    }
}
