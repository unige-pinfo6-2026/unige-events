package ch.unige.events.comment.entity;

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

/**
 * Owned by comment-service. Carbon-copy of legacy
 * ch.unige.events.entity.Comment — same FKs, same indexes, same
 * column-name conventions. The {@code event} / {@code author} associations
 * point at the local read-only stubs in this module so Hibernate can
 * navigate them lazily in tests ; the underlying foreign keys still
 * reference {@code events(id)} / {@code users(id)} of the shared schema.
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

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "event_id", nullable = false)
    public EventStub event;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "author_id", nullable = false)
    public UserStub author;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_comment_id")
    public Comment parentComment;

    @Column(columnDefinition = "TEXT", nullable = false)
    @NotBlank
    @Size(max = 2000)
    public String content;

    @Column(name = "like_count", nullable = false)
    public int likeCount = 0;

    @Column(name = "created_at", nullable = false, updatable = false)
    public LocalDateTime createdAt;

    @PrePersist
    public void prePersist() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }
}
