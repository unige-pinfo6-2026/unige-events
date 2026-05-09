package ch.unige.events.calendar.entity;

import io.quarkus.hibernate.orm.panache.PanacheEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.util.List;
import java.util.UUID;

/**
 * Read-only stub of the Favorite entity. calendar-service joins the
 * user's favorited event IDs with their attending event IDs, then bulk-
 * fetches the matching events for the ICS feed. Full Favorite entity
 * lives in favorite-service (PR 3, already extracted) — at that point
 * this stub is replaced by a REST client to favorite-service.
 */
@Entity
@Table(name = "favorites")
public class FavoriteStub extends PanacheEntity {

    @Column(name = "user_id", nullable = false)
    public UUID userId;

    @Column(name = "event_id", nullable = false)
    public Long eventId;

    public static List<FavoriteStub> findAllByUser(UUID userId) {
        return list("userId = ?1", userId);
    }
}
