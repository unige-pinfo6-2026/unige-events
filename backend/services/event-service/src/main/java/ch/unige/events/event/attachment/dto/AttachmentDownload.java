package ch.unige.events.event.attachment.dto;

import java.util.Arrays;
import java.util.Objects;

/**
 * Internal carrier returned by {@code EventAttachmentService.download} to
 * feed the streaming response of {@code EventAttachmentResource.downloadAttachment}
 * — SCRUM-149 follow-up.
 *
 * <p>Not part of the public OpenAPI surface ; the HTTP response is a raw
 * binary stream with {@code Content-Disposition: attachment}.
 *
 * <p>{@code equals} / {@code hashCode} / {@code toString} are overridden
 * by hand (Sonar java:S6218) because the default record accessors compare
 * the {@code byte[]} field by reference, which would silently break any
 * future use of this carrier as a map key or in assertions. The natural
 * semantics here is value equality (same bytes + same metadata), so the
 * overrides delegate to {@link Arrays#equals(byte[], byte[])} and
 * {@link Arrays#hashCode(byte[])} for the array component.
 */
public record AttachmentDownload(byte[] bytes, String mimeType, String fileName) {

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof AttachmentDownload other)) return false;
        return Objects.equals(mimeType, other.mimeType)
                && Objects.equals(fileName, other.fileName)
                && Arrays.equals(bytes, other.bytes);
    }

    @Override
    public int hashCode() {
        int result = Objects.hash(mimeType, fileName);
        result = 31 * result + Arrays.hashCode(bytes);
        return result;
    }

    @Override
    public String toString() {
        // Omit the raw byte payload — printing megabytes of binary in a
        // log line is useless and dangerous. Surface its length instead.
        return "AttachmentDownload[mimeType=" + mimeType
                + ", fileName=" + fileName
                + ", bytes.length=" + (bytes == null ? 0 : bytes.length)
                + "]";
    }
}
