package ch.unige.events.user.dto;

import ch.unige.events.user.entity.FollowStatus;
import ch.unige.events.user.entity.User;

public record PublicProfileView(
    User user,
    long followerCount,
    long followingCount,
    FollowStatus followStatus
) {
    public static PublicProfileView anonymous(User user) {
        return new PublicProfileView(user, 0L, 0L, null);
    }
}
