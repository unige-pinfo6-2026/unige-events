package ch.unige.events.dto.user;

import ch.unige.events.entity.FollowStatus;
import ch.unige.events.entity.User;

/**
 * Vue agrégée retournée par {@link ch.unige.events.service.UserService#getPublicProfile(java.util.UUID, String)}.
 * Sert de pont entre Service et Resource sans exposer FollowService dans la Resource.
 *
 * <p>Les compteurs et le {@link FollowStatus} sont calculés par le Service ; la Resource
 * compose ensuite l'envelope DTO via les factories de {@link UserPublicResponse}.
 *
 * <p>Pour un appelant anonyme, le Service court-circuite avant d'invoquer FollowService :
 * compteurs à 0, {@code followStatus} à {@code null}.
 */
public record PublicProfileView(
    User user,
    long followerCount,
    long followingCount,
    FollowStatus followStatus
) {
    /** Helper pour les anonymes — compteurs à 0, followStatus null. */
    public static PublicProfileView anonymous(User user) {
        return new PublicProfileView(user, 0L, 0L, null);
    }
}
