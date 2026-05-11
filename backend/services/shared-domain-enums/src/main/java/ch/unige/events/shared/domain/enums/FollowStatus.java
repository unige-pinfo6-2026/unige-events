package ch.unige.events.shared.domain.enums;

public enum FollowStatus {
    PENDING,
    ACCEPTED;

    /** Whether the follow request was accepted. */
    public boolean isAccepted() {
        return this == ACCEPTED;
    }

    /** Whether the follow request is still awaiting a decision. */
    public boolean isPending() {
        return this == PENDING;
    }

    /** Whether the follow request has reached a non-pending state. */
    public boolean isFinal() {
        return this != PENDING;
    }
}
