package ch.unige.events.event.attachment.service;

import ch.unige.events.event.attachment.dto.AttachmentDTO;
import ch.unige.events.event.attachment.entity.EventAttachment;
import ch.unige.events.event.entity.Event;
import ch.unige.events.event.service.EventService;
import ch.unige.events.shared.domain.projections.CallerIdentity;
import ch.unige.events.shared.error.ApiErrorResponse;
import ch.unige.events.shared.storage.DocumentFormat;
import ch.unige.events.shared.storage.FileStorageService;

import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.ForbiddenException;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.jboss.resteasy.reactive.multipart.FileUpload;

import java.util.List;
import java.util.UUID;

/**
 * SCRUM-148 — orchestrator for {@link EventAttachment} CRUD with the
 * cascade permissions of Décision V (creator / co-org ACCEPTED / admin /
 * uploader-for-DELETE).
 *
 * <p>Layout : new sub-package {@code event/attachment/service} (Décision R).
 * {@link EventService} is touched only for the cascade DELETE in
 * Décision T (étape 19) and for the public permission helper
 * {@link EventService#isCreatorOrAcceptedCoOrganizer}.
 *
 * <p>Anti-oracle ISSUE-92 (Décision V) :
 * <ul>
 *   <li>POST on a non-existent event → 404 (not 403).</li>
 *   <li>DELETE on a {@code attachmentId} that exists but belongs to another
 *       event → 404 (path mismatch — never reveals existence of another
 *       event's row).</li>
 * </ul>
 *
 * <p>Cap of 5 attachments per event (Décision V) is enforced via
 * {@link EventAttachment#countByEvent} ; over the cap surfaces as 422
 * {@code attachment_limit_exceeded}.
 */
@ApplicationScoped
public class EventAttachmentService {

    private static final String FOLDER = "event-attachments";
    private static final int MAX_ATTACHMENTS_PER_EVENT = 5;

    private final EventService eventService;
    private final FileStorageService fileStorageService;
    private final CallerIdentity callerIdentity;

    @Inject
    public EventAttachmentService(EventService eventService,
                                  FileStorageService fileStorageService,
                                  CallerIdentity callerIdentity) {
        this.eventService = eventService;
        this.fileStorageService = fileStorageService;
        this.callerIdentity = callerIdentity;
    }

    /**
     * Uploads a new attachment for the given event. Permission cascade
     * follows SCRUM-136 (creator OR co-org ACCEPTED OR admin).
     */
    @Transactional
    public AttachmentDTO upload(Long eventId, FileUpload fileUpload, boolean isAdmin) {
        Event event = Event.<Event>findByIdOptional(eventId)
                .orElseThrow(NotFoundException::new);

        UUID callerUuid = callerIdentity.requireUuid();
        if (callerUuid == null) {
            throw new NotFoundException("User profile not found — call GET /users/me first");
        }
        if (!isAdmin && !eventService.isCreatorOrAcceptedCoOrganizer(event, callerUuid)) {
            throw new ForbiddenException(
                    "Only the event creator, an accepted co-organizer or an admin can upload attachments");
        }

        long current = EventAttachment.countByEvent(eventId);
        if (current >= MAX_ATTACHMENTS_PER_EVENT) {
            throw unprocessable("attachment_limit_exceeded",
                    "An event can have at most " + MAX_ATTACHMENTS_PER_EVENT + " attachments.");
        }

        // FileStorageService.saveFile enforces size + MIME ; 422 on failure
        // bubbles directly back to the resource layer.
        String fileUrl = fileStorageService.saveFile(
                fileUpload,
                FOLDER,
                DocumentFormat.MAX_BYTES,
                DocumentFormat.MIME_TO_EXTENSION,
                null
        );

        EventAttachment row = new EventAttachment();
        row.eventId = eventId;
        row.fileName = fileUpload.fileName();
        row.fileUrl = fileUrl;
        row.fileSize = fileUpload.size();
        row.mimeType = fileUpload.contentType();
        row.uploadedById = callerUuid;
        row.persist();
        return AttachmentDTO.from(row);
    }

    /**
     * Deletes an attachment after enforcing the cascade permissions
     * (Décision V) :
     * <ul>
     *   <li>creator / co-org ACCEPTED / admin OR</li>
     *   <li>the uploader of this specific attachment (a co-org can drop
     *       their own upload even after their ACCEPTED status changes,
     *       and a former co-org can clean up their files).</li>
     * </ul>
     *
     * <p>The path mismatch case ({@code attachmentId} exists but belongs to
     * another event) surfaces as 404 — never 403 — to avoid leaking the
     * existence of cross-event rows (anti-oracle ISSUE-92).
     *
     * <p>S3 cleanup is best-effort and hors-transaction (see
     * {@link FileStorageService#deleteObject(String)}). Failures emit a
     * WARN log via the storage layer ; the DB row is already gone.
     */
    @Transactional
    public void delete(Long eventId, Long attachmentId, boolean isAdmin) {
        EventAttachment attachment = EventAttachment.<EventAttachment>findByIdOptional(attachmentId)
                .orElseThrow(NotFoundException::new);

        // Anti-oracle path mismatch — pretend the attachment does not exist
        // rather than reveal that it belongs to another event.
        if (!attachment.eventId.equals(eventId)) {
            throw new NotFoundException();
        }

        UUID callerUuid = callerIdentity.requireUuid();
        if (callerUuid == null) {
            throw new NotFoundException("User profile not found — call GET /users/me first");
        }

        Event event = Event.<Event>findByIdOptional(eventId)
                .orElseThrow(NotFoundException::new);

        boolean isUploader = callerUuid.equals(attachment.uploadedById);
        boolean isOrganizer = eventService.isCreatorOrAcceptedCoOrganizer(event, callerUuid);
        if (!isAdmin && !isOrganizer && !isUploader) {
            throw new ForbiddenException(
                    "Only the event creator, an accepted co-organizer, the original uploader or an admin "
                            + "can delete this attachment");
        }

        // Capture the URL before the row is purged so the hors-tx S3 cleanup
        // still has the key.
        String fileUrl = attachment.fileUrl;
        attachment.delete();

        // Best-effort cleanup — defense-in-depth try/catch around the storage
        // call. FileStorageService.deleteObject already swallows S3 transients
        // with a WARN log, but mocking it in tests (or a future refactor that
        // re-throws) would otherwise bubble an exception that rolls back the
        // surrounding @Transactional. The DB row is already gone ; orphan
        // objects are accepted (cf. devops-handoff S10+ lifecycle policy).
        try {
            fileStorageService.deleteObject(fileUrl);
        } catch (Exception e) {
            Log.warnf(e,
                    "[S3_CLEANUP_FAIL_event_attachment] event=%d attachment=%d url=%s — orphan object will remain",
                    eventId, attachmentId, fileUrl);
        }
    }

    /**
     * Lists the attachments for the given event sorted by upload time
     * ASC (display order). Returns an empty list when the event has no
     * attachments — the caller decides whether to expose null (Décision Q
     * asymmetry on {@code EventDTO}) or an empty list.
     */
    public List<AttachmentDTO> getByEvent(Long eventId) {
        return EventAttachment.findByEvent(eventId).stream()
                .map(AttachmentDTO::from)
                .toList();
    }

    private static WebApplicationException unprocessable(String error, String message) {
        return new WebApplicationException(
                Response.status(422)
                        .entity(new ApiErrorResponse(error, message))
                        .type(MediaType.APPLICATION_JSON_TYPE)
                        .build());
    }
}
