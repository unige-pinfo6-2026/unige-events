package ch.unige.events.dto;

import org.eclipse.microprofile.openapi.annotations.media.Schema;

import java.util.List;

@Schema(name = "ValidationErrorResponse", description = "Validation error payload with details.")
public record ValidationErrorResponse(
    String error,
    String message,
    List<ValidationErrorDetail> details
) {
}
