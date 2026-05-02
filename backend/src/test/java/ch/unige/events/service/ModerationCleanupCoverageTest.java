package ch.unige.events.service;

import ch.unige.events.entity.Event;
import ch.unige.events.entity.EventStatus;
import ch.unige.events.entity.Report;
import ch.unige.events.entity.User;
import ch.unige.events.scheduler.ModerationCleanupJob;
import io.quarkus.test.TestTransaction;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;

@QuarkusTest
@TestProfile(ShareServiceCoverageProfile.class)
class ModerationCleanupCoverageTest {

    @Inject
    ModerationCleanupService moderationCleanupService;

    @Inject
    ModerationCleanupJob moderationCleanupJob;

    @Inject
    EntityManager em;

    // threshold = 3 (app.moderation.auto-hide-threshold default)

    @Test
    @TestTransaction
    void runCleanup_atThreshold_hidesEvent() {
        User creator = persistUser("auth0|modcov1", "modcov1@example.com");
        Event event = persistEvent("Event-3reports", creator, EventStatus.PUBLISHED);
        persistReport(event);
        persistReport(event);
        persistReport(event);

        moderationCleanupService.runCleanup();

        assertEquals(EventStatus.CANCELLED, event.status);
    }

    @Test
    @TestTransaction
    void runCleanup_belowThreshold_doesNotHideEvent() {
        User creator = persistUser("auth0|modcov2", "modcov2@example.com");
        Event event = persistEvent("Event-2reports", creator, EventStatus.PUBLISHED);
        persistReport(event);
        persistReport(event);

        moderationCleanupService.runCleanup();

        assertEquals(EventStatus.PUBLISHED, event.status);
    }

    @Test
    @TestTransaction
    void runCleanup_noReports_runsWithoutError() {
        moderationCleanupService.runCleanup();
    }

    @Test
    @TestTransaction
    void job_run_triggersCleanup() {
        User creator = persistUser("auth0|modcov3", "modcov3@example.com");
        Event event = persistEvent("Event-job-test", creator, EventStatus.PUBLISHED);
        persistReport(event);
        persistReport(event);
        persistReport(event);

        moderationCleanupJob.run();

        assertEquals(EventStatus.CANCELLED, event.status);
    }

    private User persistUser(String auth0Id, String email) {
        User user = new User();
        user.auth0Id = auth0Id;
        user.email = email;
        user.profilePublic = false;
        user.createdAt = LocalDateTime.now();
        user.username = ("u" + java.util.UUID.randomUUID().toString().replace("-", "").substring(0, 16)).toLowerCase();
        em.persist(user);
        em.flush();
        return user;
    }

    private Event persistEvent(String title, User creator, EventStatus status) {
        return ServiceCoverageTestHelper.persistEvent(em, title, creator, status, null);
    }

    private void persistReport(Event event) {
        Report report = new Report();
        report.event = event;
        em.persist(report);
        em.flush();
    }
}
