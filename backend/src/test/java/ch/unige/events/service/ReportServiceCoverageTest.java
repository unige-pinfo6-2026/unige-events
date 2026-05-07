package ch.unige.events.service;

import ch.unige.events.dto.report.CreateReportRequest;
import ch.unige.events.dto.report.HandleReportRequest;
import ch.unige.events.dto.report.ReportDTO;
import ch.unige.events.entity.CoOrganizerStatus;
import ch.unige.events.entity.Event;
import ch.unige.events.entity.EventCategory;
import ch.unige.events.entity.EventCoOrganizer;
import ch.unige.events.entity.EventStatus;
import ch.unige.events.entity.Report;
import ch.unige.events.entity.ReportReason;
import ch.unige.events.entity.ReportStatus;
import ch.unige.events.entity.User;
import io.quarkus.test.TestTransaction;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.WebApplicationException;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
@TestProfile(ShareServiceCoverageProfile.class)
class ReportServiceCoverageTest {

    @Inject
    ReportService reportService;

    @Inject
    EntityManager entityManager;

    // -------------------------------------------------------------------------
    // create()
    // -------------------------------------------------------------------------

    @Test
    @TestTransaction
    void create_validBody_persistsRow() {
        User creator = persistUser("auth0|cv-creator", "cv-creator@example.com");
        User reporter = persistUser("auth0|cv-reporter", "cv-reporter@example.com");
        Event event = persistEvent("cv-event", creator, EventStatus.PUBLISHED);

        ReportDTO dto = reportService.create(event.id, reporter.auth0Id,
                new CreateReportRequest(ReportReason.SPAM, "Looks like spam"));

        assertNotNull(dto.id());
        assertEquals(event.id, dto.eventId());
        assertEquals(reporter.id, dto.reporterId());
        assertEquals(ReportReason.SPAM, dto.reason());
        assertEquals("Looks like spam", dto.description());
        assertEquals(ReportStatus.PENDING, dto.status());
        assertNotNull(dto.createdAt());

        long count = Report.count("event.id = ?1 and reporter.id = ?2", event.id, reporter.id);
        assertEquals(1L, count);
    }

    @Test
    @TestTransaction
    void create_eventDraft_throwsBadRequest() {
        User creator = persistUser("auth0|cd-creator", "cd-creator@example.com");
        User reporter = persistUser("auth0|cd-reporter", "cd-reporter@example.com");
        Event event = persistEvent("cd-event", creator, EventStatus.DRAFT);

        WebApplicationException ex = assertThrows(WebApplicationException.class,
                () -> reportService.create(event.id, reporter.auth0Id,
                        new CreateReportRequest(ReportReason.SPAM, null)));
        assertEquals(400, ex.getResponse().getStatus());
    }

    @Test
    @TestTransaction
    void create_eventCancelled_throwsBadRequest() {
        User creator = persistUser("auth0|cc-creator", "cc-creator@example.com");
        User reporter = persistUser("auth0|cc-reporter", "cc-reporter@example.com");
        Event event = persistEvent("cc-event", creator, EventStatus.CANCELLED);

        WebApplicationException ex = assertThrows(WebApplicationException.class,
                () -> reportService.create(event.id, reporter.auth0Id,
                        new CreateReportRequest(ReportReason.SPAM, null)));
        assertEquals(400, ex.getResponse().getStatus());
    }

    @Test
    @TestTransaction
    void create_eventBanned_throwsBadRequest() {
        User creator = persistUser("auth0|cb-creator", "cb-creator@example.com");
        User reporter = persistUser("auth0|cb-reporter", "cb-reporter@example.com");
        Event event = persistEvent("cb-event", creator, EventStatus.BANNED);

        WebApplicationException ex = assertThrows(WebApplicationException.class,
                () -> reportService.create(event.id, reporter.auth0Id,
                        new CreateReportRequest(ReportReason.SPAM, null)));
        assertEquals(400, ex.getResponse().getStatus());
    }

    @Test
    @TestTransaction
    void create_ownEventByCreator_throws422() {
        User creator = persistUser("auth0|own-creator", "own-creator@example.com");
        Event event = persistEvent("own-event", creator, EventStatus.PUBLISHED);

        WebApplicationException ex = assertThrows(WebApplicationException.class,
                () -> reportService.create(event.id, creator.auth0Id,
                        new CreateReportRequest(ReportReason.SPAM, null)));
        assertEquals(422, ex.getResponse().getStatus());
    }

    @Test
    @TestTransaction
    void create_ownEventByAcceptedCoOrganizer_throws422() {
        User creator = persistUser("auth0|aco-creator", "aco-creator@example.com");
        User coOrg = persistUser("auth0|aco-coorg", "aco-coorg@example.com");
        Event event = persistEvent("aco-event", creator, EventStatus.PUBLISHED);
        persistCoOrganizer(event.id, coOrg.id, CoOrganizerStatus.ACCEPTED);

        WebApplicationException ex = assertThrows(WebApplicationException.class,
                () -> reportService.create(event.id, coOrg.auth0Id,
                        new CreateReportRequest(ReportReason.SPAM, null)));
        assertEquals(422, ex.getResponse().getStatus());
    }

    /**
     * Sentinel cascade — un co-organisateur PENDING n'a PAS les permissions du créateur,
     * donc il peut signaler l'event (cohérent avec la règle SCRUM-136).
     */
    @Test
    @TestTransaction
    void create_byPendingCoOrganizer_works() {
        User creator = persistUser("auth0|pco-creator", "pco-creator@example.com");
        User coOrg = persistUser("auth0|pco-coorg", "pco-coorg@example.com");
        Event event = persistEvent("pco-event", creator, EventStatus.PUBLISHED);
        persistCoOrganizer(event.id, coOrg.id, CoOrganizerStatus.PENDING);

        ReportDTO dto = reportService.create(event.id, coOrg.auth0Id,
                new CreateReportRequest(ReportReason.OTHER, null));

        assertNotNull(dto.id());
        assertEquals(ReportStatus.PENDING, dto.status());
    }

    @Test
    @TestTransaction
    void create_alreadyReported_throwsConflict() {
        User creator = persistUser("auth0|ar-creator", "ar-creator@example.com");
        User reporter = persistUser("auth0|ar-reporter", "ar-reporter@example.com");
        Event event = persistEvent("ar-event", creator, EventStatus.PUBLISHED);

        reportService.create(event.id, reporter.auth0Id,
                new CreateReportRequest(ReportReason.SPAM, null));

        WebApplicationException ex = assertThrows(WebApplicationException.class,
                () -> reportService.create(event.id, reporter.auth0Id,
                        new CreateReportRequest(ReportReason.OTHER, null)));
        assertEquals(409, ex.getResponse().getStatus());
    }

    @Test
    @TestTransaction
    void create_eventNotFound_throws404() {
        persistUser("auth0|nf-reporter", "nf-reporter@example.com");

        assertThrows(NotFoundException.class,
                () -> reportService.create(99999L, "auth0|nf-reporter",
                        new CreateReportRequest(ReportReason.SPAM, null)));
    }

    @Test
    @TestTransaction
    void create_userNotProvisioned_throws404() {
        User creator = persistUser("auth0|up-creator", "up-creator@example.com");
        Event event = persistEvent("up-event", creator, EventStatus.PUBLISHED);

        assertThrows(NotFoundException.class,
                () -> reportService.create(event.id, "auth0|ghost-reporter",
                        new CreateReportRequest(ReportReason.SPAM, null)));
    }

    // -------------------------------------------------------------------------
    // listByStatus()
    // -------------------------------------------------------------------------

    @Test
    @TestTransaction
    void listByStatus_pending_returnsOnlyPending() {
        User creator = persistUser("auth0|lp-creator", "lp-creator@example.com");
        User reporter = persistUser("auth0|lp-reporter", "lp-reporter@example.com");
        Event eventA = persistEvent("lp-a", creator, EventStatus.PUBLISHED);
        Event eventB = persistEvent("lp-b", creator, EventStatus.PUBLISHED);
        Event eventC = persistEvent("lp-c", creator, EventStatus.PUBLISHED);
        persistReport(eventA, reporter, ReportStatus.PENDING);
        persistReport(eventB, reporter, ReportStatus.PENDING);
        persistReport(eventC, reporter, ReportStatus.REVIEWED);

        List<ReportDTO> result = reportService.listByStatus(ReportStatus.PENDING, 0, 20);

        assertEquals(2, result.size());
        assertTrue(result.stream().allMatch(r -> r.status() == ReportStatus.PENDING));
    }

    @Test
    @TestTransaction
    void listByStatus_dismissed_returnsOnlyDismissed() {
        User creator = persistUser("auth0|ld-creator", "ld-creator@example.com");
        User reporter = persistUser("auth0|ld-reporter", "ld-reporter@example.com");
        Event event = persistEvent("ld-event", creator, EventStatus.PUBLISHED);
        persistReport(event, reporter, ReportStatus.DISMISSED);

        List<ReportDTO> result = reportService.listByStatus(ReportStatus.DISMISSED, 0, 20);

        assertEquals(1, result.size());
        assertEquals(ReportStatus.DISMISSED, result.get(0).status());
    }

    @Test
    @TestTransaction
    void listByStatus_nullStatus_defaultsToPending() {
        User creator = persistUser("auth0|ln-creator", "ln-creator@example.com");
        User reporter = persistUser("auth0|ln-reporter", "ln-reporter@example.com");
        Event eventA = persistEvent("ln-a", creator, EventStatus.PUBLISHED);
        Event eventB = persistEvent("ln-b", creator, EventStatus.PUBLISHED);
        persistReport(eventA, reporter, ReportStatus.PENDING);
        persistReport(eventB, reporter, ReportStatus.REVIEWED);

        List<ReportDTO> result = reportService.listByStatus(null, 0, 20);

        assertEquals(1, result.size());
        assertEquals(ReportStatus.PENDING, result.get(0).status());
    }

    @Test
    @TestTransaction
    void listByStatus_orderingByCreatedAtDesc() throws InterruptedException {
        // Report.prePersist() écrase createdAt = now() inconditionnellement (pas de
        // null-guard, comportement hérité de SCRUM-103 hors scope SCRUM-94). On
        // s'appuie donc sur l'ordre d'insertion naturel + un petit sleep pour garantir
        // des timestamps strictement distincts entre les deux rows.
        User creator = persistUser("auth0|lo-creator", "lo-creator@example.com");
        User reporter = persistUser("auth0|lo-reporter", "lo-reporter@example.com");
        Event eventA = persistEvent("lo-a", creator, EventStatus.PUBLISHED);
        Event eventB = persistEvent("lo-b", creator, EventStatus.PUBLISHED);
        persistReport(eventA, reporter, ReportStatus.PENDING);
        Thread.sleep(10);
        persistReport(eventB, reporter, ReportStatus.PENDING);

        List<ReportDTO> result = reportService.listByStatus(ReportStatus.PENDING, 0, 20);

        assertEquals(2, result.size());
        assertTrue(result.get(0).createdAt().isAfter(result.get(1).createdAt()));
    }

    @Test
    @TestTransaction
    void listByStatus_pagination() {
        User creator = persistUser("auth0|lpg-creator", "lpg-creator@example.com");
        User reporter = persistUser("auth0|lpg-reporter", "lpg-reporter@example.com");
        for (int i = 0; i < 5; i++) {
            Event e = persistEvent("lpg-" + i, creator, EventStatus.PUBLISHED);
            persistReport(e, reporter, ReportStatus.PENDING);
        }

        List<ReportDTO> page0 = reportService.listByStatus(ReportStatus.PENDING, 0, 2);
        assertEquals(2, page0.size());
    }

    @Test
    @TestTransaction
    void listByStatus_empty_returnsEmptyList() {
        List<ReportDTO> result = reportService.listByStatus(ReportStatus.PENDING, 0, 20);
        assertTrue(result.isEmpty());
    }

    // -------------------------------------------------------------------------
    // handle()
    // -------------------------------------------------------------------------

    @Test
    @TestTransaction
    void handle_pendingToReviewed_bansEventAndCascadesSiblings() {
        User creator = persistUser("auth0|hr-creator", "hr-creator@example.com");
        User reporterA = persistUser("auth0|hr-reporter-a", "hr-reporter-a@example.com");
        User reporterB = persistUser("auth0|hr-reporter-b", "hr-reporter-b@example.com");
        User reporterC = persistUser("auth0|hr-reporter-c", "hr-reporter-c@example.com");
        User admin = persistUser("auth0|hr-admin", "hr-admin@example.com");
        Event event = persistEvent("hr-event", creator, EventStatus.PUBLISHED);
        Report reportA = persistReport(event, reporterA, ReportStatus.PENDING);
        Report reportB = persistReport(event, reporterB, ReportStatus.PENDING);
        Report reportC = persistReport(event, reporterC, ReportStatus.PENDING);

        ReportDTO dto = reportService.handle(reportA.id, admin.auth0Id,
                new HandleReportRequest(ReportStatus.REVIEWED, "OK validated"));

        // The validated report carries the admin note and gets the explicit decision.
        assertEquals(ReportStatus.REVIEWED, dto.status());
        assertEquals("OK validated", dto.moderationNote());
        assertEquals(admin.id, dto.reviewedBy());
        assertNotNull(dto.reviewedAt());

        // The event itself is banned — definitive removal from the radar.
        Event refreshed = Event.findById(event.id);
        assertEquals(EventStatus.BANNED, refreshed.status);

        // Sibling PENDING reports are auto-closed with the same admin attribution
        // but no inherited moderation note (only the explicit decision carries one).
        Report cascadedB = Report.findById(reportB.id);
        Report cascadedC = Report.findById(reportC.id);
        assertEquals(ReportStatus.REVIEWED, cascadedB.status);
        assertEquals(ReportStatus.REVIEWED, cascadedC.status);
        assertEquals(admin.id, cascadedB.reviewedBy.id);
        assertEquals(admin.id, cascadedC.reviewedBy.id);
        assertNotNull(cascadedB.reviewedAt);
        assertNotNull(cascadedC.reviewedAt);
        assertNull(cascadedB.moderationNote);
        assertNull(cascadedC.moderationNote);
    }

    @Test
    @TestTransaction
    void handle_pendingToDismissed_persistsAndDoesNotBanOrCascade() {
        User creator = persistUser("auth0|hd-creator", "hd-creator@example.com");
        User reporterA = persistUser("auth0|hd-reporter-a", "hd-reporter-a@example.com");
        User reporterB = persistUser("auth0|hd-reporter-b", "hd-reporter-b@example.com");
        User admin = persistUser("auth0|hd-admin", "hd-admin@example.com");
        Event event = persistEvent("hd-event", creator, EventStatus.PUBLISHED);
        Report reportA = persistReport(event, reporterA, ReportStatus.PENDING);
        Report reportB = persistReport(event, reporterB, ReportStatus.PENDING);

        ReportDTO dto = reportService.handle(reportA.id, admin.auth0Id,
                new HandleReportRequest(ReportStatus.DISMISSED, null));

        assertEquals(ReportStatus.DISMISSED, dto.status());
        assertNull(dto.moderationNote());
        assertEquals(admin.id, dto.reviewedBy());

        // Dismiss is neutral: event stays PUBLISHED, sibling reports stay PENDING.
        Event refreshed = Event.findById(event.id);
        assertEquals(EventStatus.PUBLISHED, refreshed.status);
        Report stillPending = Report.findById(reportB.id);
        assertEquals(ReportStatus.PENDING, stillPending.status);
        assertNull(stillPending.reviewedBy);
        assertNull(stillPending.reviewedAt);
    }

    @Test
    @TestTransaction
    void handle_alreadyReviewed_throwsConflict() {
        User creator = persistUser("auth0|hc-creator", "hc-creator@example.com");
        User reporter = persistUser("auth0|hc-reporter", "hc-reporter@example.com");
        User admin = persistUser("auth0|hc-admin", "hc-admin@example.com");
        Event event = persistEvent("hc-event", creator, EventStatus.PUBLISHED);
        Report report = persistReport(event, reporter, ReportStatus.REVIEWED);

        WebApplicationException ex = assertThrows(WebApplicationException.class,
                () -> reportService.handle(report.id, admin.auth0Id,
                        new HandleReportRequest(ReportStatus.DISMISSED, null)));
        assertEquals(409, ex.getResponse().getStatus());
    }

    @Test
    @TestTransaction
    void handle_invalidTargetStatusPending_throwsBadRequest() {
        User admin = persistUser("auth0|hi-admin", "hi-admin@example.com");

        WebApplicationException ex = assertThrows(WebApplicationException.class,
                () -> reportService.handle(1L, admin.auth0Id,
                        new HandleReportRequest(ReportStatus.PENDING, null)));
        assertEquals(400, ex.getResponse().getStatus());
    }

    @Test
    @TestTransaction
    void handle_reportNotFound_throws404() {
        User admin = persistUser("auth0|hn-admin", "hn-admin@example.com");

        assertThrows(NotFoundException.class,
                () -> reportService.handle(99999L, admin.auth0Id,
                        new HandleReportRequest(ReportStatus.REVIEWED, null)));
    }

    @Test
    @TestTransaction
    void handle_adminNotProvisioned_throws404() {
        User creator = persistUser("auth0|ha-creator", "ha-creator@example.com");
        User reporter = persistUser("auth0|ha-reporter", "ha-reporter@example.com");
        Event event = persistEvent("ha-event", creator, EventStatus.PUBLISHED);
        Report report = persistReport(event, reporter, ReportStatus.PENDING);

        assertThrows(NotFoundException.class,
                () -> reportService.handle(report.id, "auth0|ghost-admin",
                        new HandleReportRequest(ReportStatus.REVIEWED, null)));
    }

    @Test
    @TestTransaction
    void handle_setsReviewedAtClose() {
        User creator = persistUser("auth0|hs-creator", "hs-creator@example.com");
        User reporter = persistUser("auth0|hs-reporter", "hs-reporter@example.com");
        User admin = persistUser("auth0|hs-admin", "hs-admin@example.com");
        Event event = persistEvent("hs-event", creator, EventStatus.PUBLISHED);
        Report report = persistReport(event, reporter, ReportStatus.PENDING);

        LocalDateTime before = LocalDateTime.now().minusSeconds(1);
        ReportDTO dto = reportService.handle(report.id, admin.auth0Id,
                new HandleReportRequest(ReportStatus.REVIEWED, null));
        LocalDateTime after = LocalDateTime.now().plusSeconds(1);

        assertNotNull(dto.reviewedAt());
        assertTrue(dto.reviewedAt().isAfter(before));
        assertTrue(dto.reviewedAt().isBefore(after));
    }

    @Test
    @TestTransaction
    void create_descriptionPersistedCorrectly() {
        User creator = persistUser("auth0|de-creator", "de-creator@example.com");
        User reporter = persistUser("auth0|de-reporter", "de-reporter@example.com");
        Event event = persistEvent("de-event", creator, EventStatus.PUBLISHED);
        String longText = "Long description that contains plenty of context about the issue.";

        ReportDTO dto = reportService.create(event.id, reporter.auth0Id,
                new CreateReportRequest(ReportReason.FAKE, longText));

        assertEquals(longText, dto.description());
        Report fromDb = Report.findById(dto.id());
        assertEquals(longText, fromDb.description);
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private User persistUser(String auth0Id, String email) {
        User user = new User();
        user.auth0Id = auth0Id;
        user.email = email;
        user.profilePublic = false;
        user.createdAt = LocalDateTime.now();
        entityManager.persist(user);
        entityManager.flush();
        return user;
    }

    private Event persistEvent(String title, User creator, EventStatus status) {
        Event event = new Event();
        event.title = title;
        event.location = "Uni Mail";
        event.startDate = LocalDateTime.now().plusDays(1);
        event.endDate = LocalDateTime.now().plusDays(2);
        event.category = EventCategory.ACADEMIC;
        event.status = status;
        event.creator = creator;
        entityManager.persist(event);
        entityManager.flush();
        return event;
    }

    private Report persistReport(Event event, User reporter, ReportStatus status) {
        Report r = new Report();
        r.event = event;
        r.reporter = reporter;
        r.reason = ReportReason.OTHER;
        r.status = status;
        entityManager.persist(r);
        entityManager.flush();
        return r;
    }

    private void persistCoOrganizer(Long eventId, java.util.UUID userId, CoOrganizerStatus status) {
        EventCoOrganizer e = new EventCoOrganizer();
        e.eventId = eventId;
        e.userId = userId;
        e.status = status;
        entityManager.persist(e);
        entityManager.flush();
    }
}
