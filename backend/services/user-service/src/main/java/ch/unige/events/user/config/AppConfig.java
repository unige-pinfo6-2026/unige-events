package ch.unige.events.user.config;

import ch.unige.events.shared.storage.StorageConfig;
import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithName;

@ConfigMapping(prefix = "app")
public interface AppConfig extends StorageConfig {

    @WithName("frontend.url")
    String frontendUrl();
}
