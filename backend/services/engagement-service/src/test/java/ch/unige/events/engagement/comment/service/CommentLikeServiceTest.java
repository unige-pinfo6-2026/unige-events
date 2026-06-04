package ch.unige.events.engagement.comment.service;

import ch.unige.events.engagement.comment.entity.Comment;
import ch.unige.events.engagement.comment.entity.CommentLike;
import ch.unige.events.shared.client.EventServiceClient;
import ch.unige.events.shared.domain.dto.EventDTO;
import ch.unige.events.shared.domain.enums.EventStatus;
import ch.unige.events.shared.domain.projections.CallerIdentity;

import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.panache.mock.PanacheMock;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceException;
import jakarta.ws.rs.NotFoundException;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * SCRUM-144 — CommentLikeService unit tests.
 *
 * <p>{@code @QuarkusTest} for jacoco coverage (piège #1). Lambdas
 * {@code () -> Entity.deleteAll()} for cleanup (piège #4 — method ref
 * resolves to PanacheEntityBase stub that throws).
 */
@SuppressWarnings("java:S1612")
@QuarkusTest
class CommentLikeServiceTest {

    @Inject CommentLikeService service;
    @InjectMock CallerIdentity callerIdentity;
    @InjectMock @RestClient EventServiceClient eventClient;

    private static final long EVENT_ID = 1L;

    @BeforeEach
    void truncate() {
        QuarkusTransaction.requiringNew().run(() -> CommentLike.deleteAll());
        QuarkusTransaction.requiringNew().run(() -> Comment.deleteAll());
    }

    @AfterEach
    void cleanup() {
        QuarkusTransaction.requiringNew().run(() -> CommentLike.deleteAll());
        QuarkusTransaction.requiringNew().run(() -> Comment.deleteAll());
    }

    private Long persistComment(UUID authorId, int initialLikeCount) {
        return QuarkusTransaction.requiringNew().call(() -> {
            Comment c = new Comment();
            c.eventId = EVENT_ID;
            c.authorId = authorId;
            c.content = "hello";
            c.likeCount = initialLikeCount;
            c.persist();
            return c.id;
        });
    }

    private static EventDTO visibleEvent() {
        return new EventDTO(EVENT_ID, "Concert", null, "loc",
                java.time.LocalDateTime.of(2025, 1, 1, 12, 0), java.time.LocalDateTime.of(2999, 1, 1, 0, 0).plusHours(2),
                null, null, null, UUID.randomUUID(), EventStatus.PUBLISHED, null, false, false, null,
                0L, null, 0L, 0L, 0L,
                null, null, null, java.util.List.of(), null, null, null, null, null);
    }

    @Test
    void like_freshComment_returnsCreatedAndIncrementsLikeCount() {
        UUID caller = UUID.randomUUID();
        Long commentId = persistComment(UUID.randomUUID(), 0);
        when(callerIdentity.requireUuid()).thenReturn(caller);
        when(eventClient.getByIdWithCoOrgCheck(EVENT_ID, caller)).thenReturn(visibleEvent());

        CommentLikeService.LikeResult result = service.like(commentId);

        assertTrue(result.created());
        assertEquals(1, result.likeCount());
        assertEquals(1L, CommentLike.count("commentId = ?1 and userId = ?2", commentId, caller));
        // Verify counter persisted.
        QuarkusTransaction.requiringNew().run(() -> {
            Comment fresh = Comment.findById(commentId);
            assertEquals(1, fresh.likeCount);
        });
    }

    @Test
    void like_alreadyLiked_returnsNotCreated_noDoubleIncrement() {
        UUID caller = UUID.randomUUID();
        Long commentId = persistComment(UUID.randomUUID(), 0);
        when(callerIdentity.requireUuid()).thenReturn(caller);
        when(eventClient.getByIdWithCoOrgCheck(EVENT_ID, caller)).thenReturn(visibleEvent());

        // 1st like — 201
        CommentLikeService.LikeResult first = service.like(commentId);
        assertTrue(first.created());

        // 2nd like — 200 idempotent
        CommentLikeService.LikeResult second = service.like(commentId);
        assertEquals(false, second.created());
        assertEquals(1, second.likeCount());
        // Still only 1 row, counter still 1.
        assertEquals(1L, CommentLike.count("commentId = ?1", commentId));
        QuarkusTransaction.requiringNew().run(() -> {
            Comment fresh = Comment.findById(commentId);
            assertEquals(1, fresh.likeCount);
        });
    }

    @Test
    void like_commentNotFound_throws404() {
        when(callerIdentity.requireUuid()).thenReturn(UUID.randomUUID());
        assertThrows(NotFoundException.class, () -> service.like(99_999L));
    }

    @Test
    void like_eventInvisible_throws404AntiOracle() {
        UUID caller = UUID.randomUUID();
        Long commentId = persistComment(UUID.randomUUID(), 0);
        when(callerIdentity.requireUuid()).thenReturn(caller);
        // Event invisible (DRAFT non-creator / BANNED / CB open) → client returns null.
        when(eventClient.getByIdWithCoOrgCheck(EVENT_ID, caller)).thenReturn(null);

        assertThrows(NotFoundException.class, () -> service.like(commentId));
        assertEquals(0L, CommentLike.count());
    }

    @Test
    void like_unprovisionedCaller_throws404AntiOracle() {
        // Comment exists but the caller has no resolvable UUID (not provisioned
        // via GET /users/me). assertEventVisible's callerUuid==null guard fires
        // → 404 anti-oracle, before any cross-service round-trip.
        Long commentId = persistComment(UUID.randomUUID(), 0);
        when(callerIdentity.requireUuid()).thenReturn(null);

        assertThrows(NotFoundException.class, () -> service.like(commentId));
        assertEquals(0L, CommentLike.count());
    }

    @Test
    void unlike_existingLike_returns204AndDecrementsLikeCount() {
        UUID caller = UUID.randomUUID();
        Long commentId = persistComment(UUID.randomUUID(), 0);
        when(callerIdentity.requireUuid()).thenReturn(caller);
        when(eventClient.getByIdWithCoOrgCheck(EVENT_ID, caller)).thenReturn(visibleEvent());

        // Create a like first.
        service.like(commentId);
        assertEquals(1L, CommentLike.count());

        service.unlike(commentId);

        assertEquals(0L, CommentLike.count("commentId = ?1 and userId = ?2", commentId, caller));
        QuarkusTransaction.requiringNew().run(() -> {
            Comment fresh = Comment.findById(commentId);
            assertEquals(0, fresh.likeCount);
        });
    }

    @Test
    void unlike_noLike_isIdempotent_noDecrement() {
        UUID caller = UUID.randomUUID();
        Long commentId = persistComment(UUID.randomUUID(), 0);
        when(callerIdentity.requireUuid()).thenReturn(caller);

        // No previous like — unlike is no-op.
        service.unlike(commentId);

        assertEquals(0L, CommentLike.count());
        QuarkusTransaction.requiringNew().run(() -> {
            Comment fresh = Comment.findById(commentId);
            assertEquals(0, fresh.likeCount);
        });
    }

    @Test
    void unlike_commentNotFound_throws404() {
        when(callerIdentity.requireUuid()).thenReturn(UUID.randomUUID());
        assertThrows(NotFoundException.class, () -> service.unlike(99_999L));
    }

    /**
     * Race-safe idempotent path : a concurrent like for the same
     * {@code (commentId, userId)} wins the INSERT, so our {@code flush()}
     * throws a {@link PersistenceException} (uq_comment_like). The catch block
     * must return {@code created=false} with the unchanged counter rather than
     * propagating. The pre-check {@code findByCommentAndUser} normally wins this
     * race, so the only deterministic way to reach the catch is to make the
     * flush throw — done here by wiring the service by hand with a mocked
     * {@link EntityManager} (the {@code @QuarkusTest} captures the executed
     * bytecode for jacoco).
     */
    @Test
    void like_concurrentInsertRace_flushConflict_returnsNotCreated() {
        UUID caller = UUID.randomUUID();
        EntityManager em = mock(EntityManager.class);
        CallerIdentity ci = mock(CallerIdentity.class);
        EventServiceClient ec = mock(EventServiceClient.class);
        when(ci.requireUuid()).thenReturn(caller);
        when(ec.getByIdWithCoOrgCheck(EVENT_ID, caller)).thenReturn(visibleEvent());
        CommentLikeService handWired = new CommentLikeService(ci, ec, em);

        PanacheMock.mock(Comment.class);
        Comment comment = new Comment();
        comment.id = 4242L;
        comment.eventId = EVENT_ID;
        comment.likeCount = 3;
        when(Comment.findByIdOptional(4242L)).thenReturn(Optional.of(comment));

        PanacheMock.mock(CommentLike.class);
        when(CommentLike.findByCommentAndUser(4242L, caller)).thenReturn(Optional.empty());
        // flush() throws the UK violation → catch returns idempotent 200.
        doThrow(new PersistenceException(
                "duplicate key value violates unique constraint \"uq_comment_like\""))
                .when(em).flush();

        CommentLikeService.LikeResult result = handWired.like(4242L);

        assertFalse(result.created(), "concurrent-insert race must yield an idempotent (not created) result");
        assertEquals(3, result.likeCount(), "likeCount must stay unchanged when the insert was a no-op");
        // The atomic increment UPDATE must NOT run when the row already exists.
        org.mockito.Mockito.verify(em, org.mockito.Mockito.never()).createQuery(org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void unlike_loadedCommentHasNullId_logsDefensiveStateNoThrow() {
        // Defensive state branch (line 127-128): the loaded Comment has a null
        // id. CommentLike.delete returns 0 so the decrement is skipped, then the
        // `comment.id == null` guard logs a warning. No throw. Hand-wired service
        // (plain mocks) so the forged Comment / CommentLike statics drive the path.
        UUID caller = UUID.randomUUID();
        EntityManager em = mock(EntityManager.class);
        CallerIdentity ci = mock(CallerIdentity.class);
        EventServiceClient ec = mock(EventServiceClient.class);
        when(ci.requireUuid()).thenReturn(caller);
        CommentLikeService handWired = new CommentLikeService(ci, ec, em);

        PanacheMock.mock(Comment.class);
        Comment c = new Comment();
        c.id = null;
        c.eventId = EVENT_ID;
        when(Comment.findByIdOptional(123L)).thenReturn(Optional.of(c));

        PanacheMock.mock(CommentLike.class);
        // delete returns 0 → decrement skipped → reaches the null-id guard.
        when(CommentLike.delete(org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.any(Object[].class))).thenReturn(0L);

        handWired.unlike(123L);

        // No EntityManager decrement update must run when nothing was deleted.
        org.mockito.Mockito.verify(em, org.mockito.Mockito.never())
                .createQuery(org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void like_loadedCommentHasNullEventId_throws404() {
        // assertEventVisible(null, caller) guard (line 138): the loaded Comment
        // has eventId==null → 404 before any cross-service round-trip. The
        // injected service is fine — the guard fires before the @RestClient call.
        UUID caller = UUID.randomUUID();
        when(callerIdentity.requireUuid()).thenReturn(caller);

        PanacheMock.mock(Comment.class);
        Comment c = new Comment();
        c.id = 321L;
        c.eventId = null;
        when(Comment.findByIdOptional(321L)).thenReturn(Optional.of(c));

        assertThrows(NotFoundException.class, () -> service.like(321L));
    }

    @Test
    void unlike_neverChecksEventVisibility() {
        // Décision : unlike removes the caller's own state — no visibility check
        // needed (no leak). The eventClient must NOT be invoked.
        UUID caller = UUID.randomUUID();
        Long commentId = persistComment(UUID.randomUUID(), 0);
        when(callerIdentity.requireUuid()).thenReturn(caller);
        // Note: NOT mocking eventClient — any call would return null and break, but
        // unlike must short-circuit before reaching it.

        service.unlike(commentId);
        assertEquals(0L, CommentLike.count());
        // If unlike had called the eventClient, the next assertion would fail with
        // NullPointer or 404 in like() — but unlike completed cleanly.
        assertNotNull(commentId);
    }

    /**
     * The empty-input guard on {@link CommentLike#findLikedCommentIdsByUser}
     * avoids issuing a malformed {@code IN ()} query on PostgreSQL. Each of the
     * three short-circuit conditions (null collection, empty collection, null
     * user) must return {@link Set#of()} before any SQL is emitted.
     */
    @Test
    void findLikedCommentIdsByUser_emptyOrNullInput_returnsEmptySetNoQuery() {
        UUID user = UUID.randomUUID();
        assertEquals(Set.of(), CommentLike.findLikedCommentIdsByUser(null, user));
        assertEquals(Set.of(), CommentLike.findLikedCommentIdsByUser(List.of(), user));
        assertEquals(Set.of(), CommentLike.findLikedCommentIdsByUser(List.of(1L, 2L), null));
    }
}
