package ch.unige.events.event.dto;

import ch.unige.events.shared.domain.enums.EventCategory;
import ch.unige.events.shared.domain.enums.EventStatus;
import ch.unige.events.shared.domain.enums.Faculty;
import ch.unige.events.shared.domain.enums.RecurrenceFrequency;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Month;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SuppressWarnings("java:S100")
class CreateEventRequestTest {

    @Test
    void canonicalConstructor_nullTags_normalisedToEmptyImmutableList() {
        CreateEventRequest req = new CreateEventRequest(
                "T", "D", "L",
                LocalDateTime.of(2099, Month.JANUARY, 1, 10, 0),
                LocalDateTime.of(2099, Month.JANUARY, 1, 12, 0),
                EventCategory.OTHER, null, null,
                null, null, null, null, null,
                null, null, null);
        assertNotNull(req.tags());
        assertTrue(req.tags().isEmpty());
        assertNull(req.status());
        assertNull(req.recurrence());
    }

    @Test
    void canonicalConstructor_populatedFields_keepsAllValues() {
        RecurrenceRequest rec = new RecurrenceRequest(RecurrenceFrequency.WEEKLY, LocalDate.of(2099, Month.JUNE, 1), 5);
        CreateEventRequest req = new CreateEventRequest(
                "My Title", "Body", "Geneva",
                LocalDateTime.of(2099, Month.JANUARY, 1, 10, 0),
                LocalDateTime.of(2099, Month.JANUARY, 1, 12, 0),
                EventCategory.ACADEMIC, Faculty.SCIENCES, "https://banner",
                50, Boolean.FALSE, "https://site", "a@b.c",
                LocalDateTime.of(2098, Month.DECEMBER, 1, 10, 0),
                List.of("t1", "t2"),
                EventStatus.PUBLISHED, rec);

        assertEquals("My Title", req.title());
        assertEquals(EventCategory.ACADEMIC, req.category());
        assertEquals(Faculty.SCIENCES, req.faculty());
        assertEquals(EventStatus.PUBLISHED, req.status());
        assertEquals(50, req.capacity());
        assertEquals(List.of("t1", "t2"), req.tags());
        assertNotNull(req.recurrence());
        assertEquals(RecurrenceFrequency.WEEKLY, req.recurrence().frequency());
        assertEquals(5, req.recurrence().maxOccurrences());
    }

    @Test
    void canonicalConstructor_tagsAreDefensivelyCopied() {
        java.util.ArrayList<String> mutable = new java.util.ArrayList<>(List.of("a", "b"));
        CreateEventRequest req = new CreateEventRequest(
                "T", null, "L",
                LocalDateTime.of(2099, Month.JANUARY, 1, 10, 0),
                LocalDateTime.of(2099, Month.JANUARY, 1, 12, 0),
                EventCategory.OTHER, null, null,
                null, null, null, null, null,
                mutable, null, null);
        mutable.clear();
        assertEquals(2, req.tags().size());
    }
}
