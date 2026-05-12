package ch.unige.events.shared.domain.projections;

/**
 * Capacity projection helpers — replaces the duplicated
 * {@code computeAvailableSpots} private static that lived in 6
 * services (event, attendance, co-organizer, favorite, calendar,
 * stats). Cf. REFACTOR-005.
 */
public final class EventCapacity {

    private EventCapacity() {
    }

    /**
     * Returns the number of spots still open for an event, or
     * {@code null} when the event has unlimited capacity.
     *
     * <p>Always non-negative when defined — over-attendance (which
     * should never happen with the WAITLISTED gating) clamps to 0
     * rather than returning a negative.
     */
    public static Long computeAvailableSpots(Integer capacity, long attendingCount) {
        if (capacity == null) {
            return null;
        }
        return Math.max(0L, capacity.longValue() - attendingCount);
    }
}
