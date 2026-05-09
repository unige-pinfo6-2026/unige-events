package ch.unige.events.service;

import ch.unige.events.dto.event.EventDTO;
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

import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
@TestProfile(ShareServiceCoverageProfile.class)
class EventDuplicateCoverageTest {

    @Inject
    EventService eventService;

    @Inject
    EntityManager em;

    // -------------------------------------------------------------------------
    // Happy path — duplication creates correct copy
    // -------------------------------------------------------------------------

    @Test
    @TestTransaction
    void duplicate_createsNewIdAndDraftStatus() {
        User creator = persistUser("auth0|dup-creator", "dup-creator@example.com");
        Event original = persistEvent("Conference Java", creator, EventStatus.PUBLISHED);
        em.flush();

        EventDTO copy = eventService.duplicate(original.id, creator.auth0Id);

        assertNotEquals(original.id, copy.id(), "Duplicate must have a different ID");
        assertEquals(EventStatus.DRAFT, copy.status(), "Duplicate must have status DRAFT");
    }

    @Test
    @TestTransaction
    void duplicate_titlePrefixedWithCopieDe() {
        User creator = persistUser("auth0|dup-title", "dup-title@example.com");
        Event original = persistEvent("My Event", creator, EventStatus.PUBLISHED);
        em.flush();

        EventDTO copy = eventService.duplicate(original.id, creator.auth0Id);

        assertEquals("Copie de My Event", copy.title());
    }

    @Test
    @TestTransaction
    void duplicate_datesShiftedByExactlySevenDays() {
        User creator = persistUser("auth0|dup-dates", "dup-dates@example.com");
        Event original = persistEvent("Dated Event", creator, EventStatus.PUBLISHED);
        LocalDateTime origStart = original.startDate;
        LocalDateTime origEnd = original.endDate;
        em.flush();

        EventDTO copy = eventService.duplicate(original.id, creator.auth0Id);

        assertEquals(origStart.plusDays(7), copy.startDate(),
                "startDate must be shifted by exactly +7 days");
        assertEquals(origEnd.plusDays(7), copy.endDate(),
                "endDate must be shifted by exactly +7 days");
    }

    @Test
    @TestTransaction
    void duplicate_originalRemainsUnchanged() {
        User creator = persistUser("auth0|dup-orig", "dup-orig@example.com");
        Event original = persistEvent("Stable Event", creator, EventStatus.PUBLISHED);
        em.flush();

        eventService.duplicate(original.id, creator.auth0Id);
        em.refresh(original);

        assertEquals(EventStatus.PUBLISHED, original.status,
                "Original event status must not change after duplication");
        assertEquals("Stable Event", original.title);
    }

    // -------------------------------------------------------------------------
    // Non-owner → 403
    // -------------------------------------------------------------------------

    @Test
    @TestTransaction
    void duplicate_nonOwner_throwsForbidden() {
        User creator = persistUser("auth0|dup-own", "dup-own@example.com");
        persistUser("auth0|dup-other", "dup-other@example.com");
        Event original = persistEvent("Owner Event", creator, EventStatus.PUBLISHED);
        em.flush();

        assertThrows(ForbiddenException.class,
                () -> eventService.duplicate(original.id, "auth0|dup-other"));
    }

    @Test
    @TestTransaction
    void duplicate_unknownEvent_throwsNotFound() {
        persistUser("auth0|dup-nf", "dup-nf@example.com");
        em.flush();

        assertThrows(NotFoundException.class,
                () -> eventService.duplicate(999999L, "auth0|dup-nf"));
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private User persistUser(String auth0Id, String email) {
        User u = new User();
        u.auth0Id = auth0Id;
        u.email = email;
        u.profilePublic = false;
        u.createdAt = LocalDateTime.now();
        em.persist(u);
        em.flush();
        return u;
    }

    private Event persistEvent(String title, User creator, EventStatus status) {
        Event e = new Event();
        e.title = title;
        e.location = "Uni Mail";
        e.startDate = LocalDateTime.now().plusDays(1);
        e.endDate = LocalDateTime.now().plusDays(2);
        e.category = EventCategory.ACADEMIC;
        e.status = status;
        e.creator = creator;
        em.persist(e);
        em.flush();
        return e;
    }
}
