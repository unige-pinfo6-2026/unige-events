package ch.unige.events.shared.storage;

import io.quarkus.runtime.StartupEvent;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.BucketAlreadyExistsException;
import software.amazon.awssdk.services.s3.model.BucketAlreadyOwnedByYouException;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.PutBucketPolicyRequest;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Coverage of the {@code @Observes StartupEvent} initializer that
 * provisions the bucket + applies the public-read policy. The method is
 * package-private and the @Observes annotation makes it discoverable
 * by Quarkus's CDI runtime ; here we invoke it directly as a regular
 * method to avoid spinning up a full Quarkus application.
 */
class FileStorageServiceInitTest {

    private static FileStorageService service(S3Client s3) {
        StorageConfig config = mock(StorageConfig.class);
        when(config.s3Bucket()).thenReturn("bucket");
        when(config.s3Url()).thenReturn("http://s3");
        return new FileStorageService(s3, config);
    }

    @Test
    void init_freshBucket_createsAndAppliesPolicy() {
        S3Client s3 = mock(S3Client.class);
        FileStorageService svc = service(s3);

        assertDoesNotThrow(() -> svc.init(new StartupEvent()));

        verify(s3).createBucket(any(CreateBucketRequest.class));
        verify(s3).putBucketPolicy(any(PutBucketPolicyRequest.class));
    }

    @Test
    void init_bucketAlreadyOwned_skipsCreateButAppliesPolicy() {
        S3Client s3 = mock(S3Client.class);
        when(s3.createBucket(any(CreateBucketRequest.class)))
                .thenThrow(BucketAlreadyOwnedByYouException.builder().message("owned").build());
        FileStorageService svc = service(s3);

        assertDoesNotThrow(() -> svc.init(new StartupEvent()));

        // The policy is still applied — the bucket exists, just not under our control.
        verify(s3).putBucketPolicy(any(PutBucketPolicyRequest.class));
    }

    @Test
    void init_bucketAlreadyExists_skipsCreateButAppliesPolicy() {
        S3Client s3 = mock(S3Client.class);
        when(s3.createBucket(any(CreateBucketRequest.class)))
                .thenThrow(BucketAlreadyExistsException.builder().message("exists").build());
        FileStorageService svc = service(s3);

        assertDoesNotThrow(() -> svc.init(new StartupEvent()));

        verify(s3).putBucketPolicy(any(PutBucketPolicyRequest.class));
    }

    @Test
    void init_unreachableS3_logsAndReturnsWithoutPolicy() {
        // S3 endpoint unreachable in dev / preview before MinIO is ready —
        // must not crash the service ; the upload methods will fail later
        // when actually called.
        S3Client s3 = mock(S3Client.class);
        when(s3.createBucket(any(CreateBucketRequest.class)))
                .thenThrow(new RuntimeException("connection refused"));
        FileStorageService svc = service(s3);

        assertDoesNotThrow(() -> svc.init(new StartupEvent()));

        // Returned early — no policy attempt.
        verify(s3, never()).putBucketPolicy(any(PutBucketPolicyRequest.class));
    }

    @Test
    void init_policyFailure_isLoggedNotPropagated() {
        S3Client s3 = mock(S3Client.class);
        when(s3.putBucketPolicy(any(PutBucketPolicyRequest.class)))
                .thenThrow(new RuntimeException("forbidden"));
        FileStorageService svc = service(s3);

        // Policy failure must not propagate — the service still starts.
        assertDoesNotThrow(() -> svc.init(new StartupEvent()));

        verify(s3).createBucket(any(CreateBucketRequest.class));
        verify(s3).putBucketPolicy(any(PutBucketPolicyRequest.class));
    }
}
