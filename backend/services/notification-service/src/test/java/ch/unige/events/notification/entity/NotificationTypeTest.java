package ch.unige.events.notification.entity;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Sentinel: pin the phase 1 enum surface so accidentally adding a phase 2
 * value before its V2 widening migration breaks the test (and the build).
 */
class NotificationTypeTest {

    @Test
    void phase1_hasExactlyFourValues() {
        assertEquals(4, NotificationType.values().length,
                "Phase 1 enum is locked to 4 values until SCRUM-140/144/145 ship the V2 widening migration");
    }

    @Test
    void phase1_valuesAreInExpectedOrder() {
        // Order pinned so ordinal-based persistence won't surprise us if
        // anyone toggles to @Enumerated(ORDINAL) by accident.
        assertEquals(NotificationType.EVENT_UPDATED,   NotificationType.values()[0]);
        assertEquals(NotificationType.EVENT_CANCELLED, NotificationType.values()[1]);
        assertEquals(NotificationType.EVENT_REMINDER,  NotificationType.values()[2]);
        assertEquals(NotificationType.NEW_ATTENDEE,    NotificationType.values()[3]);
    }

    @Test
    void valueOf_roundtripsNames() {
        for (NotificationType t : NotificationType.values()) {
            assertEquals(t, NotificationType.valueOf(t.name()));
            assertNotNull(t.name());
        }
    }
}
