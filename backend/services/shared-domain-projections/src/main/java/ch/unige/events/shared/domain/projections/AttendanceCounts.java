package ch.unige.events.shared.domain.projections;

/**
 * Compact view of the attendance state of a single event — used as
 * intermediate aggregation across attending / waitlisted / interested
 * (legacy field, kept for back-compat).
 */
public record AttendanceCounts(long attending, long waitlisted, long interested) {

    public static AttendanceCounts empty() {
        return new AttendanceCounts(0L, 0L, 0L);
    }
}
