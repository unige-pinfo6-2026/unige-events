package ch.unige.events.shared.domain.enums;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins the behavioral helpers added to {@link CoOrganizerStatus} in
 * Étape 24.6.3 (Étape 24.7.7 — C9 coverage).
 */
class CoOrganizerStatusBehaviorTest {

    @Test
    void isFinal_trueForAcceptedAndDeclined_falseForPending() {
        assertFalse(CoOrganizerStatus.PENDING.isFinal());
        assertTrue(CoOrganizerStatus.ACCEPTED.isFinal());
        assertTrue(CoOrganizerStatus.DECLINED.isFinal());
    }

    @Test
    void isOpen_onlyPending() {
        assertTrue(CoOrganizerStatus.PENDING.isOpen());
        assertFalse(CoOrganizerStatus.ACCEPTED.isOpen());
        assertFalse(CoOrganizerStatus.DECLINED.isOpen());
    }

    @Test
    void isAccepted_onlyAccepted() {
        assertFalse(CoOrganizerStatus.PENDING.isAccepted());
        assertTrue(CoOrganizerStatus.ACCEPTED.isAccepted());
        assertFalse(CoOrganizerStatus.DECLINED.isAccepted());
    }
}
