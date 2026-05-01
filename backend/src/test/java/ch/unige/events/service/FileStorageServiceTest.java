package ch.unige.events.service;

import ch.unige.events.config.AppConfig;
import ch.unige.events.exception.InvalidFileTypeException;
import jakarta.ws.rs.InternalServerErrorException;
import org.jboss.resteasy.reactive.multipart.FileUpload;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.core.sync.RequestBody;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class FileStorageServiceTest {

    private static FileStorageService service(S3Client s3) {
        AppConfig config = mock(AppConfig.class);
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
                () -> svc.saveImage(upload, "folder"));
    }

    @Test
    void saveImage_svgContentType_throwsInvalidFileType() {
        FileUpload upload = mock(FileUpload.class);
        when(upload.contentType()).thenReturn("image/svg+xml");
        FileStorageService svc = service(mock(S3Client.class));

        assertThrows(InvalidFileTypeException.class,
                () -> svc.saveImage(upload, "folder"));
    }

    // --- saveImage: magic bytes mismatch ---

    @Test
    void saveImage_pngContentTypeWithJpegBytes_throwsInvalidFileType(@TempDir Path tmp) throws IOException {
        Path file = tmp.resolve("fake.png");
        Files.write(file, new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0, 0, 0, 0, 0, 0, 0, 0, 0});

        FileUpload upload = mock(FileUpload.class);
        when(upload.contentType()).thenReturn("image/png");
        when(upload.uploadedFile()).thenReturn(file);
        FileStorageService svc = service(mock(S3Client.class));

        assertThrows(InvalidFileTypeException.class,
                () -> svc.saveImage(upload, "folder"));
    }

    // --- saveImage: IOException when reading file ---

    @Test
    void saveImage_unreadableFile_throwsInternalServerError() {
        FileUpload upload = mock(FileUpload.class);
        when(upload.contentType()).thenReturn("image/jpeg");
        when(upload.uploadedFile()).thenReturn(Path.of("/nonexistent/missing.jpg"));
        FileStorageService svc = service(mock(S3Client.class));

        assertThrows(InternalServerErrorException.class,
                () -> svc.saveImage(upload, "folder"));
    }

    // --- saveImage: S3 putObject failure ---

    @Test
    void saveImage_s3PutFails_throwsInternalServerError(@TempDir Path tmp) throws IOException {
        Path file = tmp.resolve("img.jpg");
        Files.write(file, new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0, 0, 0, 0, 0, 0, 0, 0, 0});

        FileUpload upload = mock(FileUpload.class);
        when(upload.contentType()).thenReturn("image/jpeg");
        when(upload.uploadedFile()).thenReturn(file);

        S3Client s3 = mock(S3Client.class);
        when(s3.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                .thenThrow(new RuntimeException("S3 unavailable"));
        FileStorageService svc = service(s3);

        assertThrows(InternalServerErrorException.class,
                () -> svc.saveImage(upload, "folder"));
    }

    // --- saveImage: happy path ---

    @Test
    void saveImage_validJpeg_returnsS3Url(@TempDir Path tmp) throws IOException {
        Path file = tmp.resolve("img.jpg");
        Files.write(file, new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0, 0, 0, 0, 0, 0, 0, 0, 0});

        FileUpload upload = mock(FileUpload.class);
        when(upload.contentType()).thenReturn("image/jpeg");
        when(upload.uploadedFile()).thenReturn(file);

        S3Client s3 = mock(S3Client.class);
        String url = service(s3).saveImage(upload, "events");

        assertTrue(url.startsWith("http://s3/bucket/events/"));
        assertTrue(url.endsWith(".jpg"));
        verify(s3).putObject(any(PutObjectRequest.class), any(RequestBody.class));
    }
}
