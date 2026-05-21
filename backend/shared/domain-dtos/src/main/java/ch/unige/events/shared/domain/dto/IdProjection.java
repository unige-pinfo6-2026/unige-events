package ch.unige.events.shared.domain.dto;

import java.util.UUID;

/**
 * Slim record used by internal endpoints that project an identity tuple
 * without loading the full {@code User} payload.
 *
 * <p>Two callers today :
 * <ul>
 *   <li>SCRUM-99 — {@code GET /users/_internal-by-auth0-id/{auth0Id}}.
 *       Only {@code id} is populated ; {@code username} is {@code null}.</li>
 *   <li>SCRUM-145 — {@code GET /users/_internal-by-usernames?usernames=…}.
 *       Both fields are populated so notification-service can build the
 *       handle→UUID map without round-tripping the public profile
 *       endpoint per handle.</li>
 * </ul>
 *
 * <p>{@code username} is {@link jakarta.annotation.Nullable nullable} for
 * backward compatibility with the SCRUM-99 endpoint — consumers of that
 * endpoint already read {@link #id()} only, so the new field does not
 * affect them. Lives in {@code shared-domain-dtos} so the REST clients in
 * the same module can return it without a cross-service stub.
 */
public record IdProjection(UUID id, String username) {

    /**
     * Convenience constructor for callers that only need the UUID (the
     * pre-SCRUM-145 shape). Equivalent to {@code new IdProjection(id, null)}.
     */
    public IdProjection(UUID id) {
        this(id, null);
    }
}
