package ch.unige.events.engagement.comment.entity;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Lifecycle coverage for {@link CommentLike}. The entity has one JPA hook
 * ({@code @PrePersist}) that lazily stamps {@code createdAt}.
 *
 * <p>{@code @QuarkusTest} so quarkus-jacoco counts the hook coverage (plain
 * JUnit runs aren't captured). The assertions are pure in-memory method calls
 * — {@code prePersist()} is a direct call, no transaction needed.
 */
@QuarkusTest
class CommentLikeEntityTest {

    @Test
    void prePersist_setsCreatedAt_whenNull() {
        CommentLike like = new CommentLike();
        like.commentId = 1L;
        like.userId = java.util.UUID.randomUUID();
        like.prePersist();

        assertNotNull(like.createdAt);
    }

    @Test
    void prePersist_keepsCreatedAt_whenAlreadySet() {
        LocalDateTime fixed = LocalDateTime.of(2020, 1, 1, 0, 0);
        CommentLike like = new CommentLike();
        like.commentId = 1L;
        like.userId = java.util.UUID.randomUUID();
        like.createdAt = fixed;
        like.prePersist();

        assertEquals(fixed, like.createdAt);
    }
}
