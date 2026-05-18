package ch.unige.events.shared.storage;

import java.util.Map;
import java.util.Set;

/**
 * MIME whitelist + extension map for event attachment uploads — SCRUM-148
 * (Décision S).
 *
 * <p>Consumed by {@code EventAttachmentService.upload} as the second
 * argument to {@link FileStorageService#saveFile} so the generic uploader
 * stays free of any domain-specific hardcoding.
 *
 * <p>This constants class deliberately mirrors the surface of
 * {@link ImageFormat} (which is the equivalent for images) so the two
 * categories of uploads share a uniform shape from the caller's POV. It is
 * <strong>not</strong> a drop-in replacement — images get strict
 * magic-number validation in {@link FileStorageService#saveImage} whereas
 * documents do not (cf. Décision S "acceptable risk" rationale).
 *
 * <p>The map size is 4 (PDF / DOC / DOCX / XLSX). The DB CHECK
 * {@code event_attachments_mime_check} mirrors this set as the last line
 * of defense (V13 migration).
 */
public final class DocumentFormat {

    /** Hard upper bound on document upload size — 10 MiB. */
    public static final long MAX_BYTES = 10L * 1024L * 1024L;

    /** MIME header → file extension (lowercase, leading dot). */
    public static final Map<String, String> MIME_TO_EXTENSION = Map.of(
            "application/pdf", ".pdf",
            "application/msword", ".doc",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document", ".docx",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", ".xlsx"
    );

    /**
     * Convenience accessor — equivalent to {@code MIME_TO_EXTENSION.keySet()}.
     * Kept as a separate constant so callers that only need the whitelist
     * (e.g. an OpenAPI enum generator) avoid pulling the extension map.
     */
    public static final Set<String> ALLOWED_MIMES = MIME_TO_EXTENSION.keySet();

    private DocumentFormat() {
        // utility class
    }
}
