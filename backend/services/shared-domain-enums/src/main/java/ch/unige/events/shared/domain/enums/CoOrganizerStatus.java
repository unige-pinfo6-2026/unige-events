package ch.unige.events.shared.domain.enums;

public enum CoOrganizerStatus {
    PENDING,
    ACCEPTED,
    DECLINED;

    /** Whether the invitation has reached a non-pending state. */
    public boolean isFinal() {
        return this != PENDING;
    }

    /** Whether the invitation is still awaiting a decision. */
    public boolean isOpen() {
        return this == PENDING;
    }

    /** Whether the invitation was accepted. */
    public boolean isAccepted() {
        return this == ACCEPTED;
    }
}
