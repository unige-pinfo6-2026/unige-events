package ch.unige.events.event.attachment.dto;

/**
 * Internal carrier returned by {@code EventAttachmentService.download} to
 * feed the streaming response of {@code EventAttachmentResource.downloadAttachment}
 * — SCRUM-149 follow-up.
 *
 * <p>Not part of the public OpenAPI surface ; the HTTP response is a raw
 * binary stream with {@code Content-Disposition: attachment}.
 */
public record AttachmentDownload(byte[] bytes, String mimeType, String fileName) {
}
