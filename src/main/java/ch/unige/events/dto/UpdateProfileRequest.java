package ch.unige.events.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

@Schema(name = "UpdateProfileRequest", description = "Partial self-profile update payload. All fields are optional.")
@JsonIgnoreProperties(ignoreUnknown = false)
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record UpdateProfileRequest(
    String displayName,
    String faculty,
    String studyLevel,
    String bio,
    String interests,
    String avatarUrl,
    Boolean isProfilePublic
) {}