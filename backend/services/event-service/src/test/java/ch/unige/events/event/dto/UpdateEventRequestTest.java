package ch.unige.events.event.dto;

import ch.unige.events.shared.domain.enums.EventCategory;
import ch.unige.events.shared.domain.enums.EventStatus;
import ch.unige.events.shared.domain.enums.Faculty;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SuppressWarnings("java:S100")
class UpdateEventRequestTest {

    @Test
    void canonicalConstructor_nullTags_normalisedToEmptyImmutableList() {
        UpdateEventRequest req = new UpdateEventRequest(
                "T", "D", "L",
                LocalDateTime.of(2099, 2, 1, 10, 0),
                LocalDateTime.of(2099, 2, 1, 12, 0),
                EventCategory.SPORTS, null, null,
                null, null, null, null, null,
                null, null);
        assertNotNull(req.tags());
        assertTrue(req.tags().isEmpty());
        assertNull(req.status());
    }

    @Test
    void canonicalConstructor_populatedFields_keepsAllValues() {
        UpdateEventRequest req = new UpdateEventRequest(
                "Updated", null, "Geneva",
                LocalDateTime.of(2099, 2, 1, 10, 0),
                LocalDateTime.of(2099, 2, 1, 12, 0),
                EventCategory.SPORTS, Faculty.MEDICINE, null,
                null, null, null, null, null,
                null, EventStatus.CANCELLED);

        assertEquals("Updated", req.title());
        assertEquals(EventCategory.SPORTS, req.category());
        assertEquals(Faculty.MEDICINE, req.faculty());
        assertEquals(EventStatus.CANCELLED, req.status());
    }

    @Test
    void status_acceptsAllEnumValues() {
        for (EventStatus s : EventStatus.values()) {
            UpdateEventRequest req = new UpdateEventRequest(
                    "T", null, "L",
                    LocalDateTime.of(2099, 2, 1, 10, 0),
                    LocalDateTime.of(2099, 2, 1, 12, 0),
                    EventCategory.SPORTS, null, null,
                    null, null, null, null, null,
                    List.of(), s);
            assertEquals(s, req.status());
        }
    }
}
