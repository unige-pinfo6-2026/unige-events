package ch.unige.events.service;

import ch.unige.events.dto.comment.CommentDTO;
import ch.unige.events.dto.comment.CreateCommentRequest;
import io.quarkus.test.Mock;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.NotFoundException;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Test mock for {@link CommentService} — pattern aligné sur
 * {@link ReportServiceMock} avec des flags volatile pour piloter les rejets
 * attendus dans les tests Resource.
 */
@Mock
@ApplicationScoped
public class CommentServiceMock extends CommentService {

    public static volatile boolean forceNotFoundOnEvent = false;
    public static volatile boolean forceCannotCommentDraft = false;
    public static volatile boolean forceCannotCommentCancelled = false;
    public static volatile boolean forceCannotCommentExpired = false;
    public static volatile boolean forceParentNotFound = false;
    public static volatile boolean forceParentNotInEvent = false;
    public static volatile boolean forceRepliesTooDeep = false;
    public static volatile boolean forceCommentNotFound = false;
    public static volatile boolean forceForbiddenOnDelete = false;

    /**
     * Fixtures for {@link #getByEvent} — instance field, cohérent avec les autres
     * mocks du projet (ReportServiceMock.reportsFixture, FollowServiceMock.rowsById).
     * Seuls les flags `force*` restent {@code static volatile} pour rester pilotables
     * depuis les tests Resource. Cf. review Copilot (PR #156, comment 3207926394).
     */
    private final List<CommentDTO> getByEventFixture = new ArrayList<>();
    private final AtomicLong idSequence = new AtomicLong(1);

    public void reset() {
        forceNotFoundOnEvent = false;
        forceCannotCommentDraft = false;
        forceCannotCommentCancelled = false;
        forceCannotCommentExpired = false;
        forceParentNotFound = false;
        forceParentNotInEvent = false;
        forceRepliesTooDeep = false;
        forceCommentNotFound = false;
        forceForbiddenOnDelete = false;
        getByEventFixture.clear();
        idSequence.set(1);
    }

    public void seedTopLevel(CommentDTO dto) {
        getByEventFixture.add(dto);
    }

    @Override
    public CommentDTO post(String auth0Id, Long eventId, CreateCommentRequest request) {
        if (forceNotFoundOnEvent) throw new NotFoundException();
        if (forceCannotCommentDraft) throw badRequest("cannot_comment_draft_event",
                "An event must be PUBLISHED before it accepts comments.");
        if (forceCannotCommentCancelled) throw badRequest("cannot_comment_cancelled_event",
                "Cannot comment a cancelled event.");
        if (forceCannotCommentExpired) throw badRequest("cannot_comment_expired_event",
                "Cannot comment an expired event.");
        if (forceParentNotFound) throw notFound("parent_comment_not_found",
                "The parent comment does not exist.");
        if (forceParentNotInEvent) throw unprocessable("parent_comment_not_in_event",
                "The parent comment belongs to a different event.");
        if (forceRepliesTooDeep) throw unprocessable("replies_too_deep",
                "Replies are limited to one level of depth.");
        return new CommentDTO(
                idSequence.getAndIncrement(),
                request.content() != null ? request.content().strip() : "",
                UUID.randomUUID(),
                "Mock author",
                null,
                false,
                0,
                false,
                LocalDateTime.now(),
                request.parentCommentId(),
                List.of()
        );
    }

    @Override
    public List<CommentDTO> getByEvent(Long eventId, String auth0Id, int page, int size) {
        if (forceNotFoundOnEvent) throw new NotFoundException();
        return List.copyOf(getByEventFixture);
    }

    @Override
    public void delete(String auth0Id, Long commentId) {
        if (forceCommentNotFound) throw notFound("comment_not_found",
                "The comment does not exist.");
        if (forceForbiddenOnDelete) throw forbidden("forbidden",
                "Only the author, an event organizer or an admin can delete this comment.");
    }
}
