package ch.unige.events.shared.domain.enums;

public enum ReportStatus {
    PENDING,
    REVIEWED,
    DISMISSED;

    /** Whether the report has reached a closed state (reviewed or dismissed). */
    public boolean isClosed() {
        return this == REVIEWED || this == DISMISSED;
    }

    /** Whether the report is still awaiting moderation. */
    public boolean isOpen() {
        return this == PENDING;
    }
}
