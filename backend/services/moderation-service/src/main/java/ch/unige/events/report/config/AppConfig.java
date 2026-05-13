package ch.unige.events.report.config;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithName;

@ConfigMapping(prefix = "app")
public interface AppConfig {

    @WithName("moderation.auto-hide-threshold")
    int moderationAutoHideThreshold();
}
