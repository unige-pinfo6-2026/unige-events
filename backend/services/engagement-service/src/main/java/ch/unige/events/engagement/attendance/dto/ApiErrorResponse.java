package ch.unige.events.engagement.attendance.dto;

import org.eclipse.microprofile.openapi.annotations.media.Schema;

@Schema(name = "ApiErrorResponse", description = "Standard API error payload.")
public record ApiErrorResponse(
    String error,
    String message
) {
}
