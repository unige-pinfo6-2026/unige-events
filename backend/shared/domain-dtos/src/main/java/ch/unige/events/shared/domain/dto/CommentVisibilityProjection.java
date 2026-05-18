package ch.unige.events.shared.domain.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.UUID;

/**
 * Internal projection of a comment for the cross-service visibility check
 * used by moderation-service to validate report-comment requests (SCRUM-144).
 *
 * <p>Owned by engagement-service, exposed via
 * {@code GET /comments/{commentId}/_internal-visibility?callerId={uuid}}
 * (Décision L). Consumed by moderation-service via
 * {@code EngagementServiceClient.getCommentVisibility}.
 *
 * <p>{@code callerHasAccess} is always {@code true} when the projection is
 * returned (the endpoint propagates 404 anti-oracle when the caller has no
 * access to the parent event — ISSUE-92 cascade via
 * {@code EventServiceClient.getByIdWithCoOrgCheck}). The field is kept for
 * forward symmetry — a future use case might want to distinguish access
 * levels without changing the wire shape.
 *
 * <p>{@code authorId} carries the comment author's UUID so the caller can
 * enforce the {@code cannot_report_own_comment} rule (Décision N) without
 * a second hop. Cross-service UUID consistency is application-level
 * (DB-per-service strict — piège #8).
 *
 * <p>Internal-only record — not in {@code openapi/openapi.yaml} (cf.
 * {@code internal-endpoints.md} entry #10).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record CommentVisibilityProjection(
        Long commentId,
        Long eventId,
        UUID authorId,
        boolean callerHasAccess
) {
}
