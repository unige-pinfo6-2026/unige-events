package ch.unige.events.service;

import ch.unige.events.config.AppConfig;
import ch.unige.events.exception.InvalidFileTypeException;
import ch.unige.events.util.ImageFormat;
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
import software.amazon.awssdk.services.s3.model.PutBucketPolicyRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.io.IOException;
import java.util.Locale;
import java.util.UUID;

@ApplicationScoped
public class FileStorageService {

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
        if (contentType == null) {
            throw new InvalidFileTypeException(INVALID_FILE_MESSAGE);
        }
        contentType = contentType.split(";")[0].strip().toLowerCase(Locale.ROOT);
        if (!ImageFormat.MIME_TO_EXTENSION.containsKey(contentType)) {
            throw new InvalidFileTypeException(INVALID_FILE_MESSAGE);
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
            throw new InternalServerErrorException("Failed to save image: " + e.getMessage());
        }

        return config.s3Url() + "/" + config.s3Bucket() + "/" + key;
    }
}
