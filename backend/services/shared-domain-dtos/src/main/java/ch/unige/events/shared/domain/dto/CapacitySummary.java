package ch.unige.events.shared.domain.dto;

/**
 * Internal projection of an event's capacity state. Returned by
 * event-service's {@code GET /events/{id}/capacity-summary} — consumed
 * by engagement-service (renamed post-finalization) for capacity-gated promotion decisions
 * (cf. completion-spec Décision B + internal-endpoints.md).
 *
 * <p>{@code capacity} is null for unbounded events.
 */
public record CapacitySummary(
        Integer capacity,
        long currentAttending,
        long waitlistedCount
) {
}
