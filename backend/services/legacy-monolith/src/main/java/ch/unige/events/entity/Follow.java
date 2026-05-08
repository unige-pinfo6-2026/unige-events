package ch.unige.events.entity;

import io.quarkus.hibernate.orm.panache.PanacheEntity;
import jakarta.persistence.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

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
public class Follow extends PanacheEntity {

    @Column(name = "follower_id", nullable = false)
    public UUID followerId;

    @Column(name = "followed_id", nullable = false)
    public UUID followedId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    public FollowStatus status;

    @Column(name = "created_at", nullable = false, updatable = false)
    public LocalDateTime createdAt;

    @PrePersist
    public void prePersist() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }

    public static Optional<Follow> findByFollowerAndFollowed(UUID followerId, UUID followedId) {
        return find("followerId = ?1 and followedId = ?2", followerId, followedId).firstResultOptional();
    }

    public static List<Follow> findFollowersOf(UUID followedId, int page, int size) {
        return find("followedId = ?1 and status = ?2 order by createdAt desc, id desc",
                followedId, FollowStatus.ACCEPTED)
                .page(page, size)
                .list();
    }

    public static List<Follow> findFollowingOf(UUID followerId, int page, int size) {
        return find("followerId = ?1 and status = ?2 order by createdAt desc, id desc",
                followerId, FollowStatus.ACCEPTED)
                .page(page, size)
                .list();
    }

    public static List<Follow> findPendingRequestsFor(UUID followedId, int page, int size) {
        return find("followedId = ?1 and status = ?2 order by createdAt desc, id desc",
                followedId, FollowStatus.PENDING)
                .page(page, size)
                .list();
    }

    /**
     * SCRUM-168 (S9, anticipé ici par SCRUM-138) — projection JPQL directe des
     * `followedId` ACCEPTED d'un `follower`. Évite de matérialiser les entités Follow
     * pour alimenter le filtre {@code followedOnly} de {@code GET /api/events}.
     */
    public static List<UUID> findAcceptedFollowedIds(UUID followerId) {
        return getEntityManager()
                .createQuery(
                    "select f.followedId from Follow f " +
                    "where f.followerId = :follower and f.status = :status",
                    UUID.class)
                .setParameter("follower", followerId)
                .setParameter("status", FollowStatus.ACCEPTED)
                .getResultList();
    }

    public static long countFollowersOf(UUID followedId) {
        return count("followedId = ?1 and status = ?2", followedId, FollowStatus.ACCEPTED);
    }

    public static long countFollowingOf(UUID followerId) {
        return count("followerId = ?1 and status = ?2", followerId, FollowStatus.ACCEPTED);
    }
}
