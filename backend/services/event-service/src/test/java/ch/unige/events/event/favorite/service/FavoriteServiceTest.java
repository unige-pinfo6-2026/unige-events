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

import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;

import java.lang.reflect.Method;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

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
    void getFavorites_nullTagsEvent_yieldsEmptyTagList() {
        // favorite/dto/EventDTO L110 tags==null arm — Event.tags defaults to an
        // empty ArrayList, so it must be explicitly nulled. Driven through the
        // @QuarkusTest service so jacoco counts the executed DTO bytecode.
        Event e = create(EventStatus.PUBLISHED);
        e.tags = null;
        em.flush();
        service.addFavorite("auth0|x", e.id);
        em.flush();

        List<EventDTO> list = service.getFavorites("auth0|x", 0, 20);
        assertEquals(1, list.size());
        assertEquals(List.of(), list.get(0).tags());
    }

    @Test
    @TestTransaction
    void getFavorites_summariesNullClient_safe() {
        // P2: the bulk-summary client may return null (its @Fallback
        // default). getFavorites must coalesce to an empty map and enrich
        // with zeroed counts rather than NPE.
        Event e = create(EventStatus.PUBLISHED);
        em.flush();
        service.addFavorite("auth0|x", e.id);
        em.flush();
        when(engagementClient.getAttendanceSummariesBulk(any())).thenReturn(null);

        List<EventDTO> list = service.getFavorites("auth0|x", 0, 20);
        assertEquals(1, list.size());
    }

    @Test
    @TestTransaction
    void getFavorites_anonymous_throws404() {
        JwtTestContext.clear();
        assertThrows(NotFoundException.class,
                () -> service.getFavorites("auth0|x", 0, 20));
    }

    // -----------------------------------------------------------
    // sentinel — pin the bulk-fetch path that replaces the
    // per-favorite findByIdOptional. Counts JDBC
    // prepared statements via Hibernate Statistics: with N=5
    // favorites the listing must issue O(1) queries (Favorite list
    // + Event bulk load), not 1 + N. Threshold is set generously
    // (≤ 4) so the assertion stays robust against incidental
    // metadata queries while still catching a regression to
    // 1 + N = 6 SELECTs.
    // -----------------------------------------------------------

    @Test
    @TestTransaction
    @SuppressWarnings("java:S100")
    void getFavorites_doesNotIssueNPlusOneQueries() {
        int n = 5;
        List<Long> eventIds = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            Event e = create(EventStatus.PUBLISHED);
            eventIds.add(e.id);
        }
        for (Long id : eventIds) {
            Favorite f = new Favorite();
            f.userId = userId;
            f.eventId = id;
            f.persist();
        }
        em.flush();
        em.clear();

        SessionFactory sf = em.getEntityManagerFactory().unwrap(SessionFactory.class);
        Statistics stats = sf.getStatistics();
        boolean wasEnabled = stats.isStatisticsEnabled();
        stats.setStatisticsEnabled(true);
        try {
            stats.clear();

            List<EventDTO> result = service.getFavorites("auth0|x", 0, 20);

            assertEquals(n, result.size());
            long prepared = stats.getPrepareStatementCount();
            assertTrue(prepared <= 4,
                    "getFavorites must not issue N+1 queries; got " + prepared
                            + " prepared statements for " + n
                            + " favorites (expected ≤ 4: 1 favorite list + 1 event bulk + ≤ 2 overhead)");
        } finally {
            stats.setStatisticsEnabled(wasEnabled);
        }
    }

    // -----------------------------------------------------------
    // BUG-006-bis sentinel
    // Pins the catch (PersistenceException) idempotence branch:
    // when two transactions race past the pre-check and both reach
    // persist+flush, the unique constraint uq_favorite_user_event
    // turns the loser into a noop. Both addFavorite() calls must
    // return without throwing, and the DB ends with exactly one row.
    //
    // The JWT test context is a static volatile shared across threads,
    // so all worker threads observe the same JWT. We activate the CDI
    // request context per worker thread (Arc.container().requestContext())
    // so Instance<JsonWebToken> can resolve.
    // -----------------------------------------------------------

    @Test
    @SuppressWarnings("java:S100")
    void addFavorite_concurrentDoubleTap_isIdempotentNoop() throws Exception {
        Long eventId;
        // Persist the event in its own transaction so both racing threads
        // can see it. Cannot use @TestTransaction here because the two
        // addFavorite() calls must run in their own transactions to
        // race for the unique constraint.
        io.quarkus.narayana.jta.QuarkusTransaction.requiringNew().run(() -> { });
        eventId = io.quarkus.narayana.jta.QuarkusTransaction.requiringNew().call(() -> {
            Event e = create(EventStatus.PUBLISHED);
            em.flush();
            return e.id;
        });

        // The JWT test context is a static — set once before launching threads.
        JwtTestContext.set(JwtTestHelper.jwtFor(userId));

        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            CountDownLatch start = new CountDownLatch(1);
            final Long evId = eventId;
            Runnable callAddFavorite = () -> {
                io.quarkus.arc.ArcContainer arc = io.quarkus.arc.Arc.container();
                io.quarkus.arc.ManagedContext requestContext = arc.requestContext();
                requestContext.activate();
                try {
                    start.await();
                    service.addFavorite("auth0|x", evId);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                } finally {
                    requestContext.terminate();
                }
            };
            CompletableFuture<Void> a = CompletableFuture.runAsync(callAddFavorite, pool);
            CompletableFuture<Void> b = CompletableFuture.runAsync(callAddFavorite, pool);
            start.countDown();
            CompletableFuture.allOf(a, b).get(15, TimeUnit.SECONDS);

            assertEquals(1L, Favorite.count("userId = ?1 AND eventId = ?2", userId, eventId),
                    "Concurrent double-tap must converge to exactly one Favorite row");
        } finally {
            pool.shutdown();
            JwtTestContext.clear();
            io.quarkus.narayana.jta.QuarkusTransaction.requiringNew().run(() -> {
                Favorite.delete("userId = ?1 AND eventId = ?2", userId, eventId);
                Event.deleteById(eventId);
            });
        }
    }

    // -----------------------------------------------------------
    // sentinels — pin the narrowing of the unique-violation catch in
    // addFavorite() to the named constraint
    // uq_favorite_user_event. The previous isUniqueConstraintViolation()
    // matched any ConstraintViolationException, so a future migration
    // adding another UNIQUE on the favorites table (or a stray FK / NOT
    // NULL surfacing as a CCE in a sibling exception chain) would have
    // been silently absorbed by the double-tap catch. Reflection on the
    // private static helper is intentional: the goal is to lock the
    // narrowing predicate, not to retest the public addFavorite path
    // which is already covered by the C2 concurrent sentinel above.
    // -----------------------------------------------------------

    @Test
    void isUniqueFavoriteConflict_namedConstraint_returnsTrue() throws Exception {
        var hibCce = new org.hibernate.exception.ConstraintViolationException(
                "duplicate", new SQLException("dup", "23505"), "uq_favorite_user_event");
        var pe = new jakarta.persistence.PersistenceException("flush failed", hibCce);

        Method method = FavoriteService.class.getDeclaredMethod(
                "isUniqueFavoriteConflict", jakarta.persistence.PersistenceException.class);
        method.setAccessible(true);
        boolean result = (boolean) method.invoke(null, pe);

        assertTrue(result, "uq_favorite_user_event must be matched as the double-tap conflict");
    }

    @Test
    void isUniqueFavoriteConflict_otherConstraintName_returnsFalse_thusReThrown() throws Exception {
        var hibCce = new org.hibernate.exception.ConstraintViolationException(
                "duplicate", new SQLException("dup", "23505"), "uq_some_other_constraint");
        var pe = new jakarta.persistence.PersistenceException("flush failed", hibCce);

        Method method = FavoriteService.class.getDeclaredMethod(
                "isUniqueFavoriteConflict", jakarta.persistence.PersistenceException.class);
        method.setAccessible(true);
        boolean result = (boolean) method.invoke(null, pe);

        assertFalse(result,
                "Other unique constraints must not be absorbed by the favorite double-tap catch");
    }
}
