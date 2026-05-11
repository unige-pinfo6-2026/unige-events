package ch.unige.events.shared.domain.enums;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins the behavioral helpers added to {@link EventStatus} in
 * Étape 24.6.3 (Étape 24.7.7 — C9 coverage). One assertion per
 * helper, exhaustive across every enum value, so silent renames /
 * deletions surface immediately.
 */
class EventStatusBehaviorTest {

    @Test
    void isTerminal_coversCancelledExpiredBanned() {
        assertFalse(EventStatus.DRAFT.isTerminal());
        assertFalse(EventStatus.PUBLISHED.isTerminal());
        assertTrue(EventStatus.CANCELLED.isTerminal());
        assertTrue(EventStatus.EXPIRED.isTerminal());
        assertTrue(EventStatus.BANNED.isTerminal());
    }

    @Test
    void isVisible_onlyPublished() {
        for (EventStatus s : EventStatus.values()) {
            assertEquals(s == EventStatus.PUBLISHED, s.isVisible(),
                    "isVisible mismatch for " + s);
        }
    }

    @Test
    void isSoftDeleted_coversCancelledAndBanned() {
        assertFalse(EventStatus.DRAFT.isSoftDeleted());
        assertFalse(EventStatus.PUBLISHED.isSoftDeleted());
        assertTrue(EventStatus.CANCELLED.isSoftDeleted());
        assertFalse(EventStatus.EXPIRED.isSoftDeleted());
        assertTrue(EventStatus.BANNED.isSoftDeleted());
    }

    @Test
    void canTransitionTo_allowedTransitions() {
        assertTrue(EventStatus.DRAFT.canTransitionTo(EventStatus.PUBLISHED));
        assertTrue(EventStatus.DRAFT.canTransitionTo(EventStatus.CANCELLED));
        assertTrue(EventStatus.PUBLISHED.canTransitionTo(EventStatus.CANCELLED));
        assertTrue(EventStatus.PUBLISHED.canTransitionTo(EventStatus.EXPIRED));
        assertTrue(EventStatus.PUBLISHED.canTransitionTo(EventStatus.BANNED));
    }

    @Test
    void canTransitionTo_disallowedTransitions() {
        // DRAFT → terminal (other than CANCELLED) blocked
        assertFalse(EventStatus.DRAFT.canTransitionTo(EventStatus.EXPIRED));
        assertFalse(EventStatus.DRAFT.canTransitionTo(EventStatus.BANNED));

        // PUBLISHED → DRAFT blocked
        assertFalse(EventStatus.PUBLISHED.canTransitionTo(EventStatus.DRAFT));

        // Terminal → anything blocked
        assertFalse(EventStatus.CANCELLED.canTransitionTo(EventStatus.PUBLISHED));
        assertFalse(EventStatus.CANCELLED.canTransitionTo(EventStatus.DRAFT));
        assertFalse(EventStatus.EXPIRED.canTransitionTo(EventStatus.PUBLISHED));
        assertFalse(EventStatus.BANNED.canTransitionTo(EventStatus.PUBLISHED));
    }

    @Test
    void canTransitionTo_selfLoop_returnsFalse() {
        for (EventStatus s : EventStatus.values()) {
            assertFalse(s.canTransitionTo(s),
                    "self-loop must be disallowed for " + s);
        }
    }
}
