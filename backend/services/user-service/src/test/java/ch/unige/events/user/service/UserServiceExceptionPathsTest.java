package ch.unige.events.user.service;

import ch.unige.events.shared.storage.FileStorageService;
import ch.unige.events.user.dto.UpdateProfileRequest;
import ch.unige.events.user.entity.User;
import ch.unige.events.user.test.JwtTestHelper;

import io.quarkus.panache.mock.PanacheMock;
import io.quarkus.test.TestTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.enterprise.inject.Instance;
import jakarta.persistence.EntityManager;
import jakarta.persistence.OptimisticLockException;
import jakarta.persistence.PersistenceException;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.WebApplicationException;
import org.eclipse.microprofile.jwt.JsonWebToken;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Coverage suite for the JPA-failure branches of {@link UserService} — the
 * {@code catch} blocks that translate (or rethrow) a flush-time
 * {@link PersistenceException} / {@link OptimisticLockException}, plus the
 * private exception-classification helpers they drive ({@code
 * isUniqueAuth0Conflict}, {@code isUniqueUsernameConflict}, {@code
 * isOptimisticLockConflict}, {@code containsMessage}).
 *
 * <p>These paths are not reachable through the real DevServices datasource:
 * the unique-conflict branches require a race the pre-checks otherwise win,
 * and the optimistic-lock branch needs a deterministic version clash. So the
 * service is wired by hand with a mocked {@code Instance<EntityManager>}
 * whose {@code flush()} throws on demand, and the Panache statics are stubbed
 * via {@link PanacheMock}. Annotated {@code @QuarkusTest} so the executed
 * service bytecode is captured by quarkus-jacoco (plain unit tests are not).
 */
@QuarkusTest
class UserServiceExceptionPathsTest {

    private UserService service;
    private EntityManager em;

    /** A non-jakarta exception whose class name carries the OptimisticLock marker. */
    static class OptimisticLockMarkerException extends PersistenceException {
        OptimisticLockMarkerException(String m) { super(m); }
    }

    /**
     * A non-jakarta exception whose class name carries the {@code StaleState}
     * marker (and NOT {@code OptimisticLock}) — drives the
     * {@code className.contains("StaleState")} half of
     * {@code isOptimisticLockConflict}.
     */
    static class StaleStateMarkerException extends PersistenceException {
        StaleStateMarkerException(String m) { super(m); }
    }

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        service = new UserService();
        service.fileStorageService = mock(FileStorageService.class);
        Instance<EntityManager> emInstance = mock(Instance.class);
        em = mock(EntityManager.class);
        when(emInstance.get()).thenReturn(em);
        service.entityManager = emInstance;
    }

    private static UpdateProfileRequest profileReq() {
        return new UpdateProfileRequest("New Name", null, null, null, null, null, null, null, null);
    }

    private static JsonWebToken jwt(String auth0Id) {
        return JwtTestHelper.jwtFor(auth0Id,
                Map.of("email", auth0Id + "@example.com", "name", "Some User"));
    }

    // ── getOrCreateUser — PersistenceException catch ───────────────────────

    @Test
    @TestTransaction
    void getOrCreateUser_uniqueAuth0Race_refetchesExistingRow() {
        PanacheMock.mock(User.class);
        User existing = new User();
        existing.id = UUID.randomUUID();
        existing.auth0Id = "auth0|race";
        // First lookup (line 45) misses → we create; the post-flush refetch
        // (line 81) wins the race and returns the row the concurrent tx inserted.
        when(User.findByAuth0Id("auth0|race"))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(existing));
        when(User.findByUsername(anyString())).thenReturn(Optional.empty());
        doThrow(new PersistenceException(
                "duplicate key value violates unique constraint \"users_auth0_id_unique\""))
                .when(em).flush();

        User result = service.getOrCreateUser("auth0|race", jwt("auth0|race"));
        assertSame(existing, result);
    }

    @Test
    @TestTransaction
    void getOrCreateUser_unrelatedPersistenceException_rethrows() {
        PanacheMock.mock(User.class);
        when(User.findByAuth0Id("auth0|pe")).thenReturn(Optional.empty());
        when(User.findByUsername(anyString())).thenReturn(Optional.empty());
        PersistenceException boom = new PersistenceException("connection reset by peer");
        doThrow(boom).when(em).flush();

        PersistenceException thrown = assertThrows(PersistenceException.class,
                () -> service.getOrCreateUser("auth0|pe", jwt("auth0|pe")));
        assertSame(boom, thrown);
    }

    // ── updateMyProfile — OptimisticLock / PersistenceException catches ────

    @Test
    void updateMyProfile_optimisticLockException_throws409() {
        PanacheMock.mock(User.class);
        stagedUser("auth0|ole");
        doThrow(new OptimisticLockException("stale @Version")).when(em).flush();

        WebApplicationException ex = assertThrows(WebApplicationException.class,
                () -> service.updateMyProfile("auth0|ole", profileReq()));
        assertEquals(409, ex.getResponse().getStatus());
        assertEquals("optimistic_lock_conflict", ex.getMessage());
    }

    @Test
    void updateMyProfile_persistenceExceptionWrappingOptimisticLock_throws409() {
        PanacheMock.mock(User.class);
        stagedUser("auth0|wrapole");
        // PersistenceException whose cause is an OptimisticLockException →
        // isOptimisticLockConflict walks the cause chain (instanceof branch).
        doThrow(new PersistenceException("flush failed",
                new OptimisticLockException("row version mismatch")))
                .when(em).flush();

        WebApplicationException ex = assertThrows(WebApplicationException.class,
                () -> service.updateMyProfile("auth0|wrapole", profileReq()));
        assertEquals(409, ex.getResponse().getStatus());
    }

    @Test
    void updateMyProfile_persistenceExceptionWithOptimisticLockClassName_throws409() {
        PanacheMock.mock(User.class);
        stagedUser("auth0|nameole");
        // Not instanceof jakarta OptimisticLockException, but the class name
        // carries the "OptimisticLock" marker → className branch.
        doThrow(new OptimisticLockMarkerException("vendor-specific lock failure"))
                .when(em).flush();

        WebApplicationException ex = assertThrows(WebApplicationException.class,
                () -> service.updateMyProfile("auth0|nameole", profileReq()));
        assertEquals(409, ex.getResponse().getStatus());
    }

    @Test
    void updateMyProfile_unrelatedPersistenceException_rethrows() {
        PanacheMock.mock(User.class);
        stagedUser("auth0|peplain");
        PersistenceException boom = new PersistenceException("check constraint violated");
        doThrow(boom).when(em).flush();

        PersistenceException thrown = assertThrows(PersistenceException.class,
                () -> service.updateMyProfile("auth0|peplain", profileReq()));
        assertSame(boom, thrown);
    }

    @Test
    void updateMyProfile_persistenceExceptionWithStaleStateClassName_throws409() {
        PanacheMock.mock(User.class);
        stagedUser("auth0|stalestate");
        // Not instanceof jakarta OptimisticLockException, and the class name
        // carries the "StaleState" marker (not "OptimisticLock") →
        // className.contains("StaleState") branch of isOptimisticLockConflict.
        doThrow(new StaleStateMarkerException("row was updated or deleted by another transaction"))
                .when(em).flush();

        WebApplicationException ex = assertThrows(WebApplicationException.class,
                () -> service.updateMyProfile("auth0|stalestate", profileReq()));
        assertEquals(409, ex.getResponse().getStatus());
        assertEquals("optimistic_lock_conflict", ex.getMessage());
    }

    // ── updateUsername — null guard + PersistenceException catch ───────────

    @Test
    void updateUsername_null_throws400_usernameInvalid() {
        WebApplicationException ex = assertThrows(WebApplicationException.class,
                () -> service.updateUsername("auth0|whatever", null));
        assertEquals(400, ex.getResponse().getStatus());
        assertEquals("username_invalid", ex.getMessage());
    }

    @Test
    void updateUsername_uniqueUsernameRace_throws409_usernameTaken() {
        PanacheMock.mock(User.class);
        User u = new User();
        u.id = UUID.randomUUID();
        u.auth0Id = "auth0|un";
        u.username = "old.handle";
        when(User.findByAuth0Id("auth0|un")).thenReturn(Optional.of(u));
        when(User.findByUsername("new.handle")).thenReturn(Optional.empty()); // pre-check passes
        doThrow(new PersistenceException(
                "duplicate key value violates unique constraint \"uq_users_username\""))
                .when(em).flush();

        WebApplicationException ex = assertThrows(WebApplicationException.class,
                () -> service.updateUsername("auth0|un", "new.handle"));
        assertEquals(409, ex.getResponse().getStatus());
        assertEquals("username_taken", ex.getMessage());
    }

    @Test
    void updateUsername_unrelatedPersistenceException_rethrows() {
        PanacheMock.mock(User.class);
        User u = new User();
        u.id = UUID.randomUUID();
        u.auth0Id = "auth0|un2";
        u.username = "old.handle2";
        when(User.findByAuth0Id("auth0|un2")).thenReturn(Optional.of(u));
        when(User.findByUsername("new.handle2")).thenReturn(Optional.empty());
        PersistenceException boom = new PersistenceException("deadlock detected");
        doThrow(boom).when(em).flush();

        PersistenceException thrown = assertThrows(PersistenceException.class,
                () -> service.updateUsername("auth0|un2", "new.handle2"));
        assertSame(boom, thrown);
    }

    @Test
    void updateUsername_nullMessageCausePersistenceException_rethrows() {
        // isUniqueUsernameConflict drives containsMessage, which walks the
        // cause chain comparing each getMessage() to the marker. The cause
        // here has a null message, exercising the `message == null` half of
        // containsMessage; no link contains "uq_users_username" so the helper
        // returns false and the exception is rethrown (not a 409).
        PanacheMock.mock(User.class);
        User u = new User();
        u.id = UUID.randomUUID();
        u.auth0Id = "auth0|un3";
        u.username = "old.handle3";
        when(User.findByAuth0Id("auth0|un3")).thenReturn(Optional.of(u));
        when(User.findByUsername("new.handle3")).thenReturn(Optional.empty());
        PersistenceException boom = new PersistenceException("wrapper", new RuntimeException((String) null));
        doThrow(boom).when(em).flush();

        PersistenceException thrown = assertThrows(PersistenceException.class,
                () -> service.updateUsername("auth0|un3", "new.handle3"));
        assertSame(boom, thrown);
    }

    // ── getByUsername — null guard ─────────────────────────────────────────

    @Test
    void getByUsername_null_throwsNotFound() {
        assertThrows(NotFoundException.class,
                () -> service.getByUsername(null, "auth0|caller", false));
    }

    /** Stage a managed-looking user for updateMyProfile (findByAuth0Id hit). */
    private static User stagedUser(String auth0Id) {
        User u = new User();
        u.id = UUID.randomUUID();
        u.auth0Id = auth0Id;
        when(User.findByAuth0Id(auth0Id)).thenReturn(Optional.of(u));
        return u;
    }
}
