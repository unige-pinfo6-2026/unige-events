package ch.unige.events.user.follow.dto;

import ch.unige.events.user.follow.entity.Follow;
import ch.unige.events.user.entity.FollowStatus;

import java.time.LocalDateTime;
import java.util.UUID;

public record FollowDTO(
    Long id,
    UUID followerId,
    UUID followedId,
    FollowStatus status,
    LocalDateTime createdAt
) {
    public static FollowDTO from(Follow f) {
        return new FollowDTO(f.id, f.followerId, f.followedId, f.status, f.createdAt);
    }
}
