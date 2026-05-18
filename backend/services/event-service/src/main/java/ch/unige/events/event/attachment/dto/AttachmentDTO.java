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
 */
public record AttachmentDTO(
        Long id,
        String fileName,
        String fileUrl,
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
                ea.fileSize,
                ea.mimeType,
                ea.uploadedById,
                ea.uploadedAt
        );
    }
}
