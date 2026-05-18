package ch.unige.events.notification.entity;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Sentinel: pin the enum surface so accidentally adding a phase 4 value
 * before its widening migration breaks the test (and the build).
 *
 * <p>Post-SCRUM-140 phase 2 : the enum holds <strong>9</strong> values
 * (4 phase 1 SCRUM-99 + 3 phase 2 SCRUM-140 emitted + 2 phase 3 SCRUM-145
 * reserved-not-emitted). The DB CHECK constraint
 * {@code notifications_type_check} is widened to match by
 * {@code V2__widen_notification_type_check.sql}. Adding a 10th value here
 * without a V3 migration would crash at INSERT time.
 */
class NotificationTypeTest {

    @Test
    void enum_hasExactlyNineValues() {
        assertEquals(9, NotificationType.values().length,
                "The enum is locked to 9 values (4 SCRUM-99 + 3 SCRUM-140 + 2 SCRUM-145 reserved) "
                        + "— extending it requires a V3 widening migration first");
    }

    @Test
    void enum_valuesAreInExpectedOrder() {
        // Order pinned so ordinal-based persistence won't surprise us if
        // anyone toggles to @Enumerated(ORDINAL) by accident. SCRUM-140 phase
        // 2 appends 3 new values + 2 SCRUM-145 reserved — phase 1 ordinals
        // are preserved.
        assertEquals(NotificationType.EVENT_UPDATED,    NotificationType.values()[0]);
        assertEquals(NotificationType.EVENT_CANCELLED,  NotificationType.values()[1]);
        assertEquals(NotificationType.EVENT_REMINDER,   NotificationType.values()[2]);
        assertEquals(NotificationType.NEW_ATTENDEE,     NotificationType.values()[3]);
        assertEquals(NotificationType.NEW_FOLLOWER,     NotificationType.values()[4]);
        assertEquals(NotificationType.FOLLOW_REQUEST,   NotificationType.values()[5]);
        assertEquals(NotificationType.FOLLOW_ACCEPTED,  NotificationType.values()[6]);
        assertEquals(NotificationType.COMMENT_MENTION,  NotificationType.values()[7]);
        assertEquals(NotificationType.NEW_COMMENT,      NotificationType.values()[8]);
    }

    @Test
    void valueOf_roundtripsNames() {
        for (NotificationType t : NotificationType.values()) {
            assertEquals(t, NotificationType.valueOf(t.name()));
            assertNotNull(t.name());
        }
    }
}
