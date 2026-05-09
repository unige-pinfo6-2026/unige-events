package ch.unige.events.user.service;

import ch.unige.events.user.config.AppConfig;
import ch.unige.events.user.exception.FileTooLargeException;
import ch.unige.events.user.exception.InvalidFileTypeException;
import ch.unige.events.user.util.ImageFormat;
import io.quarkus.logging.Log;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import jakarta.ws.rs.InternalServerErrorException;
import org.jboss.resteasy.reactive.multipart.FileUpload;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.BucketAlreadyExistsException;
import software.amazon.awssdk.services.s3.model.BucketAlreadyOwnedByYouException;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.PutBucketPolicyRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.io.IOException;
import java.util.Locale;
import java.util.UUID;

/**
 * S3-backed image storage. Carbon-copy of legacy
 * ch.unige.events.service.FileStorageService — same MIME validation,
 * size cap, magic-number check, public-read bucket policy. Soft-extracted
 * into user-service for the avatar / banner uploads ; a parallel copy
 * lives in event-service for the event banner upload (the legacy file
 * was a single shared singleton ; soft-extraction prefers duplication
 * over a shared lib until the migration settles, cf.
 * backend/docs/microservices-migration-roadmap.md decision on
 * FileStorageService).
 */
@ApplicationScoped
public class FileStorageService {

    public static final long MAX_AVATAR_BYTES = 2L * 1024 * 1024;
    public static final long MAX_BANNER_BYTES = 5L * 1024 * 1024;

    private static final String INVALID_FILE_MESSAGE =
            "File must be a JPEG, PNG, WebP or GIF image";

    private final S3Client s3;
    private final AppConfig config;

    @Inject
    public FileStorageService(S3Client s3, AppConfig config) {
        this.s3 = s3;
        this.config = config;
    }

    void init(@Observes StartupEvent ev) {
        try {
            s3.createBucket(CreateBucketRequest.builder().bucket(config.s3Bucket()).build());
        } catch (BucketAlreadyOwnedByYouException | BucketAlreadyExistsException ignored) {
            // bucket already exists, no-op
        } catch (Exception e) {
            // S3 endpoint unreachable in tests — log and continue ; the
            // upload methods will surface errors when actually called.
            Log.warnf(e, "Failed to ensure bucket '%s' exists at startup", config.s3Bucket());
            return;
        }

        String policy = """
        {
            "Version": "2012-10-17",
            "Statement": [{
                "Effect": "Allow",
                "Principal": "*",
                "Action": ["s3:GetObject"],
                "Resource": ["arn:aws:s3:::%s/*"]
            }]
        }
        """.formatted(config.s3Bucket());

        try {
            s3.putBucketPolicy(PutBucketPolicyRequest.builder()
                .bucket(config.s3Bucket())
                .policy(policy)
                .build());
        } catch (Exception e) {
            Log.warnf(e, "Failed to apply bucket policy to '%s'", config.s3Bucket());
        }
    }

    public String saveImage(FileUpload fileUpload, String folder, long maxBytes, String oldUrl) {
        String contentType = fileUpload.contentType();
        if (contentType == null) {
            throw new InvalidFileTypeException(INVALID_FILE_MESSAGE);
        }
        contentType = contentType.split(";")[0].strip().toLowerCase(Locale.ROOT);
        if (!ImageFormat.MIME_TO_EXTENSION.containsKey(contentType)) {
            throw new InvalidFileTypeException(INVALID_FILE_MESSAGE);
        }

        if (fileUpload.size() > maxBytes) {
            long limitMb = maxBytes / (1024 * 1024);
            throw new FileTooLargeException("File exceeds " + limitMb + " MB limit");
        }

        try {
            if (!ImageFormat.matches(fileUpload.uploadedFile(), contentType)) {
                throw new InvalidFileTypeException(INVALID_FILE_MESSAGE);
            }
        } catch (IOException e) {
            throw new InternalServerErrorException("Failed to read uploaded file", e);
        }

        String extension = ImageFormat.MIME_TO_EXTENSION.get(contentType);
        String key = folder + "/" + UUID.randomUUID() + extension;

        try {
            s3.putObject(
                    PutObjectRequest.builder()
                            .bucket(config.s3Bucket())
                            .key(key)
                            .contentType(contentType)
                            .build(),
                    RequestBody.fromFile(fileUpload.uploadedFile())
            );
        } catch (Exception e) {
            throw new InternalServerErrorException("Failed to save image", e);
        }

        tryDeleteObject(oldUrl, folder);

        return config.s3Url() + "/" + config.s3Bucket() + "/" + key;
    }

    public void deleteObject(String url, String expectedFolder) {
        tryDeleteObject(url, expectedFolder);
    }

    private void tryDeleteObject(String url, String expectedFolder) {
        if (url == null || url.isBlank()) {
            return;
        }
        String prefix = config.s3Url() + "/" + config.s3Bucket() + "/";
        if (!url.startsWith(prefix)) {
            return;
        }
        String key = url.substring(prefix.length());
        if (!key.startsWith(expectedFolder + "/")) {
            return;
        }
        try {
            s3.deleteObject(DeleteObjectRequest.builder()
                    .bucket(config.s3Bucket())
                    .key(key)
                    .build());
        } catch (Exception e) {
            Log.warnf(e, "Failed to delete S3 object '%s'", key);
        }
    }
}
