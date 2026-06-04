package ch.unige.events.shared.storage;

import jakarta.ws.rs.WebApplicationException;
import org.jboss.resteasy.reactive.multipart.FileUpload;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * SCRUM-148 — coverage for {@link FileStorageService#saveFile} (Décision S).
 *
 * <p>Standalone Mockito tests (no {@code @QuarkusTest}) mirror the existing
 * {@link FileStorageServiceTest} pattern in this module — the class under
 * test is a CDI-friendly POJO directly instantiable with a mock
 * {@link S3Client} + {@link StorageConfig}. Coverage feeds into Sonar via
 * the standard surefire path.
 */
class FileStorageServiceSaveFileTest {

    private static final String FOLDER = "event-attachments";

    private static FileStorageService service(S3Client s3) {
        StorageConfig config = mock(StorageConfig.class);
        when(config.s3Bucket()).thenReturn("bucket");
        when(config.s3Url()).thenReturn("http://s3");
        return new FileStorageService(s3, config);
    }

    private static FileUpload upload(String mime, long size, Path tmp, String body) throws IOException {
        Path file = tmp.resolve("payload.bin");
        Files.write(file, body.getBytes());
        FileUpload f = mock(FileUpload.class);
        when(f.contentType()).thenReturn(mime);
        when(f.size()).thenReturn(size);
        when(f.uploadedFile()).thenReturn(file);
        return f;
    }

    @Test
    void saveFile_validPdf_returnsAbsoluteS3Url(@TempDir Path tmp) throws IOException {
        S3Client s3 = mock(S3Client.class);
        FileUpload pdf = upload("application/pdf", 1024L, tmp, "%PDF-1.4 fake");

        String url = service(s3).saveFile(pdf, FOLDER, DocumentFormat.MAX_BYTES,
                DocumentFormat.MIME_TO_EXTENSION, null);

        assertTrue(url.startsWith("http://s3/bucket/" + FOLDER + "/"),
                "saveFile must return the absolute S3 URL — got " + url);
        assertTrue(url.endsWith(".pdf"),
                "extension must mirror the mime-whitelist value for application/pdf — got " + url);
        verify(s3).putObject(any(PutObjectRequest.class), any(software.amazon.awssdk.core.sync.RequestBody.class));
    }

    @Test
    void saveFile_oversized_throws422(@TempDir Path tmp) throws IOException {
        S3Client s3 = mock(S3Client.class);
        FileUpload oversize = upload("application/pdf",
                DocumentFormat.MAX_BYTES + 1, tmp, "fake");

        WebApplicationException ex = assertThrows(WebApplicationException.class,
                () -> service(s3).saveFile(oversize, FOLDER, DocumentFormat.MAX_BYTES,
                        DocumentFormat.MIME_TO_EXTENSION, null));
        assertEquals(422, ex.getResponse().getStatus());
        verify(s3, never()).putObject(any(PutObjectRequest.class),
                any(software.amazon.awssdk.core.sync.RequestBody.class));
    }

    @Test
    void saveFile_mimeNotAllowed_throws422(@TempDir Path tmp) throws IOException {
        S3Client s3 = mock(S3Client.class);
        // SCRUM-149 — image/png is now in the documents whitelist, so use
        // an unrelated MIME (zip) to exercise the rejection branch.
        FileUpload zip = upload("application/zip", 1024L, tmp, "fake zip");

        WebApplicationException ex = assertThrows(WebApplicationException.class,
                () -> service(s3).saveFile(zip, FOLDER, DocumentFormat.MAX_BYTES,
                        DocumentFormat.MIME_TO_EXTENSION, null));
        assertEquals(422, ex.getResponse().getStatus());
        verify(s3, never()).putObject(any(PutObjectRequest.class),
                any(software.amazon.awssdk.core.sync.RequestBody.class));
    }

    @Test
    void saveFile_nullMime_throws422(@TempDir Path tmp) throws IOException {
        S3Client s3 = mock(S3Client.class);
        FileUpload anonymous = upload(null, 1024L, tmp, "fake");

        WebApplicationException ex = assertThrows(WebApplicationException.class,
                () -> service(s3).saveFile(anonymous, FOLDER, DocumentFormat.MAX_BYTES,
                        DocumentFormat.MIME_TO_EXTENSION, null));
        assertEquals(422, ex.getResponse().getStatus());
    }

    @Test
    void saveFile_normalizesMimeCase(@TempDir Path tmp) throws IOException {
        // Browsers occasionally send Title-Case MIME headers — must not 422.
        S3Client s3 = mock(S3Client.class);
        FileUpload pdf = upload("Application/PDF", 1024L, tmp, "%PDF-1.4");

        String url = service(s3).saveFile(pdf, FOLDER, DocumentFormat.MAX_BYTES,
                DocumentFormat.MIME_TO_EXTENSION, null);
        assertTrue(url.endsWith(".pdf"));
    }

    @Test
    void saveFile_stripsMimeParameters(@TempDir Path tmp) throws IOException {
        // The "; charset=binary" suffix is legal but must not break the
        // whitelist lookup.
        S3Client s3 = mock(S3Client.class);
        FileUpload docx = upload(
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document; charset=binary",
                1024L, tmp, "docx-body");

        String url = service(s3).saveFile(docx, FOLDER, DocumentFormat.MAX_BYTES,
                DocumentFormat.MIME_TO_EXTENSION, null);
        assertTrue(url.endsWith(".docx"));
    }

    @Test
    void saveFile_oldUrlInSameFolder_isCleanedUp(@TempDir Path tmp) throws IOException {
        S3Client s3 = mock(S3Client.class);
        FileUpload pdf = upload("application/pdf", 1024L, tmp, "%PDF-1.4");

        String oldUrl = "http://s3/bucket/" + FOLDER + "/previous.pdf";
        service(s3).saveFile(pdf, FOLDER, DocumentFormat.MAX_BYTES,
                DocumentFormat.MIME_TO_EXTENSION, oldUrl);

        verify(s3, atLeastOnce()).deleteObject(any(DeleteObjectRequest.class));
    }

    @Test
    void saveFile_oldUrlInDifferentFolder_isNotDeleted(@TempDir Path tmp) throws IOException {
        // Folder mismatch — the 2-arg deleteObject(...) explicitly guards
        // against cross-folder cleanup so a forged oldUrl can't be exploited
        // to delete a banner from the avatars folder.
        S3Client s3 = mock(S3Client.class);
        FileUpload pdf = upload("application/pdf", 1024L, tmp, "%PDF-1.4");

        String oldUrl = "http://s3/bucket/users/avatars/foo.png";
        service(s3).saveFile(pdf, FOLDER, DocumentFormat.MAX_BYTES,
                DocumentFormat.MIME_TO_EXTENSION, oldUrl);

        verify(s3, never()).deleteObject(any(DeleteObjectRequest.class));
    }

    @Test
    void saveFile_s3PutFails_throws503(@TempDir Path tmp) throws IOException {
        S3Client s3 = mock(S3Client.class);
        when(s3.putObject(any(PutObjectRequest.class), any(software.amazon.awssdk.core.sync.RequestBody.class)))
                .thenThrow(new RuntimeException("S3 down"));
        FileUpload pdf = upload("application/pdf", 1024L, tmp, "fake");

        WebApplicationException ex = assertThrows(WebApplicationException.class,
                () -> service(s3).saveFile(pdf, FOLDER, DocumentFormat.MAX_BYTES,
                        DocumentFormat.MIME_TO_EXTENSION, null));
        assertEquals(503, ex.getResponse().getStatus());
    }

    @Test
    void deleteObject_oneArg_inBucket_invokesS3() {
        S3Client s3 = mock(S3Client.class);
        String url = "http://s3/bucket/" + FOLDER + "/abc.pdf";

        service(s3).deleteObject(url);

        verify(s3).deleteObject(any(DeleteObjectRequest.class));
    }

    @Test
    void deleteObject_oneArg_outsideBucket_silentlyNoOps() {
        // Defense against a forged DB row pointing outside our bucket — the
        // cascade cleanup must never reach across buckets.
        S3Client s3 = mock(S3Client.class);
        service(s3).deleteObject("http://other-cloud/somewhere/file.pdf");
        verify(s3, never()).deleteObject(any(DeleteObjectRequest.class));
    }

    @Test
    void deleteObject_oneArg_nullOrBlank_silentlyNoOps() {
        S3Client s3 = mock(S3Client.class);
        service(s3).deleteObject(null);
        service(s3).deleteObject("");
        service(s3).deleteObject("   ");
        verify(s3, never()).deleteObject(any(DeleteObjectRequest.class));
    }

    @Test
    void deleteObject_oneArg_s3Failure_isSwallowed() {
        // Best-effort contract — caller (EventService.delete cascade) doesn't
        // want a thrown S3 error to roll back the surrounding tx.
        S3Client s3 = mock(S3Client.class);
        when(s3.deleteObject(any(DeleteObjectRequest.class)))
                .thenThrow(new RuntimeException("transient S3 failure"));
        service(s3).deleteObject("http://s3/bucket/" + FOLDER + "/orphan.pdf");
        // No exception bubbled, yet the failing S3 delete was still attempted.
        verify(s3).deleteObject(any(DeleteObjectRequest.class));
    }

    @Test
    void saveFile_acceptsTinyEdgeOfMaxBytes(@TempDir Path tmp) throws IOException {
        S3Client s3 = mock(S3Client.class);
        FileUpload exactlyMax = upload("application/pdf",
                DocumentFormat.MAX_BYTES, tmp, "%PDF-1.4");
        // <= maxBytes must be accepted ; the strict inequality kicks in at
        // maxBytes+1 (asserted in saveFile_oversized_throws422).
        String url = service(s3).saveFile(exactlyMax, FOLDER, DocumentFormat.MAX_BYTES,
                DocumentFormat.MIME_TO_EXTENSION, null);
        assertTrue(url.endsWith(".pdf"));
    }

    @Test
    void saveFile_customMimeWhitelist_isHonoured(@TempDir Path tmp) throws IOException {
        // The whitelist is a caller parameter — future callers can pass a
        // different map without modifying saveFile.
        S3Client s3 = mock(S3Client.class);
        Map<String, String> csvOnly = Map.of("text/csv", ".csv");
        FileUpload csv = upload("text/csv", 1024L, tmp, "a,b,c");

        String url = service(s3).saveFile(csv, "exports", 4096L, csvOnly, null);
        assertTrue(url.endsWith(".csv"));
    }
}
