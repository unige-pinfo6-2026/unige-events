package ch.unige.events.shared.storage;

import io.smallrye.config.WithName;

/**
 * Subset of each service's {@code AppConfig} that the shared
 * {@link FileStorageService} needs : S3 endpoint URL + bucket name.
 *
 * <p>Each service's {@code @ConfigMapping}-annotated AppConfig
 * <em>extends</em> this interface, so SmallRye Config exposes a single
 * bean that satisfies both injection points (the service's local
 * {@code AppConfig} reference and {@code FileStorageService}'s
 * {@code StorageConfig} parameter). Mappings inherit {@code @WithName}
 * annotations from parent interfaces, so the wire keys
 * {@code app.s3.url} and {@code app.s3.bucket} resolve unchanged.
 */
public interface StorageConfig {

    @WithName("s3.url")
    String s3Url();

    @WithName("s3.bucket")
    String s3Bucket();
}
