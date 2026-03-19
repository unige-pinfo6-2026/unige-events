package ch.unige.events.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

import java.util.List;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
@Schema(name = "ValidationErrorResponse", description = "Validation error payload with details.")
public record ValidationErrorResponse(
    String error,
    String message,
    List<ValidationErrorDetail> details
) {
}
