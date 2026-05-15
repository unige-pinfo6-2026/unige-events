package ch.unige.events.user.dto;

import ch.unige.events.shared.domain.enums.FollowStatus;
import ch.unige.events.user.entity.User;

/**
 * Service-layer projection wrapping a {@link User} plus the enrichment
 * counters and the caller-relative {@code followStatus}.
 *
 * <p>SCRUM-169 revision — {@code restricted} signals that the caller is a
 * non-self non-admin who is not allowed to read the private fields of a
 * {@code profilePublic=false} target. The resource layer surfaces this by
 * serialising the response through
 * {@link UserPublicResponse#fromAnonymous(User)} (id + username +
 * displayName + avatarUrl ; the rest is stripped) — instead of the strict
 * 404 the original anti-oracle used to throw. Owner self-view and admin
 * bypass still produce {@code restricted=false} + the full projection.
 */
public record PublicProfileView(
    User user,
    long followerCount,
    long followingCount,
    FollowStatus followStatus,
    boolean restricted
) {
    /**
     * Backward-compatible 4-arg constructor — preserves the canonical
     * signature used by existing callers and test code that construct
     * a {@code PublicProfileView} directly. Delegates to the 5-arg
     * canonical with {@code restricted=false}.
     */
    public PublicProfileView(User user, long followerCount, long followingCount, FollowStatus followStatus) {
        this(user, followerCount, followingCount, followStatus, false);
    }

    public static PublicProfileView anonymous(User user) {
        return new PublicProfileView(user, 0L, 0L, null, false);
    }

    /**
     * SCRUM-169 revision — projection returned to a non-self non-admin
     * caller of a private profile. The resource layer must serialise the
     * payload through {@link UserPublicResponse#fromAnonymous(User)} so
     * only the public-facing identifier (id, username, displayName,
     * avatarUrl) is exposed.
     */
    public static PublicProfileView restricted(User user) {
        return new PublicProfileView(user, 0L, 0L, null, true);
    }
}
