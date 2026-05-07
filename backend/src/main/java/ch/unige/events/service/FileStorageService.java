package ch.unige.events.service;

import ch.unige.events.config.AppConfig;
import ch.unige.events.exception.FileTooLargeException;
import ch.unige.events.exception.InvalidFileTypeException;
import ch.unige.events.util.ImageFormat;
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
            // bucket déjà présent, rien à faire
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

        s3.putBucketPolicy(PutBucketPolicyRequest.builder()
            .bucket(config.s3Bucket())
            .policy(policy)
            .build());
    }

    /**
     * Validates, size-checks, uploads a new image, and deletes the previous one if present.
     *
     * @param fileUpload the uploaded file
     * @param folder     S3 folder prefix (e.g. "users/avatars")
     * @param maxBytes   maximum allowed file size in bytes
     * @param oldUrl     full URL of the previous object to delete, or null to skip deletion
     * @return public URL of the newly uploaded object
     */
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

        tryDeleteObject(oldUrl);

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
            throw new InternalServerErrorException("Failed to save image: " + e.getMessage());
        }

        return config.s3Url() + "/" + config.s3Bucket() + "/" + key;
    }

    /**
     * Deletes the S3 object identified by its full public URL.
     * Logs a warning on failure without propagating the exception.
     */
    public void deleteObject(String url) {
        tryDeleteObject(url);
    }

    private void tryDeleteObject(String url) {
        if (url == null || url.isBlank()) {
            return;
        }
        String prefix = config.s3Url() + "/" + config.s3Bucket() + "/";
        if (!url.startsWith(prefix)) {
            return;
        }
        String key = url.substring(prefix.length());
        try {
            s3.deleteObject(DeleteObjectRequest.builder()
                    .bucket(config.s3Bucket())
                    .key(key)
                    .build());
        } catch (Exception e) {
            Log.warnf("Failed to delete S3 object '%s': %s", key, e.getMessage());
        }
    }
}
