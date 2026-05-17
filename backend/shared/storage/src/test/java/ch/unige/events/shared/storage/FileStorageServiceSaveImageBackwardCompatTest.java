package ch.unige.events.shared.storage;

import org.jboss.resteasy.reactive.multipart.FileUpload;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import software.amazon.awssdk.services.s3.S3Client;

import java.io.IOException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * SCRUM-148 — sentinel locking in the backward-compat contract of
 * {@link FileStorageService#saveImage} after the addition of the generic
 * {@code saveFile} method (Décision S).
 *
 * <p>The strict magic-number check on images must remain untouched : a
 * regression here re-opens the SVG-as-PNG XSS vector (pentest 2026-04-17
 * finding 4.13). Therefore this test :
 * <ol>
 *   <li>asserts {@link ImageFormat#MIME_TO_EXTENSION} still contains the
 *       four pre-existing image MIMEs and nothing else (no leak from the
 *       new {@link DocumentFormat} whitelist) ;</li>
 *   <li>asserts the public {@code saveImage(FileUpload, String, long, String)}
 *       method signature is preserved (reflection — a rename or extra
 *       parameter would silently break user-service avatar/banner callers) ;</li>
 *   <li>exercises the magic-number check on a renamed payload (claims
 *       {@code image/png} but body bytes are a PDF) and verifies the
 *       service still throws {@link InvalidFileTypeException}.</li>
 * </ol>
 *
 * <p>If this test fails, callers in user-service (avatar/banner) and
 * event-service (banner image) will break — the breakage is intentionally
 * gated here so a Sonar reviewer catches it before merge.
 */
class FileStorageServiceSaveImageBackwardCompatTest {

    @Test
    void imageMimeToExtension_containsExactlyTheFourPreExistingEntries() {
        Set<String> expected = Set.of("image/jpeg", "image/png", "image/webp", "image/gif");
        assertEquals(expected, ImageFormat.MIME_TO_EXTENSION.keySet(),
                "ImageFormat.MIME_TO_EXTENSION must NOT gain document MIMEs ; the magic-number "
                        + "check is image-only by design");
    }

    @Test
    void imageMimeToExtension_isImmutable() {
        // Defensive : Map.of() returns unmodifiable. A future refactor that
        // switches to a HashMap would silently allow runtime mutation and
        // is what this sentinel catches.
        assertThrows(UnsupportedOperationException.class,
                () -> ImageFormat.MIME_TO_EXTENSION.put("application/pdf", ".pdf"));
    }

    @Test
    void saveImage_publicSignaturePreserved() throws NoSuchMethodException {
        Method m = FileStorageService.class.getMethod(
                "saveImage", FileUpload.class, String.class, long.class, String.class);
        assertNotNull(m);
        assertTrue(Modifier.isPublic(m.getModifiers()),
                "saveImage(FileUpload, String, long, String) must remain public — "
                        + "user-service avatar + banner callers depend on this signature");
        assertEquals(String.class, m.getReturnType(),
                "saveImage must still return the absolute S3 URL");
    }

    @Test
    void saveImage_pdfBytesClaimingPngMime_stillRejected(@TempDir Path tmp) throws IOException {
        // Magic-number primitive : the file is a PDF (starts with %PDF) but
        // claims image/png MIME. The strict check in ImageFormat.matches
        // must reject this even if saveFile was added in this commit.
        Path file = tmp.resolve("fake.png");
        Files.write(file, "%PDF-1.4\nfake".getBytes());

        FileUpload upload = mock(FileUpload.class);
        when(upload.contentType()).thenReturn("image/png");
        when(upload.uploadedFile()).thenReturn(file);
        when(upload.size()).thenReturn((long) Files.size(file));

        S3Client s3 = mock(S3Client.class);
        StorageConfig config = mock(StorageConfig.class);
        when(config.s3Bucket()).thenReturn("bucket");
        when(config.s3Url()).thenReturn("http://s3");
        FileStorageService svc = new FileStorageService(s3, config);

        assertThrows(InvalidFileTypeException.class,
                () -> svc.saveImage(upload, "folder", FileStorageService.MAX_AVATAR_BYTES, null));
    }
}
