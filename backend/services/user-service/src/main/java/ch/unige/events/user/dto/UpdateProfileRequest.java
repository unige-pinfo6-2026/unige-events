package ch.unige.events.user.dto;

import ch.unige.events.shared.domain.enums.Faculty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

import java.util.List;

@Schema(name = "UpdateProfileRequest", description = "Partial self-profile update payload. All fields are optional.")
public record UpdateProfileRequest(
    @Size(max = 120) String displayName,
    Faculty faculty,
    @Schema(description = "Free-text until StudyLevel enum lands (S9+). Expected: LICENCE | MASTER | PHD | OTHER")
    @Size(max = 120) String studyLevel,
    @Size(max = 2000) String bio,
    List<String> interests,
    @Size(max = 2048)
    @Pattern(regexp = "^(https?://).+$", message = "must be a valid http(s) URL")
    String avatarUrl,
    Boolean profilePublic
) {
}
