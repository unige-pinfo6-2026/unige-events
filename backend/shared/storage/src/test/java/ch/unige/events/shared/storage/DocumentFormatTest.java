package ch.unige.events.shared.storage;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * SCRUM-148 — sanity tests for {@link DocumentFormat} (Décision S).
 *
 * <p>The MIME whitelist + extension map drives the validation logic of
 * {@code FileStorageService.saveFile}. A regression here cascades into a
 * miss-classified DB CHECK or an out-of-sync OpenAPI enum, so the
 * mapping is asserted exhaustively.
 */
class DocumentFormatTest {

    @Test
    void mimeToExtension_pdf_mapsToPdfExt() {
        assertEquals(".pdf", DocumentFormat.MIME_TO_EXTENSION.get("application/pdf"));
    }

    @Test
    void mimeToExtension_doc_mapsToDocExt() {
        assertEquals(".doc", DocumentFormat.MIME_TO_EXTENSION.get("application/msword"));
    }

    @Test
    void mimeToExtension_docx_mapsToDocxExt() {
        assertEquals(".docx", DocumentFormat.MIME_TO_EXTENSION.get(
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document"));
    }

    @Test
    void mimeToExtension_xlsx_mapsToXlsxExt() {
        assertEquals(".xlsx", DocumentFormat.MIME_TO_EXTENSION.get(
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"));
    }

    @Test
    void allowedMimes_containsExactlyFourEntries() {
        assertEquals(4, DocumentFormat.ALLOWED_MIMES.size());
    }

    @Test
    void allowedMimes_isViewOfMimeToExtensionKeys() {
        // The constant exists so callers needing only the whitelist don't
        // pull the extension map. They must stay in sync.
        assertSame(DocumentFormat.MIME_TO_EXTENSION.keySet(), DocumentFormat.ALLOWED_MIMES);
    }

    @Test
    void maxBytes_is10MiB() {
        assertEquals(10L * 1024 * 1024, DocumentFormat.MAX_BYTES);
    }

    @Test
    void mimeToExtension_isImmutable() {
        assertThrows(UnsupportedOperationException.class,
                () -> DocumentFormat.MIME_TO_EXTENSION.put("application/foo", ".foo"));
    }

    @Test
    void allowedMimes_isImmutable() {
        // Map.of() returns an unmodifiable map ; mutating keySet() must
        // bubble the contract up to the Set view.
        assertThrows(UnsupportedOperationException.class,
                () -> DocumentFormat.ALLOWED_MIMES.add("image/png"));
    }

    @Test
    void allowedMimes_excludesImageMimes() {
        // Defense-in-depth — image MIMEs belong to saveImage / ImageFormat
        // not saveFile / DocumentFormat. Conflation would let a renamed
        // .png bypass the magic-number check.
        assertTrue(DocumentFormat.ALLOWED_MIMES.stream().noneMatch(mime -> mime.startsWith("image/")));
    }
}
