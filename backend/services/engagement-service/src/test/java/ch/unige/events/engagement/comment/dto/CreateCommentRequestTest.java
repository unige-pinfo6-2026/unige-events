package ch.unige.events.engagement.comment.dto;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class CreateCommentRequestTest {

    @Test
    void canonicalConstructor_topLevelComment_keepsAllFields() {
        CreateCommentRequest req = new CreateCommentRequest("hello world", null);

        assertEquals("hello world", req.content());
        assertNull(req.parentCommentId());
    }

    @Test
    void canonicalConstructor_replyComment_keepsParentId() {
        CreateCommentRequest req = new CreateCommentRequest("agreed", 42L);

        assertEquals("agreed", req.content());
        assertEquals(42L, req.parentCommentId());
    }

    @Test
    void recordEqualsAndHashCode_canonicalContract() {
        CreateCommentRequest a = new CreateCommentRequest("hi", 1L);
        CreateCommentRequest b = new CreateCommentRequest("hi", 1L);
        CreateCommentRequest c = new CreateCommentRequest("hi", 2L);

        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
        assertNotEquals(a, c);
    }
}
