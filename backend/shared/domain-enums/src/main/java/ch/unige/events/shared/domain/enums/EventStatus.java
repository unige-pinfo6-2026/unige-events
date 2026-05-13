package ch.unige.events.shared.domain.enums;

public enum EventStatus {
    DRAFT,
    PUBLISHED,
    CANCELLED,
    EXPIRED,
    BANNED;

    /** Terminal states: no further transitions allowed. */
    public boolean isTerminal() {
        return this == CANCELLED || this == EXPIRED || this == BANNED;
    }

    /** Visible publicly (anonymous + non-admin non-creator). */
    public boolean isVisible() {
        return this == PUBLISHED;
    }

    /** Whether the soft-delete is active (i.e. event hidden from public lists). */
    public boolean isSoftDeleted() {
        return this == CANCELLED || this == BANNED;
    }

    /** Validates a state machine transition. Returns true if {@code this -> next} is allowed. */
    public boolean canTransitionTo(EventStatus next) {
        if (this == next) {
            return false;
        }
        return switch (this) {
            case DRAFT -> next == PUBLISHED || next == CANCELLED;
            case PUBLISHED -> next == CANCELLED || next == EXPIRED || next == BANNED;
            case CANCELLED, EXPIRED, BANNED -> false;
        };
    }
}
