package ch.unige.events.shared.domain.enums;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins the behavioral helpers added to {@link FollowStatus} in
 * Étape 24.6.3 (Étape 24.7.7 — C9 coverage).
 *
 * <p>Note: {@code FollowStatus} only defines {@code PENDING} and
 * {@code ACCEPTED} (a {@code REJECTED} value was deliberately NOT
 * added in Vague 6 — DB CHECK constraint V14 is incompatible with
 * a third value). With two values, {@code isFinal()} (= "not pending")
 * collapses to {@code == ACCEPTED} on the value space, but the helper
 * intent stays "non-pending state reached".
 */
class FollowStatusBehaviorTest {

    @Test
    void isAccepted_onlyAccepted() {
        assertFalse(FollowStatus.PENDING.isAccepted());
        assertTrue(FollowStatus.ACCEPTED.isAccepted());
    }

    @Test
    void isPending_onlyPending() {
        assertTrue(FollowStatus.PENDING.isPending());
        assertFalse(FollowStatus.ACCEPTED.isPending());
    }

    @Test
    void isFinal_trueForAccepted_falseForPending() {
        assertFalse(FollowStatus.PENDING.isFinal());
        assertTrue(FollowStatus.ACCEPTED.isFinal());
    }

    @Test
    void pendingAndFinal_arePartition() {
        for (FollowStatus s : FollowStatus.values()) {
            assertTrue(s.isPending() ^ s.isFinal(),
                    "isPending/isFinal must partition the value space, failed on " + s);
        }
    }
}
