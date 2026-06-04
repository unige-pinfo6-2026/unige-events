package ch.unige.events.user.test;

import ch.unige.events.shared.storage.FileStorageService;
import ch.unige.events.shared.storage.FileTooLargeException;
import ch.unige.events.shared.storage.InvalidFileTypeException;
import ch.unige.events.shared.storage.StorageConfig;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.ws.rs.InternalServerErrorException;
import org.jboss.resteasy.reactive.multipart.FileUpload;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Coverage exercises for the shared {@link FileStorageService}.
 * The S3 client and the {@link StorageConfig} are pure Mockito mocks —
 * no real S3 endpoint required.
 */
@QuarkusTest
class FileStorageServiceCoverageTest {

    private FileStorageService newService(S3Client s3, StorageConfig config) {
        FileStorageService svc = new FileStorageService(s3, config);
        return svc;
    }

    private static StorageConfig stubConfig() {
        StorageConfig cfg = mock(StorageConfig.class);
        when(cfg.s3Url()).thenReturn("https://s3.example.com");
        when(cfg.s3Bucket()).thenReturn("test-bucket");
        return cfg;
    }

    private static FileUpload pngUpload() throws Exception {
        FileUpload f = mock(FileUpload.class);
        when(f.contentType()).thenReturn("image/png");
        when(f.size()).thenReturn(100L);
        Path tmp = Files.createTempFile("png-", ".png");
        Files.write(tmp, new byte[]{(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A,
                                    0x00, 0x00, 0x00, 0x00});
        when(f.uploadedFile()).thenReturn(tmp);
        return f;
    }

    @Test
    void saveImage_nullContentType_throwsInvalidFileType() {
        FileStorageService svc = newService(mock(S3Client.class), stubConfig());
        FileUpload f = mock(FileUpload.class);
        when(f.contentType()).thenReturn(null);

        assertThrows(InvalidFileTypeException.class,
                () -> svc.saveImage(f, "users/avatars", 100L, null));
    }

    @Test
    void saveImage_unsupportedMime_throwsInvalidFileType() {
        FileStorageService svc = newService(mock(S3Client.class), stubConfig());
        FileUpload f = mock(FileUpload.class);
        when(f.contentType()).thenReturn("application/pdf");

        assertThrows(InvalidFileTypeException.class,
                () -> svc.saveImage(f, "users/avatars", 100L, null));
    }

    @Test
    void saveImage_oversizedFile_throwsFileTooLarge() {
        FileStorageService svc = newService(mock(S3Client.class), stubConfig());
        FileUpload f = mock(FileUpload.class);
        when(f.contentType()).thenReturn("image/png");
        when(f.size()).thenReturn(99_999_999L);

        FileTooLargeException ex = assertThrows(FileTooLargeException.class,
                () -> svc.saveImage(f, "users/avatars", 1024L * 1024L, null));
        assertNotNull(ex.getMessage());
    }

    @Test
    void saveImage_lyingMime_throwsInvalidFileType() throws Exception {
        FileStorageService svc = newService(mock(S3Client.class), stubConfig());
        FileUpload f = mock(FileUpload.class);
        when(f.contentType()).thenReturn("image/png");
        when(f.size()).thenReturn(100L);
        Path tmp = Files.createTempFile("svg-", ".png");
        Files.writeString(tmp, "<svg xmlns='...'></svg>");
        when(f.uploadedFile()).thenReturn(tmp);

        try {
            assertThrows(InvalidFileTypeException.class,
                    () -> svc.saveImage(f, "users/avatars", 1024L * 1024L, null));
        } finally {
            Files.deleteIfExists(tmp);
        }
    }

    @Test
    void saveImage_validPng_uploadsAndReturnsUrl() throws Exception {
        S3Client s3 = mock(S3Client.class);
        StorageConfig cfg = stubConfig();
        FileStorageService svc = newService(s3, cfg);
        FileUpload f = pngUpload();

        String url = svc.saveImage(f, "users/avatars", 1024L * 1024L, null);
        assertEquals(true, url.startsWith("https://s3.example.com/test-bucket/users/avatars/"));
        assertEquals(true, url.endsWith(".png"));
        verify(s3, times(1)).putObject(any(PutObjectRequest.class),
                any(software.amazon.awssdk.core.sync.RequestBody.class));

        Files.deleteIfExists(f.uploadedFile());
    }

    @Test
    void saveImage_s3Throws_wrappedAsInternalServerError() throws Exception {
        S3Client s3 = mock(S3Client.class);
        when(s3.putObject(any(PutObjectRequest.class),
                any(software.amazon.awssdk.core.sync.RequestBody.class)))
                .thenThrow(new RuntimeException("S3 down"));

        FileStorageService svc = newService(s3, stubConfig());
        FileUpload f = pngUpload();

        try {
            assertThrows(InternalServerErrorException.class,
                    () -> svc.saveImage(f, "users/avatars", 1024L * 1024L, null));
        } finally {
            Files.deleteIfExists(f.uploadedFile());
        }
    }

    @Test
    void saveImage_withOldUrlSameFolder_deletesOldObject() throws Exception {
        S3Client s3 = mock(S3Client.class);
        StorageConfig cfg = stubConfig();
        FileStorageService svc = newService(s3, cfg);
        FileUpload f = pngUpload();

        String oldUrl = "https://s3.example.com/test-bucket/users/avatars/old.png";
        svc.saveImage(f, "users/avatars", 1024L * 1024L, oldUrl);
        verify(s3, times(1)).deleteObject(any(DeleteObjectRequest.class));

        Files.deleteIfExists(f.uploadedFile());
    }

    @Test
    void saveImage_withOldUrlDifferentFolder_doesNotDelete() throws Exception {
        S3Client s3 = mock(S3Client.class);
        StorageConfig cfg = stubConfig();
        FileStorageService svc = newService(s3, cfg);
        FileUpload f = pngUpload();

        // Different folder prefix (banners vs avatars). Must NOT delete.
        String oldUrl = "https://s3.example.com/test-bucket/users/banners/old.png";
        svc.saveImage(f, "users/avatars", 1024L * 1024L, oldUrl);
        verify(s3, times(0)).deleteObject(any(DeleteObjectRequest.class));

        Files.deleteIfExists(f.uploadedFile());
    }

    @Test
    void deleteObject_nullUrl_isNoOp() {
        S3Client s3 = mock(S3Client.class);
        FileStorageService svc = newService(s3, stubConfig());
        svc.deleteObject(null, "users/avatars");
        verify(s3, times(0)).deleteObject(any(DeleteObjectRequest.class));
    }

    @Test
    void deleteObject_blankUrl_isNoOp() {
        S3Client s3 = mock(S3Client.class);
        FileStorageService svc = newService(s3, stubConfig());
        svc.deleteObject("   ", "users/avatars");
        verify(s3, times(0)).deleteObject(any(DeleteObjectRequest.class));
    }

    @Test
    void deleteObject_externalUrl_isNoOp() {
        S3Client s3 = mock(S3Client.class);
        FileStorageService svc = newService(s3, stubConfig());
        // URL doesn't match s3 prefix → no-op.
        svc.deleteObject("https://other.cdn/foo.png", "users/avatars");
        verify(s3, times(0)).deleteObject(any(DeleteObjectRequest.class));
    }

    @Test
    void deleteObject_wrongFolder_isNoOp() {
        S3Client s3 = mock(S3Client.class);
        FileStorageService svc = newService(s3, stubConfig());
        // URL prefixed by s3 bucket but folder mismatch → no-op (security check).
        svc.deleteObject("https://s3.example.com/test-bucket/other/x.png", "users/avatars");
        verify(s3, times(0)).deleteObject(any(DeleteObjectRequest.class));
    }

    @Test
    void deleteObject_validUrl_invokesS3Delete() {
        S3Client s3 = mock(S3Client.class);
        FileStorageService svc = newService(s3, stubConfig());
        svc.deleteObject("https://s3.example.com/test-bucket/users/avatars/x.png", "users/avatars");
        verify(s3, times(1)).deleteObject(any(DeleteObjectRequest.class));
    }

    @Test
    void deleteObject_s3Throws_swallowsException() {
        S3Client s3 = mock(S3Client.class);
        when(s3.deleteObject(any(DeleteObjectRequest.class)))
                .thenThrow(new RuntimeException("S3 down"));
        FileStorageService svc = newService(s3, stubConfig());
        // Must NOT propagate (Log.warnf is best-effort) — but the failing S3 delete
        // is still attempted exactly once before the exception is swallowed.
        svc.deleteObject("https://s3.example.com/test-bucket/users/avatars/x.png", "users/avatars");
        verify(s3, times(1)).deleteObject(any(DeleteObjectRequest.class));
    }
}
