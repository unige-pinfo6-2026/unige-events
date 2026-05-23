package ch.unige.events.event.favorite.service;

import ch.unige.events.event.entity.Event;
import ch.unige.events.event.favorite.entity.Favorite;
import ch.unige.events.shared.domain.projections.CallerIdentity;

import io.quarkus.panache.mock.PanacheMock;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Coverage suite for the flush-failure branch of {@link FavoriteService#addFavorite}
 * — the {@code catch (PersistenceException)} that rethrows when the failure is
 * <em>not</em> the {@code uq_favorite_user_event} double-tap conflict.
 *
 * <p>This rethrow is unreachable through the real DevServices datasource: the
 * pre-check wins the common idempotent path, and the only flush failure the DB
 * produces in the concurrent race is the named unique violation (absorbed as a
 * noop). So the service is wired by hand with a mocked {@link EntityManager}
 * whose {@code flush()} throws a non-conflict {@link PersistenceException}, and
 * the Panache statics are stubbed via {@link PanacheMock}. Annotated
 * {@code @QuarkusTest} so the executed service bytecode is captured by
 * quarkus-jacoco (plain unit tests are not instrumented).
 */
@QuarkusTest
class FavoriteServiceExceptionPathsTest {

    private FavoriteService service;
    private EntityManager em;

    private final UUID userId = UUID.randomUUID();
    private final Long eventId = 4242L;

    @BeforeEach
    void setUp() {
        service = new FavoriteService();
        CallerIdentity callerIdentity = mock(CallerIdentity.class);
        when(callerIdentity.requireUuid()).thenReturn(userId);
        service.callerIdentity = callerIdentity;
        em = mock(EntityManager.class);
        service.entityManager = em;
    }

    @Test
    void addFavorite_unrelatedPersistenceException_rethrows() {
        PanacheMock.mock(Event.class, Favorite.class);
        when(Event.findByIdOptional(eventId)).thenReturn(Optional.of(new Event()));
        // Pre-check misses → the method proceeds to persist + flush.
        when(Favorite.findByUserAndEvent(eq(userId), eq(eventId))).thenReturn(Optional.empty());
        // flush() raises a PersistenceException that is NOT the named
        // uq_favorite_user_event conflict → isUniqueFavoriteConflict==false → rethrow.
        PersistenceException boom = new PersistenceException("deadlock detected");
        doThrow(boom).when(em).flush();

        PersistenceException thrown = assertThrows(PersistenceException.class,
                () -> service.addFavorite("auth0|x", eventId));
        assertSame(boom, thrown);
    }

    @Test
    void addFavorite_uniqueFavoriteConflict_swallowedAsIdempotentNoop() {
        PanacheMock.mock(Event.class, Favorite.class);
        when(Event.findByIdOptional(eventId)).thenReturn(Optional.of(new Event()));
        when(Favorite.findByUserAndEvent(eq(userId), eq(eventId))).thenReturn(Optional.empty());
        // flush() raises the named unique-violation conflict → swallowed as a
        // benign double-tap noop (Log.debugf), no exception propagated.
        var hibCce = new org.hibernate.exception.ConstraintViolationException(
                "duplicate", new java.sql.SQLException("dup", "23505"), "uq_favorite_user_event");
        doThrow(new PersistenceException("flush failed", hibCce)).when(em).flush();

        // Must NOT throw — the loser INSERT is an idempotent noop.
        service.addFavorite("auth0|x", eventId);
    }

    @Test
    void addFavorite_alreadyFavorited_shortCircuitsBeforeFlush() {
        PanacheMock.mock(Event.class, Favorite.class);
        when(Event.findByIdOptional(eventId)).thenReturn(Optional.of(new Event()));
        // Pre-check hit → return early, flush() never reached.
        when(Favorite.findByUserAndEvent(eq(userId), eq(eventId)))
                .thenReturn(Optional.of(new Favorite()));

        service.addFavorite("auth0|x", eventId);
        org.mockito.Mockito.verify(em, org.mockito.Mockito.never()).flush();
    }

    @Test
    void addFavorite_unknownEvent_throwsBeforeResolvingUser() {
        PanacheMock.mock(Event.class);
        when(Event.findByIdOptional(any())).thenReturn(Optional.empty());

        assertThrows(jakarta.ws.rs.NotFoundException.class,
                () -> service.addFavorite("auth0|x", eventId));
    }
}
