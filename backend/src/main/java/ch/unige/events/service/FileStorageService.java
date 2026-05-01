package ch.unige.events.service;

import ch.unige.events.config.AppConfig;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.InternalServerErrorException;
import org.jboss.resteasy.reactive.multipart.FileUpload;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.BucketAlreadyExistsException;
import software.amazon.awssdk.services.s3.model.BucketAlreadyOwnedByYouException;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.PutBucketPolicyRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import ch.unige.events.util.ImageMagicBytes;

import java.util.Map;
import java.util.UUID;

@ApplicationScoped
public class FileStorageService {

    private static final Map<String, String> ALLOWED_TYPES = Map.of(
            "image/jpeg", ".jpg",
            "image/png",  ".png",
            "image/webp", ".webp",
            "image/gif",  ".gif"
    );

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

    public String saveImage(FileUpload fileUpload, String folder) {
        String contentType = fileUpload.contentType();
        if (contentType == null || !ALLOWED_TYPES.containsKey(contentType)) {
            throw new BadRequestException(INVALID_FILE_MESSAGE);
        }

        if (!ImageMagicBytes.matches(fileUpload.uploadedFile(), contentType)) {
            throw new BadRequestException(INVALID_FILE_MESSAGE);
        }

        String extension = ALLOWED_TYPES.get(contentType);
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
            throw new InternalServerErrorException("Failed to save image: " + e.getMessage());
        }

        return config.s3Url() + "/" + config.s3Bucket() + "/" + key;
    }
}
