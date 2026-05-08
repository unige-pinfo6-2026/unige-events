package ch.unige.events.dto.comment;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Inbound DTO for {@code POST /api/events/{eventId}/comments} (SCRUM-139).
 *
 * <p>{@code parentCommentId} is optional ({@code null} = top-level). When set, the
 * service verifies that the parent exists, belongs to the same event and is not
 * itself a reply (max 1 niveau, cf. spec décision 7).
 */
public record CreateCommentRequest(
        @NotBlank
        @Size(max = 2000)
        String content,
        Long parentCommentId
) {}
