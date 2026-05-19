package ch.unige.events.event.attachment.dto;

import ch.unige.events.event.attachment.entity.EventAttachment;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Wire-format projection of {@link EventAttachment} — SCRUM-148 (Décisions
 * Q + W).
 *
 * <p>Exposed publicly via the OpenAPI {@code AttachmentDTO} schema and
 * embedded asymmetrically into {@code EventDTO.attachments} (populated
 * only on {@code GET /events/{id}} — Décision Q).
 *
 * <p>{@code uploadedById} is exposed so the frontend can render an
 * "uploaded by X" hint ; the {@code uploadedAt} is the canonical
 * server-side timestamp (matches the DB column).
 *
 * <p><strong>SCRUM-149 follow-up</strong> — {@code downloadUrl} is the
 * same-origin endpoint the frontend should hit to actually download the
 * file. {@code fileUrl} stays as the raw internal S3 URL (still used by
 * the backend cleanup paths) but is no longer suitable for the browser
 * because the bucket isn't publicly routable (and even if it were, the
 * {@code <a download>} attribute is ignored cross-origin).
 */
public record AttachmentDTO(
        Long id,
        String fileName,
        String fileUrl,
        String downloadUrl,
        Long fileSize,
        String mimeType,
        UUID uploadedById,
        LocalDateTime uploadedAt
) {
    public static AttachmentDTO from(EventAttachment ea) {
        return new AttachmentDTO(
                ea.id,
                ea.fileName,
                ea.fileUrl,
                buildDownloadUrl(ea.eventId, ea.id),
                ea.fileSize,
                ea.mimeType,
                ea.uploadedById,
                ea.uploadedAt
        );
    }

    private static String buildDownloadUrl(Long eventId, Long attachmentId) {
        return "/api/events/" + eventId + "/attachments/" + attachmentId + "/download";
    }
}
