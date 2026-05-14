package ch.unige.events.user.dto;

import ch.unige.events.shared.domain.enums.Faculty;
import ch.unige.events.user.entity.User;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Schema(name = "UserProfileResponse", description = "Full authenticated user profile.")
public record UserProfileResponse(
    @Schema(readOnly = true) UUID id,
    @Schema(readOnly = true, description = "Auth0 user ID from JWT sub") String auth0Id,
    @Schema(readOnly = true) String email,
    @Schema(description = "Public-facing handle (SCRUM-169) — used in /profile/{username} URLs.")
    String username,
    String displayName,
    Faculty faculty,
    @Schema(description = "Free-text until StudyLevel enum lands (S9+). Expected: LICENCE | MASTER | PHD | OTHER")
    String studyLevel,
    String bio,
    List<String> interests,
    String avatarUrl,
    String bannerUrl,
    boolean profilePublic,
    @Schema(readOnly = true) LocalDateTime createdAt
) {
    public static UserProfileResponse from(User user) {
        return new UserProfileResponse(
                user.id,
                user.auth0Id,
                user.email,
                user.username,
                user.displayName,
                user.faculty,
                user.studyLevel,
                user.bio,
                user.interests,
                user.avatarUrl,
                user.bannerUrl,
                user.profilePublic,
                user.createdAt
        );
    }
}
