package ch.unige.events.service;

import ch.unige.events.dto.event.EventDTO;
import ch.unige.events.dto.event.ShareResponse;
import ch.unige.events.dto.favorite.FavoriteDTO;
import ch.unige.events.entity.Attendance;
import ch.unige.events.entity.AttendanceStatus;
import ch.unige.events.entity.Event;
import ch.unige.events.entity.EventStatus;
import ch.unige.events.entity.Favorite;
import ch.unige.events.entity.User;
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
@TestProfile(SharedServiceCoverageProfile.class)
class FavoriteServiceCoverageTest {

    @Inject
    FavoriteService favoriteService;

    @Inject
    ShareService shareService;

    @Inject
    EntityManager entityManager;

    // =========================================================
    // FavoriteService — addFavorite
    // =========================================================

    @Test
    @TestTransaction
    void addFavorite_newFavorite_persistsSuccessfully() {
        User user = persistUser("auth0|add1", "add1@example.com");
        Event event = persistEvent("Event A", user);

        favoriteService.addFavorite("auth0|add1", event.id);

        entityManager.flush();
        boolean exists = Favorite.findByUserAndEvent(user.id, event.id).isPresent();
        assertTrue(exists);
    }

    @Test
    @TestTransaction
    void addFavorite_alreadyExists_isIdempotent() {
        User user = persistUser("auth0|add2", "add2@example.com");
        Event event = persistEvent("Event B", user);
        persistFavorite(user.id, event.id);

        // Ne lève pas d'exception, ne crée pas de doublon
        assertDoesNotThrow(() -> favoriteService.addFavorite("auth0|add2", event.id));
    }

    @Test
    @TestTransaction
    void addFavorite_unknownEvent_throwsNotFound() {
        persistUser("auth0|add3", "add3@example.com");

        assertThrows(NotFoundException.class,
                () -> favoriteService.addFavorite("auth0|add3", 999999L));
    }

    @Test
    @TestTransaction
    void addFavorite_unknownUser_throwsNotFound() {
        User user = persistUser("auth0|owner", "owner@example.com");
        Event event = persistEvent("Event C", user);

        assertThrows(NotFoundException.class,
                () -> favoriteService.addFavorite("auth0|nobody", event.id));
    }

    // =========================================================
    // FavoriteService — removeFavorite
    // =========================================================

    @Test
    @TestTransaction
    void removeFavorite_existingFavorite_deletesIt() {
        User user = persistUser("auth0|rem1", "rem1@example.com");
        Event event = persistEvent("Event D", user);
        persistFavorite(user.id, event.id);

        favoriteService.removeFavorite("auth0|rem1", event.id);

        entityManager.flush();
        boolean exists = Favorite.findByUserAndEvent(user.id, event.id).isPresent();
        assertFalse(exists);
    }

    @Test
    @TestTransaction
    void removeFavorite_notFavorited_throwsNotFound() {
        User user = persistUser("auth0|rem2", "rem2@example.com");
        Event event = persistEvent("Event E", user);

        assertThrows(NotFoundException.class,
                () -> favoriteService.removeFavorite("auth0|rem2", event.id));
    }

    @Test
    @TestTransaction
    void removeFavorite_unknownUser_throwsNotFound() {
        assertThrows(NotFoundException.class,
                () -> favoriteService.removeFavorite("auth0|nobody", 1L));
    }

    // =========================================================
    // FavoriteService — getFavorites
    // =========================================================

    @Test
    @TestTransaction
    void getFavorites_withFavorites_returnsEventDTOs() {
        User user = persistUser("auth0|get1", "get1@example.com");
        Event event = persistEvent("My Favorite Event", user);
        persistFavorite(user.id, event.id);

        List<EventDTO> result = favoriteService.getFavorites("auth0|get1", 0, 20);

        assertEquals(1, result.size());
        assertEquals("My Favorite Event", result.get(0).title());
    }

    @Test
    @TestTransaction
    void getFavorites_noFavorites_returnsEmptyList() {
        persistUser("auth0|get2", "get2@example.com");

        List<EventDTO> result = favoriteService.getFavorites("auth0|get2", 0, 20);

        assertTrue(result.isEmpty());
    }

    @Test
    @TestTransaction
    void getFavorites_pagination_respectsPageAndSize() {
        User user = persistUser("auth0|get3", "get3@example.com");
        Event e1 = persistEvent("Event 1", user);
        Event e2 = persistEvent("Event 2", user);
        Event e3 = persistEvent("Event 3", user);
        persistFavorite(user.id, e1.id);
        persistFavorite(user.id, e2.id);
        persistFavorite(user.id, e3.id);

        List<EventDTO> page0 = favoriteService.getFavorites("auth0|get3", 0, 2);
        List<EventDTO> page1 = favoriteService.getFavorites("auth0|get3", 1, 2);

        assertEquals(2, page0.size());
        assertEquals(1, page1.size());
    }

    @Test
    @TestTransaction
    void getFavorites_unknownUser_throwsNotFound() {
        assertThrows(NotFoundException.class,
                () -> favoriteService.getFavorites("auth0|nobody", 0, 20));
    }

    // =========================================================
    // FavoriteService — getFavorites counts
    // =========================================================

    @Test
    @TestTransaction
    void getFavorites_withAttendance_returnsRealAttendingCount() {
        User user = persistUser("auth0|cnt-fav1", "cnt-fav1@example.com");
        Event event = persistEvent("Attended Favorite", user);
        persistFavorite(user.id, event.id);
        persistAttendanceRecord(user.id, event.id, AttendanceStatus.ATTENDING);

        List<EventDTO> result = favoriteService.getFavorites("auth0|cnt-fav1", 0, 20);

        assertEquals(1, result.size());
        assertEquals(1L, result.get(0).attendingCount());
    }

    // =========================================================
    // ShareService — getShareInfo
    // =========================================================

    @Test
    @TestTransaction
    void getShareInfo_firstCall_generatesShortCode() {
        User user = persistUser("auth0|sh1", "sh1@example.com");
        Event event = persistEvent("Share Me", user);

        ShareResponse response = shareService.getShareInfo(event.id);

        assertNotNull(response.shortCode());
        assertFalse(response.shortCode().isBlank());
        assertTrue(response.shareUrl().contains("/events/" + event.id));
    }

    @Test
    @TestTransaction
    void getShareInfo_secondCall_returnsSameShortCode() {
        User user = persistUser("auth0|sh2", "sh2@example.com");
        Event event = persistEvent("Share Me Again", user);

        ShareResponse first = shareService.getShareInfo(event.id);
        ShareResponse second = shareService.getShareInfo(event.id);

        assertEquals(first.shortCode(), second.shortCode());
    }

    @Test
    @TestTransaction
    void getShareInfo_unknownEvent_throwsNotFound() {
        assertThrows(NotFoundException.class,
                () -> shareService.getShareInfo(999999L));
    }

    // =========================================================
    // ShareService — resolveByShortCode (couvre Event.findByShareCode)
    // =========================================================

    @Test
    @TestTransaction
    void resolveByShortCode_existingCode_returnsEvent() {
        User user = persistUser("auth0|sc1", "sc1@example.com");
        Event event = persistEvent("Linkable Event", user);
        // Génère le shareCode via getShareInfo
        ShareResponse info = shareService.getShareInfo(event.id);

        Event resolved = shareService.resolveByShortCode(info.shortCode());

        assertEquals(event.id, resolved.id);
    }

    @Test
    @TestTransaction
    void resolveByShortCode_unknownCode_throwsNotFound() {
        assertThrows(NotFoundException.class,
                () -> shareService.resolveByShortCode("unknown0"));
    }

    // =========================================================
    // FavoriteDTO.from — couverture du factory method
    // =========================================================

    @Test
    void favoriteDTO_from_mapsAllFields() {
        Favorite favorite = new Favorite();
        favorite.id = 1L;
        favorite.userId = UUID.randomUUID();
        favorite.eventId = 42L;
        favorite.createdAt = LocalDateTime.now();

        FavoriteDTO dto = FavoriteDTO.from(favorite);

        assertEquals(1L, dto.id());
        assertEquals(favorite.userId, dto.userId());
        assertEquals(42L, dto.eventId());
        assertEquals(favorite.createdAt, dto.createdAt());
    }

    // =========================================================
    // Helpers
    // =========================================================

    private User persistUser(String auth0Id, String email) {
        return ServiceCoverageTestHelper.persistUser(entityManager, auth0Id, email);
    }

    private Event persistEvent(String title, User creator) {
        return ServiceCoverageTestHelper.persistEvent(entityManager, title, creator, EventStatus.PUBLISHED, null);
    }

    private Favorite persistFavorite(UUID userId, Long eventId) {
        Favorite favorite = new Favorite();
        favorite.userId = userId;
        favorite.eventId = eventId;
        entityManager.persist(favorite);
        entityManager.flush();
        return favorite;
    }

    private Attendance persistAttendanceRecord(UUID userId, Long eventId, AttendanceStatus status) {
        return ServiceCoverageTestHelper.persistAttendance(entityManager, userId, eventId, status);
    }
}
