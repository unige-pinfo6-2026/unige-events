package ch.unige.events.event.config;

import ch.unige.events.shared.storage.StorageConfig;
import io.smallrye.config.ConfigMapping;

/**
 * SmallRye {@code @ConfigMapping} for the {@code app.*} keys consumed by
 * event-service. Inherits the {@code app.s3.url} + {@code app.s3.bucket}
 * methods from {@link StorageConfig} — the shared {@code FileStorageService}
 * resolves its config dependency to this same bean via the inherited
 * interface type.
 */
@ConfigMapping(prefix = "app")
public interface AppConfig extends StorageConfig {
}
