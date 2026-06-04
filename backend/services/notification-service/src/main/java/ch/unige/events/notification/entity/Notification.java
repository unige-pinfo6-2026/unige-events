package ch.unige.events.notification.entity;

import io.quarkus.hibernate.orm.panache.PanacheEntity;
import io.quarkus.panache.common.Sort;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Owned by notification-service. SCRUM-99 phase 1 — backs the
 * {@code /api/users/me/notifications/*} endpoints.
 *
 * <p>No FK on {@code userId} / {@code relatedUserId} / {@code eventId}: the
 * service owns its dedicated Postgres (postgres-notification) and cannot
 * reference cross-service tables. UUID consistency is enforced at the
 * application layer (cf. SCRUM-99 Décision F).
 *
 * <p>Tri par défaut sur le listing (Décision J) : {@code read ASC,
 * createdAt DESC, id DESC} — unread first, then most recent. L'index
 * composite {@code idx_notification_user_read_created} sert le tri sans
 * scan séquentiel.
 */
@Entity
@Table(
    name = "notifications",
    indexes = {
        @Index(name = "idx_notification_user_read_created", columnList = "user_id, read, created_at DESC"),
        @Index(name = "idx_notification_user_created",      columnList = "user_id, created_at DESC")
    }
)
public class Notification extends PanacheEntity {

    @Column(name = "user_id", nullable = false)
    public UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    public NotificationType type;

    @Column(name = "event_id")
    public Long eventId;

    @Column(name = "related_user_id")
    public UUID relatedUserId;

    @Column(nullable = false, columnDefinition = "TEXT")
    public String message;

    @Column(nullable = false)
    public boolean read = false;

    @Column(name = "created_at", nullable = false, updatable = false)
    public LocalDateTime createdAt;

    @Column(name = "read_at")
    public LocalDateTime readAt;

    @PrePersist
    public void prePersist() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now(ZoneId.systemDefault());
        }
    }

    /**
     * Listing paginé avec tri unread-first (cf. Décision J). En SQL standard
     * boolean ordering, {@code false < true}, donc {@code ORDER BY read ASC}
     * met les non lues en tête.
     */
    public static List<Notification> findByUser(UUID userId, int page, int size) {
        return find("userId = ?1", Sort.by("read").ascending()
                        .and("createdAt", Sort.Direction.Descending)
                        .and("id", Sort.Direction.Descending),
                userId)
                .page(page, size)
                .list();
    }

    /**
     * Anti-oracle : retourne {@link Optional#empty()} quand la row n'existe
     * pas <em>ou</em> appartient à un autre user. Le caller traduit en 404.
     */
    public static Optional<Notification> findByIdAndUser(Long id, UUID userId) {
        return find("id = ?1 and userId = ?2", id, userId).firstResultOptional();
    }

    public static long countUnreadByUser(UUID userId) {
        return count("userId = ?1 and read = false", userId);
    }

    /**
     * Bulk update — passe en {@code read = true, read_at = now()} toutes les
     * notifs non lues du caller. Retourne le nombre de rows affectées (utile
     * pour {@code ReadAllResponse.updated}).
     *
     * <p>Implémenté en JPQL explicit (pas la forme courte Panache
     * {@code update("read = true, readAt = ?1 where ...")}) parce que le
     * parser Panache traite ambiguëment les SET multi-colonnes séparés par
     * virgule sans le mot-clé {@code SET}, ce qui se traduisait en CI par
     * un {@code read = true} appliqué mais {@code readAt} ignoré.
     */
    public static int markAllReadByUser(UUID userId) {
        return getEntityManager()
                .createQuery("UPDATE Notification n SET n.read = true, n.readAt = :readAt"
                        + " WHERE n.userId = :userId AND n.read = false")
                .setParameter("readAt", LocalDateTime.now(ZoneId.systemDefault()))
                .setParameter("userId", userId)
                .executeUpdate();
    }
}
