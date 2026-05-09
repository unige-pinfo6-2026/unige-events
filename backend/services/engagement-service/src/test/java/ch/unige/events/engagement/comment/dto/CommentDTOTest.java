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
    void fromTopLevelWithReplies_buildsNestedDTOs() {
        UUID topAuthorId = UUID.randomUUID();
        UUID replyAuthorId = UUID.randomUUID();

        Comment top = new Comment();
        top.id = 50L;
        top.eventId = 1L;
        top.authorId = topAuthorId;
        top.content = "top-level";
        top.likeCount = 2;
        top.createdAt = LocalDateTime.of(2026, 5, 1, 12, 0);

        Comment reply = new Comment();
        reply.id = 51L;
        reply.eventId = 1L;
        reply.authorId = replyAuthorId;
        reply.parentComment = top;
        reply.content = "reply-body";
        reply.likeCount = 0;
        reply.createdAt = LocalDateTime.of(2026, 5, 1, 12, 1);

        UserPublicResponse topAuthor = new UserPublicResponse(
                topAuthorId, "Alice", null, null, null, null,
                "https://avatars/alice.png", null, 0L, 0L, null);
        UserPublicResponse replyAuthor = new UserPublicResponse(
                replyAuthorId, "Bob", null, null, null, null,
                null, null, 0L, 0L, null);

        CommentDTO dto = CommentDTO.fromTopLevelWithReplies(
                top, topAuthor,
                List.of(reply),
                java.util.Map.of(replyAuthorId, replyAuthor),
                true,
                java.util.Map.of(replyAuthorId, false));

        assertEquals(50L, dto.id());
        assertEquals("top-level", dto.content());
        assertEquals("Alice", dto.authorDisplayName());
        assertTrue(dto.authorIsOrganizer());
        assertEquals(1, dto.replies().size());

        CommentDTO replyDto = dto.replies().get(0);
        assertEquals(51L, replyDto.id());
        assertEquals("Bob", replyDto.authorDisplayName());
        assertFalse(replyDto.authorIsOrganizer());
        assertEquals(50L, replyDto.parentCommentId());
    }

    @Test
    void fromTopLevelWithReplies_withoutAuthor_keepsNullDisplayName() {
        UUID topAuthorId = UUID.randomUUID();
        Comment top = new Comment();
        top.id = 60L;
        top.eventId = 1L;
        top.authorId = topAuthorId;
        top.content = "no-author-info";
        top.likeCount = 0;
        top.createdAt = LocalDateTime.of(2026, 5, 1, 12, 0);

        CommentDTO dto = CommentDTO.fromTopLevelWithReplies(
                top, null, List.of(), java.util.Map.of(), false, java.util.Map.of());

        assertNull(dto.authorDisplayName());
        assertNull(dto.authorAvatarUrl());
        assertTrue(dto.replies().isEmpty());
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
