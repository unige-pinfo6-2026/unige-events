package ch.unige.events.event.dto;

import ch.unige.events.shared.domain.enums.EventCategory;
import ch.unige.events.shared.domain.enums.EventStatus;
import ch.unige.events.shared.domain.enums.Faculty;
import ch.unige.events.shared.domain.enums.RecurrenceFrequency;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SuppressWarnings("java:S100")
class CreateEventRequestTest {

    @Test
    void defaults_areCleanAndStatusUnset() {
        CreateEventRequest req = new CreateEventRequest();
        assertNull(req.getStatus());
        assertNull(req.recurrence);
        // EventRequestBase fields default
        assertNull(req.title);
        assertNotNull(req.tags);
        assertTrue(req.tags.isEmpty());
    }

    @Test
    void mutators_assignAllPublicFields() {
        CreateEventRequest req = new CreateEventRequest();
        req.title = "My Title";
        req.description = "Body";
        req.location = "Geneva";
        req.startDate = LocalDateTime.of(2099, 1, 1, 10, 0);
        req.endDate = LocalDateTime.of(2099, 1, 1, 12, 0);
        req.category = EventCategory.ACADEMIC;
        req.faculty = Faculty.SCIENCES;
        req.bannerUrl = "https://banner";
        req.capacity = 50;
        req.allDay = Boolean.FALSE;
        req.websiteUrl = "https://site";
        req.contactEmail = "a@b.c";
        req.registrationDeadline = LocalDateTime.of(2098, 12, 1, 10, 0);
        req.tags = List.of("t1", "t2");
        req.setStatus(EventStatus.PUBLISHED);
        req.recurrence = new RecurrenceRequest(RecurrenceFrequency.WEEKLY, LocalDate.of(2099, 6, 1), 5);

        assertEquals("My Title", req.title);
        assertEquals(EventCategory.ACADEMIC, req.category);
        assertEquals(Faculty.SCIENCES, req.faculty);
        assertEquals(EventStatus.PUBLISHED, req.getStatus());
        assertEquals(50, req.capacity);
        assertEquals(List.of("t1", "t2"), req.tags);
        assertNotNull(req.recurrence);
        assertEquals(RecurrenceFrequency.WEEKLY, req.recurrence.frequency());
        assertEquals(5, req.recurrence.maxOccurrences());
    }

    @Test
    void setStatus_canBeNulledExplicitly() {
        CreateEventRequest req = new CreateEventRequest();
        req.setStatus(EventStatus.DRAFT);
        assertEquals(EventStatus.DRAFT, req.getStatus());
        req.setStatus(null);
        assertNull(req.getStatus());
    }
}
