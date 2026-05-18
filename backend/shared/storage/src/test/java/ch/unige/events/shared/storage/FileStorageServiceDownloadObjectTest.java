package ch.unige.events.shared.storage;

import jakarta.ws.rs.WebApplicationException;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.S3Exception;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * SCRUM-149 follow-up — coverage for
 * {@link FileStorageService#downloadObject(String)}. The public
 * download endpoint of {@code EventAttachmentResource} delegates
 * here, so every URL-parsing and S3-failure branch must be exercised
 * by unit tests (the resource itself is covered by Quarkus IT, which
 * needs Docker — these tests are reactor-runnable without it).
 */
class FileStorageServiceDownloadObjectTest {

    private static final String S3_URL = "http://s3";
    private static final String BUCKET = "bucket";
    private static final byte[] PAYLOAD = new byte[]{0x25, 0x50, 0x44, 0x46}; // %PDF

    private static FileStorageService service(S3Client s3) {
        StorageConfig config = mock(StorageConfig.class);
        when(config.s3Bucket()).thenReturn(BUCKET);
        when(config.s3Url()).thenReturn(S3_URL);
        return new FileStorageService(s3, config);
    }

    private static ResponseBytes<GetObjectResponse> bytesResponse() {
        return ResponseBytes.fromByteArray(GetObjectResponse.builder().build(), PAYLOAD);
    }

    @Test
    void downloadObject_returnsBytes_forValidUrl() {
        S3Client s3 = mock(S3Client.class);
        when(s3.getObjectAsBytes(any(GetObjectRequest.class))).thenReturn(bytesResponse());

        byte[] result = service(s3).downloadObject("http://s3/bucket/event-attachments/abc.pdf");

        assertArrayEquals(PAYLOAD, result);
        verify(s3).getObjectAsBytes(eq(GetObjectRequest.builder()
                .bucket(BUCKET)
                .key("event-attachments/abc.pdf")
                .build()));
    }

    @Test
    void downloadObject_returnsNull_forNullUrl() {
        S3Client s3 = mock(S3Client.class);
        assertNull(service(s3).downloadObject(null));
        verify(s3, never()).getObjectAsBytes(any(GetObjectRequest.class));
    }

    @Test
    void downloadObject_returnsNull_forBlankUrl() {
        S3Client s3 = mock(S3Client.class);
        assertNull(service(s3).downloadObject("   "));
        verify(s3, never()).getObjectAsBytes(any(GetObjectRequest.class));
    }

    @Test
    void downloadObject_returnsNull_forUrlOutsideBucket() {
        S3Client s3 = mock(S3Client.class);
        // Defensive — caller could theoretically pass an unrelated URL ;
        // we must never proxy it.
        assertNull(service(s3).downloadObject("https://evil.example.com/abc.pdf"));
        verify(s3, never()).getObjectAsBytes(any(GetObjectRequest.class));
    }

    @Test
    void downloadObject_returnsNull_whenS3KeyMissing() {
        S3Client s3 = mock(S3Client.class);
        when(s3.getObjectAsBytes(any(GetObjectRequest.class)))
                .thenThrow(NoSuchKeyException.builder().message("missing").build());

        byte[] result = service(s3).downloadObject("http://s3/bucket/event-attachments/gone.pdf");

        assertNull(result);
    }

    @Test
    void downloadObject_throws503_whenS3Errors() {
        S3Client s3 = mock(S3Client.class);
        when(s3.getObjectAsBytes(any(GetObjectRequest.class)))
                .thenThrow(S3Exception.builder().message("boom").statusCode(500).build());

        WebApplicationException ex = assertThrows(WebApplicationException.class,
                () -> service(s3).downloadObject("http://s3/bucket/event-attachments/x.pdf"));
        assertEquals(503, ex.getResponse().getStatus());
    }
}
