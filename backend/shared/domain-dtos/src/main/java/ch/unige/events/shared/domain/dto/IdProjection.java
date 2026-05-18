package ch.unige.events.shared.domain.dto;

import java.util.UUID;

/**
 * Minimal record returned by internal endpoints that project a single
 * {@code UUID} identity. Primary consumer (SCRUM-99) :
 * {@code GET /users/_internal-by-auth0-id/{auth0Id}} on user-service —
 * notification-service uses it to resolve {@code auth0Id → userId}
 * without loading the full {@code User} payload.
 *
 * <p>Lives in {@code shared-domain-dtos} so the matching REST client
 * method on {@code UserServiceClient} can return it without
 * cross-service stub. Other internal endpoints that need to surface
 * "just an UUID" can reuse this record without introducing a per-call
 * one-off type.
 */
public record IdProjection(UUID id) {
}
