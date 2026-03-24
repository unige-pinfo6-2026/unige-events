package ch.unige.events.service;

import ch.unige.events.dto.event.CreateEventRequest;
import ch.unige.events.dto.event.EventDTO;
import ch.unige.events.dto.event.UpdateEventRequest;
import ch.unige.events.entity.Event;
import ch.unige.events.entity.EventCategory;
import ch.unige.events.entity.EventStatus;
import ch.unige.events.entity.User;
import io.quarkus.test.TestTransaction;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.ws.rs.ForbiddenException;
import jakarta.ws.rs.NotFoundException;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
@TestProfile(EventServiceCoverageProfile.class)
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

        List<EventDTO> result = eventService.getAll(0, 20, null, null, null);

        assertEquals(2, result.size());
    }

    @Test
    @TestTransaction
    void getAll_withStatusFilter_returnsFiltered() {
        deleteAll();
        User user = persistUser("auth0|b", "b@example.com");
        persistEvent("Draft", EventCategory.ACADEMIC, EventStatus.DRAFT, user);
        persistEvent("Published", EventCategory.ACADEMIC, EventStatus.PUBLISHED, user);

        List<EventDTO> result = eventService.getAll(0, 20, EventStatus.PUBLISHED, null, null);

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

        List<EventDTO> result = eventService.getAll(0, 20, null, EventCategory.SPORTS, null);

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

        List<EventDTO> result = eventService.getAll(0, 20, null, null, alice.id);

        assertEquals(1, result.size());
        assertEquals("Alice's event", result.get(0).title());
    }

    @Test
    @TestTransaction
    void getAll_withPagination_returnsPage() {
        deleteAll();
        User user = persistUser("auth0|page", "page@example.com");
        persistEvent("E1", EventCategory.ACADEMIC, EventStatus.DRAFT, user);
        persistEvent("E2", EventCategory.ACADEMIC, EventStatus.DRAFT, user);
        persistEvent("E3", EventCategory.ACADEMIC, EventStatus.DRAFT, user);

        List<EventDTO> page0 = eventService.getAll(0, 2, null, null, null);
        List<EventDTO> page1 = eventService.getAll(1, 2, null, null, null);

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
    void delete_asCreator_setsStatusCancelled() {
        deleteAll();
        User user = persistUser("auth0|deleter", "deleter@example.com");
        Event event = persistEvent("Cancel Me", EventCategory.ACADEMIC, EventStatus.PUBLISHED, user);

        eventService.delete(event.id, "auth0|deleter");

        entityManager.flush();
        entityManager.refresh(event);
        assertEquals(EventStatus.CANCELLED, event.status);
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
        Event event = new Event();
        event.title = title;
        event.location = "Uni Mail";
        event.startDate = LocalDateTime.now().plusDays(1);
        event.endDate = LocalDateTime.now().plusDays(2);
        event.category = category;
        event.status = status;
        event.creator = creator;
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
}
