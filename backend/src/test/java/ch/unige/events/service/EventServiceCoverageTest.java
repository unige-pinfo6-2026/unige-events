package ch.unige.events.service;

import ch.unige.events.dto.event.CreateEventRequest;
import ch.unige.events.dto.event.EventDTO;
import ch.unige.events.dto.event.UpdateEventRequest;
import ch.unige.events.entity.Event;
import ch.unige.events.entity.EventCategory;
import ch.unige.events.entity.EventStatus;
import ch.unige.events.entity.Faculty;
import ch.unige.events.entity.User;
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
        deleteAll();
        User user = persistUser("auth0|a", "a@example.com");
        persistEvent("Event 1", EventCategory.ACADEMIC, EventStatus.DRAFT, user);
        persistEvent("Event 2", EventCategory.SPORTS, EventStatus.PUBLISHED, user);

        List<EventDTO> result = eventService.getAll(0, 20, null, null, null, null, null, null);

        assertEquals(2, result.size());
    }

    @Test
    @TestTransaction
    void getAll_withStatusFilter_returnsFiltered() {
        deleteAll();
        User user = persistUser("auth0|b", "b@example.com");
        persistEvent("Draft", EventCategory.ACADEMIC, EventStatus.DRAFT, user);
        persistEvent("Published", EventCategory.ACADEMIC, EventStatus.PUBLISHED, user);

        List<EventDTO> result = eventService.getAll(0, 20, EventStatus.PUBLISHED, null, null, null, null, null);

        assertEquals(1, result.size());
        assertEquals("Published", result.get(0).title());
    }

    @Test
    @TestTransaction
    void getAll_withCategoryFilter_returnsFiltered() {
        deleteAll();
        User user = persistUser("auth0|c", "c@example.com");
        persistEvent("Academic", EventCategory.ACADEMIC, EventStatus.DRAFT, user);
        persistEvent("Sports", EventCategory.SPORTS, EventStatus.DRAFT, user);

        List<EventDTO> result = eventService.getAll(0, 20, null, EventCategory.SPORTS, null, null, null, null);

        assertEquals(1, result.size());
        assertEquals("Sports", result.get(0).title());
    }

    @Test
    @TestTransaction
    void getAll_withOrganizerIdFilter_returnsFiltered() {
        deleteAll();
        User alice = persistUser("auth0|alice", "alice@example.com");
        User bob = persistUser("auth0|bob", "bob@example.com");
        persistEvent("Alice's event", EventCategory.ACADEMIC, EventStatus.DRAFT, alice);
        persistEvent("Bob's event", EventCategory.ACADEMIC, EventStatus.DRAFT, bob);

        List<EventDTO> result = eventService.getAll(0, 20, null, null, alice.id, null, null, null);

        assertEquals(1, result.size());
        assertEquals("Alice's event", result.get(0).title());
    }

    @Test
    @TestTransaction
    void getAll_withEndDateFromFilter_excludesEndedEvents() {
        deleteAll();
        User user = persistUser("auth0|edf", "edf@example.com");
        persistEvent("Active Event", EventCategory.ACADEMIC, EventStatus.PUBLISHED, user);
        persistEvent("Also Active", EventCategory.SPORTS, EventStatus.PUBLISHED, user);

        List<EventDTO> result = eventService.getAll(0, 20, null, null, null, LocalDateTime.now().minusDays(1), null, null);

        assertEquals(2, result.size());
    }

    @Test
    @TestTransaction
    void getAll_withPagination_returnsPage() {
        deleteAll();
        User user = persistUser("auth0|page", "page@example.com");
        persistEvent("E1", EventCategory.ACADEMIC, EventStatus.DRAFT, user);
        persistEvent("E2", EventCategory.ACADEMIC, EventStatus.DRAFT, user);
        persistEvent("E3", EventCategory.ACADEMIC, EventStatus.DRAFT, user);

        List<EventDTO> page0 = eventService.getAll(0, 2, null, null, null, null, null, null);
        List<EventDTO> page1 = eventService.getAll(1, 2, null, null, null, null, null, null);

        assertEquals(2, page0.size());
        assertEquals(1, page1.size());
    }

    // --- create ---

    @Test
    @TestTransaction
    void create_withExistingUser_linksCreator() {
        deleteAll();
        persistUser("auth0|creator", "creator@example.com");

        CreateEventRequest req = validCreateRequest();
        EventDTO result = eventService.create("auth0|creator", req);

        assertNotNull(result.creatorId());
        assertEquals("Test Event", result.title());
    }

    @Test
    @TestTransaction
    void create_withUnknownUser_throwsNotFoundException() {
        deleteAll();

        CreateEventRequest req = validCreateRequest();
        assertThrows(NotFoundException.class, () -> eventService.create("auth0|unknown", req));
    }

    @Test
    @TestTransaction
    void create_withPublishedStatus_persistsPublished() {
        deleteAll();
        persistUser("auth0|pub", "pub@example.com");

        CreateEventRequest req = validCreateRequest();
        req.setStatus(EventStatus.PUBLISHED);
        EventDTO result = eventService.create("auth0|pub", req);

        assertEquals(EventStatus.PUBLISHED, result.status());
    }

    @Test
    @TestTransaction
    void create_withoutStatus_defaultsToDraft() {
        deleteAll();
        persistUser("auth0|draft", "draft@example.com");

        CreateEventRequest req = validCreateRequest();
        req.setStatus(null);
        EventDTO result = eventService.create("auth0|draft", req);

        assertEquals(EventStatus.DRAFT, result.status());
    }

    @Test
    @TestTransaction
    void create_withCancelledStatus_throwsBadRequest() {
        deleteAll();
        persistUser("auth0|cancelled", "cancelled@example.com");

        CreateEventRequest req = validCreateRequest();
        req.setStatus(EventStatus.CANCELLED);

        assertThrows(BadRequestException.class, () -> eventService.create("auth0|cancelled", req));
    }

    // --- getById ---

    @Test
    @TestTransaction
    void getById_existingEvent_returnsDTO() {
        deleteAll();
        User user = persistUser("auth0|get", "get@example.com");
        Event event = persistEvent("Find Me", EventCategory.CULTURAL, EventStatus.DRAFT, user);

        EventDTO result = eventService.getById(event.id);

        assertEquals(event.id, result.id());
        assertEquals("Find Me", result.title());
    }

    @Test
    @TestTransaction
    void getById_unknownEvent_throwsNotFound() {
        deleteAll();

        assertThrows(NotFoundException.class, () -> eventService.getById(999999L));
    }

    // --- update ---

    @Test
    @TestTransaction
    void update_asCreator_updatesEvent() {
        deleteAll();
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
        deleteAll();
        User user = persistUser("auth0|status", "status@example.com");
        Event event = persistEvent("Title", EventCategory.ACADEMIC, EventStatus.DRAFT, user);

        UpdateEventRequest req = validUpdateRequest("Title", EventCategory.ACADEMIC, EventStatus.PUBLISHED);
        EventDTO result = eventService.update(event.id, "auth0|status", req);

        assertEquals(EventStatus.PUBLISHED, result.status());
    }

    @Test
    @TestTransaction
    void update_unknownEvent_throwsNotFound() {
        deleteAll();

        UpdateEventRequest req = validUpdateRequest("X", EventCategory.ACADEMIC, null);
        assertThrows(NotFoundException.class, () -> eventService.update(999999L, "auth0|x", req));
    }

    @Test
    @TestTransaction
    void update_nullCreator_throwsForbidden() {
        deleteAll();
        Event event = persistEvent("No Creator", EventCategory.ACADEMIC, EventStatus.DRAFT, null);

        UpdateEventRequest req = validUpdateRequest("X", EventCategory.ACADEMIC, null);
        assertThrows(ForbiddenException.class, () -> eventService.update(event.id, "auth0|x", req));
    }

    @Test
    @TestTransaction
    void update_differentUser_throwsForbidden() {
        deleteAll();
        User user = persistUser("auth0|owner", "owner@example.com");
        Event event = persistEvent("Owner's Event", EventCategory.ACADEMIC, EventStatus.DRAFT, user);

        UpdateEventRequest req = validUpdateRequest("X", EventCategory.ACADEMIC, null);
        assertThrows(ForbiddenException.class, () -> eventService.update(event.id, "auth0|intruder", req));
    }

    // --- delete ---

    @Test
    @TestTransaction
    void delete_cancelledEvent_asCreator_removesEntity() {
        deleteAll();
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
        deleteAll();
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
        deleteAll();
        User user = persistUser("auth0|canc1", "canc1@example.com");
        Event event = persistEvent("Draft", EventCategory.ACADEMIC, EventStatus.DRAFT, user);

        EventDTO result = eventService.cancel(event.id, "auth0|canc1");

        assertEquals(EventStatus.CANCELLED, result.status());
    }

    @Test
    @TestTransaction
    void cancel_publishedEvent_setsStatusCancelled() {
        deleteAll();
        User user = persistUser("auth0|canc2", "canc2@example.com");
        Event event = persistEvent("Published", EventCategory.ACADEMIC, EventStatus.PUBLISHED, user);

        EventDTO result = eventService.cancel(event.id, "auth0|canc2");

        assertEquals(EventStatus.CANCELLED, result.status());
    }

    @Test
    @TestTransaction
    void cancel_alreadyCancelled_throwsConflict() {
        deleteAll();
        User user = persistUser("auth0|canc3", "canc3@example.com");
        Event event = persistEvent("Cancelled", EventCategory.ACADEMIC, EventStatus.CANCELLED, user);

        WebApplicationException ex = assertThrows(WebApplicationException.class,
                () -> eventService.cancel(event.id, "auth0|canc3"));
        assertEquals(409, ex.getResponse().getStatus());
    }

    @Test
    @TestTransaction
    void cancel_unknownEvent_throwsNotFound() {
        deleteAll();
        assertThrows(NotFoundException.class, () -> eventService.cancel(999999L, "auth0|x"));
    }

    @Test
    @TestTransaction
    void cancel_notCreator_throwsForbidden() {
        deleteAll();
        User user = persistUser("auth0|cancOwn", "cancOwn@example.com");
        Event event = persistEvent("Owner", EventCategory.ACADEMIC, EventStatus.DRAFT, user);

        assertThrows(ForbiddenException.class, () -> eventService.cancel(event.id, "auth0|intruder"));
    }

    // --- restore ---

    @Test
    @TestTransaction
    void restore_cancelledEvent_setsStatusDraft() {
        deleteAll();
        User user = persistUser("auth0|rest1", "rest1@example.com");
        Event event = persistEvent("Cancelled", EventCategory.ACADEMIC, EventStatus.CANCELLED, user);

        EventDTO result = eventService.restore(event.id, "auth0|rest1");

        assertEquals(EventStatus.DRAFT, result.status());
    }

    @Test
    @TestTransaction
    void restore_nonCancelled_throwsConflict() {
        deleteAll();
        User user = persistUser("auth0|rest2", "rest2@example.com");
        Event event = persistEvent("Draft", EventCategory.ACADEMIC, EventStatus.DRAFT, user);

        WebApplicationException ex = assertThrows(WebApplicationException.class,
                () -> eventService.restore(event.id, "auth0|rest2"));
        assertEquals(409, ex.getResponse().getStatus());
    }

    @Test
    @TestTransaction
    void restore_unknownEvent_throwsNotFound() {
        deleteAll();
        assertThrows(NotFoundException.class, () -> eventService.restore(999999L, "auth0|x"));
    }

    @Test
    @TestTransaction
    void restore_notCreator_throwsForbidden() {
        deleteAll();
        User user = persistUser("auth0|restOwn", "restOwn@example.com");
        Event event = persistEvent("Cancelled", EventCategory.ACADEMIC, EventStatus.CANCELLED, user);

        assertThrows(ForbiddenException.class, () -> eventService.restore(event.id, "auth0|intruder"));
    }

    // --- update rejects cancelled ---

    @Test
    @TestTransaction
    void update_cancelledEvent_throwsConflict() {
        deleteAll();
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
        deleteAll();

        assertThrows(NotFoundException.class, () -> eventService.delete(999999L, "auth0|x"));
    }

    @Test
    @TestTransaction
    void delete_nullCreator_throwsForbidden() {
        deleteAll();
        Event event = persistEvent("No Creator", EventCategory.ACADEMIC, EventStatus.DRAFT, null);

        assertThrows(ForbiddenException.class, () -> eventService.delete(event.id, "auth0|x"));
    }

    @Test
    @TestTransaction
    void delete_differentUser_throwsForbidden() {
        deleteAll();
        User user = persistUser("auth0|owner2", "owner2@example.com");
        Event event = persistEvent("Owner's Event", EventCategory.ACADEMIC, EventStatus.DRAFT, user);

        assertThrows(ForbiddenException.class, () -> eventService.delete(event.id, "auth0|intruder"));
    }

    // --- publish ---

    @Test
    @TestTransaction
    void publish_asCreator_setsStatusPublished() {
        deleteAll();
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
        deleteAll();
        User user = persistUser("auth0|owner", "owner@example.com");
        Event event = persistEvent("Draft", EventCategory.ACADEMIC, EventStatus.DRAFT, user);

        EventDTO result = eventService.publish(event.id, "auth0|admin", true);

        assertEquals(EventStatus.PUBLISHED, result.status());
    }

    @Test
    @TestTransaction
    void publish_unknownEvent_throwsNotFound() {
        deleteAll();

        assertThrows(NotFoundException.class, () -> eventService.publish(999999L, "auth0|x", false));
    }

    @Test
    @TestTransaction
    void publish_notCreatorNotAdmin_throwsForbidden() {
        deleteAll();
        User user = persistUser("auth0|owner", "owner2@example.com");
        Event event = persistEvent("Draft", EventCategory.ACADEMIC, EventStatus.DRAFT, user);

        assertThrows(ForbiddenException.class, () -> eventService.publish(event.id, "auth0|intruder", false));
    }

    @Test
    @TestTransaction
    void publish_nullCreator_throwsForbidden() {
        deleteAll();
        Event event = persistEvent("Draft", EventCategory.ACADEMIC, EventStatus.DRAFT, null);

        assertThrows(ForbiddenException.class, () -> eventService.publish(event.id, "auth0|x", false));
    }

    @Test
    @TestTransaction
    void publish_alreadyPublished_throws409() {
        deleteAll();
        User user = persistUser("auth0|pub2", "pub2@example.com");
        Event event = persistEvent("Published", EventCategory.ACADEMIC, EventStatus.PUBLISHED, user);

        WebApplicationException ex = assertThrows(WebApplicationException.class,
                () -> eventService.publish(event.id, "auth0|pub2", false));
        assertEquals(409, ex.getResponse().getStatus());
    }

    @Test
    @TestTransaction
    void publish_cancelledEvent_throws409() {
        deleteAll();
        User user = persistUser("auth0|pub3", "pub3@example.com");
        Event event = persistEvent("Cancelled", EventCategory.ACADEMIC, EventStatus.CANCELLED, user);

        WebApplicationException ex = assertThrows(WebApplicationException.class,
                () -> eventService.publish(event.id, "auth0|pub3", false));
        assertEquals(409, ex.getResponse().getStatus());
    }

    @Test
    @TestTransaction
    void publish_pastStartDate_throws422WithErrors() {
        deleteAll();
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
        deleteAll();
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
        deleteAll();
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
        deleteAll();
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
        deleteAll();
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
        deleteAll();
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
        deleteAll();
        Path fakeFile = tempDir.resolve("f.jpg");
        Files.write(fakeFile, new byte[0]);
        FileUpload upload = new StubFileUpload("f.jpg", "image/jpeg", fakeFile);

        assertThrows(NotFoundException.class,
                () -> eventService.uploadImage(999999L, "auth0|x", upload, false));
    }

    @Test
    @TestTransaction
    void uploadImage_notCreatorNotAdmin_throwsForbidden(@TempDir Path tempDir) throws IOException {
        deleteAll();
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
        deleteAll();
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
        deleteAll();
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
        deleteAll();
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
        deleteAll();
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
        deleteAll();
        User user = persistUser("auth0|fac1", "fac1@example.com");
        persistEvent("Sciences Event", EventCategory.ACADEMIC, EventStatus.PUBLISHED, user, Faculty.SCIENCES);
        persistEvent("Law Event", EventCategory.ACADEMIC, EventStatus.PUBLISHED, user, Faculty.LAW);

        List<EventDTO> result = eventService.getAll(0, 20, null, null, null, null, Faculty.SCIENCES, null);

        assertEquals(1, result.size());
        assertEquals(Faculty.SCIENCES, result.get(0).faculty());
    }

    @Test
    @TestTransaction
    void getAll_withFacultyNull_returnsAll() {
        deleteAll();
        User user = persistUser("auth0|fac2", "fac2@example.com");
        persistEvent("Sciences Event", EventCategory.ACADEMIC, EventStatus.PUBLISHED, user, Faculty.SCIENCES);
        persistEvent("No Faculty", EventCategory.ACADEMIC, EventStatus.PUBLISHED, user, null);

        List<EventDTO> result = eventService.getAll(0, 20, null, null, null, null, null, null);

        assertEquals(2, result.size());
    }

    @Test
    @TestTransaction
    void create_withFaculty_persistsFaculty() {
        deleteAll();
        persistUser("auth0|facCreate", "facCreate@example.com");

        CreateEventRequest req = validCreateRequest();
        req.faculty = Faculty.MEDICINE;
        EventDTO result = eventService.create("auth0|facCreate", req);

        assertEquals(Faculty.MEDICINE, result.faculty());
    }

    @Test
    @TestTransaction
    void update_withFaculty_updatesFaculty() {
        deleteAll();
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
        deleteAll();
        User user = persistUser("auth0|facNone", "facNone@example.com");
        persistEvent("Sciences Event", EventCategory.ACADEMIC, EventStatus.PUBLISHED, user, Faculty.SCIENCES);
        persistEvent("No Faculty Event", EventCategory.ACADEMIC, EventStatus.PUBLISHED, user, null);

        List<EventDTO> result = eventService.getAll(0, 20, null, null, null, null, null, true);

        assertEquals(1, result.size());
        assertNull(result.get(0).faculty());
        assertEquals("No Faculty Event", result.get(0).title());
    }

    @Test
    @TestTransaction
    void getAll_withFacultyNoneAndFaculty_facultyNoneWins() {
        deleteAll();
        User user = persistUser("auth0|facNonePrio", "facNonePrio@example.com");
        persistEvent("Sciences Event", EventCategory.ACADEMIC, EventStatus.PUBLISHED, user, Faculty.SCIENCES);
        persistEvent("No Faculty Event", EventCategory.ACADEMIC, EventStatus.PUBLISHED, user, null);

        List<EventDTO> result = eventService.getAll(0, 20, null, null, null, null, Faculty.SCIENCES, true);

        assertEquals(1, result.size());
        assertNull(result.get(0).faculty());
    }

    @Test
    @TestTransaction
    void update_withNullFaculty_clearsFaculty() {
        deleteAll();
        User user = persistUser("auth0|facClr", "facClr@example.com");
        Event event = persistEvent("Event", EventCategory.ACADEMIC, EventStatus.DRAFT, user, Faculty.SCIENCES);

        UpdateEventRequest req = validUpdateRequest("Event", EventCategory.ACADEMIC, null);
        req.faculty = null;
        EventDTO result = eventService.update(event.id, "auth0|facClr", req);

        assertNull(result.faculty());
    }

    // --- helpers ---

    private void deleteAll() {
        entityManager.createNativeQuery("delete from events").executeUpdate();
        entityManager.createNativeQuery("delete from users").executeUpdate();
        entityManager.clear();
    }

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
