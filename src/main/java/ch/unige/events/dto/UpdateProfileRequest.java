package ch.unige.events.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

@Schema(name = "UpdateProfileRequest", description = "Partial self-profile update payload. All fields are optional.")
@JsonIgnoreProperties(ignoreUnknown = false)
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record UpdateProfileRequest(
    @Size(max = 120)
    String displayName,
    @Size(max = 120)
    String faculty,
    @Size(max = 120)
    String studyLevel,
    @Size(max = 2000)
    String bio,
    @Size(max = 2000)
    String interests,
    @Size(max = 2048)
    @Pattern(regexp = "^(https?://).+$", message = "must be a valid http(s) URL")
    String avatarUrl,
    Boolean profilePublic
) {}