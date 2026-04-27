package ch.unige.events.scheduler;

import ch.unige.events.service.ModerationCleanupService;
import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class ModerationCleanupJob {

    @Inject
    public ModerationCleanupService moderationCleanupService;

    @Scheduled(cron = "0 0 3 * * ?")
    public void run() {
        moderationCleanupService.runCleanup();
    }
}
