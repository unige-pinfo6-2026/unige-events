package ch.unige.events.engagement.comment.service;

import ch.unige.events.engagement.comment.dto.CommentDTO;
import ch.unige.events.engagement.comment.entity.Comment;
import ch.unige.events.engagement.comment.entity.CommentLike;
import ch.unige.events.shared.client.EventServiceClient;
import ch.unige.events.shared.client.UserServiceClient;
import ch.unige.events.shared.domain.dto.EventDTO;
import ch.unige.events.shared.domain.dto.UserPublicResponse;
import ch.unige.events.shared.domain.enums.EventStatus;
import ch.unige.events.shared.domain.projections.CallerIdentity;

import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * SCRUM-144 — sentinel test pour le bulk fetch {@code likedByMe} (Décision K).
 *
 * <p>Vérifie que {@code CommentService.getByEvent} :
 * <ul>
 *   <li>peuple {@code likedByMe = true} pour les commentaires effectivement
 *       likés par le caller, {@code false} pour les autres ;</li>
 *   <li>ne consulte pas {@code comment_likes} pour un caller anonyme ;</li>
 *   <li>retourne {@code likedByMe = false} partout si le caller n'est pas
 *       provisionné côté user-service (CallerIdentity.getUuid() == null).</li>
 * </ul>
 */
@SuppressWarnings("java:S1612")
@QuarkusTest
class CommentServiceLikedByMeBatchTest {

    @Inject CommentService service;

    @InjectMock @RestClient EventServiceClient eventClient;
    @InjectMock @RestClient UserServiceClient userClient;
    @InjectMock CallerIdentity callerIdentity;

    private static final long EVENT_ID = 7L;

    @BeforeEach
    @AfterEach
    void truncate() {
        QuarkusTransaction.requiringNew().run(() -> CommentLike.deleteAll());
        QuarkusTransaction.requiringNew().run(() -> Comment.deleteAll());
    }

    private static EventDTO publishedEvent(UUID creator) {
        return new EventDTO(EVENT_ID, "Concert", null, "loc",
                java.time.LocalDateTime.now(), java.time.LocalDateTime.now().plusHours(2),
                null, null, null, creator, EventStatus.PUBLISHED, null, false, false, null,
                0L, null, 0L, 0L, 0L,
                null, null, null, List.of(), null, null, null, null, null);
    }

    private static UserPublicResponse anon(UUID id) {
        return UserPublicResponse.anonymous(id, "u-" + id, "User-" + id, null);
    }

    private Long persistComment(UUID author) {
        return QuarkusTransaction.requiringNew().call(() -> {
            Comment c = new Comment();
            c.eventId = EVENT_ID;
            c.authorId = author;
            c.content = "hello";
            c.persist();
            return c.id;
        });
    }

    private void persistLike(Long commentId, UUID userId) {
        QuarkusTransaction.requiringNew().run(() -> {
            CommentLike l = new CommentLike();
            l.commentId = commentId;
            l.userId = userId;
            l.persist();
        });
    }

    private void stubEventAndUsers(UUID creator) {
        when(eventClient.getById(EVENT_ID)).thenReturn(publishedEvent(creator));
        when(eventClient.getByIdWithCoOrgCheck(any(Long.class), any(UUID.class)))
                .thenReturn(publishedEvent(creator));
        lenient().when(eventClient.getOrganizerUuids(any(Long.class))).thenReturn(List.of());
        lenient().when(userClient.getById(any(UUID.class))).thenAnswer(inv ->
                anon(inv.getArgument(0)));
    }

    @Test
    void getByEvent_authenticatedWithSomeLikes_populatesLikedByMeFlag() {
        UUID author = UUID.randomUUID();
        UUID caller = UUID.randomUUID();

        Long c1 = persistComment(author);
        persistComment(author);       // c2 — neutral, no like
        Long c3 = persistComment(author);

        // Caller likes c1 and c3 (not c2).
        persistLike(c1, caller);
        persistLike(c3, caller);

        stubEventAndUsers(author);
        when(callerIdentity.getUuid()).thenReturn(caller);

        List<CommentDTO> dtos = service.getByEvent(EVENT_ID, "auth0|caller", 0, 20);

        assertEquals(3, dtos.size());
        for (CommentDTO d : dtos) {
            if (d.id().equals(c1) || d.id().equals(c3)) {
                assertTrue(d.likedByMe(), "liked comment " + d.id() + " must have likedByMe=true");
            } else {
                assertFalse(d.likedByMe(), "unliked comment " + d.id() + " must have likedByMe=false");
            }
        }
    }

    @Test
    void getByEvent_anonymous_returnsLikedByMeFalseEverywhere() {
        UUID author = UUID.randomUUID();
        UUID someone = UUID.randomUUID();

        Long c1 = persistComment(author);
        // Even if `someone` has likes, the anonymous caller sees likedByMe=false.
        persistLike(c1, someone);

        stubEventAndUsers(author);

        List<CommentDTO> dtos = service.getByEvent(EVENT_ID, null, 0, 20);

        assertEquals(1, dtos.size());
        assertFalse(dtos.get(0).likedByMe());
        // CallerIdentity should NOT have been consulted for an anonymous call.
        // (No mock interaction expectation — anonymous flow short-circuits before.)
    }

    @Test
    void getByEvent_callerNotProvisioned_returnsLikedByMeFalse() {
        UUID author = UUID.randomUUID();
        Long c1 = persistComment(author);
        persistLike(c1, UUID.randomUUID());

        stubEventAndUsers(author);
        // CallerIdentity.getUuid() returns null when the user is not provisioned.
        when(callerIdentity.getUuid()).thenReturn(null);

        List<CommentDTO> dtos = service.getByEvent(EVENT_ID, "auth0|unprovisioned", 0, 20);

        assertEquals(1, dtos.size());
        assertFalse(dtos.get(0).likedByMe());
    }

    @Test
    void getByEvent_likedByMePropagatesToReplies() {
        UUID author = UUID.randomUUID();
        UUID caller = UUID.randomUUID();

        // Top-level + reply.
        Long top = persistComment(author);
        Long reply = QuarkusTransaction.requiringNew().call(() -> {
            Comment top0 = Comment.findById(top);
            Comment r = new Comment();
            r.eventId = EVENT_ID;
            r.authorId = author;
            r.content = "reply";
            r.parentComment = top0;
            r.persist();
            return r.id;
        });

        // Caller likes the reply only.
        persistLike(reply, caller);

        stubEventAndUsers(author);
        when(callerIdentity.getUuid()).thenReturn(caller);

        List<CommentDTO> dtos = service.getByEvent(EVENT_ID, "auth0|caller", 0, 20);

        assertEquals(1, dtos.size());
        CommentDTO topDto = dtos.get(0);
        assertFalse(topDto.likedByMe(), "top-level not liked");
        assertEquals(1, topDto.replies().size());
        assertTrue(topDto.replies().get(0).likedByMe(), "reply must have likedByMe=true");
    }
}
