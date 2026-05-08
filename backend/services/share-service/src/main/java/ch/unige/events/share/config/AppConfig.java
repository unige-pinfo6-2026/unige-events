package ch.unige.events.share.config;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithName;

@ConfigMapping(prefix = "app")
public interface AppConfig {

    @WithName("frontend.url")
    String frontendUrl();
}
