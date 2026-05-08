package ch.unige.events.service;

import ch.unige.events.dto.event.CreateEventRequest;
import ch.unige.events.dto.event.EventDTO;
import ch.unige.events.dto.event.UpdateEventRequest;
import ch.unige.events.entity.Attendance;
import ch.unige.events.entity.AttendanceStatus;
import ch.unige.events.entity.Event;
import ch.unige.events.entity.EventCategory;
import ch.unige.events.entity.EventStatus;
import ch.unige.events.entity.EventView;
import ch.unige.events.entity.Faculty;
import ch.unige.events.entity.Favorite;
import ch.unige.events.entity.User;
import ch.unige.events.exception.InvalidFileTypeException;

import java.util.UUID;
import io.quarkus.test.TestTransaction;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.ForbiddenException;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.WebApplicationException;
import org.jboss.resteasy.reactive.multipart.FileUpload;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
@TestProfile(ShareServiceCoverageProfile.class)
class EventServiceCoverageTest {

    @Inject
    EventService eventService;

    @Inject
    EntityManager entityManager;

    // --- getAll ---

    @Test
    @TestTransaction
    void getAll_noFilters_returnsAll() {
        User user = persistUser("auth0|a", "a@example.com");
        persistEvent("Event 1", EventCategory.ACADEMIC, EventStatus.DRAFT, user);
        persistEvent("Event 2", EventCategory.SPORTS, EventStatus.PUBLISHED, user);

        List<EventDTO> result = eventService.getAll(0, 20, null, null, null, null, null, null, null);

        assertEquals(2, result.size());
    }

    @Test
    @TestTransaction
    void getAll_noFilters_excludesExpiredEvents() {
        User user = persistUser("auth0|exp-excl", "exp-excl@example.com");
        persistEvent("Published", EventCategory.ACADEMIC, EventStatus.PUBLISHED, user);
        persistEvent("Expired", EventCategory.ACADEMIC, EventStatus.EXPIRED, user);

        List<EventDTO> result = eventService.getAll(0, 20, null, null, null, null, null, null, null);

        assertEquals(1, result.size());
        assertEquals("Published", result.get(0).title());
    }

    @Test
    @TestTransaction
    void getAll_withStatusFilter_returnsFiltered() {
        User user = persistUser("auth0|b", "b@example.com");
        persistEvent("Draft", EventCategory.ACADEMIC, EventStatus.DRAFT, user);
        persistEvent("Published", EventCategory.ACADEMIC, EventStatus.PUBLISHED, user);

        List<EventDTO> result = eventService.getAll(0, 20, EventStatus.PUBLISHED, null, null, null, null, null, null);

        assertEquals(1, result.size());
        assertEquals("Published", result.get(0).title());
    }

    @Test
    @TestTransaction
    void getAll_withCategoryFilter_returnsFiltered() {
        User user = persistUser("auth0|c", "c@example.com");
        persistEvent("Academic", EventCategory.ACADEMIC, EventStatus.DRAFT, user);
        persistEvent("Sports", EventCategory.SPORTS, EventStatus.DRAFT, user);

        List<EventDTO> result = eventService.getAll(0, 20, null, EventCategory.SPORTS, null, null, null, null, null);

        assertEquals(1, result.size());
        assertEquals("Sports", result.get(0).title());
    }

    @Test
    @TestTransaction
    void getAll_withOrganizerIdFilter_returnsFiltered() {
        User alice = persistUser("auth0|alice", "alice@example.com");
        User bob = persistUser("auth0|bob", "bob@example.com");
        persistEvent("Alice's event", EventCategory.ACADEMIC, EventStatus.DRAFT, alice);
        persistEvent("Bob's event", EventCategory.ACADEMIC, EventStatus.DRAFT, bob);

        List<EventDTO> result = eventService.getAll(0, 20, null, null, alice.id, null, null, null, null);

        assertEquals(1, result.size());
        assertEquals("Alice's event", result.get(0).title());
    }

    @Test
    @TestTransaction
    void getAll_withEndDateFromFilter_excludesEndedEvents() {
        User user = persistUser("auth0|edf", "edf@example.com");
        persistEvent("Active Event", EventCategory.ACADEMIC, EventStatus.PUBLISHED, user);
        persistEvent("Also Active", EventCategory.SPORTS, EventStatus.PUBLISHED, user);

        List<EventDTO> result = eventService.getAll(0, 20, null, null, null, LocalDateTime.now().minusDays(1), null, null, null);

        assertEquals(2, result.size());
    }

    @Test
    @TestTransaction
    void getAll_withPagination_returnsPage() {
        User user = persistUser("auth0|page", "page@example.com");
        persistEvent("E1", EventCategory.ACADEMIC, EventStatus.DRAFT, user);
        persistEvent("E2", EventCategory.ACADEMIC, EventStatus.DRAFT, user);
        persistEvent("E3", EventCategory.ACADEMIC, EventStatus.DRAFT, user);

        List<EventDTO> page0 = eventService.getAll(0, 2, null, null, null, null, null, null, null);
        List<EventDTO> page1 = eventService.getAll(1, 2, null, null, null, null, null, null, null);

        assertEquals(2, page0.size());
        assertEquals(1, page1.size());
    }

    @Test
    @TestTransaction
    void getAll_withFeaturedTrue_returnsOnlyFeatured() {
        User user = persistUser("auth0|feat", "feat@example.com");
        Event featured = persistEvent("Featured", EventCategory.ACADEMIC, EventStatus.PUBLISHED, user);
        featured.featured = true;
        persistEvent("Plain", EventCategory.SPORTS, EventStatus.PUBLISHED, user);

        List<EventDTO> result = eventService.getAll(0, 20, null, null, null, null, null, null, true);

        assertEquals(1, result.size());
        assertEquals("Featured", result.get(0).title());
    }

    @Test
    @TestTransaction
    void getAll_withFeaturedFalse_isANoop_returnsAll() {
        // Boolean.TRUE.equals(false) → false, so the featured branch must not engage.
        User user = persistUser("auth0|featf", "featf@example.com");
        Event featured = persistEvent("Featured", EventCategory.ACADEMIC, EventStatus.PUBLISHED, user);
        featured.featured = true;
        persistEvent("Plain", EventCategory.SPORTS, EventStatus.PUBLISHED, user);

        List<EventDTO> result = eventService.getAll(0, 20, null, null, null, null, null, null, false);

        assertEquals(2, result.size());
    }

    // --- getMyEvents (SCRUM-133) ---

    @Test
    @TestTransaction
    void getMyEvents_returnsAllStatuses() {
        User user = persistUser("auth0|me-all", "me-all@example.com");
        persistEvent("Draft", EventCategory.ACADEMIC, EventStatus.DRAFT, user);
        persistEvent("Published", EventCategory.ACADEMIC, EventStatus.PUBLISHED, user);
        persistEvent("Cancelled", EventCategory.ACADEMIC, EventStatus.CANCELLED, user);

        List<EventDTO> result = eventService.getMyEvents("auth0|me-all", null, 0, 20);

        assertEquals(3, result.size());
    }

    @Test
    @TestTransaction
    void getMyEvents_filtersOnStatus() {
        User user = persistUser("auth0|me-filter", "me-filter@example.com");
        persistEvent("Draft", EventCategory.ACADEMIC, EventStatus.DRAFT, user);
        persistEvent("Published", EventCategory.ACADEMIC, EventStatus.PUBLISHED, user);
        persistEvent("Cancelled", EventCategory.ACADEMIC, EventStatus.CANCELLED, user);

        List<EventDTO> drafts = eventService.getMyEvents("auth0|me-filter", EventStatus.DRAFT, 0, 20);

        assertEquals(1, drafts.size());
        assertEquals("Draft", drafts.get(0).title());
    }

    @Test
    @TestTransaction
    void getMyEvents_ordersByCreatedAtDesc() {
        User user = persistUser("auth0|me-order", "me-order@example.com");
        LocalDateTime base = LocalDateTime.now().minusDays(3);
        persistEventWithCreatedAt("Oldest", EventStatus.DRAFT, user, base);
        persistEventWithCreatedAt("Middle", EventStatus.DRAFT, user, base.plusHours(1));
        persistEventWithCreatedAt("Newest", EventStatus.DRAFT, user, base.plusHours(2));

        List<EventDTO> result = eventService.getMyEvents("auth0|me-order", null, 0, 20);

        assertEquals(3, result.size());
        assertEquals("Newest", result.get(0).title());
        assertEquals("Middle", result.get(1).title());
        assertEquals("Oldest", result.get(2).title());
    }

    @Test
    @TestTransaction
    void getMyEvents_emptyForUnrelatedUser() {
        User alice = persistUser("auth0|me-alice", "alice@example.com");
        persistUser("auth0|me-bob", "bob@example.com");
        persistEvent("Alice event", EventCategory.ACADEMIC, EventStatus.DRAFT, alice);

        List<EventDTO> bobEvents = eventService.getMyEvents("auth0|me-bob", null, 0, 20);

        assertEquals(0, bobEvents.size());
    }

    @Test
    @TestTransaction
    void getMyEvents_userNotFound_throwsNotFound() {

        assertThrows(NotFoundException.class,
                () -> eventService.getMyEvents("auth0|me-unknown", null, 0, 20));
    }

    @Test
    @TestTransaction
    void getMyEvents_populatesAvailableSpotsAndWaitlistedCount() {
        User creator = persistUser("auth0|me-cap", "cap@example.com");
        User attender1 = persistUser("auth0|me-att1", "att1@example.com");
        User attender2 = persistUser("auth0|me-att2", "att2@example.com");
        User waiter = persistUser("auth0|me-wait", "wait@example.com");
        Event event = persistEvent("Capacity event", EventCategory.ACADEMIC, EventStatus.PUBLISHED, creator);
        event.capacity = 2;
        entityManager.flush();
        persistAttendanceForEvent(event.id, attender1.id, AttendanceStatus.ATTENDING);
        persistAttendanceForEvent(event.id, attender2.id, AttendanceStatus.ATTENDING);
        persistAttendanceForEvent(event.id, waiter.id, AttendanceStatus.WAITLISTED);
        entityManager.flush();

        List<EventDTO> result = eventService.getMyEvents("auth0|me-cap", null, 0, 20);

        assertEquals(1, result.size());
        assertEquals(2L, result.get(0).attendingCount());
        assertEquals(0L, result.get(0).availableSpots());
        assertEquals(1L, result.get(0).waitlistedCount());
    }

    @Test
    @TestTransaction
    void getMyEvents_nullCapacityReturnsNullAvailableSpots() {
        User creator = persistUser("auth0|me-nocap", "nocap@example.com");
        persistEvent("Uncapped", EventCategory.ACADEMIC, EventStatus.DRAFT, creator);

        List<EventDTO> result = eventService.getMyEvents("auth0|me-nocap", null, 0, 20);

        assertEquals(1, result.size());
        assertNull(result.get(0).availableSpots());
    }

    @Test
    @TestTransaction
    void getMyEvents_pagination() {
        User user = persistUser("auth0|me-page", "page@example.com");
        LocalDateTime base = LocalDateTime.now().minusDays(5);
        for (int i = 0; i < 5; i++) {
            persistEventWithCreatedAt("E" + i, EventStatus.DRAFT, user, base.plusMinutes(i));
        }

        List<EventDTO> page0 = eventService.getMyEvents("auth0|me-page", null, 0, 2);
        List<EventDTO> page1 = eventService.getMyEvents("auth0|me-page", null, 1, 2);
        List<EventDTO> page2 = eventService.getMyEvents("auth0|me-page", null, 2, 2);

        assertEquals(2, page0.size());
        assertEquals(2, page1.size());
        assertEquals(1, page2.size());
    }

    @Test
    @TestTransaction
    void getMyEvents_tieBreakerById() {
        User user = persistUser("auth0|me-tie", "tie@example.com");
        LocalDateTime sameTs = LocalDateTime.now().minusHours(1);
        Event first = persistEventWithCreatedAt("First", EventStatus.DRAFT, user, sameTs);
        Event second = persistEventWithCreatedAt("Second", EventStatus.DRAFT, user, sameTs);

        List<EventDTO> result = eventService.getMyEvents("auth0|me-tie", null, 0, 20);

        assertEquals(2, result.size());
        // Tie-breaker is id DESC → second (higher id) comes first.
        assertTrue(second.id > first.id);
        assertEquals(second.id, result.get(0).id());
        assertEquals(first.id, result.get(1).id());
    }

    // Regression guard: PR #145 review (SCRUM-135) surfaced that the EXPIRED tab on
    // MyPublicationsPage appeared empty. Root-cause investigation showed the JPQL
    // correctly filters by creator — these tests pin that behavior so future changes
    // can't silently regress it (PR #125, SCRUM-98 originally shipped without
    // EXPIRED-path coverage).
    @Test
    @TestTransaction
    void getMyEvents_filterExpired_returnsOnlyExpiredEventsForCreator() {
        User user = persistUser("auth0|me-expired", "expired@example.com");
        persistEvent("Published title", EventCategory.ACADEMIC, EventStatus.PUBLISHED, user);
        persistEvent("Expired title",   EventCategory.ACADEMIC, EventStatus.EXPIRED,   user);

        List<EventDTO> result = eventService.getMyEvents("auth0|me-expired", EventStatus.EXPIRED, 0, 20);

        assertEquals(1, result.size());
        assertEquals("Expired title", result.get(0).title());
        assertEquals(EventStatus.EXPIRED, result.get(0).status());
    }

    @Test
    @TestTransaction
    void getMyEvents_filterExpired_excludesOtherUsersExpiredEvents() {
        User me    = persistUser("auth0|me-expired",    "me@example.com");
        User other = persistUser("auth0|other-expired", "other@example.com");
        persistEvent("Mine",   EventCategory.ACADEMIC, EventStatus.EXPIRED, me);
        persistEvent("Theirs", EventCategory.ACADEMIC, EventStatus.EXPIRED, other);

        List<EventDTO> result = eventService.getMyEvents("auth0|me-expired", EventStatus.EXPIRED, 0, 20);

        assertEquals(1, result.size());
        assertEquals("Mine", result.get(0).title());
    }

    // --- create ---

    @Test
    @TestTransaction
    void create_withExistingUser_linksCreator() {
        persistUser("auth0|creator", "creator@example.com");

        CreateEventRequest req = validCreateRequest();
        EventDTO result = eventService.create("auth0|creator", req);

        assertNotNull(result.creatorId());
        assertEquals("Test Event", result.title());
    }

    @Test
    @TestTransaction
    void create_withUnknownUser_throwsNotFoundException() {

        CreateEventRequest req = validCreateRequest();
        assertThrows(NotFoundException.class, () -> eventService.create("auth0|unknown", req));
    }

    @Test
    @TestTransaction
    void create_withPublishedStatus_persistsPublished() {
        persistUser("auth0|pub", "pub@example.com");

        CreateEventRequest req = validCreateRequest();
        req.setStatus(EventStatus.PUBLISHED);
        EventDTO result = eventService.create("auth0|pub", req);

        assertEquals(EventStatus.PUBLISHED, result.status());
    }

    @Test
    @TestTransaction
    void create_withoutStatus_defaultsToDraft() {
        persistUser("auth0|draft", "draft@example.com");

        CreateEventRequest req = validCreateRequest();
        req.setStatus(null);
        EventDTO result = eventService.create("auth0|draft", req);

        assertEquals(EventStatus.DRAFT, result.status());
    }

    @Test
    @TestTransaction
    void create_withCancelledStatus_throwsBadRequest() {
        persistUser("auth0|cancelled", "cancelled@example.com");

        CreateEventRequest req = validCreateRequest();
        req.setStatus(EventStatus.CANCELLED);

        assertThrows(BadRequestException.class, () -> eventService.create("auth0|cancelled", req));
    }

    @Test
    @TestTransaction
    void create_withExpiredStatus_throwsBadRequest() {
        persistUser("auth0|expired-create", "expired-create@example.com");

        CreateEventRequest req = validCreateRequest();
        req.setStatus(EventStatus.EXPIRED);

        assertThrows(BadRequestException.class, () -> eventService.create("auth0|expired-create", req));
    }

    @Test
    @TestTransaction
    void update_withExpiredStatus_throwsBadRequest() {
        User user = persistUser("auth0|expired-upd", "expired-upd@example.com");
        Event event = persistEvent("Active", EventCategory.ACADEMIC, EventStatus.PUBLISHED, user);

        UpdateEventRequest req = validUpdateRequest("Active", EventCategory.ACADEMIC, EventStatus.EXPIRED);

        assertThrows(BadRequestException.class, () -> eventService.update(event.id, "auth0|expired-upd", req));
    }

    // --- SCRUM-97: BANNED is moderation-only and terminal for the creator ---

    @Test
    @TestTransaction
    void create_withBannedStatus_throwsBadRequest() {
        persistUser("auth0|banned-create", "banned-create@example.com");

        CreateEventRequest req = validCreateRequest();
        req.setStatus(EventStatus.BANNED);

        assertThrows(BadRequestException.class, () -> eventService.create("auth0|banned-create", req));
    }

    @Test
    @TestTransaction
    void update_withBannedStatus_throwsBadRequest() {
        User user = persistUser("auth0|banned-upd-target", "banned-upd-target@example.com");
        Event event = persistEvent("Active", EventCategory.ACADEMIC, EventStatus.PUBLISHED, user);

        UpdateEventRequest req = validUpdateRequest("Active", EventCategory.ACADEMIC, EventStatus.BANNED);

        assertThrows(BadRequestException.class, () -> eventService.update(event.id, "auth0|banned-upd-target", req));
    }

    @Test
    @TestTransaction
    void update_bannedEvent_throwsConflict() {
        User user = persistUser("auth0|banned-evt-upd", "banned-evt-upd@example.com");
        Event event = persistEvent("Already banned", EventCategory.ACADEMIC, EventStatus.BANNED, user);

        UpdateEventRequest req = validUpdateRequest("Tentative de modif", EventCategory.ACADEMIC, EventStatus.PUBLISHED);

        WebApplicationException ex = assertThrows(WebApplicationException.class,
                () -> eventService.update(event.id, "auth0|banned-evt-upd", req));
        assertEquals(409, ex.getResponse().getStatus());
    }

    @Test
    @TestTransaction
    void cancel_bannedEvent_throwsConflict() {
        User user = persistUser("auth0|banned-cancel", "banned-cancel@example.com");
        Event event = persistEvent("Banni", EventCategory.ACADEMIC, EventStatus.BANNED, user);

        WebApplicationException ex = assertThrows(WebApplicationException.class,
                () -> eventService.cancel(event.id, "auth0|banned-cancel"));
        assertEquals(409, ex.getResponse().getStatus());
    }

    @Test
    @TestTransaction
    void getById_bannedEvent_anon_throwsNotFound() {
        User user = persistUser("auth0|banned-anon", "banned-anon@example.com");
        Event event = persistEvent("Banni", EventCategory.ACADEMIC, EventStatus.BANNED, user);

        assertThrows(NotFoundException.class, () -> eventService.getById(event.id, null, false));
    }

    @Test
    @TestTransaction
    void getById_bannedEvent_creator_throwsNotFound() {
        // Anti-leak: even the creator cannot retrieve a banned event by id.
        User user = persistUser("auth0|banned-creator", "banned-creator@example.com");
        Event event = persistEvent("Banni", EventCategory.ACADEMIC, EventStatus.BANNED, user);

        assertThrows(NotFoundException.class,
                () -> eventService.getById(event.id, "auth0|banned-creator", false));
    }

    @Test
    @TestTransaction
    void getById_bannedEvent_admin_throwsNotFound() {
        // Even an admin gets 404 — drill-down must happen via /admin/reports.
        User user = persistUser("auth0|banned-admin-target", "banned-admin-target@example.com");
        Event event = persistEvent("Banni", EventCategory.ACADEMIC, EventStatus.BANNED, user);

        assertThrows(NotFoundException.class, () -> eventService.getById(event.id, "auth0|admin", true));
    }

    @Test
    @TestTransaction
    void getAll_defaultListing_excludesBannedEvents() {
        User user = persistUser("auth0|listing-banned", "listing-banned@example.com");
        persistEvent("Visible", EventCategory.ACADEMIC, EventStatus.PUBLISHED, user);
        persistEvent("Hidden by ban", EventCategory.ACADEMIC, EventStatus.BANNED, user);

        List<EventDTO> result = eventService.getAll(0, 50, null, null, null, null, null, null, null);

        assertEquals(1, result.size());
        assertEquals("Visible", result.get(0).title());
    }

    // --- getById ---

    @Test
    @TestTransaction
    void getById_existingEvent_returnsDTO() {
        User user = persistUser("auth0|get", "get@example.com");
        Event event = persistEvent("Find Me", EventCategory.CULTURAL, EventStatus.DRAFT, user);

        EventDTO result = eventService.getById(event.id, "auth0|get", false);

        assertEquals(event.id, result.id());
        assertEquals("Find Me", result.title());
    }

    @Test
    @TestTransaction
    void getById_unknownEvent_throwsNotFound() {

        assertThrows(NotFoundException.class, () -> eventService.getById(999999L, null, false));
    }

    // --- ISSUE-92 (pentest 4.12 + 4.15) — getById visibility rule ---

    @Test
    @TestTransaction
    void getById_publishedEvent_anon_returns200() {
        User user = persistUser("auth0|pub-anon", "pub-anon@example.com");
        Event event = persistEvent("Public", EventCategory.ACADEMIC, EventStatus.PUBLISHED, user);

        EventDTO result = eventService.getById(event.id, null, false);

        assertEquals(event.id, result.id());
        assertEquals("Public", result.title());
    }

    @Test
    @TestTransaction
    void getById_draftEvent_anon_throwsNotFound() {
        User user = persistUser("auth0|draft-anon", "draft-anon@example.com");
        Event event = persistEvent("Draft", EventCategory.ACADEMIC, EventStatus.DRAFT, user);

        assertThrows(NotFoundException.class, () -> eventService.getById(event.id, null, false));
    }

    @Test
    @TestTransaction
    void getById_cancelledEvent_anon_throwsNotFound() {
        User user = persistUser("auth0|cancel-anon", "cancel-anon@example.com");
        Event event = persistEvent("Cancelled", EventCategory.ACADEMIC, EventStatus.CANCELLED, user);

        assertThrows(NotFoundException.class, () -> eventService.getById(event.id, null, false));
    }

    @Test
    @TestTransaction
    void getById_draftEvent_otherUser_throwsNotFound() {
        User alice = persistUser("auth0|hide-alice", "hide-alice@example.com");
        persistUser("auth0|hide-bob", "hide-bob@example.com");
        Event event = persistEvent("Alice's draft", EventCategory.ACADEMIC, EventStatus.DRAFT, alice);

        assertThrows(NotFoundException.class,
                () -> eventService.getById(event.id, "auth0|hide-bob", false));
    }

    @Test
    @TestTransaction
    void getById_draftEvent_creator_returns200() {
        User alice = persistUser("auth0|own-draft", "own-draft@example.com");
        Event event = persistEvent("My draft", EventCategory.ACADEMIC, EventStatus.DRAFT, alice);

        EventDTO result = eventService.getById(event.id, "auth0|own-draft", false);

        assertEquals(event.id, result.id());
        assertEquals("My draft", result.title());
    }

    @Test
    @TestTransaction
    void getById_cancelledEvent_creator_returns200() {
        User alice = persistUser("auth0|own-cancel", "own-cancel@example.com");
        Event event = persistEvent("My cancel", EventCategory.ACADEMIC, EventStatus.CANCELLED, alice);

        EventDTO result = eventService.getById(event.id, "auth0|own-cancel", false);

        assertEquals(event.id, result.id());
    }

    @Test
    @TestTransaction
    void getById_draftEvent_admin_returns200() {
        User alice = persistUser("auth0|draft-for-admin", "draft-for-admin@example.com");
        Event event = persistEvent("Inspect me", EventCategory.ACADEMIC, EventStatus.DRAFT, alice);

        EventDTO result = eventService.getById(event.id, "auth0|admin", true);

        assertEquals(event.id, result.id());
    }

    @Test
    @TestTransaction
    void getById_cancelledEvent_admin_returns200() {
        User alice = persistUser("auth0|cancel-for-admin", "cancel-for-admin@example.com");
        Event event = persistEvent("Inspect cancel", EventCategory.ACADEMIC, EventStatus.CANCELLED, alice);

        EventDTO result = eventService.getById(event.id, "auth0|admin", true);

        assertEquals(event.id, result.id());
    }

    @Test
    @TestTransaction
    void getById_draftEvent_authenticatedButNoProfile_throwsNotFound() {
        // auth0Id provided but no matching User row in DB.
        // A user Auth0-valid-but-not-provisioned cannot be a creator (FK on event.creator_id),
        // so the check falls through to 404 on any non-PUBLISHED event.
        User alice = persistUser("auth0|ghost-alice", "ghost-alice@example.com");
        Event event = persistEvent("Alice's draft", EventCategory.ACADEMIC, EventStatus.DRAFT, alice);

        assertThrows(NotFoundException.class,
                () -> eventService.getById(event.id, "auth0|ghost-not-provisioned", false));
    }

    // --- update ---

    @Test
    @TestTransaction
    void update_asCreator_updatesEvent() {
        User user = persistUser("auth0|updater", "updater@example.com");
        Event event = persistEvent("Old Title", EventCategory.ACADEMIC, EventStatus.DRAFT, user);

        UpdateEventRequest req = validUpdateRequest("New Title", EventCategory.CULTURAL, null);
        EventDTO result = eventService.update(event.id, "auth0|updater", req);

        assertEquals("New Title", result.title());
        assertEquals(EventCategory.CULTURAL, result.category());
    }

    @Test
    @TestTransaction
    void update_withStatusChange_updatesStatus() {
        User user = persistUser("auth0|status", "status@example.com");
        Event event = persistEvent("Title", EventCategory.ACADEMIC, EventStatus.DRAFT, user);

        UpdateEventRequest req = validUpdateRequest("Title", EventCategory.ACADEMIC, EventStatus.PUBLISHED);
        EventDTO result = eventService.update(event.id, "auth0|status", req);

        assertEquals(EventStatus.PUBLISHED, result.status());
    }

    @Test
    @TestTransaction
    void update_unknownEvent_throwsNotFound() {

        UpdateEventRequest req = validUpdateRequest("X", EventCategory.ACADEMIC, null);
        assertThrows(NotFoundException.class, () -> eventService.update(999999L, "auth0|x", req));
    }

    @Test
    @TestTransaction
    void update_nullCreator_throwsForbidden() {
        Event event = persistEvent("No Creator", EventCategory.ACADEMIC, EventStatus.DRAFT, null);

        UpdateEventRequest req = validUpdateRequest("X", EventCategory.ACADEMIC, null);
        assertThrows(ForbiddenException.class, () -> eventService.update(event.id, "auth0|x", req));
    }

    @Test
    @TestTransaction
    void update_differentUser_throwsForbidden() {
        User user = persistUser("auth0|owner", "owner@example.com");
        Event event = persistEvent("Owner's Event", EventCategory.ACADEMIC, EventStatus.DRAFT, user);

        UpdateEventRequest req = validUpdateRequest("X", EventCategory.ACADEMIC, null);
        assertThrows(ForbiddenException.class, () -> eventService.update(event.id, "auth0|intruder", req));
    }

    // --- delete ---

    @Test
    @TestTransaction
    void delete_cancelledEvent_asCreator_removesEntity() {
        User user = persistUser("auth0|deleter", "deleter@example.com");
        Event event = persistEvent("Delete Me", EventCategory.ACADEMIC, EventStatus.CANCELLED, user);
        Long id = event.id;

        eventService.delete(id, "auth0|deleter");

        entityManager.flush();
        assertNull(Event.findById(id));
    }

    @Test
    @TestTransaction
    void delete_nonCancelledEvent_throwsConflict() {
        User user = persistUser("auth0|delNonCan", "delNonCan@example.com");
        Event event = persistEvent("Draft Event", EventCategory.ACADEMIC, EventStatus.DRAFT, user);

        WebApplicationException ex = assertThrows(WebApplicationException.class,
                () -> eventService.delete(event.id, "auth0|delNonCan"));
        assertEquals(409, ex.getResponse().getStatus());
    }

    // --- cancel ---

    @Test
    @TestTransaction
    void cancel_draftEvent_setsStatusCancelled() {
        User user = persistUser("auth0|canc1", "canc1@example.com");
        Event event = persistEvent("Draft", EventCategory.ACADEMIC, EventStatus.DRAFT, user);

        EventDTO result = eventService.cancel(event.id, "auth0|canc1");

        assertEquals(EventStatus.CANCELLED, result.status());
    }

    @Test
    @TestTransaction
    void cancel_publishedEvent_setsStatusCancelled() {
        User user = persistUser("auth0|canc2", "canc2@example.com");
        Event event = persistEvent("Published", EventCategory.ACADEMIC, EventStatus.PUBLISHED, user);

        EventDTO result = eventService.cancel(event.id, "auth0|canc2");

        assertEquals(EventStatus.CANCELLED, result.status());
    }

    @Test
    @TestTransaction
    void cancel_alreadyCancelled_throwsConflict() {
        User user = persistUser("auth0|canc3", "canc3@example.com");
        Event event = persistEvent("Cancelled", EventCategory.ACADEMIC, EventStatus.CANCELLED, user);

        WebApplicationException ex = assertThrows(WebApplicationException.class,
                () -> eventService.cancel(event.id, "auth0|canc3"));
        assertEquals(409, ex.getResponse().getStatus());
    }

    @Test
    @TestTransaction
    void cancel_unknownEvent_throwsNotFound() {
        assertThrows(NotFoundException.class, () -> eventService.cancel(999999L, "auth0|x"));
    }

    @Test
    @TestTransaction
    void cancel_notCreator_throwsForbidden() {
        User user = persistUser("auth0|cancOwn", "cancOwn@example.com");
        Event event = persistEvent("Owner", EventCategory.ACADEMIC, EventStatus.DRAFT, user);

        assertThrows(ForbiddenException.class, () -> eventService.cancel(event.id, "auth0|intruder"));
    }

    // --- restore ---

    @Test
    @TestTransaction
    void restore_cancelledEvent_setsStatusDraft() {
        User user = persistUser("auth0|rest1", "rest1@example.com");
        Event event = persistEvent("Cancelled", EventCategory.ACADEMIC, EventStatus.CANCELLED, user);

        EventDTO result = eventService.restore(event.id, "auth0|rest1");

        assertEquals(EventStatus.DRAFT, result.status());
    }

    @Test
    @TestTransaction
    void restore_nonCancelled_throwsConflict() {
        User user = persistUser("auth0|rest2", "rest2@example.com");
        Event event = persistEvent("Draft", EventCategory.ACADEMIC, EventStatus.DRAFT, user);

        WebApplicationException ex = assertThrows(WebApplicationException.class,
                () -> eventService.restore(event.id, "auth0|rest2"));
        assertEquals(409, ex.getResponse().getStatus());
    }

    @Test
    @TestTransaction
    void restore_unknownEvent_throwsNotFound() {
        assertThrows(NotFoundException.class, () -> eventService.restore(999999L, "auth0|x"));
    }

    @Test
    @TestTransaction
    void restore_notCreator_throwsForbidden() {
        User user = persistUser("auth0|restOwn", "restOwn@example.com");
        Event event = persistEvent("Cancelled", EventCategory.ACADEMIC, EventStatus.CANCELLED, user);

        assertThrows(ForbiddenException.class, () -> eventService.restore(event.id, "auth0|intruder"));
    }

    // --- update rejects cancelled ---

    @Test
    @TestTransaction
    void update_cancelledEvent_throwsConflict() {
        User user = persistUser("auth0|updCan", "updCan@example.com");
        Event event = persistEvent("Cancelled", EventCategory.ACADEMIC, EventStatus.CANCELLED, user);

        UpdateEventRequest req = validUpdateRequest("New", EventCategory.ACADEMIC, null);
        WebApplicationException ex = assertThrows(WebApplicationException.class,
                () -> eventService.update(event.id, "auth0|updCan", req));
        assertEquals(409, ex.getResponse().getStatus());
    }

    @Test
    @TestTransaction
    void delete_unknownEvent_throwsNotFound() {

        assertThrows(NotFoundException.class, () -> eventService.delete(999999L, "auth0|x"));
    }

    @Test
    @TestTransaction
    void delete_nullCreator_throwsForbidden() {
        Event event = persistEvent("No Creator", EventCategory.ACADEMIC, EventStatus.DRAFT, null);

        assertThrows(ForbiddenException.class, () -> eventService.delete(event.id, "auth0|x"));
    }

    @Test
    @TestTransaction
    void delete_differentUser_throwsForbidden() {
        User user = persistUser("auth0|owner2", "owner2@example.com");
        Event event = persistEvent("Owner's Event", EventCategory.ACADEMIC, EventStatus.DRAFT, user);

        assertThrows(ForbiddenException.class, () -> eventService.delete(event.id, "auth0|intruder"));
    }

    // --- delete cascade (pentest finding 4.21) ---

    @Test
    @TestTransaction
    void delete_cancelledEvent_removesLinkedAttendances() {
        User creator = persistUser("auth0|del-cas-c", "del-cas-c@example.com");
        User attendee = persistUser("auth0|del-cas-a", "del-cas-a@example.com");
        Event event = persistEvent("Cascade Del", EventCategory.ACADEMIC, EventStatus.CANCELLED, creator);
        persistAttendanceForEvent(event.id, attendee.id, AttendanceStatus.ATTENDING);
        entityManager.flush();
        long before = Attendance.count("eventId = ?1", event.id);
        assertEquals(1, before);

        eventService.delete(event.id, "auth0|del-cas-c");
        entityManager.flush();

        assertEquals(0, Attendance.count("eventId = ?1", event.id));
    }

    @Test
    @TestTransaction
    void delete_cancelledEvent_removesLinkedFavorites() {
        User creator = persistUser("auth0|del-fav-c", "del-fav-c@example.com");
        User fan = persistUser("auth0|del-fav-f", "del-fav-f@example.com");
        Event event = persistEvent("Fav Del", EventCategory.ACADEMIC, EventStatus.CANCELLED, creator);
        persistFavoriteFor(event.id, fan.id);
        entityManager.flush();
        assertEquals(1, Favorite.count("eventId = ?1", event.id));

        eventService.delete(event.id, "auth0|del-fav-c");
        entityManager.flush();

        assertEquals(0, Favorite.count("eventId = ?1", event.id));
    }

    @Test
    @TestTransaction
    void delete_cancelledEvent_removesLinkedEventViews() {
        User creator = persistUser("auth0|del-view-c", "del-view-c@example.com");
        User viewer = persistUser("auth0|del-view-v", "del-view-v@example.com");
        Event event = persistEvent("View Del", EventCategory.ACADEMIC, EventStatus.CANCELLED, creator);
        persistEventViewFor(event.id, viewer.id);
        entityManager.flush();
        assertEquals(1, EventView.count("eventId = ?1", event.id));

        eventService.delete(event.id, "auth0|del-view-c");
        entityManager.flush();

        assertEquals(0, EventView.count("eventId = ?1", event.id));
    }

    @Test
    @TestTransaction
    void delete_doesNotAffectOtherEventsAttendances() {
        User creator = persistUser("auth0|del-nr-c", "del-nr-c@example.com");
        User attendee = persistUser("auth0|del-nr-a", "del-nr-a@example.com");
        Event toDelete = persistEvent("Delete This", EventCategory.ACADEMIC, EventStatus.CANCELLED, creator);
        Event keeper = persistEvent("Keep This", EventCategory.ACADEMIC, EventStatus.PUBLISHED, creator);
        persistAttendanceForEvent(toDelete.id, attendee.id, AttendanceStatus.ATTENDING);
        persistAttendanceForEvent(keeper.id, attendee.id, AttendanceStatus.ATTENDING);
        entityManager.flush();

        eventService.delete(toDelete.id, "auth0|del-nr-c");
        entityManager.flush();

        assertEquals(0, Attendance.count("eventId = ?1", toDelete.id));
        assertEquals(1, Attendance.count("eventId = ?1", keeper.id));
    }

    // --- publish ---

    @Test
    @TestTransaction
    void publish_asCreator_setsStatusPublished() {
        User user = persistUser("auth0|pub", "pub@example.com");
        Event event = persistEvent("Draft", EventCategory.ACADEMIC, EventStatus.DRAFT, user);

        EventDTO result = eventService.publish(event.id, "auth0|pub", false);

        entityManager.flush();
        entityManager.refresh(event);
        assertEquals(EventStatus.PUBLISHED, result.status());
        assertEquals(EventStatus.PUBLISHED, event.status);
    }

    @Test
    @TestTransaction
    void publish_asAdmin_onAnyEvent_setsStatusPublished() {
        User user = persistUser("auth0|owner", "owner@example.com");
        Event event = persistEvent("Draft", EventCategory.ACADEMIC, EventStatus.DRAFT, user);

        EventDTO result = eventService.publish(event.id, "auth0|admin", true);

        assertEquals(EventStatus.PUBLISHED, result.status());
    }

    @Test
    @TestTransaction
    void publish_unknownEvent_throwsNotFound() {

        assertThrows(NotFoundException.class, () -> eventService.publish(999999L, "auth0|x", false));
    }

    @Test
    @TestTransaction
    void publish_notCreatorNotAdmin_throwsForbidden() {
        User user = persistUser("auth0|owner", "owner2@example.com");
        Event event = persistEvent("Draft", EventCategory.ACADEMIC, EventStatus.DRAFT, user);

        assertThrows(ForbiddenException.class, () -> eventService.publish(event.id, "auth0|intruder", false));
    }

    @Test
    @TestTransaction
    void publish_nullCreator_throwsForbidden() {
        Event event = persistEvent("Draft", EventCategory.ACADEMIC, EventStatus.DRAFT, null);

        assertThrows(ForbiddenException.class, () -> eventService.publish(event.id, "auth0|x", false));
    }

    @Test
    @TestTransaction
    void publish_alreadyPublished_throws409() {
        User user = persistUser("auth0|pub2", "pub2@example.com");
        Event event = persistEvent("Published", EventCategory.ACADEMIC, EventStatus.PUBLISHED, user);

        WebApplicationException ex = assertThrows(WebApplicationException.class,
                () -> eventService.publish(event.id, "auth0|pub2", false));
        assertEquals(409, ex.getResponse().getStatus());
    }

    @Test
    @TestTransaction
    void publish_cancelledEvent_throws409() {
        User user = persistUser("auth0|pub3", "pub3@example.com");
        Event event = persistEvent("Cancelled", EventCategory.ACADEMIC, EventStatus.CANCELLED, user);

        WebApplicationException ex = assertThrows(WebApplicationException.class,
                () -> eventService.publish(event.id, "auth0|pub3", false));
        assertEquals(409, ex.getResponse().getStatus());
    }

    @Test
    @TestTransaction
    void publish_pastStartDate_throws422WithErrors() {
        User user = persistUser("auth0|past", "past@example.com");
        Event event = persistEvent("Past", EventCategory.ACADEMIC, EventStatus.DRAFT, user);
        event.startDate = LocalDateTime.now().minusDays(1);
        event.endDate = LocalDateTime.now().minusDays(1).plusHours(2);
        entityManager.flush();

        WebApplicationException ex = assertThrows(WebApplicationException.class,
                () -> eventService.publish(event.id, "auth0|past", false));
        assertEquals(422, ex.getResponse().getStatus());
        Object entity = ex.getResponse().getEntity();
        assertTrue(entity.toString().contains("date"));
    }

    @Test
    @TestTransaction
    void publish_missingRequiredFields_throws422WithAllErrors() {
        User user = persistUser("auth0|miss", "miss@example.com");
        Event event = persistEvent("X", EventCategory.ACADEMIC, EventStatus.DRAFT, user);
        event.title = "  ";
        event.location = "";
        event.category = null;
        event.endDate = null;
        entityManager.flush();

        WebApplicationException ex = assertThrows(WebApplicationException.class,
                () -> eventService.publish(event.id, "auth0|miss", false));
        assertEquals(422, ex.getResponse().getStatus());
        @SuppressWarnings("unchecked")
        java.util.Map<String, Object> body = (java.util.Map<String, Object>) ex.getResponse().getEntity();
        @SuppressWarnings("unchecked")
        List<String> errors = (List<String>) body.get("errors");
        assertTrue(errors.stream().anyMatch(e -> e.contains("titre")));
        assertTrue(errors.stream().anyMatch(e -> e.contains("lieu")));
        assertTrue(errors.stream().anyMatch(e -> e.contains("catégorie")));
        assertTrue(errors.stream().anyMatch(e -> e.contains("fin")));
    }

    @Test
    @TestTransaction
    void publish_fullLifecycle_publishedCancelledRestoredPublishedAgain() {
        User user = persistUser("auth0|cycle", "cycle@example.com");
        Event event = persistEvent("Cycle", EventCategory.ACADEMIC, EventStatus.PUBLISHED, user);

        eventService.cancel(event.id, "auth0|cycle");
        entityManager.flush();
        entityManager.refresh(event);
        assertEquals(EventStatus.CANCELLED, event.status);

        eventService.restore(event.id, "auth0|cycle");
        entityManager.flush();
        entityManager.refresh(event);
        assertEquals(EventStatus.DRAFT, event.status);

        EventDTO republished = eventService.publish(event.id, "auth0|cycle", false);
        assertEquals(EventStatus.PUBLISHED, republished.status());
    }

    @Test
    @TestTransaction
    void publish_endDateBeforeStartDate_throws422() {
        User user = persistUser("auth0|end", "end@example.com");
        Event event = persistEvent("E", EventCategory.ACADEMIC, EventStatus.DRAFT, user);
        event.endDate = event.startDate.minusHours(1);
        entityManager.flush();

        WebApplicationException ex = assertThrows(WebApplicationException.class,
                () -> eventService.publish(event.id, "auth0|end", false));
        assertEquals(422, ex.getResponse().getStatus());
    }

    // --- uploadImage ---

    @Test
    @TestTransaction
    void uploadImage_asCreator_updatesbannerUrl(@TempDir Path tempDir) throws IOException {
        User user = persistUser("auth0|img", "img@example.com");
        Event event = persistEvent("Event", EventCategory.ACADEMIC, EventStatus.DRAFT, user);

        Path fakeFile = tempDir.resolve("banner.jpg");
        Files.write(fakeFile, jpegHeader());
        FileUpload upload = new StubFileUpload("banner.jpg", "image/jpeg", fakeFile);

        EventDTO result = eventService.uploadImage(event.id, "auth0|img", upload, false);

        assertNotNull(result.bannerUrl());
        assertTrue(result.bannerUrl().startsWith("http"));
        assertTrue(result.bannerUrl().endsWith(".jpg"));
    }

    @Test
    @TestTransaction
    void uploadImage_asAdmin_updatesbannerUrl(@TempDir Path tempDir) throws IOException {
        User user = persistUser("auth0|owner3", "owner3@example.com");
        Event event = persistEvent("Event", EventCategory.ACADEMIC, EventStatus.DRAFT, user);

        Path fakeFile = tempDir.resolve("banner.png");
        Files.write(fakeFile, pngHeader());
        FileUpload upload = new StubFileUpload("banner.png", "image/png", fakeFile);

        EventDTO result = eventService.uploadImage(event.id, "auth0|admin2", upload, true);

        assertTrue(result.bannerUrl().startsWith("http"));
        assertTrue(result.bannerUrl().endsWith(".png"));
    }

    @Test
    @TestTransaction
    void uploadImage_unknownEvent_throwsNotFound(@TempDir Path tempDir) throws IOException {
        Path fakeFile = tempDir.resolve("f.jpg");
        Files.write(fakeFile, new byte[0]);
        FileUpload upload = new StubFileUpload("f.jpg", "image/jpeg", fakeFile);

        // Event lookup happens before file validation — NotFoundException is expected.
        assertThrows(NotFoundException.class,
                () -> eventService.uploadImage(999999L, "auth0|x", upload, false));
    }

    @Test
    @TestTransaction
    void uploadImage_notCreatorNotAdmin_throwsForbidden(@TempDir Path tempDir) throws IOException {
        User user = persistUser("auth0|owner4", "owner4@example.com");
        Event event = persistEvent("Event", EventCategory.ACADEMIC, EventStatus.DRAFT, user);

        Path fakeFile = tempDir.resolve("f.jpg");
        Files.write(fakeFile, new byte[0]);
        FileUpload upload = new StubFileUpload("f.jpg", "image/jpeg", fakeFile);

        // Auth check happens before file validation — ForbiddenException is expected.
        assertThrows(ForbiddenException.class,
                () -> eventService.uploadImage(event.id, "auth0|intruder", upload, false));
    }

    @Test
    @TestTransaction
    void uploadImage_invalidMime_throwsBadRequest(@TempDir Path tempDir) throws IOException {
        User user = persistUser("auth0|mime", "mime@example.com");
        Event event = persistEvent("Event", EventCategory.ACADEMIC, EventStatus.DRAFT, user);

        Path fakeFile = tempDir.resolve("script.sh");
        Files.write(fakeFile, "#!/bin/bash".getBytes());
        FileUpload upload = new StubFileUpload("script.sh", "text/plain", fakeFile);

        assertThrows(InvalidFileTypeException.class,
                () -> eventService.uploadImage(event.id, "auth0|mime", upload, false));
    }

    @Test
    @TestTransaction
    void uploadImage_nullMime_throwsBadRequest(@TempDir Path tempDir) throws IOException {
        User user = persistUser("auth0|nullmime", "nullmime@example.com");
        Event event = persistEvent("Event", EventCategory.ACADEMIC, EventStatus.DRAFT, user);

        Path fakeFile = tempDir.resolve("file.bin");
        Files.write(fakeFile, new byte[0]);
        FileUpload upload = new StubFileUpload("file.bin", null, fakeFile);

        assertThrows(InvalidFileTypeException.class,
                () -> eventService.uploadImage(event.id, "auth0|nullmime", upload, false));
    }

    @Test
    @TestTransaction
    void uploadImage_extensionDerivedFromMime_notFromFileName(@TempDir Path tempDir) throws IOException {
        User user = persistUser("auth0|noext", "noext@example.com");
        Event event = persistEvent("Event", EventCategory.ACADEMIC, EventStatus.DRAFT, user);

        Path fakeFile = tempDir.resolve("image");
        Files.write(fakeFile, jpegHeader());
        FileUpload upload = new StubFileUpload("image", "image/jpeg", fakeFile);

        EventDTO result = eventService.uploadImage(event.id, "auth0|noext", upload, false);

        // Extension comes from the validated MIME type, not the client filename.
        assertTrue(result.bannerUrl().endsWith(".jpg"));
    }

    @Test
    @TestTransaction
    void uploadImage_nullFileName_extensionFromMime(@TempDir Path tempDir) throws IOException {
        User user = persistUser("auth0|nullname", "nullname@example.com");
        Event event = persistEvent("Event", EventCategory.ACADEMIC, EventStatus.DRAFT, user);

        Path fakeFile = tempDir.resolve("file");
        Files.write(fakeFile, jpegHeader());
        FileUpload upload = new StubFileUpload(null, "image/jpeg", fakeFile);

        EventDTO result = eventService.uploadImage(event.id, "auth0|nullname", upload, false);

        assertTrue(result.bannerUrl().endsWith(".jpg"));
    }

    // --- faculty filter (SCRUM-77) ---

    @Test
    @TestTransaction
    void getAll_withFacultyFilter_returnsMatchingEvents() {
        User user = persistUser("auth0|fac1", "fac1@example.com");
        persistEvent("Sciences Event", EventCategory.ACADEMIC, EventStatus.PUBLISHED, user, Faculty.SCIENCES);
        persistEvent("Law Event", EventCategory.ACADEMIC, EventStatus.PUBLISHED, user, Faculty.LAW);

        List<EventDTO> result = eventService.getAll(0, 20, null, null, null, null, Faculty.SCIENCES, null, null);

        assertEquals(1, result.size());
        assertEquals(Faculty.SCIENCES, result.get(0).faculty());
    }

    @Test
    @TestTransaction
    void getAll_withFacultyNull_returnsAll() {
        User user = persistUser("auth0|fac2", "fac2@example.com");
        persistEvent("Sciences Event", EventCategory.ACADEMIC, EventStatus.PUBLISHED, user, Faculty.SCIENCES);
        persistEvent("No Faculty", EventCategory.ACADEMIC, EventStatus.PUBLISHED, user, null);

        List<EventDTO> result = eventService.getAll(0, 20, null, null, null, null, null, null, null);

        assertEquals(2, result.size());
    }

    @Test
    @TestTransaction
    void create_withFaculty_persistsFaculty() {
        persistUser("auth0|facCreate", "facCreate@example.com");

        CreateEventRequest req = validCreateRequest();
        req.faculty = Faculty.MEDICINE;
        EventDTO result = eventService.create("auth0|facCreate", req);

        assertEquals(Faculty.MEDICINE, result.faculty());
    }

    @Test
    @TestTransaction
    void update_withFaculty_updatesFaculty() {
        User user = persistUser("auth0|facUpd", "facUpd@example.com");
        Event event = persistEvent("Event", EventCategory.ACADEMIC, EventStatus.DRAFT, user, Faculty.SCIENCES);

        UpdateEventRequest req = validUpdateRequest("Event", EventCategory.ACADEMIC, null);
        req.faculty = Faculty.LETTERS;
        EventDTO result = eventService.update(event.id, "auth0|facUpd", req);

        assertEquals(Faculty.LETTERS, result.faculty());
    }

    @Test
    @TestTransaction
    void getAll_withFacultyNone_returnsNullFacultyEvents() {
        User user = persistUser("auth0|facNone", "facNone@example.com");
        persistEvent("Sciences Event", EventCategory.ACADEMIC, EventStatus.PUBLISHED, user, Faculty.SCIENCES);
        persistEvent("No Faculty Event", EventCategory.ACADEMIC, EventStatus.PUBLISHED, user, null);

        List<EventDTO> result = eventService.getAll(0, 20, null, null, null, null, null, true, null);

        assertEquals(1, result.size());
        assertNull(result.get(0).faculty());
        assertEquals("No Faculty Event", result.get(0).title());
    }

    @Test
    @TestTransaction
    void getAll_withFacultyNoneAndFaculty_facultyNoneWins() {
        User user = persistUser("auth0|facNonePrio", "facNonePrio@example.com");
        persistEvent("Sciences Event", EventCategory.ACADEMIC, EventStatus.PUBLISHED, user, Faculty.SCIENCES);
        persistEvent("No Faculty Event", EventCategory.ACADEMIC, EventStatus.PUBLISHED, user, null);

        List<EventDTO> result = eventService.getAll(0, 20, null, null, null, null, Faculty.SCIENCES, true, null);

        assertEquals(1, result.size());
        assertNull(result.get(0).faculty());
    }

    @Test
    @TestTransaction
    void update_withNullFaculty_clearsFaculty() {
        User user = persistUser("auth0|facClr", "facClr@example.com");
        Event event = persistEvent("Event", EventCategory.ACADEMIC, EventStatus.DRAFT, user, Faculty.SCIENCES);

        UpdateEventRequest req = validUpdateRequest("Event", EventCategory.ACADEMIC, null);
        req.faculty = null;
        EventDTO result = eventService.update(event.id, "auth0|facClr", req);

        assertNull(result.faculty());
    }

    // --- allDay (SCRUM-117) ---

    @Test
    @TestTransaction
    void create_withAllDayTrue_persistsTrue() {
        persistUser("auth0|allDayT", "allDayT@example.com");

        CreateEventRequest req = validCreateRequest();
        req.allDay = true;
        EventDTO result = eventService.create("auth0|allDayT", req);

        assertTrue(result.allDay());
    }

    @Test
    @TestTransaction
    void create_withAllDayFalse_persistsFalse() {
        persistUser("auth0|allDayF", "allDayF@example.com");

        CreateEventRequest req = validCreateRequest();
        req.allDay = false;
        EventDTO result = eventService.create("auth0|allDayF", req);

        assertFalse(result.allDay());
    }

    @Test
    @TestTransaction
    void create_withAllDayNull_defaultsToFalse() {
        persistUser("auth0|allDayN", "allDayN@example.com");

        CreateEventRequest req = validCreateRequest();
        req.allDay = null;
        EventDTO result = eventService.create("auth0|allDayN", req);

        assertFalse(result.allDay());
    }

    @Test
    @TestTransaction
    void update_withAllDayTrue_setsTrue() {
        User user = persistUser("auth0|updADT", "updADT@example.com");
        Event event = persistEvent("Event", EventCategory.ACADEMIC, EventStatus.DRAFT, user);

        UpdateEventRequest req = validUpdateRequest("Event", EventCategory.ACADEMIC, null);
        req.allDay = true;
        EventDTO result = eventService.update(event.id, "auth0|updADT", req);

        assertTrue(result.allDay());
    }

    @Test
    @TestTransaction
    void update_withAllDayFalse_setsFalse() {
        User user = persistUser("auth0|updADF", "updADF@example.com");
        Event event = persistEvent("Event", EventCategory.ACADEMIC, EventStatus.DRAFT, user);
        event.allDay = true;
        entityManager.flush();

        UpdateEventRequest req = validUpdateRequest("Event", EventCategory.ACADEMIC, null);
        req.allDay = false;
        EventDTO result = eventService.update(event.id, "auth0|updADF", req);

        assertFalse(result.allDay());
    }

    @Test
    @TestTransaction
    void update_withAllDayNull_defaultsToFalse() {
        User user = persistUser("auth0|updADN", "updADN@example.com");
        Event event = persistEvent("Event", EventCategory.ACADEMIC, EventStatus.DRAFT, user);
        event.allDay = true;
        entityManager.flush();

        UpdateEventRequest req = validUpdateRequest("Event", EventCategory.ACADEMIC, null);
        req.allDay = null;
        EventDTO result = eventService.update(event.id, "auth0|updADN", req);

        assertFalse(result.allDay());
    }

    @Test
    @TestTransaction
    void create_thenGetById_preservesAllDay() {
        persistUser("auth0|adPersist", "adPersist@example.com");

        CreateEventRequest req = validCreateRequest();
        req.allDay = true;
        EventDTO created = eventService.create("auth0|adPersist", req);
        entityManager.flush();
        entityManager.clear();

        EventDTO fetched = eventService.getById(created.id(), "auth0|adPersist", false);
        assertTrue(fetched.allDay());
    }

    @Test
    @TestTransaction
    void create_withoutAllDay_persistedDefaultsToFalse() {
        persistUser("auth0|adDefault", "adDefault@example.com");

        CreateEventRequest req = validCreateRequest();
        EventDTO created = eventService.create("auth0|adDefault", req);
        entityManager.flush();
        entityManager.clear();

        EventDTO fetched = eventService.getById(created.id(), "auth0|adDefault", false);
        assertFalse(fetched.allDay());
    }

    // =========================================================
    // SCRUM-126 + SCRUM-129 — nouveaux champs et compteurs
    // =========================================================

    @Test
    @TestTransaction
    void create_withNewFieldsAndTags_persistsNormalized() {
        persistUser("auth0|tags1", "tags1@example.com");

        CreateEventRequest req = validCreateRequest();
        req.websiteUrl = "https://example.com/e";
        req.contactEmail = "orga@example.com";
        req.registrationDeadline = LocalDateTime.now().plusDays(1);
        req.tags = new java.util.ArrayList<>(java.util.Arrays.asList("Foo", " FOO ", "bar", "foo"));

        EventDTO created = eventService.create("auth0|tags1", req);

        assertEquals("https://example.com/e", created.websiteUrl());
        assertEquals("orga@example.com", created.contactEmail());
        assertNotNull(created.registrationDeadline());
        // Normalisation : trim + lowercase + dédup, ordre d'insertion préservé
        assertEquals(java.util.List.of("foo", "bar"), created.tags());
    }

    @Test
    @TestTransaction
    void create_withNullTags_persistsEmpty() {
        persistUser("auth0|tagsnull", "tagsnull@example.com");

        CreateEventRequest req = validCreateRequest();
        req.tags = null;

        EventDTO created = eventService.create("auth0|tagsnull", req);

        assertNotNull(created.tags());
        assertTrue(created.tags().isEmpty());
    }

    @Test
    @TestTransaction
    void update_withTagsCleared_persistsEmpty() {
        User user = persistUser("auth0|tagsclr", "tagsclr@example.com");
        Event event = persistEvent("Tagged", EventCategory.CULTURAL, EventStatus.DRAFT, user);
        event.tags = new java.util.ArrayList<>(java.util.Arrays.asList("old1", "old2"));
        entityManager.flush();

        UpdateEventRequest req = validUpdateRequest("Tagged", EventCategory.CULTURAL, null);
        req.tags = new java.util.ArrayList<>();

        EventDTO updated = eventService.update(event.id, "auth0|tagsclr", req);

        assertTrue(updated.tags().isEmpty());
    }

    @Test
    @TestTransaction
    void getById_withCapacityAndAttending_computesAvailableSpots() {
        User user = persistUser("auth0|spots", "spots@example.com");
        Event event = persistEvent("Spots", EventCategory.CONFERENCE, EventStatus.PUBLISHED, user);
        event.capacity = 5;
        entityManager.flush();

        // 3 ATTENDING
        User a1 = persistUser("auth0|sp-a1", "a1@example.com");
        User a2 = persistUser("auth0|sp-a2", "a2@example.com");
        User a3 = persistUser("auth0|sp-a3", "a3@example.com");
        persistAttendanceForEvent(event.id, a1.id, AttendanceStatus.ATTENDING);
        persistAttendanceForEvent(event.id, a2.id, AttendanceStatus.ATTENDING);
        persistAttendanceForEvent(event.id, a3.id, AttendanceStatus.ATTENDING);
        // 2 WAITLISTED
        User w1 = persistUser("auth0|sp-w1", "w1@example.com");
        User w2 = persistUser("auth0|sp-w2", "w2@example.com");
        persistAttendanceForEvent(event.id, w1.id, AttendanceStatus.WAITLISTED);
        persistAttendanceForEvent(event.id, w2.id, AttendanceStatus.WAITLISTED);
        entityManager.flush();

        EventDTO dto = eventService.getById(event.id, null, false);

        assertEquals(3L, dto.attendingCount());
        assertEquals(2L, dto.availableSpots());
        assertEquals(2L, dto.waitlistedCount());
    }

    @Test
    @TestTransaction
    void getById_capacityReducedBelowAttending_clampsAvailableSpotsToZero() {
        User user = persistUser("auth0|clamp", "clamp@example.com");
        Event event = persistEvent("Clamp", EventCategory.CONFERENCE, EventStatus.PUBLISHED, user);
        event.capacity = 2;
        entityManager.flush();

        User a1 = persistUser("auth0|cl-1", "cl-1@example.com");
        User a2 = persistUser("auth0|cl-2", "cl-2@example.com");
        User a3 = persistUser("auth0|cl-3", "cl-3@example.com");
        persistAttendanceForEvent(event.id, a1.id, AttendanceStatus.ATTENDING);
        persistAttendanceForEvent(event.id, a2.id, AttendanceStatus.ATTENDING);
        persistAttendanceForEvent(event.id, a3.id, AttendanceStatus.ATTENDING);
        entityManager.flush();

        EventDTO dto = eventService.getById(event.id, null, false);

        assertEquals(3L, dto.attendingCount());
        assertEquals(0L, dto.availableSpots());
    }

    @Test
    @TestTransaction
    void getAll_returnsWaitlistedCountsInBulk() {
        User user = persistUser("auth0|bulk", "bulk@example.com");
        Event e1 = persistEvent("Bulk1", EventCategory.CONFERENCE, EventStatus.PUBLISHED, user);
        e1.capacity = 1;
        Event e2 = persistEvent("Bulk2", EventCategory.CONFERENCE, EventStatus.PUBLISHED, user);
        e2.capacity = 1;
        entityManager.flush();

        User a1 = persistUser("auth0|b-a1", "b-a1@example.com");
        User a2 = persistUser("auth0|b-a2", "b-a2@example.com");
        persistAttendanceForEvent(e1.id, a1.id, AttendanceStatus.WAITLISTED);
        persistAttendanceForEvent(e1.id, a2.id, AttendanceStatus.WAITLISTED);
        persistAttendanceForEvent(e2.id, a1.id, AttendanceStatus.WAITLISTED);
        entityManager.flush();

        List<EventDTO> all = eventService.getAll(0, 20, EventStatus.PUBLISHED, null, null, null, null, null, null);

        EventDTO dto1 = all.stream().filter(d -> d.id().equals(e1.id)).findFirst().orElseThrow();
        EventDTO dto2 = all.stream().filter(d -> d.id().equals(e2.id)).findFirst().orElseThrow();
        assertEquals(2L, dto1.waitlistedCount());
        assertEquals(1L, dto2.waitlistedCount());
    }

    @Test
    void computeAvailableSpots_nullCapacity_returnsNull() {
        assertNull(EventService.computeAvailableSpots(null, 0L));
    }

    @Test
    void computeAvailableSpots_attendingBelowCapacity_returnsRemaining() {
        assertEquals(3L, EventService.computeAvailableSpots(5, 2L));
    }

    @Test
    void computeAvailableSpots_attendingAboveCapacity_returnsZero() {
        assertEquals(0L, EventService.computeAvailableSpots(5, 10L));
    }

    @Test
    void normalizeTags_nullInput_returnsEmptyList() {
        assertTrue(EventService.normalizeTags(null).isEmpty());
    }

    @Test
    void normalizeTags_dedupAndLowercase_preservesFirstOccurrenceOrder() {
        java.util.List<String> result = EventService.normalizeTags(
                java.util.Arrays.asList("Foo", " BAR ", "foo", "bar", null, "", "  "));
        assertEquals(java.util.List.of("foo", "bar"), result);
    }

    private void persistAttendanceForEvent(Long eventId, UUID userId, AttendanceStatus status) {
        Attendance a = new Attendance();
        a.userId = userId;
        a.eventId = eventId;
        a.status = status;
        entityManager.persist(a);
    }

    private void persistEventViewFor(Long eventId, UUID userId) {
        EventView v = new EventView();
        v.eventId = eventId;
        v.userId = userId;
        entityManager.persist(v);
    }

    private void persistFavoriteFor(Long eventId, UUID userId) {
        Favorite f = new Favorite();
        f.eventId = eventId;
        f.userId = userId;
        entityManager.persist(f);
    }

    // --- review #90 — public stats counters on getById ---

    @Test
    @TestTransaction
    void getById_exposesViewCountAndInterestedCount() {
        User user = persistUser("auth0|stats-pub", "stats-pub@example.com");
        Event event = persistEvent("Public stats", EventCategory.ACADEMIC, EventStatus.PUBLISHED, user);
        entityManager.flush();

        User v1 = persistUser("auth0|stats-v1", "v1@example.com");
        User v2 = persistUser("auth0|stats-v2", "v2@example.com");
        User f1 = persistUser("auth0|stats-f1", "f1@example.com");
        persistEventViewFor(event.id, v1.id);
        persistEventViewFor(event.id, v2.id);
        persistFavoriteFor(event.id, f1.id);
        entityManager.flush();

        EventDTO dto = eventService.getById(event.id, null, false);

        assertEquals(2L, dto.viewCount());
        assertEquals(1L, dto.interestedCount());
    }

    @Test
    @TestTransaction
    void getById_withNoViewsOrFavorites_returnsZeroCounters() {
        User user = persistUser("auth0|stats-empty", "stats-empty@example.com");
        Event event = persistEvent("Empty stats", EventCategory.ACADEMIC, EventStatus.PUBLISHED, user);
        entityManager.flush();

        EventDTO dto = eventService.getById(event.id, null, false);

        assertEquals(0L, dto.viewCount());
        assertEquals(0L, dto.interestedCount());
    }

    // =========================================================
    // SCRUM-136 — Cascade isCreatorOrAcceptedCoOrganizer
    // =========================================================

    @Test
    @TestTransaction
    void update_byAcceptedCoOrganizer_succeeds() {
        User creator = persistUser("auth0|cas-upd-c", "cas-upd-c@example.com");
        User coOrg = persistUser("auth0|cas-upd-co", "cas-upd-co@example.com");
        Event event = persistEvent("Cascade Update", EventCategory.ACADEMIC, EventStatus.PUBLISHED, creator);
        persistCoOrg(event.id, coOrg.id, ch.unige.events.entity.CoOrganizerStatus.ACCEPTED);

        EventDTO updated = eventService.update(event.id, coOrg.auth0Id,
                validUpdateRequest("Edited by co-org", EventCategory.ACADEMIC, EventStatus.PUBLISHED));

        assertEquals("Edited by co-org", updated.title());
    }

    @Test
    @TestTransaction
    void update_byPendingCoOrganizer_throws403() {
        User creator = persistUser("auth0|cas-pen-c", "cas-pen-c@example.com");
        User coOrg = persistUser("auth0|cas-pen-co", "cas-pen-co@example.com");
        Event event = persistEvent("Cascade Pending", EventCategory.ACADEMIC, EventStatus.PUBLISHED, creator);
        persistCoOrg(event.id, coOrg.id, ch.unige.events.entity.CoOrganizerStatus.PENDING);

        assertThrows(ForbiddenException.class,
                () -> eventService.update(event.id, coOrg.auth0Id,
                        validUpdateRequest("nope", EventCategory.ACADEMIC, EventStatus.PUBLISHED)));
    }

    @Test
    @TestTransaction
    void cancel_byAcceptedCoOrganizer_succeeds() {
        User creator = persistUser("auth0|cas-can-c", "cas-can-c@example.com");
        User coOrg = persistUser("auth0|cas-can-co", "cas-can-co@example.com");
        Event event = persistEvent("Cascade Cancel", EventCategory.ACADEMIC, EventStatus.PUBLISHED, creator);
        persistCoOrg(event.id, coOrg.id, ch.unige.events.entity.CoOrganizerStatus.ACCEPTED);

        EventDTO cancelled = eventService.cancel(event.id, coOrg.auth0Id);

        assertEquals(EventStatus.CANCELLED, cancelled.status());
    }

    @Test
    @TestTransaction
    void restore_byAcceptedCoOrganizer_succeeds() {
        User creator = persistUser("auth0|cas-res-c", "cas-res-c@example.com");
        User coOrg = persistUser("auth0|cas-res-co", "cas-res-co@example.com");
        Event event = persistEvent("Cascade Restore", EventCategory.ACADEMIC, EventStatus.CANCELLED, creator);
        persistCoOrg(event.id, coOrg.id, ch.unige.events.entity.CoOrganizerStatus.ACCEPTED);

        EventDTO restored = eventService.restore(event.id, coOrg.auth0Id);

        assertEquals(EventStatus.DRAFT, restored.status());
    }

    @Test
    @TestTransaction
    void publish_byAcceptedCoOrganizer_succeeds() {
        User creator = persistUser("auth0|cas-pub-c", "cas-pub-c@example.com");
        User coOrg = persistUser("auth0|cas-pub-co", "cas-pub-co@example.com");
        Event event = persistEvent("Cascade Publish", EventCategory.ACADEMIC, EventStatus.DRAFT, creator);
        persistCoOrg(event.id, coOrg.id, ch.unige.events.entity.CoOrganizerStatus.ACCEPTED);

        EventDTO published = eventService.publish(event.id, coOrg.auth0Id, false);

        assertEquals(EventStatus.PUBLISHED, published.status());
    }

    @Test
    @TestTransaction
    void uploadImage_byAcceptedCoOrganizer_succeeds(@TempDir Path tempDir) throws IOException {
        User creator = persistUser("auth0|cas-upl-c", "cas-upl-c@example.com");
        User coOrg = persistUser("auth0|cas-upl-co", "cas-upl-co@example.com");
        Event event = persistEvent("Cascade Upload", EventCategory.ACADEMIC, EventStatus.PUBLISHED, creator);
        persistCoOrg(event.id, coOrg.id, ch.unige.events.entity.CoOrganizerStatus.ACCEPTED);

        Path src = tempDir.resolve("banner.jpg");
        Files.write(src, jpegHeader());
        StubFileUpload upload = new StubFileUpload("banner.jpg", "image/jpeg", src);

        EventDTO updated = eventService.uploadImage(event.id, coOrg.auth0Id, upload, false);

        assertNotNull(updated.bannerUrl());
    }

    @Test
    @TestTransaction
    void delete_byAcceptedCoOrganizer_throws403() {
        User creator = persistUser("auth0|cas-del-c", "cas-del-c@example.com");
        User coOrg = persistUser("auth0|cas-del-co", "cas-del-co@example.com");
        Event event = persistEvent("Cascade Delete", EventCategory.ACADEMIC, EventStatus.CANCELLED, creator);
        persistCoOrg(event.id, coOrg.id, ch.unige.events.entity.CoOrganizerStatus.ACCEPTED);

        // Sentinel décision 2 — delete reste strict-creator même pour un co-org ACCEPTED.
        assertThrows(ForbiddenException.class,
                () -> eventService.delete(event.id, coOrg.auth0Id));
    }

    @Test
    @TestTransaction
    void getById_draftByAcceptedCoOrganizer_returns200() {
        User creator = persistUser("auth0|cas-gb-c", "cas-gb-c@example.com");
        User coOrg = persistUser("auth0|cas-gb-co", "cas-gb-co@example.com");
        Event event = persistEvent("Cascade GetById", EventCategory.ACADEMIC, EventStatus.DRAFT, creator);
        persistCoOrg(event.id, coOrg.id, ch.unige.events.entity.CoOrganizerStatus.ACCEPTED);

        EventDTO dto = eventService.getById(event.id, coOrg.auth0Id, false);

        assertEquals(event.id, dto.id());
        assertEquals(EventStatus.DRAFT, dto.status());
    }

    @Test
    @TestTransaction
    void getById_draftByPendingCoOrganizer_throws404() {
        User creator = persistUser("auth0|cas-gbp-c", "cas-gbp-c@example.com");
        User coOrg = persistUser("auth0|cas-gbp-co", "cas-gbp-co@example.com");
        Event event = persistEvent("Cascade GetById Pending", EventCategory.ACADEMIC, EventStatus.DRAFT, creator);
        persistCoOrg(event.id, coOrg.id, ch.unige.events.entity.CoOrganizerStatus.PENDING);

        assertThrows(NotFoundException.class,
                () -> eventService.getById(event.id, coOrg.auth0Id, false));
    }

    @Test
    @TestTransaction
    void findByIdsAsDTO_returnsMappedDTOs() {
        User creator = persistUser("auth0|fbids-c", "fbids-c@example.com");
        Event e1 = persistEvent("FB1", EventCategory.ACADEMIC, EventStatus.PUBLISHED, creator);
        Event e2 = persistEvent("FB2", EventCategory.ACADEMIC, EventStatus.PUBLISHED, creator);

        java.util.Map<Long, EventDTO> map = eventService.findByIdsAsDTO(List.of(e1.id, e2.id));

        assertEquals(2, map.size());
        assertEquals("FB1", map.get(e1.id).title());
        assertEquals("FB2", map.get(e2.id).title());
    }

    @Test
    @TestTransaction
    void findByIdsAsDTO_emptyInput_returnsEmptyMap() {
        assertTrue(eventService.findByIdsAsDTO(List.of()).isEmpty());
        assertTrue(eventService.findByIdsAsDTO(null).isEmpty());
    }

    private void persistCoOrg(Long eventId, UUID userId, ch.unige.events.entity.CoOrganizerStatus status) {
        ch.unige.events.entity.EventCoOrganizer e = new ch.unige.events.entity.EventCoOrganizer();
        e.eventId = eventId;
        e.userId = userId;
        e.status = status;
        entityManager.persist(e);
        entityManager.flush();
    }

    // --- helpers ---

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

    // --- featured filter (SCRUM-102) ---

    @Test
    @TestTransaction
    void getAll_withFeaturedFilter_returnsOnlyFeaturedEvents() {
        User user = persistUser("auth0|featFilter", "featFilter@test.com");
        Event featured = persistEvent("Featured", EventCategory.ACADEMIC, EventStatus.PUBLISHED, user);
        featured.featured = true;
        Event notFeatured = persistEvent("NotFeatured", EventCategory.ACADEMIC, EventStatus.PUBLISHED, user);
        notFeatured.featured = false;
        entityManager.flush();

        List<EventDTO> result = eventService.getAll(0, 20, null, null, null, null, null, null, Boolean.TRUE);

        assertEquals(1, result.size());
        assertTrue(result.get(0).featured());
    }

    @Test
    @TestTransaction
    void getAll_withFeaturedNull_returnsAll() {
        User user = persistUser("auth0|featNull", "featNull@test.com");
        Event featured = persistEvent("Featured", EventCategory.ACADEMIC, EventStatus.PUBLISHED, user);
        featured.featured = true;
        persistEvent("NotFeatured", EventCategory.ACADEMIC, EventStatus.PUBLISHED, user);
        entityManager.flush();

        List<EventDTO> result = eventService.getAll(0, 20, null, null, null, null, null, null, null);

        assertEquals(2, result.size());
    }

    private Event persistEvent(String title, EventCategory category, EventStatus status, User creator) {
        return persistEvent(title, category, status, creator, null);
    }

    private Event persistEvent(String title, EventCategory category, EventStatus status, User creator, Faculty faculty) {
        Event event = new Event();
        event.title = title;
        event.location = "Uni Mail";
        event.startDate = LocalDateTime.now().plusDays(1);
        event.endDate = LocalDateTime.now().plusDays(2);
        event.category = category;
        event.status = status;
        event.creator = creator;
        event.faculty = faculty;
        entityManager.persist(event);
        entityManager.flush();
        return event;
    }

    private Event persistEventWithCreatedAt(String title, EventStatus status, User creator, LocalDateTime createdAt) {
        Event event = persistEvent(title, EventCategory.ACADEMIC, status, creator);
        entityManager.createQuery("UPDATE Event e SET e.createdAt = :ts WHERE e.id = :id")
                .setParameter("ts", createdAt)
                .setParameter("id", event.id)
                .executeUpdate();
        entityManager.clear();
        return Event.<Event>findById(event.id);
    }

    private CreateEventRequest validCreateRequest() {
        CreateEventRequest req = new CreateEventRequest();
        req.title = "Test Event";
        req.location = "Uni Mail";
        req.startDate = LocalDateTime.now().plusDays(1);
        req.endDate = LocalDateTime.now().plusDays(2);
        req.category = EventCategory.CONFERENCE;
        return req;
    }

    private UpdateEventRequest validUpdateRequest(String title, EventCategory category, EventStatus status) {
        UpdateEventRequest req = new UpdateEventRequest();
        req.title = title;
        req.location = "Uni Mail";
        req.startDate = LocalDateTime.now().plusDays(1);
        req.endDate = LocalDateTime.now().plusDays(2);
        req.category = category;
        req.status = status;
        return req;
    }

    static byte[] jpegHeader() {
        return new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 0, 0, 0};
    }

    static byte[] pngHeader() {
        return new byte[]{(byte) 0x89, 'P', 'N', 'G', (byte) 0x0D, (byte) 0x0A, (byte) 0x1A, (byte) 0x0A};
    }

    static class StubFileUpload implements FileUpload {
        private final String fileName;
        private final String contentType;
        private final Path uploadedFile;

        StubFileUpload(String fileName, String contentType, Path uploadedFile) {
            this.fileName = fileName;
            this.contentType = contentType;
            this.uploadedFile = uploadedFile;
        }

        @Override public String name() { return "file"; }
        @Override public Path uploadedFile() { return uploadedFile; }
        @Override public Path filePath() { return uploadedFile; }
        @Override public String fileName() { return fileName; }
        @Override public long size() { return 0; }
        @Override public String contentType() { return contentType; }
        @Override public String charSet() { return null; }
        @Override public jakarta.ws.rs.core.MultivaluedMap<String, String> getHeaders() { return new jakarta.ws.rs.core.MultivaluedHashMap<>(); }
    }

    // --- SCRUM-147 — Recurrence (createRecurring + getOccurrences) ---

    private CreateEventRequest validCreateRequestWithRecurrence(
            ch.unige.events.entity.RecurrenceFrequency frequency,
            java.time.LocalDate endDate,
            Integer maxOccurrences) {
        CreateEventRequest req = validCreateRequest();
        req.recurrence = new ch.unige.events.dto.event.RecurrenceRequest(frequency, endDate, maxOccurrences);
        return req;
    }

    @Test
    @TestTransaction
    void createRecurring_weekly4Occurrences_persists1ParentAnd3Children() {
        User user = persistUser("auth0|rec1", "rec1@example.com");
        CreateEventRequest req = validCreateRequestWithRecurrence(
                ch.unige.events.entity.RecurrenceFrequency.WEEKLY, null, 4);

        EventDTO parent = eventService.create(user.auth0Id, req);

        assertEquals("FREQ=WEEKLY;COUNT=4", parent.recurrenceRule());
        assertNull(parent.parentEventId());

        List<Event> children = Event.list("parentEventId = ?1 order by startDate", parent.id());
        assertEquals(3, children.size());
        assertTrue(children.stream().allMatch(c -> c.recurrenceRule == null));
        assertTrue(children.stream().allMatch(c -> c.parentEventId.equals(parent.id())));
        // 7-day spacing
        assertEquals(req.startDate.plusDays(7), children.get(0).startDate);
        assertEquals(req.startDate.plusDays(14), children.get(1).startDate);
        assertEquals(req.startDate.plusDays(21), children.get(2).startDate);
    }

    @Test
    @TestTransaction
    void createRecurring_biweekly_persistsCorrectSpacing() {
        User user = persistUser("auth0|rec-biw", "rec-biw@example.com");
        CreateEventRequest req = validCreateRequestWithRecurrence(
                ch.unige.events.entity.RecurrenceFrequency.BIWEEKLY, null, 3);

        EventDTO parent = eventService.create(user.auth0Id, req);

        List<Event> children = Event.list("parentEventId = ?1 order by startDate", parent.id());
        assertEquals(2, children.size());
        assertEquals(req.startDate.plusDays(14), children.get(0).startDate);
        assertEquals(req.startDate.plusDays(28), children.get(1).startDate);
    }

    @Test
    @TestTransaction
    void createRecurring_monthly_persistsCorrectSpacing() {
        User user = persistUser("auth0|rec-month", "rec-month@example.com");
        CreateEventRequest req = validCreateRequestWithRecurrence(
                ch.unige.events.entity.RecurrenceFrequency.MONTHLY, null, 3);

        EventDTO parent = eventService.create(user.auth0Id, req);

        List<Event> children = Event.list("parentEventId = ?1 order by startDate", parent.id());
        assertEquals(2, children.size());
        assertEquals(req.startDate.plusMonths(1), children.get(0).startDate);
        assertEquals(req.startDate.plusMonths(2), children.get(1).startDate);
    }

    @Test
    @TestTransaction
    void createRecurring_withoutEndDateOrMaxOccurrences_returns400_recurrenceUnbounded() {
        User user = persistUser("auth0|rec-unb", "rec-unb@example.com");
        CreateEventRequest req = validCreateRequestWithRecurrence(
                ch.unige.events.entity.RecurrenceFrequency.WEEKLY, null, null);

        WebApplicationException ex = assertThrows(
                WebApplicationException.class,
                () -> eventService.create(user.auth0Id, req));
        assertEquals(400, ex.getResponse().getStatus());
        ch.unige.events.dto.ApiErrorResponse body =
                (ch.unige.events.dto.ApiErrorResponse) ex.getResponse().getEntity();
        assertEquals("recurrence_unbounded", body.error());
    }

    @Test
    @TestTransaction
    void createRecurring_endDateBeforeStart_returns400_recurrenceEndBeforeStart() {
        User user = persistUser("auth0|rec-ebs", "rec-ebs@example.com");
        CreateEventRequest req = validCreateRequest();
        req.startDate = LocalDateTime.now().plusDays(10);
        req.endDate = LocalDateTime.now().plusDays(11);
        req.recurrence = new ch.unige.events.dto.event.RecurrenceRequest(
                ch.unige.events.entity.RecurrenceFrequency.WEEKLY,
                req.startDate.toLocalDate().minusDays(1),
                null);

        WebApplicationException ex = assertThrows(
                WebApplicationException.class,
                () -> eventService.create(user.auth0Id, req));
        assertEquals(400, ex.getResponse().getStatus());
        ch.unige.events.dto.ApiErrorResponse body =
                (ch.unige.events.dto.ApiErrorResponse) ex.getResponse().getEntity();
        assertEquals("recurrence_end_before_start", body.error());
    }

    @Test
    @TestTransaction
    void createRecurring_inheritsParentStatusPublished() {
        User user = persistUser("auth0|rec-pub", "rec-pub@example.com");
        CreateEventRequest req = validCreateRequestWithRecurrence(
                ch.unige.events.entity.RecurrenceFrequency.WEEKLY, null, 3);
        req.setStatus(EventStatus.PUBLISHED);

        EventDTO parent = eventService.create(user.auth0Id, req);

        assertEquals(EventStatus.PUBLISHED, parent.status());
        List<Event> children = Event.list("parentEventId = ?1", parent.id());
        assertEquals(2, children.size());
        assertTrue(children.stream().allMatch(c -> c.status == EventStatus.PUBLISHED));
    }

    @Test
    @TestTransaction
    void createRecurring_defaultsToDraft() {
        User user = persistUser("auth0|rec-draft", "rec-draft@example.com");
        CreateEventRequest req = validCreateRequestWithRecurrence(
                ch.unige.events.entity.RecurrenceFrequency.WEEKLY, null, 3);

        EventDTO parent = eventService.create(user.auth0Id, req);

        assertEquals(EventStatus.DRAFT, parent.status());
        List<Event> children = Event.list("parentEventId = ?1", parent.id());
        assertTrue(children.stream().allMatch(c -> c.status == EventStatus.DRAFT));
    }

    @Test
    @TestTransaction
    void create_withoutRecurrence_legacyBehaviorUnchanged() {
        User user = persistUser("auth0|legacy", "legacy@example.com");
        CreateEventRequest req = validCreateRequest();

        EventDTO standalone = eventService.create(user.auth0Id, req);

        assertNull(standalone.parentEventId());
        assertNull(standalone.recurrenceRule());
        // No children persisted.
        long childrenCount = Event.count("parentEventId = ?1", standalone.id());
        assertEquals(0L, childrenCount);
    }

    @Test
    @TestTransaction
    void createRecurring_recurrenceRuleEncodesUntilAndCount() {
        User user = persistUser("auth0|rec-rule", "rec-rule@example.com");
        java.time.LocalDate until = LocalDateTime.now().plusDays(30).toLocalDate();
        CreateEventRequest req = validCreateRequestWithRecurrence(
                ch.unige.events.entity.RecurrenceFrequency.MONTHLY, until, 3);

        EventDTO parent = eventService.create(user.auth0Id, req);

        // Format: FREQ=MONTHLY;UNTIL=YYYYMMDD;COUNT=3
        assertTrue(parent.recurrenceRule().startsWith("FREQ=MONTHLY;UNTIL="));
        assertTrue(parent.recurrenceRule().endsWith(";COUNT=3"));
    }

    @Test
    @TestTransaction
    void getOccurrences_parentRecurring_returnsChildrenSortedAsc() {
        User user = persistUser("auth0|occ-sort", "occ-sort@example.com");
        CreateEventRequest req = validCreateRequestWithRecurrence(
                ch.unige.events.entity.RecurrenceFrequency.WEEKLY, null, 4);

        EventDTO parent = eventService.create(user.auth0Id, req);
        List<EventDTO> occurrences = eventService.getOccurrences(parent.id(), user.auth0Id, false, 0, 52);

        assertEquals(3, occurrences.size());
        assertTrue(occurrences.get(0).startDate().isBefore(occurrences.get(1).startDate()));
        assertTrue(occurrences.get(1).startDate().isBefore(occurrences.get(2).startDate()));
        assertTrue(occurrences.stream().allMatch(o -> o.parentEventId().equals(parent.id())));
    }

    @Test
    @TestTransaction
    void getOccurrences_standaloneEvent_returns200EmptyList() {
        User user = persistUser("auth0|occ-stand", "occ-stand@example.com");
        Event standalone = persistEvent("Lonely", EventCategory.ACADEMIC, EventStatus.PUBLISHED, user);

        List<EventDTO> occurrences = eventService.getOccurrences(standalone.id, null, false, 0, 52);

        assertNotNull(occurrences);
        assertTrue(occurrences.isEmpty());
    }

    @Test
    @TestTransaction
    void getOccurrences_draftByNonCreator_returns404_antiOracle() {
        User creator = persistUser("auth0|occ-creator", "occ-creator@example.com");
        Event draft = persistEvent("Hidden draft", EventCategory.ACADEMIC, EventStatus.DRAFT, creator);

        assertThrows(NotFoundException.class,
                () -> eventService.getOccurrences(draft.id, "auth0|stranger", false, 0, 52));
    }

    @Test
    @TestTransaction
    void getOccurrences_draftByCreator_returns200() {
        User creator = persistUser("auth0|occ-self", "occ-self@example.com");
        Event draft = persistEvent("Visible draft", EventCategory.ACADEMIC, EventStatus.DRAFT, creator);

        List<EventDTO> occurrences = eventService.getOccurrences(draft.id, creator.auth0Id, false, 0, 52);

        assertNotNull(occurrences);
        assertTrue(occurrences.isEmpty());
    }

    @Test
    @TestTransaction
    void getOccurrences_bannedEvent_returns404() {
        User creator = persistUser("auth0|occ-ban", "occ-ban@example.com");
        Event banned = persistEvent("Banned", EventCategory.ACADEMIC, EventStatus.BANNED, creator);

        assertThrows(NotFoundException.class,
                () -> eventService.getOccurrences(banned.id, creator.auth0Id, false, 0, 52));
    }

    @Test
    @TestTransaction
    void update_parentTitle_doesNotPropagateToOccurrences() {
        User user = persistUser("auth0|upd-rec", "upd-rec@example.com");
        CreateEventRequest req = validCreateRequestWithRecurrence(
                ch.unige.events.entity.RecurrenceFrequency.WEEKLY, null, 3);
        EventDTO parent = eventService.create(user.auth0Id, req);

        UpdateEventRequest upd = validUpdateRequest("Renamed", EventCategory.ACADEMIC, EventStatus.DRAFT);
        upd.location = req.location;
        upd.startDate = req.startDate;
        upd.endDate = req.endDate;
        eventService.update(parent.id(), user.auth0Id, upd);

        Event refreshedParent = Event.findById(parent.id());
        assertEquals("Renamed", refreshedParent.title);
        // Children still carry the original title.
        List<Event> children = Event.list("parentEventId = ?1", parent.id());
        assertEquals(2, children.size());
        assertTrue(children.stream().allMatch(c -> c.title.equals("Test Event")));
    }

    @Test
    @TestTransaction
    void cancel_parentDoesNotCascadeToOccurrences() {
        User user = persistUser("auth0|cancel-rec", "cancel-rec@example.com");
        CreateEventRequest req = validCreateRequestWithRecurrence(
                ch.unige.events.entity.RecurrenceFrequency.WEEKLY, null, 3);
        req.setStatus(EventStatus.PUBLISHED);
        EventDTO parent = eventService.create(user.auth0Id, req);

        eventService.cancel(parent.id(), user.auth0Id);

        Event refreshedParent = Event.findById(parent.id());
        assertEquals(EventStatus.CANCELLED, refreshedParent.status);
        // Children remain PUBLISHED.
        List<Event> children = Event.list("parentEventId = ?1", parent.id());
        assertEquals(2, children.size());
        assertTrue(children.stream().allMatch(c -> c.status == EventStatus.PUBLISHED));
    }

    @Test
    @TestTransaction
    void delete_parent_setsOccurrencesParentEventIdToNull() {
        User user = persistUser("auth0|del-rec", "del-rec@example.com");
        CreateEventRequest req = validCreateRequestWithRecurrence(
                ch.unige.events.entity.RecurrenceFrequency.WEEKLY, null, 3);
        EventDTO parent = eventService.create(user.auth0Id, req);

        // EventService.delete requires the event to be CANCELLED first.
        eventService.cancel(parent.id(), user.auth0Id);
        eventService.delete(parent.id(), user.auth0Id);
        entityManager.flush();
        entityManager.clear();

        // Children survive with parent_event_id = NULL (FK ON DELETE SET NULL).
        List<Event> survivors = Event.list("title = ?1", "Test Event");
        assertEquals(2, survivors.size());
        assertTrue(survivors.stream().allMatch(c -> c.parentEventId == null));
    }
}
