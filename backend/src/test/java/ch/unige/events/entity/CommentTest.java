package ch.unige.events.entity;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
class CommentTest {

    @Test
    void prePersist_setsCreatedAt() {
        Comment c = new Comment();
        assertNull(c.createdAt);

        c.prePersist();

        assertNotNull(c.createdAt);
    }

    @Test
    void prePersist_doesNotOverrideExistingCreatedAt() {
        Comment c = new Comment();
        LocalDateTime fixed = LocalDateTime.of(2026, 1, 1, 12, 0);
        c.createdAt = fixed;

        c.prePersist();

        assertEquals(fixed, c.createdAt);
    }

    @Test
    void defaultLikeCount_isZero() {
        Comment c = new Comment();
        assertEquals(0, c.likeCount);
    }

    @Test
    void fieldsAreAssignable() {
        Comment c = new Comment();
        Event event = new Event();
        User author = new User();
        Comment parent = new Comment();
        c.event = event;
        c.author = author;
        c.parentComment = parent;
        c.content = "Hello world";
        c.likeCount = 0;

        assertSame(event, c.event);
        assertSame(author, c.author);
        assertSame(parent, c.parentComment);
        assertEquals("Hello world", c.content);
    }
}
