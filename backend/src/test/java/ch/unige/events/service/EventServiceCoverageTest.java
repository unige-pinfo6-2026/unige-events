package ch.unige.events.service;

import ch.unige.events.dto.event.CreateEventRequest;
import ch.unige.events.dto.event.EventDTO;
import ch.unige.events.dto.event.UpdateEventRequest;
import ch.unige.events.entity.Attendance;
import ch.unige.events.entity.AttendanceStatus;
import ch.unige.events.entity.Event;
import ch.unige.events.entity.EventCategory;
import ch.unige.events.entity.EventStatus;
import ch.unige.events.entity.Faculty;
import ch.unige.events.entity.User;

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

    // --- getById ---

    @Test
    @TestTransaction
    void getById_existingEvent_returnsDTO() {
        User user = persistUser("auth0|get", "get@example.com");
        Event event = persistEvent("Find Me", EventCategory.CULTURAL, EventStatus.DRAFT, user);

        EventDTO result = eventService.getById(event.id);

        assertEquals(event.id, result.id());
        assertEquals("Find Me", result.title());
    }

    @Test
    @TestTransaction
    void getById_unknownEvent_throwsNotFound() {

        assertThrows(NotFoundException.class, () -> eventService.getById(999999L));
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
        Files.write(fakeFile, "fake-jpeg".getBytes());
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
        Files.write(fakeFile, "fake-png".getBytes());
        FileUpload upload = new StubFileUpload("banner.png", "image/png", fakeFile);

        EventDTO result = eventService.uploadImage(event.id, "auth0|admin2", upload, true);

        assertTrue(result.bannerUrl().startsWith("http"));
    }

    @Test
    @TestTransaction
    void uploadImage_unknownEvent_throwsNotFound(@TempDir Path tempDir) throws IOException {
        Path fakeFile = tempDir.resolve("f.jpg");
        Files.write(fakeFile, new byte[0]);
        FileUpload upload = new StubFileUpload("f.jpg", "image/jpeg", fakeFile);

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

        assertThrows(BadRequestException.class,
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

        assertThrows(BadRequestException.class,
                () -> eventService.uploadImage(event.id, "auth0|nullmime", upload, false));
    }

    @Test
    @TestTransaction
    void uploadImage_noExtension_usesBinExtension(@TempDir Path tempDir) throws IOException {
        User user = persistUser("auth0|noext", "noext@example.com");
        Event event = persistEvent("Event", EventCategory.ACADEMIC, EventStatus.DRAFT, user);

        Path fakeFile = tempDir.resolve("image");
        Files.write(fakeFile, "data".getBytes());
        FileUpload upload = new StubFileUpload("image", "image/jpeg", fakeFile);

        EventDTO result = eventService.uploadImage(event.id, "auth0|noext", upload, false);

        assertTrue(result.bannerUrl().endsWith(".bin"));
    }

    @Test
    @TestTransaction
    void uploadImage_nullFileName_usesBinExtension(@TempDir Path tempDir) throws IOException {
        User user = persistUser("auth0|nullname", "nullname@example.com");
        Event event = persistEvent("Event", EventCategory.ACADEMIC, EventStatus.DRAFT, user);

        Path fakeFile = tempDir.resolve("file");
        Files.write(fakeFile, "data".getBytes());
        FileUpload upload = new StubFileUpload(null, "image/jpeg", fakeFile);

        EventDTO result = eventService.uploadImage(event.id, "auth0|nullname", upload, false);

        assertTrue(result.bannerUrl().endsWith(".bin"));
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

        EventDTO fetched = eventService.getById(created.id());
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

        EventDTO fetched = eventService.getById(created.id());
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

        EventDTO dto = eventService.getById(event.id);

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

        EventDTO dto = eventService.getById(event.id);

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
}
