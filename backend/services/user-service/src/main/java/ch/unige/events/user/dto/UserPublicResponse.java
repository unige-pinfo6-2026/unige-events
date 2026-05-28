package ch.unige.events.user.dto;

import ch.unige.events.shared.domain.enums.Faculty;
import ch.unige.events.shared.domain.enums.FollowStatus;
import ch.unige.events.user.entity.User;

import java.util.List;
import java.util.UUID;

public record UserPublicResponse(
    UUID id,
    String username,
    String displayName,
    Faculty faculty,
    String studyLevel,
    String bio,
    List<String> interests,
    String avatarUrl,
    String bannerUrl,
    boolean profilePublic,
    long followerCount,
    long followingCount,
    FollowStatus followStatus,
    /**
     * Auth0 roles owned by the profile (e.g. {@code ["ADMIN"]}). Exposed even
     * on the anonymous projection — role info is not sensitive (it merely
     * tells consumers "this account is staff") and the frontend renders the
     * "Staff" badge on every profile, regardless of the viewer's auth state.
     * Empty list (never {@code null}) when the user has no role mirrored yet.
     */
    List<String> roles
) {

    public static UserPublicResponse from(User user) {
        return new UserPublicResponse(
                user.id,
                user.username,
                user.displayName,
                user.faculty,
                user.studyLevel,
                user.bio,
                user.interests,
                user.avatarUrl,
                user.bannerUrl,
                user.profilePublic,
                0L,
                0L,
                null,
                rolesOrEmpty(user)
        );
    }

    public static UserPublicResponse from(
            User user,
            long followerCount,
            long followingCount,
            FollowStatus followStatus) {
        return new UserPublicResponse(
                user.id,
                user.username,
                user.displayName,
                user.faculty,
                user.studyLevel,
                user.bio,
                user.interests,
                user.avatarUrl,
                user.bannerUrl,
                user.profilePublic,
                followerCount,
                followingCount,
                followStatus,
                rolesOrEmpty(user)
        );
    }

    private static List<String> rolesOrEmpty(User user) {
        // `user.roles` is initialised to an empty list on the entity (cf.
        // `User.roles` javadoc) and Hibernate hydrates rows with no
        // matching `user_roles` entries as empty collections too — so we
        // don't carry a null guard here. `List.copyOf` defensively
        // re-wraps so the DTO can never alias the entity's mutable bag.
        return List.copyOf(user.roles);
    }

    /**
     * Anonymous projection — keeps id, username, displayName, avatarUrl,
     * nullifies the rest (cf. SCRUM-169 Décision E : {@code username} is
     * exposed even to anonymous callers since it is the public-facing
     * identifier of the profile).
     *
     * <p>{@code profilePublic} mirrors the user's actual visibility setting,
     * independent of whether the projection is stripped : the stripping is a
     * read-permission concern, the flag tells the caller whether the target
     * profile is itself public. The frontend uses the flag to gate the
     * "private profile" placeholder — anonymous viewers of a public profile
     * see the stripped payload but the flag stays {@code true}.
     */
    public static UserPublicResponse fromAnonymous(User user) {
        return new UserPublicResponse(
                user.id,
                user.username,
                user.displayName,
                null,
                null,
                null,
                null,
                user.avatarUrl,
                null,
                user.profilePublic,
                0L,
                0L,
                null,
                rolesOrEmpty(user)
        );
    }
}
