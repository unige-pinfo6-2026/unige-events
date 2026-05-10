package ch.unige.events.event.favorite.service;

import ch.unige.events.event.entity.Event;
import ch.unige.events.event.favorite.dto.EventDTO;
import ch.unige.events.event.favorite.entity.Favorite;
import ch.unige.events.event.test.JwtTestContext;
import ch.unige.events.event.test.JwtTestHelper;
import ch.unige.events.shared.client.EngagementServiceClient;
import ch.unige.events.shared.domain.dto.AttendanceSummary;
import ch.unige.events.shared.domain.enums.EventCategory;
import ch.unige.events.shared.domain.enums.EventStatus;

import io.quarkus.test.InjectMock;
import io.quarkus.test.TestTransaction;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.ws.rs.NotFoundException;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;

@QuarkusTest
@TestSecurity(user = "auth0|fav")
class FavoriteServiceTest {

    @Inject FavoriteService service;
    @Inject EntityManager em;

    @InjectMock @RestClient EngagementServiceClient engagementClient;

    private final UUID userId = UUID.randomUUID();

    @BeforeEach
    void setup() {
        JwtTestContext.set(JwtTestHelper.jwtFor(userId));
        lenient().when(engagementClient.getAttendanceSummariesBulk(any())).thenReturn(Map.of());
    }

    @AfterEach
    void clear() {
        JwtTestContext.clear();
    }

    private Event create(EventStatus status) {
        Event e = new Event();
        e.title = "T";
        e.description = "d";
        e.location = "l";
        e.startDate = LocalDateTime.now().plusDays(1);
        e.endDate = e.startDate.plusHours(2);
        e.category = EventCategory.ACADEMIC;
        e.creatorId = UUID.randomUUID();
        e.status = status;
        e.persist();
        return e;
    }

    @Test
    @TestTransaction
    void addFavorite_createsRow() {
        Event e = create(EventStatus.PUBLISHED);
        em.flush();
        service.addFavorite("auth0|x", e.id);
        em.flush();
        assertTrue(Favorite.findByUserAndEvent(userId, e.id).isPresent());
    }

    @Test
    @TestTransaction
    void addFavorite_idempotent_noDuplicates() {
        Event e = create(EventStatus.PUBLISHED);
        em.flush();
        service.addFavorite("auth0|x", e.id);
        service.addFavorite("auth0|x", e.id);
        em.flush();
        long count = Favorite.count("userId = ?1 and eventId = ?2", userId, e.id);
        assertEquals(1L, count);
    }

    @Test
    @TestTransaction
    void addFavorite_unknownEvent_throws404() {
        assertThrows(NotFoundException.class,
                () -> service.addFavorite("auth0|x", 99999L));
    }

    @Test
    @TestTransaction
    void addFavorite_anonymousCaller_throws404() {
        Event e = create(EventStatus.PUBLISHED);
        em.flush();
        JwtTestContext.clear();
        assertThrows(NotFoundException.class,
                () -> service.addFavorite("auth0|x", e.id));
    }

    @Test
    @TestTransaction
    void removeFavorite_existing_deletes() {
        Event e = create(EventStatus.PUBLISHED);
        em.flush();
        service.addFavorite("auth0|x", e.id);
        em.flush();
        service.removeFavorite("auth0|x", e.id);
        em.flush();
        assertFalse(Favorite.findByUserAndEvent(userId, e.id).isPresent());
    }

    @Test
    @TestTransaction
    void removeFavorite_unknown_throws404() {
        Event e = create(EventStatus.PUBLISHED);
        em.flush();
        assertThrows(NotFoundException.class,
                () -> service.removeFavorite("auth0|x", e.id));
    }

    @Test
    @TestTransaction
    void getFavorites_returnsEvents() {
        Event e = create(EventStatus.PUBLISHED);
        em.flush();
        service.addFavorite("auth0|x", e.id);
        em.flush();

        List<EventDTO> list = service.getFavorites("auth0|x", 0, 20);
        assertEquals(1, list.size());
    }

    @Test
    @TestTransaction
    void getFavorites_emptyForNewUser() {
        assertTrue(service.getFavorites("auth0|x", 0, 20).isEmpty());
    }

    @Test
    @TestTransaction
    void getFavorites_anonymous_throws404() {
        JwtTestContext.clear();
        assertThrows(NotFoundException.class,
                () -> service.getFavorites("auth0|x", 0, 20));
    }
}
