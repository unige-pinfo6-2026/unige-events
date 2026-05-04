package ch.unige.events.config;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithName;

@ConfigMapping(prefix = "app")
public interface AppConfig {

    @WithName("frontend.url")
    String frontendUrl();

    @WithName("s3.url")
    String s3Url();

    @WithName("s3.bucket")
    String s3Bucket();

    @WithName("moderation.auto-hide-threshold")
    int moderationAutoHideThreshold();
}
