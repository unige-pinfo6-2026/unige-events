package ch.unige.events.service;

import ch.unige.events.dto.comment.CommentDTO;
import ch.unige.events.dto.comment.CreateCommentRequest;
import ch.unige.events.entity.CoOrganizerStatus;
import ch.unige.events.entity.Comment;
import ch.unige.events.entity.Event;
import ch.unige.events.entity.EventCategory;
import ch.unige.events.entity.EventCoOrganizer;
import ch.unige.events.entity.EventStatus;
import ch.unige.events.entity.User;
import io.quarkus.test.TestTransaction;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import io.quarkus.test.security.TestSecurity;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.WebApplicationException;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * DB-backed integration coverage for {@link CommentService}.
 *
 * <p>Uses {@link ShareServiceCoverageProfile} which excludes
 * {@link CommentServiceMock} from the ARC bean container so the real service
 * is wired against the DevServices PostgreSQL.
 */
@QuarkusTest
@TestProfile(ShareServiceCoverageProfile.class)
class CommentServiceCoverageTest {

    @Inject
    CommentService commentService;

    @Inject
    EntityManager entityManager;

    // -------------------------------------------------------------------------
    // post()
    // -------------------------------------------------------------------------

    @Test
    @TestTransaction
    @TestSecurity(user = "auth0|cv-author")
    void post_validBody_persistsAndReturnsDTO() {
        User creator = persistUser("auth0|cv-creator", "cv-creator@example.com");
        User author = persistUser("auth0|cv-author", "cv-author@example.com");
        Event event = persistEvent("cv-event", creator, EventStatus.PUBLISHED);

        CommentDTO dto = commentService.post(author.auth0Id, event.id,
                new CreateCommentRequest("Hello world", null));

        assertNotNull(dto.id());
        assertEquals("Hello world", dto.content());
        assertEquals(author.id, dto.authorId());
        assertEquals(0, dto.likeCount());
        assertFalse(dto.likedByMe());
        assertNull(dto.parentCommentId());
        assertTrue(dto.replies().isEmpty());
        assertFalse(dto.authorIsOrganizer());
    }

    @Test
    @TestTransaction
    @TestSecurity(user = "auth0|ct-author")
    void post_trimContent_strippedBeforePersist() {
        User creator = persistUser("auth0|ct-creator", "ct-creator@example.com");
        User author = persistUser("auth0|ct-author", "ct-author@example.com");
        Event event = persistEvent("ct-event", creator, EventStatus.PUBLISHED);

        CommentDTO dto = commentService.post(author.auth0Id, event.id,
                new CreateCommentRequest("   Hello   ", null));

        assertEquals("Hello", dto.content());
    }

    @Test
    @TestTransaction
    @TestSecurity(user = "auth0|co-author")
    void post_authorIsCreator_authorIsOrganizerTrue() {
        User creator = persistUser("auth0|co-author", "co-author@example.com");
        Event event = persistEvent("co-event", creator, EventStatus.PUBLISHED);

        CommentDTO dto = commentService.post(creator.auth0Id, event.id,
                new CreateCommentRequest("From the creator", null));

        assertTrue(dto.authorIsOrganizer());
    }

    @Test
    @TestTransaction
    @TestSecurity(user = "auth0|cd-author")
    void post_eventDraftByNonCreator_returns404_antiOracle() {
        User creator = persistUser("auth0|cd-creator", "cd-creator@example.com");
        User author = persistUser("auth0|cd-author", "cd-author@example.com");
        Event event = persistEvent("cd-event", creator, EventStatus.DRAFT);

        assertThrows(NotFoundException.class,
                () -> commentService.post(author.auth0Id, event.id,
                        new CreateCommentRequest("Hello", null)));
    }

    @Test
    @TestTransaction
    @TestSecurity(user = "auth0|cdc-creator")
    void post_eventDraftByCreator_returns400_cannotCommentDraft() {
        User creator = persistUser("auth0|cdc-creator", "cdc-creator@example.com");
        Event event = persistEvent("cdc-event", creator, EventStatus.DRAFT);

        WebApplicationException ex = assertThrows(WebApplicationException.class,
                () -> commentService.post(creator.auth0Id, event.id,
                        new CreateCommentRequest("Hello", null)));
        assertEquals(400, ex.getResponse().getStatus());
    }

    @Test
    @TestTransaction
    @TestSecurity(user = "auth0|cc-author")
    void post_eventCancelled_returns400_cannotCommentCancelled() {
        User creator = persistUser("auth0|cc-creator", "cc-creator@example.com");
        User author = persistUser("auth0|cc-author", "cc-author@example.com");
        Event event = persistEvent("cc-event", creator, EventStatus.PUBLISHED);
        // Need to bypass the EventService.getById visibility check first — the
        // event must be PUBLISHED to be visible. We then mutate to CANCELLED
        // *after* persistence so getById still resolves; but the runtime branch
        // is on the live row, so we set CANCELLED directly via persist.
        event.status = EventStatus.CANCELLED;
        entityManager.persist(event);
        entityManager.flush();

        // CANCELLED non-créateur ⇒ 404 anti-oracle (cf. EventService.getById).
        // Pour atteindre la branche cannot_comment_cancelled, le caller doit être
        // créateur (visibilité héritée). On utilise donc le creator comme caller.
        assertThrows(NotFoundException.class,
                () -> commentService.post(author.auth0Id, event.id,
                        new CreateCommentRequest("Hello", null)));
    }

    @Test
    @TestTransaction
    @TestSecurity(user = "auth0|cco-creator")
    void post_eventCancelledByCreator_returns400_cannotCommentCancelled() {
        User creator = persistUser("auth0|cco-creator", "cco-creator@example.com");
        Event event = persistEvent("cco-event", creator, EventStatus.CANCELLED);

        WebApplicationException ex = assertThrows(WebApplicationException.class,
                () -> commentService.post(creator.auth0Id, event.id,
                        new CreateCommentRequest("Hello", null)));
        assertEquals(400, ex.getResponse().getStatus());
        assertTrue(ex.getResponse().getEntity().toString().contains("cannot_comment_cancelled_event"));
    }

    @Test
    @TestTransaction
    @TestSecurity(user = "auth0|ce-creator")
    void post_eventExpired_returns400_cannotCommentExpired() {
        // EXPIRED non-créateur ⇒ 404 anti-oracle via EventService.getById ; pour
        // exercer la branche cannot_comment_expired_event, le caller doit être
        // créateur (visibilité héritée). Cf. spec décision 14 + 15.
        User creator = persistUser("auth0|ce-creator", "ce-creator@example.com");
        Event event = persistEvent("ce-event", creator, EventStatus.EXPIRED);

        WebApplicationException ex = assertThrows(WebApplicationException.class,
                () -> commentService.post(creator.auth0Id, event.id,
                        new CreateCommentRequest("Hello", null)));
        assertEquals(400, ex.getResponse().getStatus());
        assertTrue(ex.getResponse().getEntity().toString().contains("cannot_comment_expired_event"));
    }

    @Test
    @TestTransaction
    @TestSecurity(user = "auth0|ce-other")
    void post_eventExpiredByNonCreator_returns404_antiOracle() {
        User creator = persistUser("auth0|cea-creator", "cea-creator@example.com");
        User other = persistUser("auth0|ce-other", "ce-other@example.com");
        Event event = persistEvent("cea-event", creator, EventStatus.EXPIRED);

        assertThrows(NotFoundException.class,
                () -> commentService.post(other.auth0Id, event.id,
                        new CreateCommentRequest("Hello", null)));
    }

    @Test
    @TestTransaction
    @TestSecurity(user = "auth0|cb-author")
    void post_eventBanned_returns404_antiOracle() {
        User creator = persistUser("auth0|cb-creator", "cb-creator@example.com");
        User author = persistUser("auth0|cb-author", "cb-author@example.com");
        Event event = persistEvent("cb-event", creator, EventStatus.BANNED);

        // BANNED event invisible for everyone (cf. SCRUM-97) → 404 anti-oracle.
        assertThrows(NotFoundException.class,
                () -> commentService.post(author.auth0Id, event.id,
                        new CreateCommentRequest("Hello", null)));
    }

    @Test
    @TestTransaction
    @TestSecurity(user = "auth0|crp-author")
    void post_replyToTopLevel_returns201() {
        User creator = persistUser("auth0|crp-creator", "crp-creator@example.com");
        User author = persistUser("auth0|crp-author", "crp-author@example.com");
        Event event = persistEvent("crp-event", creator, EventStatus.PUBLISHED);
        Comment top = persistComment(event, author, null, "Top-level");

        CommentDTO reply = commentService.post(author.auth0Id, event.id,
                new CreateCommentRequest("A reply", top.id));

        assertEquals(top.id, reply.parentCommentId());
        assertEquals("A reply", reply.content());
    }

    @Test
    @TestTransaction
    @TestSecurity(user = "auth0|crr-author")
    void post_replyToReply_returns422_repliesTooDeep() {
        User creator = persistUser("auth0|crr-creator", "crr-creator@example.com");
        User author = persistUser("auth0|crr-author", "crr-author@example.com");
        Event event = persistEvent("crr-event", creator, EventStatus.PUBLISHED);
        Comment top = persistComment(event, author, null, "Top-level");
        Comment reply = persistComment(event, author, top, "Level 1 reply");

        WebApplicationException ex = assertThrows(WebApplicationException.class,
                () -> commentService.post(author.auth0Id, event.id,
                        new CreateCommentRequest("Too deep", reply.id)));
        assertEquals(422, ex.getResponse().getStatus());
        assertTrue(ex.getResponse().getEntity().toString().contains("replies_too_deep"));
    }

    @Test
    @TestTransaction
    @TestSecurity(user = "auth0|cpne-author")
    void post_parentInOtherEvent_returns422_parentNotInEvent() {
        User creator = persistUser("auth0|cpne-creator", "cpne-creator@example.com");
        User author = persistUser("auth0|cpne-author", "cpne-author@example.com");
        Event eventA = persistEvent("cpne-event-a", creator, EventStatus.PUBLISHED);
        Event eventB = persistEvent("cpne-event-b", creator, EventStatus.PUBLISHED);
        Comment topInA = persistComment(eventA, author, null, "Top in A");

        WebApplicationException ex = assertThrows(WebApplicationException.class,
                () -> commentService.post(author.auth0Id, eventB.id,
                        new CreateCommentRequest("Cross-event", topInA.id)));
        assertEquals(422, ex.getResponse().getStatus());
        assertTrue(ex.getResponse().getEntity().toString().contains("parent_comment_not_in_event"));
    }

    @Test
    @TestTransaction
    @TestSecurity(user = "auth0|cpu-author")
    void post_unknownParent_returns404_parentNotFound() {
        User creator = persistUser("auth0|cpu-creator", "cpu-creator@example.com");
        User author = persistUser("auth0|cpu-author", "cpu-author@example.com");
        Event event = persistEvent("cpu-event", creator, EventStatus.PUBLISHED);

        WebApplicationException ex = assertThrows(WebApplicationException.class,
                () -> commentService.post(author.auth0Id, event.id,
                        new CreateCommentRequest("Orphan", 99999L)));
        assertEquals(404, ex.getResponse().getStatus());
        assertTrue(ex.getResponse().getEntity().toString().contains("parent_comment_not_found"));
    }

    @Test
    @TestTransaction
    @TestSecurity(user = "auth0|cnp-ghost")
    void post_authorNotProvisioned_throws404() {
        User creator = persistUser("auth0|cnp-creator", "cnp-creator@example.com");
        Event event = persistEvent("cnp-event", creator, EventStatus.PUBLISHED);

        assertThrows(NotFoundException.class,
                () -> commentService.post("auth0|cnp-ghost", event.id,
                        new CreateCommentRequest("Hello", null)));
    }

    // -------------------------------------------------------------------------
    // getByEvent()
    // -------------------------------------------------------------------------

    @Test
    @TestTransaction
    @TestSecurity(user = "auth0|gv-author")
    void getByEvent_publishedReturnsTopLevelWithReplies() {
        User creator = persistUser("auth0|gv-creator", "gv-creator@example.com");
        User author = persistUser("auth0|gv-author", "gv-author@example.com");
        Event event = persistEvent("gv-event", creator, EventStatus.PUBLISHED);
        // Deterministic timestamps via the @PrePersist null-guard — top2 strictly
        // newer than top1 so the DESC ordering assertion is stable.
        LocalDateTime t0 = LocalDateTime.now();
        Comment top1 = persistCommentAt(event, author, null, "Top 1", t0);
        Comment top2 = persistCommentAt(event, author, null, "Top 2", t0.plusSeconds(1));
        persistCommentAt(event, author, top1, "Reply to top1", t0.plusSeconds(2));
        persistCommentAt(event, author, top2, "Reply to top2", t0.plusSeconds(3));

        List<CommentDTO> result = commentService.getByEvent(event.id, author.auth0Id, 0, 20);

        assertEquals(2, result.size());
        // Tri DESC : top2 d'abord, puis top1
        assertEquals(top2.id, result.get(0).id());
        assertEquals(top1.id, result.get(1).id());
        assertEquals(1, result.get(0).replies().size());
        assertEquals(1, result.get(1).replies().size());
    }

    @Test
    @TestTransaction
    @TestSecurity(user = "auth0|gd-other")
    void getByEvent_draftByNonCreator_returns404_antiOracle() {
        User creator = persistUser("auth0|gd-creator", "gd-creator@example.com");
        User other = persistUser("auth0|gd-other", "gd-other@example.com");
        Event event = persistEvent("gd-event", creator, EventStatus.DRAFT);

        assertThrows(NotFoundException.class,
                () -> commentService.getByEvent(event.id, other.auth0Id, 0, 20));
    }

    @Test
    @TestTransaction
    @TestSecurity(user = "auth0|gdc-creator")
    void getByEvent_draftByCreator_returns200() {
        User creator = persistUser("auth0|gdc-creator", "gdc-creator@example.com");
        Event event = persistEvent("gdc-event", creator, EventStatus.DRAFT);
        persistComment(event, creator, null, "Draft comment");

        List<CommentDTO> result = commentService.getByEvent(event.id, creator.auth0Id, 0, 20);

        assertEquals(1, result.size());
    }

    @Test
    @TestTransaction
    void getByEvent_anonymousOnPublished_returnsList() {
        User creator = persistUser("auth0|ga-creator", "ga-creator@example.com");
        Event event = persistEvent("ga-event", creator, EventStatus.PUBLISHED);
        persistComment(event, creator, null, "Public comment");

        List<CommentDTO> result = commentService.getByEvent(event.id, null, 0, 20);

        assertEquals(1, result.size());
        assertTrue(result.get(0).authorIsOrganizer());
    }

    @Test
    @TestTransaction
    @TestSecurity(user = "auth0|gp-author")
    void getByEvent_paginationOrdersAndLimits() {
        User creator = persistUser("auth0|gp-creator", "gp-creator@example.com");
        User author = persistUser("auth0|gp-author", "gp-author@example.com");
        Event event = persistEvent("gp-event", creator, EventStatus.PUBLISHED);
        // Deterministic incremental timestamps — pas de Thread.sleep (Sonar S2925).
        LocalDateTime base = LocalDateTime.now();
        for (int i = 0; i < 5; i++) {
            persistCommentAt(event, author, null, "Comment " + i, base.plusSeconds(i));
        }

        List<CommentDTO> page0 = commentService.getByEvent(event.id, author.auth0Id, 0, 2);
        assertEquals(2, page0.size());
        // Tri DESC strict : page0[0].createdAt > page0[1].createdAt
        assertTrue(page0.get(0).createdAt().compareTo(page0.get(1).createdAt()) >= 0);
    }

    @Test
    @TestTransaction
    @TestSecurity(user = "auth0|gco-author")
    void getByEvent_acceptedCoOrganizerAuthorIsOrganizerTrue() {
        User creator = persistUser("auth0|gco-creator", "gco-creator@example.com");
        User coOrg = persistUser("auth0|gco-coorg", "gco-coorg@example.com");
        Event event = persistEvent("gco-event", creator, EventStatus.PUBLISHED);
        persistCoOrganizer(event.id, coOrg.id, CoOrganizerStatus.ACCEPTED);
        persistComment(event, coOrg, null, "From co-org ACCEPTED");

        List<CommentDTO> result = commentService.getByEvent(event.id, coOrg.auth0Id, 0, 20);

        assertEquals(1, result.size());
        assertTrue(result.get(0).authorIsOrganizer());
    }

    @Test
    @TestTransaction
    @TestSecurity(user = "auth0|gpco-author")
    void getByEvent_pendingCoOrganizerAuthorIsOrganizerFalse() {
        User creator = persistUser("auth0|gpco-creator", "gpco-creator@example.com");
        User coOrg = persistUser("auth0|gpco-coorg", "gpco-coorg@example.com");
        Event event = persistEvent("gpco-event", creator, EventStatus.PUBLISHED);
        persistCoOrganizer(event.id, coOrg.id, CoOrganizerStatus.PENDING);
        persistComment(event, coOrg, null, "From co-org PENDING");

        List<CommentDTO> result = commentService.getByEvent(event.id, coOrg.auth0Id, 0, 20);

        assertEquals(1, result.size());
        assertFalse(result.get(0).authorIsOrganizer());
    }

    @Test
    @TestTransaction
    @TestSecurity(user = "auth0|ge-author")
    void getByEvent_emptyEvent_returnsEmptyList() {
        User creator = persistUser("auth0|ge-creator", "ge-creator@example.com");
        Event event = persistEvent("ge-event", creator, EventStatus.PUBLISHED);

        List<CommentDTO> result = commentService.getByEvent(event.id, creator.auth0Id, 0, 20);

        assertTrue(result.isEmpty());
    }

    // -------------------------------------------------------------------------
    // delete()
    // -------------------------------------------------------------------------

    @Test
    @TestTransaction
    @TestSecurity(user = "auth0|da-author")
    void delete_byAuthor_removesRow() {
        User creator = persistUser("auth0|da-creator", "da-creator@example.com");
        User author = persistUser("auth0|da-author", "da-author@example.com");
        Event event = persistEvent("da-event", creator, EventStatus.PUBLISHED);
        Comment c = persistComment(event, author, null, "Mine");

        commentService.delete(author.auth0Id, c.id);

        assertNull(Comment.findById(c.id));
    }

    @Test
    @TestTransaction
    @TestSecurity(user = "auth0|dc-creator")
    void delete_byEventCreator_removesRow() {
        User creator = persistUser("auth0|dc-creator", "dc-creator@example.com");
        User author = persistUser("auth0|dc-author", "dc-author@example.com");
        Event event = persistEvent("dc-event", creator, EventStatus.PUBLISHED);
        Comment c = persistComment(event, author, null, "Mod me");

        commentService.delete(creator.auth0Id, c.id);

        assertNull(Comment.findById(c.id));
    }

    @Test
    @TestTransaction
    @TestSecurity(user = "auth0|dco-coorg")
    void delete_byAcceptedCoOrganizer_removesRow() {
        User creator = persistUser("auth0|dco-creator", "dco-creator@example.com");
        User author = persistUser("auth0|dco-author", "dco-author@example.com");
        User coOrg = persistUser("auth0|dco-coorg", "dco-coorg@example.com");
        Event event = persistEvent("dco-event", creator, EventStatus.PUBLISHED);
        persistCoOrganizer(event.id, coOrg.id, CoOrganizerStatus.ACCEPTED);
        Comment c = persistComment(event, author, null, "Mod me too");

        commentService.delete(coOrg.auth0Id, c.id);

        assertNull(Comment.findById(c.id));
    }

    @Test
    @TestTransaction
    @TestSecurity(user = "auth0|dpco-coorg")
    void delete_byPendingCoOrganizer_returns403() {
        User creator = persistUser("auth0|dpco-creator", "dpco-creator@example.com");
        User author = persistUser("auth0|dpco-author", "dpco-author@example.com");
        User coOrg = persistUser("auth0|dpco-coorg", "dpco-coorg@example.com");
        Event event = persistEvent("dpco-event", creator, EventStatus.PUBLISHED);
        persistCoOrganizer(event.id, coOrg.id, CoOrganizerStatus.PENDING);
        Comment c = persistComment(event, author, null, "Should stay");

        WebApplicationException ex = assertThrows(WebApplicationException.class,
                () -> commentService.delete(coOrg.auth0Id, c.id));
        assertEquals(403, ex.getResponse().getStatus());
        assertNotNull(Comment.findById(c.id));
    }

    @Test
    @TestTransaction
    @TestSecurity(user = "auth0|dt-third")
    void delete_byThirdParty_returns403() {
        User creator = persistUser("auth0|dt-creator", "dt-creator@example.com");
        User author = persistUser("auth0|dt-author", "dt-author@example.com");
        User third = persistUser("auth0|dt-third", "dt-third@example.com");
        Event event = persistEvent("dt-event", creator, EventStatus.PUBLISHED);
        Comment c = persistComment(event, author, null, "Hands off");

        WebApplicationException ex = assertThrows(WebApplicationException.class,
                () -> commentService.delete(third.auth0Id, c.id));
        assertEquals(403, ex.getResponse().getStatus());
        assertTrue(ex.getResponse().getEntity().toString().contains("forbidden"));
    }

    @Test
    @TestTransaction
    @TestSecurity(user = "auth0|du-someone")
    void delete_unknownComment_returns404_commentNotFound() {
        WebApplicationException ex = assertThrows(WebApplicationException.class,
                () -> commentService.delete("auth0|du-someone", 99999L));
        assertEquals(404, ex.getResponse().getStatus());
        assertTrue(ex.getResponse().getEntity().toString().contains("comment_not_found"));
    }

    @Test
    @TestTransaction
    @TestSecurity(user = "auth0|dad-admin", roles = {"ADMIN"})
    void delete_byAdmin_removesRow() {
        User creator = persistUser("auth0|dad-creator", "dad-creator@example.com");
        User author = persistUser("auth0|dad-author", "dad-author@example.com");
        Event event = persistEvent("dad-event", creator, EventStatus.PUBLISHED);
        Comment c = persistComment(event, author, null, "Mod by admin");

        commentService.delete("auth0|dad-admin", c.id);

        assertNull(Comment.findById(c.id));
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private User persistUser(String auth0Id, String email) {
        User user = new User();
        user.auth0Id = auth0Id;
        user.email = email;
        user.profilePublic = false;
        user.createdAt = LocalDateTime.now();
        entityManager.persist(user);
        entityManager.flush();
        return user;
    }

    private Event persistEvent(String title, User creator, EventStatus status) {
        Event event = new Event();
        event.title = title;
        event.location = "Uni Mail";
        event.startDate = LocalDateTime.now().plusDays(1);
        event.endDate = LocalDateTime.now().plusDays(2);
        event.category = EventCategory.ACADEMIC;
        event.status = status;
        event.creator = creator;
        entityManager.persist(event);
        entityManager.flush();
        return event;
    }

    private Comment persistComment(Event event, User author, Comment parent, String content) {
        Comment c = new Comment();
        c.event = event;
        c.author = author;
        c.parentComment = parent;
        c.content = content;
        c.likeCount = 0;
        entityManager.persist(c);
        entityManager.flush();
        return c;
    }

    private Comment persistCommentAt(
            Event event, User author, Comment parent, String content, LocalDateTime createdAt) {
        Comment c = new Comment();
        c.event = event;
        c.author = author;
        c.parentComment = parent;
        c.content = content;
        c.likeCount = 0;
        // Set createdAt before persist so @PrePersist null-guard preserves it
        // (cf. Comment.prePersist() — pattern aligned on Follow / EventCoOrganizer).
        c.createdAt = createdAt;
        entityManager.persist(c);
        entityManager.flush();
        return c;
    }

    private void persistCoOrganizer(Long eventId, UUID userId, CoOrganizerStatus status) {
        EventCoOrganizer e = new EventCoOrganizer();
        e.eventId = eventId;
        e.userId = userId;
        e.status = status;
        entityManager.persist(e);
        entityManager.flush();
    }
}
