package ch.unige.events.shared.storage;

import jakarta.ws.rs.InternalServerErrorException;
import org.jboss.resteasy.reactive.multipart.FileUpload;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FileStorageServiceTest {

    private static final String AVATARS_FOLDER = "users/avatars";

    private static FileStorageService service(S3Client s3) {
        StorageConfig config = mock(StorageConfig.class);
        when(config.s3Bucket()).thenReturn("bucket");
        when(config.s3Url()).thenReturn("http://s3");
        return new FileStorageService(s3, config);
    }

    // --- saveImage: contentType validation ---

    @Test
    void saveImage_nullContentType_throwsInvalidFileType() {
        FileUpload upload = mock(FileUpload.class);
        when(upload.contentType()).thenReturn(null);
        FileStorageService svc = service(mock(S3Client.class));

        assertThrows(InvalidFileTypeException.class,
                () -> svc.saveImage(upload, "folder", FileStorageService.MAX_AVATAR_BYTES, null));
    }

    @Test
    void saveImage_svgContentType_throwsInvalidFileType() {
        FileUpload upload = mock(FileUpload.class);
        when(upload.contentType()).thenReturn("image/svg+xml");
        FileStorageService svc = service(mock(S3Client.class));

        assertThrows(InvalidFileTypeException.class,
                () -> svc.saveImage(upload, "folder", FileStorageService.MAX_AVATAR_BYTES, null));
    }

    // --- saveImage: MIME normalization (case + parameters) ---

    @Test
    void saveImage_uppercaseMime_accepted(@TempDir Path tmp) throws IOException {
        Path file = tmp.resolve("img.jpg");
        Files.write(file, new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0, 0, 0, 0, 0, 0, 0, 0, 0});

        FileUpload upload = mock(FileUpload.class);
        when(upload.contentType()).thenReturn("IMAGE/JPEG");
        when(upload.uploadedFile()).thenReturn(file);
        when(upload.size()).thenReturn(12L);

        S3Client s3 = mock(S3Client.class);
        String url = service(s3).saveImage(upload, "folder", FileStorageService.MAX_AVATAR_BYTES, null);

        assertTrue(url.endsWith(".jpg"));
    }

    @Test
    void saveImage_mixedCaseMime_accepted(@TempDir Path tmp) throws IOException {
        Path file = tmp.resolve("img.png");
        Files.write(file, new byte[]{(byte) 0x89, 'P', 'N', 'G', (byte) 0x0D, (byte) 0x0A, (byte) 0x1A, (byte) 0x0A, 0, 0, 0, 0});

        FileUpload upload = mock(FileUpload.class);
        when(upload.contentType()).thenReturn("Image/PNG");
        when(upload.uploadedFile()).thenReturn(file);
        when(upload.size()).thenReturn(12L);

        S3Client s3 = mock(S3Client.class);
        String url = service(s3).saveImage(upload, "folder", FileStorageService.MAX_AVATAR_BYTES, null);

        assertTrue(url.endsWith(".png"));
    }

    @Test
    void saveImage_mimeWithCharsetParam_accepted(@TempDir Path tmp) throws IOException {
        Path file = tmp.resolve("img.jpg");
        Files.write(file, new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0, 0, 0, 0, 0, 0, 0, 0, 0});

        FileUpload upload = mock(FileUpload.class);
        when(upload.contentType()).thenReturn("image/jpeg; charset=utf-8");
        when(upload.uploadedFile()).thenReturn(file);
        when(upload.size()).thenReturn(12L);

        S3Client s3 = mock(S3Client.class);
        String url = service(s3).saveImage(upload, "folder", FileStorageService.MAX_AVATAR_BYTES, null);

        assertTrue(url.endsWith(".jpg"));
    }

    @Test
    void saveImage_uppercaseSvg_rejected() {
        FileUpload upload = mock(FileUpload.class);
        when(upload.contentType()).thenReturn("IMAGE/SVG+XML");
        FileStorageService svc = service(mock(S3Client.class));

        assertThrows(InvalidFileTypeException.class,
                () -> svc.saveImage(upload, "folder", FileStorageService.MAX_AVATAR_BYTES, null));
    }

    @Test
    void saveImage_mimeWithWhitespace_accepted(@TempDir Path tmp) throws IOException {
        Path file = tmp.resolve("img.gif");
        Files.write(file, new byte[]{'G', 'I', 'F', '8', '9', 'a', 0, 0, 0, 0, 0, 0});

        FileUpload upload = mock(FileUpload.class);
        when(upload.contentType()).thenReturn("  image/gif  ");
        when(upload.uploadedFile()).thenReturn(file);
        when(upload.size()).thenReturn(12L);

        S3Client s3 = mock(S3Client.class);
        String url = service(s3).saveImage(upload, "folder", FileStorageService.MAX_AVATAR_BYTES, null);

        assertTrue(url.endsWith(".gif"));
    }

    // --- saveImage: magic bytes mismatch ---

    @Test
    void saveImage_pngContentTypeWithJpegBytes_throwsInvalidFileType(@TempDir Path tmp) throws IOException {
        Path file = tmp.resolve("fake.png");
        Files.write(file, new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0, 0, 0, 0, 0, 0, 0, 0, 0});

        FileUpload upload = mock(FileUpload.class);
        when(upload.contentType()).thenReturn("image/png");
        when(upload.uploadedFile()).thenReturn(file);
        when(upload.size()).thenReturn(12L);
        FileStorageService svc = service(mock(S3Client.class));

        assertThrows(InvalidFileTypeException.class,
                () -> svc.saveImage(upload, "folder", FileStorageService.MAX_AVATAR_BYTES, null));
    }

    // --- saveImage: IOException when reading file ---

    @Test
    void saveImage_unreadableFile_throwsInternalServerError() {
        FileUpload upload = mock(FileUpload.class);
        when(upload.contentType()).thenReturn("image/jpeg");
        when(upload.uploadedFile()).thenReturn(Path.of("/nonexistent/missing.jpg"));
        when(upload.size()).thenReturn(12L);
        FileStorageService svc = service(mock(S3Client.class));

        assertThrows(InternalServerErrorException.class,
                () -> svc.saveImage(upload, "folder", FileStorageService.MAX_AVATAR_BYTES, null));
    }

    // --- saveImage: S3 putObject failure ---

    @Test
    void saveImage_s3PutFails_throwsInternalServerError(@TempDir Path tmp) throws IOException {
        Path file = tmp.resolve("img.jpg");
        Files.write(file, new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0, 0, 0, 0, 0, 0, 0, 0, 0});

        FileUpload upload = mock(FileUpload.class);
        when(upload.contentType()).thenReturn("image/jpeg");
        when(upload.uploadedFile()).thenReturn(file);
        when(upload.size()).thenReturn(12L);

        S3Client s3 = mock(S3Client.class);
        when(s3.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                .thenThrow(new RuntimeException("S3 unavailable"));
        FileStorageService svc = service(s3);

        assertThrows(InternalServerErrorException.class,
                () -> svc.saveImage(upload, "folder", FileStorageService.MAX_AVATAR_BYTES, null));
    }

    // --- saveImage: happy path ---

    @Test
    void saveImage_validJpeg_returnsS3Url(@TempDir Path tmp) throws IOException {
        Path file = tmp.resolve("img.jpg");
        Files.write(file, new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0, 0, 0, 0, 0, 0, 0, 0, 0});

        FileUpload upload = mock(FileUpload.class);
        when(upload.contentType()).thenReturn("image/jpeg");
        when(upload.uploadedFile()).thenReturn(file);
        when(upload.size()).thenReturn(12L);

        S3Client s3 = mock(S3Client.class);
        String url = service(s3).saveImage(upload, "events", FileStorageService.MAX_BANNER_BYTES, null);

        assertTrue(url.startsWith("http://s3/bucket/events/"));
        assertTrue(url.endsWith(".jpg"));
        verify(s3).putObject(any(PutObjectRequest.class), any(RequestBody.class));
    }

    // --- saveImage: size limit (pentest findings 4.13/4.19) ---

    @Test
    void saveImage_fileTooLarge_throwsFileTooLarge() {
        FileUpload upload = mock(FileUpload.class);
        when(upload.contentType()).thenReturn("image/jpeg");
        when(upload.size()).thenReturn(3L * 1024 * 1024); // 3 MiB > 2 MiB limit
        FileStorageService svc = service(mock(S3Client.class));

        assertThrows(FileTooLargeException.class,
                () -> svc.saveImage(upload, "folder", FileStorageService.MAX_AVATAR_BYTES, null));
    }

    @Test
    void saveImage_fileSizeAtLimit_accepted(@TempDir Path tmp) throws IOException {
        Path file = tmp.resolve("img.jpg");
        Files.write(file, new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0, 0, 0, 0, 0, 0, 0, 0, 0});

        FileUpload upload = mock(FileUpload.class);
        when(upload.contentType()).thenReturn("image/jpeg");
        when(upload.uploadedFile()).thenReturn(file);
        when(upload.size()).thenReturn(FileStorageService.MAX_AVATAR_BYTES);

        S3Client s3 = mock(S3Client.class);
        // Exactly at limit — must not throw.
        assertDoesNotThrow(() -> service(s3).saveImage(upload, "folder", FileStorageService.MAX_AVATAR_BYTES, null));
    }

    // --- saveImage: GC of previous S3 object ---

    @Test
    void saveImage_withOldUrl_uploadsBeforeDeletingOldObject(@TempDir Path tmp) throws IOException {
        Path file = tmp.resolve("img.jpg");
        Files.write(file, new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0, 0, 0, 0, 0, 0, 0, 0, 0});

        FileUpload upload = mock(FileUpload.class);
        when(upload.contentType()).thenReturn("image/jpeg");
        when(upload.uploadedFile()).thenReturn(file);
        when(upload.size()).thenReturn(12L);

        S3Client s3 = mock(S3Client.class);
        List<String> callLog = new ArrayList<>();
        doAnswer(inv -> { callLog.add("put"); return null; })
                .when(s3).putObject(any(PutObjectRequest.class), any(RequestBody.class));
        doAnswer(inv -> { callLog.add("delete"); return null; })
                .when(s3).deleteObject(any(DeleteObjectRequest.class));

        FileStorageService svc = service(s3);
        String oldUrl = "http://s3/bucket/users/avatars/old-key.jpg";

        String newUrl = svc.saveImage(upload, AVATARS_FOLDER, FileStorageService.MAX_AVATAR_BYTES, oldUrl);

        assertNotNull(newUrl);
        assertEquals(List.of("put", "delete"), callLog, "putObject must be called before deleteObject");
    }

    @Test
    void saveImage_withNullOldUrl_skipsDelete(@TempDir Path tmp) throws IOException {
        Path file = tmp.resolve("img.jpg");
        Files.write(file, new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0, 0, 0, 0, 0, 0, 0, 0, 0});

        FileUpload upload = mock(FileUpload.class);
        when(upload.contentType()).thenReturn("image/jpeg");
        when(upload.uploadedFile()).thenReturn(file);
        when(upload.size()).thenReturn(12L);

        S3Client s3 = mock(S3Client.class);
        service(s3).saveImage(upload, "folder", FileStorageService.MAX_AVATAR_BYTES, null);

        verify(s3, never()).deleteObject(any(DeleteObjectRequest.class));
    }

    @Test
    void saveImage_withExternalOldUrl_skipsDelete(@TempDir Path tmp) throws IOException {
        Path file = tmp.resolve("img.jpg");
        Files.write(file, new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0, 0, 0, 0, 0, 0, 0, 0, 0});

        FileUpload upload = mock(FileUpload.class);
        when(upload.contentType()).thenReturn("image/jpeg");
        when(upload.uploadedFile()).thenReturn(file);
        when(upload.size()).thenReturn(12L);

        S3Client s3 = mock(S3Client.class);
        // URL from Auth0 or another provider — must not attempt delete.
        service(s3).saveImage(upload, "folder", FileStorageService.MAX_AVATAR_BYTES,
                "https://cdn.auth0.com/avatars/alice.png");

        verify(s3, never()).deleteObject(any(DeleteObjectRequest.class));
    }

    @Test
    void saveImage_withOldUrlFromDifferentFolder_skipsDelete(@TempDir Path tmp) throws IOException {
        // Security: oldUrl pointing to a different bucket folder must not be deleted.
        Path file = tmp.resolve("img.jpg");
        Files.write(file, new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0, 0, 0, 0, 0, 0, 0, 0, 0});

        FileUpload upload = mock(FileUpload.class);
        when(upload.contentType()).thenReturn("image/jpeg");
        when(upload.uploadedFile()).thenReturn(file);
        when(upload.size()).thenReturn(12L);

        S3Client s3 = mock(S3Client.class);
        String crossFolderUrl = "http://s3/bucket/events/banners/event-key.jpg";

        String newUrl = service(s3).saveImage(upload, AVATARS_FOLDER, FileStorageService.MAX_AVATAR_BYTES, crossFolderUrl);

        assertNotNull(newUrl);
        verify(s3, never()).deleteObject(any(DeleteObjectRequest.class));
    }

    @Test
    void saveImage_deleteOldFails_uploadSucceeds(@TempDir Path tmp) throws IOException {
        Path file = tmp.resolve("img.jpg");
        Files.write(file, new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0, 0, 0, 0, 0, 0, 0, 0, 0});

        FileUpload upload = mock(FileUpload.class);
        when(upload.contentType()).thenReturn("image/jpeg");
        when(upload.uploadedFile()).thenReturn(file);
        when(upload.size()).thenReturn(12L);

        S3Client s3 = mock(S3Client.class);
        when(s3.deleteObject(any(DeleteObjectRequest.class)))
                .thenThrow(new RuntimeException("S3 delete unavailable"));

        FileStorageService svc = service(s3);
        String oldUrl = "http://s3/bucket/users/avatars/old-key.jpg";

        // Delete failure must not prevent the upload.
        String newUrl = assertDoesNotThrow(() ->
                svc.saveImage(upload, AVATARS_FOLDER, FileStorageService.MAX_AVATAR_BYTES, oldUrl));

        assertNotNull(newUrl);
        verify(s3).putObject(any(PutObjectRequest.class), any(RequestBody.class));
    }

    // --- deleteObject ---

    @Test
    void deleteObject_validUrl_callsS3Delete() {
        S3Client s3 = mock(S3Client.class);
        FileStorageService svc = service(s3);

        svc.deleteObject("http://s3/bucket/users/avatars/some-key.jpg", AVATARS_FOLDER);

        verify(s3).deleteObject(any(DeleteObjectRequest.class));
    }

    @Test
    void deleteObject_wrongFolder_noOp() {
        // Security: a URL in the bucket but under a different folder must not be deleted.
        S3Client s3 = mock(S3Client.class);

        service(s3).deleteObject("http://s3/bucket/events/banners/event.jpg", AVATARS_FOLDER);

        verify(s3, never()).deleteObject(any(DeleteObjectRequest.class));
    }

    @Test
    void deleteObject_nullUrl_noOp() {
        S3Client s3 = mock(S3Client.class);
        service(s3).deleteObject(null, AVATARS_FOLDER);
        verify(s3, never()).deleteObject(any(DeleteObjectRequest.class));
    }

    @Test
    void deleteObject_blankUrl_noOp() {
        S3Client s3 = mock(S3Client.class);
        service(s3).deleteObject("   ", AVATARS_FOLDER);
        verify(s3, never()).deleteObject(any(DeleteObjectRequest.class));
    }

    @Test
    void deleteObject_externalUrl_noOp() {
        S3Client s3 = mock(S3Client.class);
        service(s3).deleteObject("https://cdn.auth0.com/avatars/alice.png", AVATARS_FOLDER);
        verify(s3, never()).deleteObject(any(DeleteObjectRequest.class));
    }
}
