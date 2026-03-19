package ch.unige.events.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
@Schema(name = "ValidationErrorDetail")
public record ValidationErrorDetail(
    @Schema(nullable = true)
    String field,
    String message
) {
}
