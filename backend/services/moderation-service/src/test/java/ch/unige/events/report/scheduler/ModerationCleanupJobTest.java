package ch.unige.events.report.scheduler;

import ch.unige.events.report.service.ModerationCleanupService;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * Verifies the {@link ModerationCleanupJob} cron entry-point delegates
 * to {@link ModerationCleanupService#runCleanup()}.
 */
@QuarkusTest
class ModerationCleanupJobTest {

    @Inject
    ModerationCleanupJob job;

    @InjectMock
    ModerationCleanupService service;

    @Test
    void run_delegatesToService() {
        job.run();
        verify(service).runCleanup();
    }

    @Test
    void constructor_directlyWires_andDelegates() {
        ModerationCleanupService localService = mock(ModerationCleanupService.class);
        ModerationCleanupJob localJob = new ModerationCleanupJob(localService);
        localJob.run();
        verify(localService).runCleanup();
    }
}
