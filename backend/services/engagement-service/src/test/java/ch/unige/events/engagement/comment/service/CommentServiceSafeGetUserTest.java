package ch.unige.events.engagement.comment.service;

import ch.unige.events.engagement.comment.dto.CommentDTO;
import ch.unige.events.engagement.comment.dto.CreateCommentRequest;
import ch.unige.events.engagement.comment.entity.Comment;
import ch.unige.events.shared.client.EventServiceClient;
import ch.unige.events.shared.client.UserServiceClient;
import ch.unige.events.shared.domain.dto.EventDTO;
import ch.unige.events.shared.domain.enums.EventStatus;
import ch.unige.events.shared.domain.projections.CallerIdentity;
import ch.unige.events.shared.kafka.events.CommentCreatedEvent;
import ch.unige.events.shared.kafka.events.CommentMentionEvent;

import io.quarkus.hibernate.orm.panache.PanacheQuery;
import io.quarkus.panache.mock.PanacheMock;
import io.quarkus.security.identity.SecurityIdentity;
import io.quarkus.test.TestTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.enterprise.event.Event;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Coverage suite for the {@code @RestClient}-driven {@code catch} branches of
 * {@link CommentService} unreachable through {@code @InjectMock @RestClient}
 * (the Fault Tolerance {@code @Fallback} swallows the throw before the service
 * sees it). The service is hand-wired with PLAIN Mockito mocks so the throw is
 * real; the class stays {@code @QuarkusTest} so quarkus-jacoco captures the
 * executed bytecode. Panache statics are stubbed via {@link PanacheMock}.
 *
 * <p>Model: user-service {@code UserServiceExceptionPathsTest}.
 */
@QuarkusTest
@SuppressWarnings({"unchecked", "rawtypes"})
class CommentServiceSafeGetUserTest {

    private CommentService newService() {
        CommentService svc = new CommentService();
        svc.userClient = mock(UserServiceClient.class);
        svc.eventClient = mock(EventServiceClient.class);
        svc.callerIdentity = mock(CallerIdentity.class);
        svc.identity = mock(SecurityIdentity.class);
        svc.mentionParser = new MentionParser();
        svc.commentCreatedEvent = mock(Event.class);
        svc.commentMentionEvent = mock(Event.class);
        return svc;
    }

    private static EventDTO publishedEvent(UUID creator) {
        return new EventDTO(7L, "Concert", null, "loc",
                LocalDateTime.of(2025, 1, 1, 12, 0), LocalDateTime.of(2999, 1, 1, 0, 0).plusHours(2),
                null, null, null, creator, EventStatus.PUBLISHED, null,
                false, false, null,
                0L, null, 0L, 0L, 0L,
                null, null, null, List.of(),
                LocalDateTime.of(2025, 1, 1, 12, 0), LocalDateTime.of(2025, 1, 1, 12, 0),
                null, null, Boolean.FALSE);
    }

    private static Comment topLevel(long id, UUID author) {
        Comment c = new Comment();
        c.id = id;
        c.eventId = 7L;
        c.authorId = author;
        c.content = "hello";
        c.likeCount = 0;
        c.createdAt = LocalDateTime.of(2025, 1, 1, 12, 0);
        return c;
    }

    @SuppressWarnings("rawtypes")
    private static PanacheQuery pagedQuery(List<Comment> rows) {
        PanacheQuery q = mock(PanacheQuery.class);
        when(q.page(org.mockito.ArgumentMatchers.anyInt(),
                org.mockito.ArgumentMatchers.anyInt())).thenReturn(q);
        when(q.list()).thenReturn(rows);
        return q;
    }

    // ── safeGetUser catches via getByEvent listing (lines 383-395) ─────────

    @Test
    void getByEvent_userServiceRuntimeFailure_authorEnrichedAsNull() {
        CommentService svc = newService();
        UUID author = UUID.randomUUID();
        UUID creator = UUID.randomUUID();
        when(svc.identity.hasRole("ADMIN")).thenReturn(false);
        // Anonymous-ish caller: auth0Id == null so resolveLikedIds short-circuits
        // and assertEventVisibleAndLoad uses getById (no callerIdentity hop).
        when(svc.eventClient.getById(7L)).thenReturn(publishedEvent(creator));
        when(svc.eventClient.getOrganizerUuids(7L)).thenReturn(List.of(creator));
        // Real throw → service safeGetUser RuntimeException catch (lines 391-395).
        when(svc.userClient.getById(author))
                .thenThrow(new RuntimeException("CB open"));

        PanacheMock.mock(Comment.class);
        // Build the paged-query mock BEFORE the outer when(...) — calling
        // pagedQuery() (which itself stubs q.page/q.list) inside .thenReturn(...)
        // triggers Mockito UnfinishedStubbing.
        PanacheQuery commentPage = pagedQuery(List.of(topLevel(1L, author)));
        when(Comment.<Comment>find(anyString(), any(Object[].class)))
                .thenReturn(commentPage);
        when(Comment.<Comment>list(anyString(), any(Object[].class)))
                .thenReturn(List.of());

        List<CommentDTO> dtos = svc.getByEvent(7L, null, 0, 20);

        assertEquals(1, dtos.size());
        // Author enrichment failed → no displayName resolved (null-author DTO).
        assertNull(dtos.get(0).authorDisplayName());
    }

    @Test
    void getByEvent_userServiceNotFound_authorEnrichedAsNull() {
        CommentService svc = newService();
        UUID author = UUID.randomUUID();
        UUID creator = UUID.randomUUID();
        when(svc.identity.hasRole("ADMIN")).thenReturn(false);
        when(svc.eventClient.getById(7L)).thenReturn(publishedEvent(creator));
        when(svc.eventClient.getOrganizerUuids(7L)).thenReturn(List.of(creator));
        // Real throw → service safeGetUser NotFoundException catch (lines 387-390).
        when(svc.userClient.getById(author))
                .thenThrow(new jakarta.ws.rs.NotFoundException("hard-deleted"));

        PanacheMock.mock(Comment.class);
        // Build the paged-query mock BEFORE the outer when(...) — calling
        // pagedQuery() (which itself stubs q.page/q.list) inside .thenReturn(...)
        // triggers Mockito UnfinishedStubbing.
        PanacheQuery commentPage = pagedQuery(List.of(topLevel(1L, author)));
        when(Comment.<Comment>find(anyString(), any(Object[].class)))
                .thenReturn(commentPage);
        when(Comment.<Comment>list(anyString(), any(Object[].class)))
                .thenReturn(List.of());

        List<CommentDTO> dtos = svc.getByEvent(7L, null, 0, 20);

        assertEquals(1, dtos.size());
        assertNull(dtos.get(0).authorDisplayName());
    }

    // ── fanOutMentions catch via post (lines 155-158) ─────────────────────

    @Test
    @TestTransaction
    void post_mentionResolveFailure_skipsFanOutButStillReturnsComment() {
        CommentService svc = newService();
        UUID author = UUID.randomUUID();
        UUID creator = UUID.randomUUID();
        when(svc.identity.hasRole("ADMIN")).thenReturn(false);
        when(svc.callerIdentity.getUuid()).thenReturn(author);
        when(svc.callerIdentity.requireUuid()).thenReturn(author);
        when(svc.eventClient.getByIdWithCoOrgCheck(7L, author))
                .thenReturn(publishedEvent(creator));
        when(svc.eventClient.getOrganizerUuids(7L)).thenReturn(List.of(creator));
        // Author enrichment succeeds (not the path under test) — return null is fine.
        when(svc.userClient.getById(author)).thenReturn(null);
        // The mention resolution itself throws → fanOutMentions catch (155-158).
        when(svc.userClient.getByUsernames(anyString()))
                .thenThrow(new RuntimeException("user-service down"));

        // Real persist (no PanacheMock) so comment.id is assigned before the
        // CommentCreatedEvent.created(long, ...) auto-unboxing fire. @TestTransaction
        // rolls the row back. No parent comment → parentCommentId null path.
        CreateCommentRequest req = new CreateCommentRequest("hey @alice.dosh check this", null);

        CommentDTO dto = svc.post("auth0|author", 7L, req);

        assertNotNull(dto);
        // Fan-out was skipped: no mention event fired despite a parsed handle.
        verify(svc.commentMentionEvent, never()).fire(any(CommentMentionEvent.class));
        // The comments.created event is still fired (post succeeded).
        verify(svc.commentCreatedEvent).fire(any(CommentCreatedEvent.class));
    }

    // ── delete — callerUuid null sub-cases of lines 209/213 ───────────────

    @Test
    void delete_callerUuidNull_nonAdmin_throwsForbidden() {
        // requireUuid() returns null (unprovisioned caller). isAuthor is false
        // (the `callerUuid != null` operand of line 209 is false), and the
        // organizer branch (line 213 `callerUuid != null`) is skipped. Neither
        // admin, author nor organizer → forbidden 403.
        CommentService svc = newService();
        when(svc.identity.hasRole("ADMIN")).thenReturn(false);
        when(svc.callerIdentity.requireUuid()).thenReturn(null);

        Comment c = new Comment();
        c.id = 9L;
        c.eventId = 7L;
        c.authorId = UUID.randomUUID();
        c.content = "hi";
        c.likeCount = 0;
        c.createdAt = LocalDateTime.of(2025, 1, 1, 12, 0);

        PanacheMock.mock(Comment.class);
        when(Comment.<Comment>findByIdOptional(9L)).thenReturn(java.util.Optional.of(c));

        jakarta.ws.rs.WebApplicationException ex =
                org.junit.jupiter.api.Assertions.assertThrows(
                        jakarta.ws.rs.WebApplicationException.class,
                        () -> svc.delete("auth0|x", 9L));
        assertEquals(403, ex.getResponse().getStatus());
        // The organizer lookup must be skipped when callerUuid is null.
        verify(svc.eventClient, never())
                .getOrganizerUuids(org.mockito.ArgumentMatchers.anyLong());
    }

    // ── post — authorId null guard (line 88) ──────────────────────────────

    @Test
    void post_unprovisionedCaller_throwsNotFound() {
        CommentService svc = newService();
        UUID creator = UUID.randomUUID();
        when(svc.identity.hasRole("ADMIN")).thenReturn(false);
        when(svc.callerIdentity.getUuid()).thenReturn(UUID.randomUUID());
        // Event resolves & is PUBLISHED, but the caller has no resolvable UUID
        // → requireUuid() null → 88.
        when(svc.eventClient.getByIdWithCoOrgCheck(any(Long.class), any(UUID.class)))
                .thenReturn(publishedEvent(creator));
        when(svc.callerIdentity.requireUuid()).thenReturn(null);

        CreateCommentRequest req = new CreateCommentRequest("hello", null);

        org.junit.jupiter.api.Assertions.assertThrows(
                jakarta.ws.rs.NotFoundException.class,
                () -> svc.post("auth0|x", 7L, req));
    }
}
