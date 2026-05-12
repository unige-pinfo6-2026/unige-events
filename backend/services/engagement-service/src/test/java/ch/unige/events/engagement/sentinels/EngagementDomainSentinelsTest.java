package ch.unige.events.engagement.sentinels;

import ch.unige.events.engagement.comment.dto.CreateCommentRequest;
import ch.unige.events.engagement.comment.entity.Comment;
import ch.unige.events.engagement.comment.service.CommentService;
import ch.unige.events.engagement.test.JwtTestContext;
import ch.unige.events.engagement.test.JwtTestHelper;
import ch.unige.events.shared.client.EventServiceClient;
import ch.unige.events.shared.client.UserServiceClient;
import ch.unige.events.shared.domain.dto.EventDTO;
import ch.unige.events.shared.domain.dto.UserPublicResponse;
import ch.unige.events.shared.domain.enums.EventStatus;

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

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

/**
 * SCRUM-144 sentinel suite — engagement-service.
 *
 * <p>The 7 anti-oracle / depth-limit / authorization sentinels drive
 * {@link CommentService} directly (rather than going through REST-Assured)
 * which gives the same coverage surface as a full HTTP round trip without
 * the overhead of a JWT signing setup — the SecurityIdentity is provided by
 * {@link TestSecurity} and the {@code uuid} claim is staged via
 * {@link JwtTestContext}.
 */
@QuarkusTest
@TestSecurity(user = "auth0|sentinel-user")
@SuppressWarnings({"java:S100", "java:S2699"})
class EngagementDomainSentinelsTest {

    @Inject
    CommentService commentService;

    @InjectMock
    @RestClient
    EventServiceClient eventClient;

    @InjectMock
    @RestClient
    UserServiceClient userClient;

    private final UUID userBId = UUID.randomUUID();   // caller
    private final UUID userAId = UUID.randomUUID();   // event creator
    private final UUID otherId = UUID.randomUUID();

    @BeforeEach
    void stageJwt() {
        JwtTestContext.set(JwtTestHelper.jwtFor(userBId));
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

    /**
     * SCRUM-144 — Comment.prePersist seeds createdAt to "now" if null,
     * otherwise leaves the caller-set value alone (idempotent).
     */
    @Test
    void prePersist_setsCreatedAt() {
        Comment c = new Comment();
        c.eventId = 42L;
        c.authorId = UUID.randomUUID();
        c.content = "Hello";
        c.prePersist();
        assertNotNull(c.createdAt);
        assertTrue(c.createdAt.isAfter(LocalDateTime.now().minusSeconds(2)));
    }

    @Test
    void post_eventDraftByNonCreator_returns404_antiOracle() {
        // Caller is userBId; creator is userAId; event is DRAFT — anti-oracle
        // collapses the visibility check to 404 (same as missing event).
        EventDTO ev = event(101L, EventStatus.DRAFT, userAId);
        when(eventClient.getByIdWithCoOrgCheck(eq(101L), any(UUID.class))).thenReturn(ev);
        when(eventClient.getOrganizerUuids(101L)).thenReturn(List.of(userAId));

        assertThrows(NotFoundException.class,
                () -> commentService.post("auth0|sentinel-user", 101L,
                        new CreateCommentRequest("hi", null)));
    }

    @Test
    void post_eventBanned_returns404_antiOracle() {
        // Even an authenticated caller (and even an admin) sees BANNED → 404.
        EventDTO ev = event(102L, EventStatus.BANNED, userAId);
        when(eventClient.getByIdWithCoOrgCheck(eq(102L), any(UUID.class))).thenReturn(ev);

        assertThrows(NotFoundException.class,
                () -> commentService.post("auth0|sentinel-user", 102L,
                        new CreateCommentRequest("hi", null)));
    }

    @Test
    void post_replyToReply_returns422_repliesTooDeep() {
        // parent.parentComment != null → 422 replies_too_deep.
        EventDTO ev = event(103L, EventStatus.PUBLISHED, userAId);
        when(eventClient.getByIdWithCoOrgCheck(eq(103L), any(UUID.class))).thenReturn(ev);

        Comment grandparent = comment(910L, 103L, otherId, null);
        Comment parent = comment(911L, 103L, otherId, grandparent);
        PanacheMock.mock(Comment.class);
        when(Comment.findByIdOptional(911L)).thenReturn(Optional.of(parent));

        WebApplicationException ex = assertThrows(WebApplicationException.class,
                () -> commentService.post("auth0|sentinel-user", 103L,
                        new CreateCommentRequest("oops", 911L)));
        assertEquals(422, ex.getResponse().getStatus());
    }

    @Test
    void post_parentInOtherEvent_returns422_parentNotInEvent() {
        // parent.eventId != requestEventId → 422 parent_comment_not_in_event.
        EventDTO ev = event(104L, EventStatus.PUBLISHED, userAId);
        when(eventClient.getByIdWithCoOrgCheck(eq(104L), any(UUID.class))).thenReturn(ev);

        Comment otherEventComment = comment(920L, 999L /* different event */, otherId, null);
        PanacheMock.mock(Comment.class);
        when(Comment.findByIdOptional(920L)).thenReturn(Optional.of(otherEventComment));

        WebApplicationException ex = assertThrows(WebApplicationException.class,
                () -> commentService.post("auth0|sentinel-user", 104L,
                        new CreateCommentRequest("oops", 920L)));
        assertEquals(422, ex.getResponse().getStatus());
    }

    @Test
    void post_unknownParent_returns404_parentNotFound() {
        EventDTO ev = event(105L, EventStatus.PUBLISHED, userAId);
        when(eventClient.getByIdWithCoOrgCheck(eq(105L), any(UUID.class))).thenReturn(ev);

        PanacheMock.mock(Comment.class);
        when(Comment.findByIdOptional(99999L)).thenReturn(Optional.empty());

        WebApplicationException ex = assertThrows(WebApplicationException.class,
                () -> commentService.post("auth0|sentinel-user", 105L,
                        new CreateCommentRequest("oops", 99999L)));
        assertEquals(404, ex.getResponse().getStatus());
    }

    @Test
    void delete_byPendingCoOrganizer_returns403() {
        // Co-organizer with status=PENDING (NOT ACCEPTED) is treated by
        // event-service as "not an organizer" → getOrganizerUuids does
        // not include the caller → DELETE refused (403).
        Comment c = spyDeletable(comment(930L, 106L, otherId, null));
        PanacheMock.mock(Comment.class);
        when(Comment.findByIdOptional(930L)).thenReturn(Optional.of(c));
        when(eventClient.getOrganizerUuids(106L)).thenReturn(List.of(userAId));
        // userBId is the caller — neither author, nor accepted organizer.

        WebApplicationException ex = assertThrows(WebApplicationException.class,
                () -> commentService.delete("auth0|sentinel-user", 930L));
        assertEquals(403, ex.getResponse().getStatus());
    }

    @Test
    void delete_unknownComment_returns404_commentNotFound() {
        PanacheMock.mock(Comment.class);
        when(Comment.findByIdOptional(99999L)).thenReturn(Optional.empty());

        WebApplicationException ex = assertThrows(WebApplicationException.class,
                () -> commentService.delete("auth0|sentinel-user", 99999L));
        assertEquals(404, ex.getResponse().getStatus());
    }

    /**
     * SCRUM-136 cascade — Décision G: CommentService consumes the
     * {@code coOrganizerOf} flag from the REST client payload (resolved
     * by event-service via {@code ?check-co-org-of=}). No local cross-
     * domain JPA logic.
     */
    @Test
    void cascadeScrum136_viaRestClient_noLocalLogic() {
        // Caller is the userBId; event creator is userAId; userBId is
        // marked as ACCEPTED co-organizer in the REST client payload —
        // CommentService.post must succeed (the cascade reads the
        // payload boolean, no local table lookup is performed).
        EventDTO ev = new EventDTO(150L, "T", "d", "l",
                LocalDateTime.now(), LocalDateTime.now().plusDays(1),
                null, null, null, userAId,
                EventStatus.PUBLISHED, null, false, false, null,
                0L, null, 0L, 0L, 0L,
                null, null, null,
                List.of(), LocalDateTime.now(), LocalDateTime.now(),
                null, null, /* coOrganizerOf */ Boolean.TRUE);
        when(eventClient.getByIdWithCoOrgCheck(eq(150L), any(UUID.class))).thenReturn(ev);

        // Should not throw — the post is authorized via the REST-client
        // boolean, never via a local cross-domain lookup.
        assertDoesNotThrow(() -> commentService.post("auth0|sentinel-user", 150L,
                new CreateCommentRequest("hi from a co-organizer", null)));
    }
}
