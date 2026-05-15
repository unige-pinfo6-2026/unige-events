package ch.unige.events.engagement.comment.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateCommentRequest(
        @NotBlank
        @Size(max = 500)
        String content,
        Long parentCommentId
) {}
