package ch.unige.events.service;

import ch.unige.events.dto.event.EventDTO;
import ch.unige.events.entity.*;
import io.quarkus.test.TestTransaction;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.ws.rs.NotFoundException;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
@TestProfile(ShareServiceCoverageProfile.class)
class FeaturedServiceCoverageTest {

    @Inject
    FeaturedService featuredService;

    @Inject
    EntityManager entityManager;

    // --- testNoFeaturedReturnsMostPopular ---

    @Test
    @TestTransaction
    void testNoFeaturedReturnsMostPopular() {
        User user = persistUser("auth0|nofeat", "nofeat@test.com");
        Event e1 = persistPublishedEvent("Popular", user);
        Event e2 = persistPublishedEvent("Unpopular", user);

        persistAttendance(e1.id, UUID.randomUUID(), AttendanceStatus.ATTENDING);
        persistAttendance(e1.id, UUID.randomUUID(), AttendanceStatus.ATTENDING);
        entityManager.flush();

        List<EventDTO> result = featuredService.getFeatured(6);

        assertEquals(2, result.size());
        assertEquals(e1.id, result.get(0).id());
    }

    // --- testPhase1FeaturedEventsReturnedFirst ---

    @Test
    @TestTransaction
    void testPhase1FeaturedEventsReturnedFirst() {
        User user = persistUser("auth0|ph1", "ph1@test.com");
        Event featured = persistFeaturedEvent("Featured", user, LocalDateTime.now().minusHours(1));
        Event popular = persistPublishedEvent("Popular", user);
        persistAttendance(popular.id, UUID.randomUUID(), AttendanceStatus.ATTENDING);
        persistAttendance(popular.id, UUID.randomUUID(), AttendanceStatus.ATTENDING);
        persistAttendance(popular.id, UUID.randomUUID(), AttendanceStatus.ATTENDING);
        entityManager.flush();

        List<EventDTO> result = featuredService.getFeatured(6);

        assertEquals(featured.id, result.get(0).id());
    }

    // --- testPhase1OrderedByFeaturedAtDesc ---

    @Test
    @TestTransaction
    void testPhase1OrderedByFeaturedAtDesc() {
        User user = persistUser("auth0|ord", "ord@test.com");
        LocalDateTime older = LocalDateTime.now().minusDays(2);
        LocalDateTime newer = LocalDateTime.now().minusDays(1);
        Event e1 = persistFeaturedEvent("Older", user, older);
        Event e2 = persistFeaturedEvent("Newer", user, newer);
        entityManager.flush();

        List<EventDTO> result = featuredService.getFeatured(6);

        assertEquals(e2.id, result.get(0).id());
        assertEquals(e1.id, result.get(1).id());
    }

    // --- testPhase2FilledWhenPhase1Incomplete ---

    @Test
    @TestTransaction
    void testPhase2FilledWhenPhase1Incomplete() {
        User user = persistUser("auth0|fill", "fill@test.com");
        persistFeaturedEvent("Featured1", user, LocalDateTime.now().minusHours(1));
        persistFeaturedEvent("Featured2", user, LocalDateTime.now().minusHours(2));
        persistPublishedEvent("Phase2A", user);
        persistPublishedEvent("Phase2B", user);
        entityManager.flush();

        List<EventDTO> result = featuredService.getFeatured(6);

        assertEquals(4, result.size());
    }

    // --- testPhase2ExcludesPhase1Ids ---

    @Test
    @TestTransaction
    void testPhase2ExcludesPhase1Ids() {
        User user = persistUser("auth0|excl", "excl@test.com");
        Event feat = persistFeaturedEvent("FeaturedOnly", user, LocalDateTime.now().minusHours(1));
        entityManager.flush();

        List<EventDTO> result = featuredService.getFeatured(6);

        assertEquals(1, result.stream().filter(e -> e.id().equals(feat.id)).count());
        assertEquals(1, result.size());
    }

    // --- testPhase2OrderedByPopularityScore ---

    @Test
    @TestTransaction
    void testPhase2OrderedByPopularityScore() {
        User user = persistUser("auth0|pop", "pop@test.com");
        Event low = persistPublishedEvent("LowPop", user);
        Event high = persistPublishedEvent("HighPop", user);
        persistAttendance(high.id, UUID.randomUUID(), AttendanceStatus.ATTENDING);
        persistAttendance(high.id, UUID.randomUUID(), AttendanceStatus.ATTENDING);
        persistFavorite(high.id, UUID.randomUUID());
        persistAttendance(low.id, UUID.randomUUID(), AttendanceStatus.ATTENDING);
        entityManager.flush();

        List<EventDTO> result = featuredService.getFeatured(6);

        assertEquals(high.id, result.get(0).id());
        assertEquals(low.id, result.get(1).id());
    }

    // --- testLimitParameter ---

    @Test
    @TestTransaction
    void testLimitParameter() {
        User user = persistUser("auth0|lim", "lim@test.com");
        persistPublishedEvent("E1", user);
        persistPublishedEvent("E2", user);
        persistPublishedEvent("E3", user);
        entityManager.flush();

        List<EventDTO> result = featuredService.getFeatured(2);

        assertEquals(2, result.size());
    }

    // --- testLimitMaxEnforced ---

    @Test
    @TestTransaction
    void testLimitMaxEnforced() {
        User user = persistUser("auth0|max", "max@test.com");
        for (int i = 0; i < 15; i++) {
            persistPublishedEvent("E" + i, user);
        }
        entityManager.flush();

        List<EventDTO> result = featuredService.getFeatured(99);

        assertEquals(12, result.size());
    }

    // --- testFeatureEvent ---

    @Test
    @TestTransaction
    void testFeatureEvent() {
        User user = persistUser("auth0|feat", "feat@test.com");
        Event event = persistPublishedEvent("ToFeature", user);
        entityManager.flush();

        EventDTO result = featuredService.feature(event.id);

        assertTrue(result.featured());
        entityManager.flush();
        entityManager.clear();
        Event reloaded = Event.findById(event.id);
        assertTrue(reloaded.featured);
        assertNotNull(reloaded.featuredAt);
    }

    // --- testUnfeatureEvent ---

    @Test
    @TestTransaction
    void testUnfeatureEvent() {
        User user = persistUser("auth0|unfeat", "unfeat@test.com");
        Event event = persistFeaturedEvent("ToUnfeature", user, LocalDateTime.now().minusHours(1));
        entityManager.flush();

        EventDTO result = featuredService.unfeature(event.id);

        assertFalse(result.featured());
        entityManager.flush();
        entityManager.clear();
        Event reloaded = Event.findById(event.id);
        assertFalse(reloaded.featured);
        assertNull(reloaded.featuredAt);
    }

    @Test
    @TestTransaction
    void testFeatureEvent_alreadyFeatured_isIdempotentAndPreservesFeaturedAt() {
        User user = persistUser("auth0|feat-idem", "featidem@test.com");
        LocalDateTime original = LocalDateTime.now().minusHours(3);
        Event event = persistFeaturedEvent("AlreadyFeatured", user, original);
        entityManager.flush();

        EventDTO result = featuredService.feature(event.id);

        assertTrue(result.featured());
        entityManager.flush();
        entityManager.clear();
        Event reloaded = Event.findById(event.id);
        assertTrue(reloaded.featured);
        // Idempotent: featuredAt timestamp preserved (no overwrite on a re-feature).
        assertEquals(original.withNano(0), reloaded.featuredAt.withNano(0));
    }

    // --- testFeatureEvent_notFound ---

    @Test
    @TestTransaction
    void testFeatureEvent_notFound_throws404() {
        assertThrows(NotFoundException.class, () -> featuredService.feature(999999L));
    }

    // --- testUnfeatureEvent_notFound ---

    @Test
    @TestTransaction
    void testUnfeatureEvent_notFound_throws404() {
        assertThrows(NotFoundException.class, () -> featuredService.unfeature(999999L));
    }

    // --- testExpiredEventsExcluded ---

    @Test
    @TestTransaction
    void testExpiredEventsExcludedFromFeatured() {
        User user = persistUser("auth0|exp", "exp@test.com");
        Event past = new Event();
        past.title = "Past";
        past.location = "Somewhere";
        past.startDate = LocalDateTime.now().minusDays(3);
        past.endDate = LocalDateTime.now().minusDays(1);
        past.status = EventStatus.PUBLISHED;
        past.featured = false;
        past.creator = user;
        entityManager.persist(past);
        persistPublishedEvent("Future", user);
        entityManager.flush();

        List<EventDTO> result = featuredService.getFeatured(6);

        assertTrue(result.stream().noneMatch(e -> e.id().equals(past.id)));
        assertEquals(1, result.size());
    }

    // --- testOnlyPublishedEventsInFeatured ---

    @Test
    @TestTransaction
    void testOnlyPublishedEventsReturnedInFeatured() {
        User user = persistUser("auth0|pubonly", "pubonly@test.com");
        Event draft = new Event();
        draft.title = "Draft";
        draft.location = "Uni Mail";
        draft.startDate = LocalDateTime.now().plusDays(1);
        draft.endDate = LocalDateTime.now().plusDays(2);
        draft.status = EventStatus.DRAFT;
        draft.featured = false;
        draft.creator = user;
        entityManager.persist(draft);
        persistPublishedEvent("Published", user);
        entityManager.flush();

        List<EventDTO> result = featuredService.getFeatured(6);

        assertEquals(1, result.size());
        assertEquals("Published", result.get(0).title());
    }

    // --- testFeatureIdempotent ---

    @Test
    @TestTransaction
    void testFeatureIdempotentDoesNotResetFeaturedAt() {
        User user = persistUser("auth0|idem", "idem@test.com");
        Event event = persistPublishedEvent("Idempotent", user);
        LocalDateTime originalFeaturedAt = LocalDateTime.now().minusDays(1).truncatedTo(java.time.temporal.ChronoUnit.MICROS);
        event.featured = true;
        event.featuredAt = originalFeaturedAt;
        entityManager.flush();

        // Calling feature() again on an already-featured event must not change featuredAt
        featuredService.feature(event.id);
        entityManager.flush();
        entityManager.refresh(event);

        assertTrue(event.featured);
        assertEquals(originalFeaturedAt, event.featuredAt);
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

    private Event persistPublishedEvent(String title, User creator) {
        Event event = new Event();
        event.title = title;
        event.location = "Uni Mail";
        event.startDate = LocalDateTime.now().plusDays(1);
        event.endDate = LocalDateTime.now().plusDays(2);
        event.category = EventCategory.ACADEMIC;
        event.status = EventStatus.PUBLISHED;
        event.featured = false;
        event.creator = creator;
        entityManager.persist(event);
        entityManager.flush();
        return event;
    }

    private Event persistFeaturedEvent(String title, User creator, LocalDateTime featuredAt) {
        Event event = persistPublishedEvent(title, creator);
        event.featured = true;
        event.featuredAt = featuredAt;
        entityManager.flush();
        return event;
    }

    private void persistAttendance(Long eventId, UUID userId, AttendanceStatus status) {
        Attendance a = new Attendance();
        a.eventId = eventId;
        a.userId = userId;
        a.status = status;
        entityManager.persist(a);
    }

    private void persistFavorite(Long eventId, UUID userId) {
        Favorite f = new Favorite();
        f.eventId = eventId;
        f.userId = userId;
        entityManager.persist(f);
    }
}
