package ch.unige.events.engagement.comment.dto;

import ch.unige.events.engagement.comment.entity.Comment;
import ch.unige.events.shared.domain.dto.UserPublicResponse;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CommentDTOTest {

    @Test
    void canonicalConstructor_keepsAllFields() {
        UUID authorId = UUID.randomUUID();
        LocalDateTime createdAt = LocalDateTime.of(2026, 5, 1, 12, 0);
        CommentDTO dto = new CommentDTO(
                7L, "hello world", authorId, "Alice",
                "https://avatars/alice.png", true, 3, false,
                createdAt, null, List.of());

        assertEquals(7L, dto.id());
        assertEquals("hello world", dto.content());
        assertEquals(authorId, dto.authorId());
        assertEquals("Alice", dto.authorDisplayName());
        assertEquals("https://avatars/alice.png", dto.authorAvatarUrl());
        assertTrue(dto.authorIsOrganizer());
        assertEquals(3, dto.likeCount());
        assertFalse(dto.likedByMe());
        assertEquals(createdAt, dto.createdAt());
        assertNull(dto.parentCommentId());
        assertTrue(dto.replies().isEmpty());
    }

    @Test
    void from_commentWithAuthor_populatesDisplayNameAndAvatar() {
        UUID authorId = UUID.randomUUID();
        LocalDateTime createdAt = LocalDateTime.of(2026, 5, 1, 12, 0);

        Comment c = new Comment();
        c.id = 11L;
        c.eventId = 42L;
        c.authorId = authorId;
        c.content = "great event";
        c.likeCount = 5;
        c.createdAt = createdAt;

        UserPublicResponse author = new UserPublicResponse(
                authorId, "Bob", null, null, null, null,
                "https://avatars/bob.png", null, 0L, 0L, null);

        CommentDTO dto = CommentDTO.from(c, author, false);

        assertEquals(11L, dto.id());
        assertEquals("great event", dto.content());
        assertEquals(authorId, dto.authorId());
        assertEquals("Bob", dto.authorDisplayName());
        assertEquals("https://avatars/bob.png", dto.authorAvatarUrl());
        assertFalse(dto.authorIsOrganizer());
        assertEquals(5, dto.likeCount());
        assertFalse(dto.likedByMe());
        assertEquals(createdAt, dto.createdAt());
        assertNull(dto.parentCommentId());
        assertTrue(dto.replies().isEmpty());
    }

    @Test
    void from_commentWithNullAuthor_keepsDisplayAndAvatarNull() {
        Comment c = new Comment();
        c.id = 1L;
        c.eventId = 99L;
        c.authorId = UUID.randomUUID();
        c.content = "anonymous";
        c.likeCount = 0;
        c.createdAt = LocalDateTime.now();

        CommentDTO dto = CommentDTO.from(c, null, false);

        assertNull(dto.authorDisplayName());
        assertNull(dto.authorAvatarUrl());
        assertEquals(1L, dto.id());
    }

    @Test
    void recordEqualsAndHashCode_canonicalContract() {
        UUID id = UUID.randomUUID();
        LocalDateTime t = LocalDateTime.of(2026, 5, 1, 12, 0);
        CommentDTO a = new CommentDTO(1L, "x", id, "X", null, false, 0, false, t, null, List.of());
        CommentDTO b = new CommentDTO(1L, "x", id, "X", null, false, 0, false, t, null, List.of());
        CommentDTO c = new CommentDTO(2L, "x", id, "X", null, false, 0, false, t, null, List.of());

        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
        assertNotEquals(a, c);
    }
}
