package ch.unige.events.dto.user;

import ch.unige.events.entity.User;
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
                user.id,
                user.auth0Id,
                user.email,
                user.displayName,
                user.faculty,
                user.studyLevel,
                user.bio,
                user.interests,
                user.avatarUrl,
                user.profilePublic,
                user.createdAt
        );
    }
}
