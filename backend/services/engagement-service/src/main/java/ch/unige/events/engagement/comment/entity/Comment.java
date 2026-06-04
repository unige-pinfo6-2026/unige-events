package ch.unige.events.engagement.comment.entity;

import io.quarkus.hibernate.orm.panache.PanacheEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.UUID;

/**
 * Owned by engagement-service. Carbon-copy of legacy
 * ch.unige.events.entity.Comment — same FKs, same indexes, same
 * column-name conventions.
 *
 * <p>Décision F finalization-ultimate (STUB-001): the cross-service
 * navigations to {@code event} / {@code author} that
 * existed in the original Comment entity have been replaced by id-only
 * columns ({@code eventId Long}, {@code authorId UUID}). The underlying
 * foreign keys still reference {@code events(id)} / {@code users(id)}
 * of the shared schema — only the JPA navigation goes away. Enrichment
 * (display name, avatar, organizer flag) is performed at the service
 * layer via REST clients to event-service / user-service.
 */
@Entity
@Table(
        name = "comments",
        indexes = {
                @Index(name = "idx_comment_event",         columnList = "event_id"),
                @Index(name = "idx_comment_parent",        columnList = "parent_comment_id"),
                @Index(name = "idx_comment_event_created", columnList = "event_id, created_at DESC")
        }
)
public class Comment extends PanacheEntity {

    @Column(name = "event_id", nullable = false)
    public Long eventId;

    @Column(name = "author_id", nullable = false)
    public UUID authorId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_comment_id")
    public Comment parentComment;

    @Column(columnDefinition = "TEXT", nullable = false)
    @NotBlank
    @Size(max = 500)
    public String content;

    @Column(name = "like_count", nullable = false)
    public int likeCount = 0;

    @Column(name = "created_at", nullable = false, updatable = false)
    public LocalDateTime createdAt;

    @PrePersist
    public void prePersist() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now(ZoneId.systemDefault());
        }
    }
}
