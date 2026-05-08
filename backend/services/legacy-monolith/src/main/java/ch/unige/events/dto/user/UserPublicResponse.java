package ch.unige.events.dto.user;

import ch.unige.events.entity.FollowStatus;
import ch.unige.events.entity.User;

import java.util.List;
import java.util.UUID;

public record UserPublicResponse(
    UUID id,
    String displayName,
    String faculty,
    String studyLevel,
    String bio,
    List<String> interests,
    String avatarUrl,
    String bannerUrl,
    long followerCount,
    long followingCount,
    FollowStatus followStatus
) {

    /**
     * Factory legacy (compteurs = 0, followStatus = null).
     * Utilisée pour les items des listes followers / following (cf. SCRUM-138 décision 11)
     * et par les tests qui n'ont pas besoin du contexte follow.
     */
    public static UserPublicResponse from(User user) {
        return new UserPublicResponse(
                user.id,
                user.displayName,
                user.faculty,
                user.studyLevel,
                user.bio,
                user.interests,
                user.avatarUrl,
                user.bannerUrl,
                0L,
                0L,
                null
        );
    }

    /**
     * Factory enrichie utilisée par {@link ch.unige.events.service.UserService#getPublicProfile}
     * pour les appelants authentifiés. Compteurs et followStatus calculés par le Service.
     */
    public static UserPublicResponse from(
            User user,
            long followerCount,
            long followingCount,
            FollowStatus followStatus) {
        return new UserPublicResponse(
                user.id,
                user.displayName,
                user.faculty,
                user.studyLevel,
                user.bio,
                user.interests,
                user.avatarUrl,
                user.bannerUrl,
                followerCount,
                followingCount,
                followStatus
        );
    }

    /**
     * Factory pour les appelants anonymes — projet uniquement id, displayName et avatarUrl.
     * Compteurs à 0, followStatus null. Hotfix pentest 2026-04-17 finding 4.1b
     * (limit anonymous harvest of opt-in public profiles).
     */
    public static UserPublicResponse fromAnonymous(User user) {
        return new UserPublicResponse(
                user.id,
                user.displayName,
                null,
                null,
                null,
                null,
                user.avatarUrl,
                null,
                0L,
                0L,
                null
        );
    }
}
