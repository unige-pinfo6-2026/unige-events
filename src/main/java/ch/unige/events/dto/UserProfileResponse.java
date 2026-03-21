package ch.unige.events.dto;

import ch.unige.events.entity.User;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Schema(name = "UserProfileResponse", description = "Full authenticated user profile.")
public record UserProfileResponse(
    @Schema(readOnly = true) UUID id,
    @Schema(readOnly = true, description = "Auth0 user ID from JWT sub") String auth0Id,
    @Schema(readOnly = true) String email,
    String displayName,
    String faculty,
    String studyLevel,
    String bio,
    List<String> interests,
    String avatarUrl,
    boolean profilePublic,
    @Schema(readOnly = true) LocalDateTime createdAt
) {
    public static UserProfileResponse from(User user) {
        return new UserProfileResponse(
            user.getId(),
            user.getAuth0Id(),
            user.getEmail(),
            user.getDisplayName(),
            user.getFaculty(),
            user.getStudyLevel(),
            user.getBio(),
            user.getInterests(),
            user.getAvatarUrl(),
            user.isProfilePublic(),
            user.getCreatedAt()
        );
    }
}
