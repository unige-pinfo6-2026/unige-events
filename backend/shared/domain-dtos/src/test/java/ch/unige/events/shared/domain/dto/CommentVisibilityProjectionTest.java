package ch.unige.events.shared.domain.dto;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CommentVisibilityProjectionTest {

    @Test
    void recordExposesAllAccessors() {
        UUID authorId = UUID.randomUUID();
        CommentVisibilityProjection p = new CommentVisibilityProjection(7L, 42L, authorId, true);
        assertEquals(7L, p.commentId());
        assertEquals(42L, p.eventId());
        assertEquals(authorId, p.authorId());
        assertTrue(p.callerHasAccess());
    }
}
