package ch.unige.events.user.entity;

import io.quarkus.hibernate.orm.panache.PanacheEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.util.Optional;
import java.util.UUID;

/**
 * Read-only stub of the Follow entity. user-service uses
 * {@link #countFollowersOf}, {@link #countFollowingOf} and
 * {@link #findByFollowerAndFollowed} to enrich the public profile view
 * with follower / following counts and the caller's relationship to the
 * target. Replaced by REST client to follow-service (extracted PR 5)
 * in a follow-up cleanup.
 */
@Entity
@Table(
    name = "follows",
    uniqueConstraints = @UniqueConstraint(
        name = "uq_follow_follower_followed",
        columnNames = {"follower_id", "followed_id"}
    ),
    indexes = {
        @Index(name = "idx_follow_followed", columnList = "followed_id"),
        @Index(name = "idx_follow_follower", columnList = "follower_id")
    }
)
public class FollowStub extends PanacheEntity {

    @Column(name = "follower_id", nullable = false)
    public UUID followerId;

    @Column(name = "followed_id", nullable = false)
    public UUID followedId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    public FollowStatus status;

    public static long countFollowersOf(UUID followedId) {
        return count("followedId = ?1 and status = ?2", followedId, FollowStatus.ACCEPTED);
    }

    public static long countFollowingOf(UUID followerId) {
        return count("followerId = ?1 and status = ?2", followerId, FollowStatus.ACCEPTED);
    }

    public static Optional<FollowStub> findByFollowerAndFollowed(UUID followerId, UUID followedId) {
        return find("followerId = ?1 and followedId = ?2", followerId, followedId).firstResultOptional();
    }
}
