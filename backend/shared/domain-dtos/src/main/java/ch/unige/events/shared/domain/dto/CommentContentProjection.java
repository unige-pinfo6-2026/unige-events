package ch.unige.events.shared.domain.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Internal projection of a comment's display context, used by
 * moderation-service to enrich the admin reports listing for comment-bound
 * reports (QA bug batch, bug ③). Owned by engagement-service, exposed via
 * {@code GET /comments/_internal-by-ids?ids=<csv>} (gated {@code @Internal}).
 *
 * <p>Carries just enough to render a comment report in the admin dashboard
 * without leaking the full comment graph : the comment {@code id}, its parent
 * {@code eventId} (so the admin can deep-link to the event detail page), and
 * the {@code content}. Not in {@code openapi/openapi.yaml} — documented in
 * {@code backend/docs/internal-endpoints.md}.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record CommentContentProjection(
        Long id,
        Long eventId,
        String content
) {
}
