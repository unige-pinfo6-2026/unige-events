package ch.unige.events.report.scheduler;

import ch.unige.events.report.service.ModerationCleanupService;
import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * Cron @ 03:00 Europe/Zurich. Lives in report-service ; helm chart pins
 * {@code replicas: 1} strict (no leader election in S8).
 */
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
