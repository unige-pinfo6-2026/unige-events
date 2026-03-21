package ch.unige.events.dto;

import org.eclipse.microprofile.openapi.annotations.media.Schema;

@Schema(name = "ValidationErrorDetail")
public record ValidationErrorDetail(
    @Schema(nullable = true)
    String field,
    String message
) {
}
