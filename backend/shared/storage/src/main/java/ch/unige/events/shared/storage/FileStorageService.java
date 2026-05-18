package ch.unige.events.shared.storage;

import io.quarkus.logging.Log;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import jakarta.ws.rs.InternalServerErrorException;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
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
import java.util.Map;
import java.util.UUID;

/**
 * S3-backed image storage. Carbon-copy of the legacy
 * {@code ch.unige.events.service.FileStorageService} — same MIME
 * validation, size cap, magic-number check, public-read bucket policy.
 *
 * <p>Originally cloned per-service during the soft-extraction (one copy
 * in user-service for avatar/banner uploads, another in event-service
 * for the event banner). This consolidated version replaces both. Each
 * service's {@code AppConfig} extends {@link StorageConfig} so the
 * existing {@code app.s3.url} / {@code app.s3.bucket} configuration
 * keys keep working without churn.
 */
@ApplicationScoped
public class FileStorageService {

    public static final long MAX_AVATAR_BYTES = 2L * 1024 * 1024;
    public static final long MAX_BANNER_BYTES = 5L * 1024 * 1024;

    private static final String INVALID_FILE_MESSAGE =
            "File must be a JPEG, PNG, WebP or GIF image";

    private final S3Client s3;
    private final StorageConfig config;

    @Inject
    public FileStorageService(S3Client s3, StorageConfig config) {
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
            Log.errorf(e,
                "[S3_POLICY_APPLY_FAIL] Failed to apply public-read bucket policy to '%s' — uploaded images will be private (403 on CDN). Operators must verify Minio/S3 access policy.",
                config.s3Bucket()
            );
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

    /**
     * Best-effort delete of an S3 object identified by its absolute URL.
     * Used by {@code EventService.delete()} cascade cleanup (Décision T) and
     * by {@code EventAttachmentService.delete()} hors-transaction cleanup
     * (Décision V).
     *
     * <p>Unlike {@link #deleteObject(String, String)}, this overload does not
     * pin a specific folder — callers have already validated the URL when
     * they stored it in the DB row they are now purging. Caller-side
     * try/catch + log WARN is the documented contract (the method itself
     * swallows S3 failures and only logs at WARN level).
     */
    public void deleteObject(String url) {
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
            Log.warnf(e, "[S3_CLEANUP_FAIL] deleteObject key='%s' — orphan object will remain", key);
        }
    }

    /**
     * Generic file upload to S3 for non-image payloads — SCRUM-148, used by
     * event-service for {@code event_attachments} (PDF / DOC / DOCX / XLSX,
     * Décision S).
     *
     * <p>Validation strategy :
     * <ul>
     *   <li><strong>Size :</strong> {@code file.size() <= maxBytes} else
     *       {@code 422 attachment_invalid_size}.</li>
     *   <li><strong>MIME header :</strong> {@code file.contentType()} must be
     *       a key of {@code mimeWhitelist} else {@code 422 attachment_invalid_type}.
     *       Comparison is case-insensitive ; the value is the file extension
     *       (with leading dot) used to suffix the S3 object key.</li>
     *   <li><strong>Magic-number :</strong> NOT validated for non-image
     *       MIME types. Acceptable risk for SCRUM-148 (PDF / DOC / DOCX /
     *       XLSX) — a malicious user could upload a renamed executable
     *       claiming {@code application/pdf}. Mitigation deferred to a
     *       future DevOps AV scan (cf. {@code devops-handoff.md}). For
     *       images, the strict magic-number check in {@link #saveImage} is
     *       preserved untouched (Décision S backward-compat).</li>
     * </ul>
     *
     * <p>Best-effort cleanup of {@code oldUrl} runs hors-transaction after a
     * successful upload — failures only emit a WARN log (orphans accepted).
     *
     * @param fileUpload   multipart upload to persist
     * @param folder       S3 sub-folder (e.g. {@code "event-attachments"})
     * @param maxBytes     hard size limit
     * @param mimeWhitelist MIME → extension map (e.g.
     *                      {@link DocumentFormat#MIME_TO_EXTENSION})
     * @param oldUrl       optional URL of a previous version to delete after
     *                     the new one is uploaded
     * @return the absolute S3 URL of the newly uploaded object — store as
     *         {@code fileUrl} in the owning entity
     * @throws WebApplicationException 422 on size or MIME validation failure
     *                                 ; 503 on S3 backend failure
     */
    public String saveFile(FileUpload fileUpload, String folder, long maxBytes,
                           Map<String, String> mimeWhitelist, String oldUrl) {
        long limitMib = maxBytes / (1024 * 1024);

        if (fileUpload.size() > maxBytes) {
            throw attachmentInvalid("attachment_invalid_size",
                    "File size exceeds the " + limitMib + " MiB limit.");
        }

        String rawContentType = fileUpload.contentType();
        if (rawContentType == null) {
            throw attachmentInvalid("attachment_invalid_type",
                    "Missing Content-Type header — only "
                            + mimeWhitelist.keySet() + " allowed.");
        }
        String contentType = rawContentType.split(";")[0].strip().toLowerCase(Locale.ROOT);
        String extension = mimeWhitelist.get(contentType);
        if (extension == null) {
            throw attachmentInvalid("attachment_invalid_type",
                    "File type '" + contentType + "' is not allowed. Accepted : "
                            + mimeWhitelist.keySet() + ".");
        }

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
            Log.errorf(e, "[S3_UPLOAD_FAIL] saveFile folder=%s mime=%s size=%d",
                    folder, contentType, fileUpload.size());
            throw new WebApplicationException(
                    "Storage backend unavailable", Response.Status.SERVICE_UNAVAILABLE);
        }

        tryDeleteObject(oldUrl, folder);

        return config.s3Url() + "/" + config.s3Bucket() + "/" + key;
    }

    private static WebApplicationException attachmentInvalid(String error, String message) {
        return new WebApplicationException(
                Response.status(422)
                        .type(MediaType.APPLICATION_JSON)
                        .entity(new StorageErrorBody(error, message))
                        .build());
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
