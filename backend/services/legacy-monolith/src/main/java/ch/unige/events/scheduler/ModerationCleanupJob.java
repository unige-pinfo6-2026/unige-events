package ch.unige.events.scheduler;

import ch.unige.events.service.ModerationCleanupService;
import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class ModerationCleanupJob {

    private final ModerationCleanupService moderationCleanupService;

    @Inject
    public ModerationCleanupJob(ModerationCleanupService moderationCleanupService) {
        this.moderationCleanupService = moderationCleanupService;
    }

    @Scheduled(cron = "0 0 3 * * ?", timeZone = "Europe/Zurich")
    public void run() {
        moderationCleanupService.runCleanup();
    }
}
