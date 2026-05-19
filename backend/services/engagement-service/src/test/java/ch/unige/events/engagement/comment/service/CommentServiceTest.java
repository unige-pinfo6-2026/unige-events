package ch.unige.events.engagement.comment.service;

import ch.unige.events.engagement.comment.dto.CommentDTO;
import ch.unige.events.engagement.comment.dto.CreateCommentRequest;
import ch.unige.events.engagement.comment.entity.Comment;
import ch.unige.events.engagement.test.JwtTestContext;
import ch.unige.events.engagement.test.JwtTestHelper;
import ch.unige.events.shared.client.EventServiceClient;
import ch.unige.events.shared.client.UserServiceClient;
import ch.unige.events.shared.domain.dto.EventDTO;
import ch.unige.events.shared.domain.dto.UserPublicResponse;
import ch.unige.events.shared.domain.enums.EventStatus;

import io.quarkus.hibernate.orm.panache.PanacheQuery;
import io.quarkus.panache.mock.PanacheMock;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import jakarta.inject.Inject;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.WebApplicationException;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

/**
 * Unit-test coverage for {@link CommentService}. Like
 * {@link ch.unige.events.engagement.attendance.service.AttendanceServiceTest},
 * uses {@link PanacheMock} for the static finders + {@link InjectMock} for
 * cross-service REST clients + {@link JwtTestContext} for JWT staging.
 */
@QuarkusTest
@TestSecurity(user = "auth0|test-comment-user")
class CommentServiceTest {

    @Inject
    CommentService service;

    @InjectMock
    @RestClient
    EventServiceClient eventClient;

    @InjectMock
    @RestClient
    UserServiceClient userClient;

    private final UUID userId = UUID.randomUUID();
    private final UUID otherUserId = UUID.randomUUID();
    private final UUID creatorId = UUID.randomUUID();

    @BeforeEach
    void stageJwt() {
        JwtTestContext.set(JwtTestHelper.jwtFor(userId));
        lenient().when(userClient.getById(any(UUID.class))).thenAnswer(inv -> {
            UUID id = inv.getArgument(0);
            return new UserPublicResponse(id, "User-" + id, null, null, null,
                    null, null, null, 0L, 0L, null);
        });
        lenient().when(eventClient.getOrganizerUuids(any(Long.class)))
                .thenReturn(List.of());
    }

    @AfterEach
    void clearJwt() {
        JwtTestContext.clear();
    }

    private static EventDTO event(Long id, EventStatus status) {
        return event(id, status, null);
    }

    private static EventDTO event(Long id, EventStatus status, UUID creatorIdParam) {
        return new EventDTO(id, "T", "d", "l",
                LocalDateTime.now(), LocalDateTime.now().plusDays(1),
                null, null, null,
                creatorIdParam,
                status, null, false, false, null,
                0L, null, 0L, 0L, 0L,
                null, null, null,
                List.of(),
                LocalDateTime.now(), LocalDateTime.now(),
                null, null, null);
    }

    private static Comment comment(Long id, Long eventId, UUID authorId, Comment parent) {
        Comment c = new Comment();
        c.id = id;
        c.eventId = eventId;
        c.authorId = authorId;
        c.parentComment = parent;
        c.content = "content-" + id;
        c.likeCount = 0;
        c.createdAt = LocalDateTime.now();
        return c;
    }

    private static Comment spyDeletable(Comment c) {
        Comment s = spy(c);
        doNothing().when(s).delete();
        return s;
    }

    // ──────────────────────────────────────────────────────────────────
    // post(...)
    // ──────────────────────────────────────────────────────────────────

    @Test
    void post_publishedEvent_returnsCreatedDTO() {
        EventDTO ev = event(1L, EventStatus.PUBLISHED, creatorId);
        when(eventClient.getByIdWithCoOrgCheck(eq(1L), any(UUID.class))).thenReturn(ev);

        CommentDTO dto = service.post(
                "auth0|test-comment-user", 1L,
                new CreateCommentRequest("Hello world", null));
        assertNotNull(dto);
        assertEquals("Hello world", dto.content());
        assertEquals(userId, dto.authorId());
    }

    // ──────────────────────────────────────────────────────────────────
    // Mention fan-out (SCRUM-145 follow-lifecycle pattern refactor)
    // ──────────────────────────────────────────────────────────────────

    @Test
    void post_withMentions_resolvesHandlesViaUserService() {
        // Content contains @<handle> → CommentService should call
        // userClient.getByUsernames so the fan-out has UUIDs to publish.
        EventDTO ev = event(50L, EventStatus.PUBLISHED, creatorId);
        when(eventClient.getByIdWithCoOrgCheck(eq(50L), any(UUID.class))).thenReturn(ev);
        when(userClient.getByUsernames(anyString())).thenReturn(java.util.List.of());

        service.post("auth0|test-comment-user", 50L,
                new CreateCommentRequest("Hi @alice.dosh and @bob.smith", null));

        org.mockito.Mockito.verify(userClient).getByUsernames(anyString());
    }

    @Test
    void post_withoutMentions_skipsUserServiceCall() {
        // No @<handle> in content → no parser hits → no userClient.getByUsernames call.
        // Saves a downstream hop on the common case.
        EventDTO ev = event(51L, EventStatus.PUBLISHED, creatorId);
        when(eventClient.getByIdWithCoOrgCheck(eq(51L), any(UUID.class))).thenReturn(ev);

        service.post("auth0|test-comment-user", 51L,
                new CreateCommentRequest("Just a normal comment", null));

        org.mockito.Mockito.verify(userClient, org.mockito.Mockito.never()).getByUsernames(anyString());
    }

    @Test
    void post_userClientThrowsDuringMentionResolve_doesNotFailComment() {
        // Comment post must remain robust if user-service is degraded —
        // the caller still gets a successful 201 ; mention notifications
        // for this delivery are silently lost.
        EventDTO ev = event(52L, EventStatus.PUBLISHED, creatorId);
        when(eventClient.getByIdWithCoOrgCheck(eq(52L), any(UUID.class))).thenReturn(ev);
        when(userClient.getByUsernames(anyString()))
                .thenThrow(new RuntimeException("user-service down"));

        CommentDTO dto = service.post("auth0|test-comment-user", 52L,
                new CreateCommentRequest("Hi @alice.dosh", null));
        assertNotNull(dto, "the comment post must succeed even when mention resolve fails");
    }

    @Test
    void post_trimsContent() {
        EventDTO ev = event(2L, EventStatus.PUBLISHED, creatorId);
        when(eventClient.getByIdWithCoOrgCheck(eq(2L), any(UUID.class))).thenReturn(ev);

        CommentDTO dto = service.post(
                "auth0|test-comment-user", 2L,
                new CreateCommentRequest("   spaced   ", null));
        assertEquals("spaced", dto.content());
    }

    @Test
    void post_unknownEvent_throwsNotFound() {
        when(eventClient.getByIdWithCoOrgCheck(eq(404L), any(UUID.class))).thenReturn(null);
        assertThrows(NotFoundException.class,
                () -> service.post("auth0|test-comment-user", 404L,
                        new CreateCommentRequest("x", null)));
    }

    @Test
    void post_draftEvent_byCreator_throwsBadRequest() {
        // Caller is the creator, so visibility check passes — but DRAFT
        // still rejects with cannot_comment_draft_event (400).
        EventDTO ev = event(3L, EventStatus.DRAFT, userId);
        when(eventClient.getByIdWithCoOrgCheck(eq(3L), any(UUID.class))).thenReturn(ev);
        when(eventClient.getOrganizerUuids(3L)).thenReturn(List.of(userId));

        WebApplicationException ex = assertThrows(WebApplicationException.class,
                () -> service.post("auth0|test-comment-user", 3L,
                        new CreateCommentRequest("x", null)));
        assertEquals(400, ex.getResponse().getStatus());
    }

    @Test
    void post_draftEvent_byNonCreator_throwsNotFound_antiOracle() {
        EventDTO ev = event(4L, EventStatus.DRAFT, creatorId);
        when(eventClient.getByIdWithCoOrgCheck(eq(4L), any(UUID.class))).thenReturn(ev);
        when(eventClient.getOrganizerUuids(4L)).thenReturn(List.of(creatorId));

        assertThrows(NotFoundException.class,
                () -> service.post("auth0|test-comment-user", 4L,
                        new CreateCommentRequest("x", null)));
    }

    @Test
    void post_bannedEvent_throwsNotFound_antiOracle() {
        EventDTO ev = event(5L, EventStatus.BANNED, userId);
        when(eventClient.getByIdWithCoOrgCheck(eq(5L), any(UUID.class))).thenReturn(ev);

        assertThrows(NotFoundException.class,
                () -> service.post("auth0|test-comment-user", 5L,
                        new CreateCommentRequest("x", null)));
    }

    @Test
    void post_cancelledEvent_throwsBadRequest() {
        EventDTO ev = event(6L, EventStatus.CANCELLED, userId);
        when(eventClient.getByIdWithCoOrgCheck(eq(6L), any(UUID.class))).thenReturn(ev);
        when(eventClient.getOrganizerUuids(6L)).thenReturn(List.of(userId));

        WebApplicationException ex = assertThrows(WebApplicationException.class,
                () -> service.post("auth0|test-comment-user", 6L,
                        new CreateCommentRequest("x", null)));
        assertEquals(400, ex.getResponse().getStatus());
    }

    @Test
    void post_expiredEvent_throwsBadRequest() {
        EventDTO ev = event(7L, EventStatus.EXPIRED, userId);
        when(eventClient.getByIdWithCoOrgCheck(eq(7L), any(UUID.class))).thenReturn(ev);
        when(eventClient.getOrganizerUuids(7L)).thenReturn(List.of(userId));

        WebApplicationException ex = assertThrows(WebApplicationException.class,
                () -> service.post("auth0|test-comment-user", 7L,
                        new CreateCommentRequest("x", null)));
        assertEquals(400, ex.getResponse().getStatus());
    }

    @Test
    @io.quarkus.test.TestTransaction
    void post_replyToTopLevel_succeeds() {
        EventDTO ev = event(8L, EventStatus.PUBLISHED, creatorId);
        when(eventClient.getByIdWithCoOrgCheck(eq(8L), any(UUID.class))).thenReturn(ev);

        // Persist a real parent so the FK on the reply insert passes.
        // @TestTransaction rolls everything back after the test.
        Comment parent = new Comment();
        parent.eventId = 8L;
        parent.authorId = otherUserId;
        parent.content = "parent";
        parent.likeCount = 0;
        parent.persist();

        CommentDTO dto = service.post(
                "auth0|test-comment-user", 8L,
                new CreateCommentRequest("reply", parent.id));
        assertNotNull(dto);
        assertEquals("reply", dto.content());
    }

    @Test
    void post_replyToReply_throws422_repliesTooDeep() {
        EventDTO ev = event(9L, EventStatus.PUBLISHED, creatorId);
        when(eventClient.getByIdWithCoOrgCheck(eq(9L), any(UUID.class))).thenReturn(ev);

        Comment grandparent = comment(110L, 9L, otherUserId, null);
        Comment parent = comment(111L, 9L, otherUserId, grandparent);
        PanacheMock.mock(Comment.class);
        when(Comment.findByIdOptional(111L)).thenReturn(Optional.of(parent));

        WebApplicationException ex = assertThrows(WebApplicationException.class,
                () -> service.post("auth0|test-comment-user", 9L,
                        new CreateCommentRequest("oops", 111L)));
        assertEquals(422, ex.getResponse().getStatus());
    }

    @Test
    void post_parentInOtherEvent_throws422() {
        EventDTO ev = event(10L, EventStatus.PUBLISHED, creatorId);
        when(eventClient.getByIdWithCoOrgCheck(eq(10L), any(UUID.class))).thenReturn(ev);

        Comment parent = comment(120L, 999L /* different event */, otherUserId, null);
        PanacheMock.mock(Comment.class);
        when(Comment.findByIdOptional(120L)).thenReturn(Optional.of(parent));

        WebApplicationException ex = assertThrows(WebApplicationException.class,
                () -> service.post("auth0|test-comment-user", 10L,
                        new CreateCommentRequest("oops", 120L)));
        assertEquals(422, ex.getResponse().getStatus());
    }

    @Test
    void post_unknownParent_throwsNotFound() {
        EventDTO ev = event(11L, EventStatus.PUBLISHED, creatorId);
        when(eventClient.getByIdWithCoOrgCheck(eq(11L), any(UUID.class))).thenReturn(ev);

        PanacheMock.mock(Comment.class);
        when(Comment.findByIdOptional(999L)).thenReturn(Optional.empty());

        WebApplicationException ex = assertThrows(WebApplicationException.class,
                () -> service.post("auth0|test-comment-user", 11L,
                        new CreateCommentRequest("oops", 999L)));
        assertEquals(404, ex.getResponse().getStatus());
    }

    @Test
    void post_anonymousJwt_throwsNotFound() {
        EventDTO ev = event(12L, EventStatus.PUBLISHED, creatorId);
        when(eventClient.getById(12L)).thenReturn(ev);
        JwtTestContext.set(JwtTestHelper.anonymous());

        assertThrows(NotFoundException.class,
                () -> service.post("auth0|test-comment-user", 12L,
                        new CreateCommentRequest("x", null)));
    }

    // ──────────────────────────────────────────────────────────────────
    // delete(...)
    // ──────────────────────────────────────────────────────────────────

    @Test
    void delete_byAuthor_succeeds() {
        Comment c = spyDeletable(comment(200L, 20L, userId, null));
        PanacheMock.mock(Comment.class);
        when(Comment.findByIdOptional(200L)).thenReturn(Optional.of(c));

        service.delete("auth0|test-comment-user", 200L);
    }

    @Test
    void delete_byOrganizer_succeeds() {
        Comment c = spyDeletable(comment(201L, 21L, otherUserId, null));
        PanacheMock.mock(Comment.class);
        when(Comment.findByIdOptional(201L)).thenReturn(Optional.of(c));
        when(eventClient.getOrganizerUuids(21L)).thenReturn(List.of(userId));

        service.delete("auth0|test-comment-user", 201L);
    }

    @Test
    void delete_byNonAuthorNonOrganizer_throwsForbidden() {
        Comment c = spyDeletable(comment(202L, 22L, otherUserId, null));
        PanacheMock.mock(Comment.class);
        when(Comment.findByIdOptional(202L)).thenReturn(Optional.of(c));
        when(eventClient.getOrganizerUuids(22L)).thenReturn(List.of(creatorId));

        WebApplicationException ex = assertThrows(WebApplicationException.class,
                () -> service.delete("auth0|test-comment-user", 202L));
        assertEquals(403, ex.getResponse().getStatus());
    }

    @Test
    void delete_unknownComment_throwsNotFound() {
        PanacheMock.mock(Comment.class);
        when(Comment.findByIdOptional(999L)).thenReturn(Optional.empty());

        WebApplicationException ex = assertThrows(WebApplicationException.class,
                () -> service.delete("auth0|test-comment-user", 999L));
        assertEquals(404, ex.getResponse().getStatus());
    }

    // ──────────────────────────────────────────────────────────────────
    // getByEvent(...)
    // ──────────────────────────────────────────────────────────────────

    @Test
    void getByEvent_publishedEvent_emptyResults() {
        EventDTO ev = event(30L, EventStatus.PUBLISHED, creatorId);
        when(eventClient.getByIdWithCoOrgCheck(eq(30L), any(UUID.class))).thenReturn(ev);

        PanacheMock.mock(Comment.class);
        @SuppressWarnings({"rawtypes", "unchecked"})
        PanacheQuery query = mock(PanacheQuery.class);
        when(query.page(0, 20)).thenReturn(query);
        when(query.list()).thenReturn(List.of());
        when(Comment.find(anyString(), any(Object[].class))).thenReturn(query);

        List<CommentDTO> comments = service.getByEvent(30L, "auth0|test-comment-user", 0, 20);
        assertTrue(comments.isEmpty());
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    @Test
    void getByEvent_topLevelOnly_returnsList() {
        EventDTO ev = event(31L, EventStatus.PUBLISHED, creatorId);
        when(eventClient.getByIdWithCoOrgCheck(eq(31L), any(UUID.class))).thenReturn(ev);
        when(eventClient.getOrganizerUuids(31L)).thenReturn(List.of(creatorId));

        Comment top1 = comment(310L, 31L, userId, null);
        Comment top2 = comment(311L, 31L, otherUserId, null);

        PanacheMock.mock(Comment.class);
        PanacheQuery topQuery = mock(PanacheQuery.class);
        when(topQuery.page(0, 20)).thenReturn(topQuery);
        when(topQuery.list()).thenReturn(List.of(top1, top2));
        when(Comment.find(anyString(), any(Object[].class))).thenReturn(topQuery);
        when(Comment.list(anyString(), any(Object[].class))).thenReturn(List.of());

        List<CommentDTO> comments = service.getByEvent(31L, "auth0|test-comment-user", 0, 20);
        assertEquals(2, comments.size());
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    @Test
    void getByEvent_withReplies_groupsCorrectly() {
        EventDTO ev = event(32L, EventStatus.PUBLISHED, creatorId);
        when(eventClient.getByIdWithCoOrgCheck(eq(32L), any(UUID.class))).thenReturn(ev);
        when(eventClient.getOrganizerUuids(32L)).thenReturn(List.of(userId));

        Comment top = comment(320L, 32L, userId, null);
        Comment reply = comment(321L, 32L, otherUserId, top);

        PanacheMock.mock(Comment.class);
        PanacheQuery topQuery = mock(PanacheQuery.class);
        when(topQuery.page(0, 20)).thenReturn(topQuery);
        when(topQuery.list()).thenReturn(List.of(top));
        when(Comment.find(anyString(), any(Object[].class))).thenReturn(topQuery);
        when(Comment.list(anyString(), any(Object[].class))).thenReturn(List.of(reply));

        List<CommentDTO> comments = service.getByEvent(32L, "auth0|test-comment-user", 0, 20);
        assertEquals(1, comments.size());
        assertEquals(1, comments.get(0).replies().size());
        assertTrue(comments.get(0).authorIsOrganizer());
    }

    @Test
    void getByEvent_anonymous_publishedEvent_works() {
        JwtTestContext.set(JwtTestHelper.anonymous());
        EventDTO ev = event(33L, EventStatus.PUBLISHED, creatorId);
        when(eventClient.getById(33L)).thenReturn(ev);

        PanacheMock.mock(Comment.class);
        @SuppressWarnings({"rawtypes", "unchecked"})
        PanacheQuery query = mock(PanacheQuery.class);
        when(query.page(0, 20)).thenReturn(query);
        when(query.list()).thenReturn(List.of());
        when(Comment.find(anyString(), any(Object[].class))).thenReturn(query);

        List<CommentDTO> comments = service.getByEvent(33L, null, 0, 20);
        assertTrue(comments.isEmpty());
    }

    @Test
    void getByEvent_unknownEvent_throwsNotFound() {
        when(eventClient.getByIdWithCoOrgCheck(eq(34L), any(UUID.class))).thenReturn(null);
        assertThrows(NotFoundException.class,
                () -> service.getByEvent(34L, "auth0|test-comment-user", 0, 20));
    }

    @Test
    void getByEvent_bannedEvent_throwsNotFound_antiOracle() {
        EventDTO ev = event(35L, EventStatus.BANNED, userId);
        when(eventClient.getByIdWithCoOrgCheck(eq(35L), any(UUID.class))).thenReturn(ev);

        assertThrows(NotFoundException.class,
                () -> service.getByEvent(35L, "auth0|test-comment-user", 0, 20));
    }
}
