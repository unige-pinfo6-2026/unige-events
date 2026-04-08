package ch.unige.events.config;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;
import io.smallrye.config.WithName;

@ConfigMapping(prefix = "app")
public interface AppConfig {

    @WithName("frontend.url")
    @WithDefault("http://localhost:5173")
    String frontendUrl();
}
