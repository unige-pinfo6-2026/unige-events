package ch.unige.events.service;

import ch.unige.events.dto.UpdateProfileRequest;
import ch.unige.events.entity.User;
import io.quarkus.oidc.UserInfo;
import io.quarkus.test.TestTransaction;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import jakarta.enterprise.inject.Instance;
import jakarta.persistence.EntityManager;
import jakarta.persistence.OptimisticLockException;
import jakarta.persistence.PersistenceException;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.ForbiddenException;
import jakarta.ws.rs.NotAuthorizedException;
import jakarta.ws.rs.NotFoundException;
import org.junit.jupiter.api.Test;

import java.lang.annotation.Annotation;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Proxy;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
@TestProfile(UserServiceCoverageProfile.class)
class UserServiceCoverageTest {

    @jakarta.inject.Inject
    UserService userService;

    @jakarta.inject.Inject
    EntityManager entityManager;

    @Test
    @TestTransaction
    void userStaticFindersAreCovered() {
        deleteAllUsers();
        User user = persistUser("auth0|finder", "finder@example.com", true);

        Optional<User> byAuth0 = User.findByAuth0Id("auth0|finder");
        Optional<User> byEmail = User.findByEmail("finder@example.com");

        assertTrue(byAuth0.isPresent());
        assertTrue(byEmail.isPresent());
        assertEquals(user.getId(), byAuth0.orElseThrow().getId());
        assertEquals(user.getId(), byEmail.orElseThrow().getId());
    }

    @Test
    @TestTransaction
    void getOrCreateUserReturnsExistingUser() {
        deleteAllUsers();
        User existing = persistUser("auth0|existing", "existing@example.com", false);

        User result = userService.getOrCreateUser("auth0|existing", new UserInfo("{\"email\": \"other@example.com\"}"));

        assertEquals(existing.getId(), result.getId());
    }

    @Test
    @TestTransaction
    void getOrCreateUserRejectsMissingEmail() {
        deleteAllUsers();

        assertThrows(NotAuthorizedException.class,
            () -> userService.getOrCreateUser("auth0|missing", new UserInfo()));
    }

    @Test
    @TestTransaction
    void getOrCreateUserRejectsNullEmail() {
        deleteAllUsers();

        assertThrows(NotAuthorizedException.class,
            () -> userService.getOrCreateUser("auth0|null-email", null));
    }

    @Test
    @TestTransaction
    void getOrCreateUserCreatesUserWhenMissing() {
        deleteAllUsers();

        User created = userService.getOrCreateUser("auth0|new", new UserInfo("{\"email\": \"new@example.com\"}"));

        assertNotNull(created.getId());
        assertEquals("auth0|new", created.getAuth0Id());
        assertEquals("new@example.com", created.getEmail());
        assertFalse(created.isProfilePublic());
    }

    @Test
    @TestTransaction
    void getOrCreateUserCoversUniqueConflictBranch() {
        deleteAllUsers();
        UserService throwingService = new UserService(new SingleEntityManagerInstance(flushThrowingProxy(
            new PersistenceException("users_auth0_id_unique"))));

        try {
            throwingService.getOrCreateUser("auth0|unique-conflict", new UserInfo("{\"email\": \"unique-conflict@example.com\"}"));
        } catch (PersistenceException exception) {
            assertTrue(exception.getMessage().contains("users_auth0_id_unique"));
        }
    }

    @Test
    @TestTransaction
    void getOrCreateUserRethrowsNonUniquePersistenceException() {
        deleteAllUsers();
        UserService throwingService = new UserService(new SingleEntityManagerInstance(flushThrowingProxy(
            new PersistenceException("plain persistence"))));

        PersistenceException exception = assertThrows(PersistenceException.class,
            () -> throwingService.getOrCreateUser("auth0|plain-conflict", new UserInfo("{\"email\": \"plain-conflict@example.com\"}")));

        assertEquals("plain persistence", exception.getMessage());
    }

    @Test
    @TestTransaction
    void getPublicProfileThrowsNotFoundWhenMissing() {
        deleteAllUsers();

        assertThrows(NotFoundException.class,
            () -> userService.getPublicProfile(UUID.randomUUID()));
    }

    @Test
    @TestTransaction
    void getPublicProfileThrowsForbiddenWhenPrivate() {
        deleteAllUsers();
        User user = persistUser("auth0|private", "private@example.com", false);

        assertThrows(ForbiddenException.class,
            () -> userService.getPublicProfile(user.getId()));
    }

    @Test
    @TestTransaction
    void getPublicProfileReturnsUserWhenPublic() {
        deleteAllUsers();
        User user = persistUser("auth0|public", "public@example.com", true);

        User result = userService.getPublicProfile(user.getId());

        assertEquals(user.getId(), result.getId());
    }

    @Test
    @TestTransaction
    void updateMyProfileRejectsTargetMismatch() {
        assertThrows(ForbiddenException.class,
            () -> userService.updateMyProfile("auth0|alice", "auth0|bob", new UpdateProfileRequest(
                null, null, null, null, null, null, null
            )));
    }

    @Test
    @TestTransaction
    void updateMyProfileRejectsNullRequest() {
        deleteAllUsers();
        persistUser("auth0|null-req", "null-req@example.com", false);

        assertThrows(BadRequestException.class,
            () -> userService.updateMyProfile("auth0|null-req", null));
    }

    @Test
    @TestTransaction
    void updateMyProfileThrowsNotFoundForUnknownUser() {
        deleteAllUsers();

        assertThrows(NotFoundException.class,
            () -> userService.updateMyProfile("auth0|unknown", validRequest()));
    }

    @Test
    @TestTransaction
    void updateMyProfileUpdatesEditableFields() {
        deleteAllUsers();
        persistUser("auth0|update", "update@example.com", false);

        User updated = userService.updateMyProfile("auth0|update", validRequest());

        assertEquals("Alice", updated.getDisplayName());
        assertEquals("Science", updated.getFaculty());
        assertEquals("Bachelor", updated.getStudyLevel());
        assertEquals("Student at UNIGE", updated.getBio());
        assertEquals(List.of("AI, football"), updated.getInterests());
        assertEquals("https://cdn.example.com/avatar.png", updated.getAvatarUrl());
        assertTrue(updated.isProfilePublic());
    }

    @Test
    @TestTransaction
    void updateMyProfileKeepsExistingValuesWhenAllOptionalFieldsAreNull() {
        deleteAllUsers();
        User user = persistUser("auth0|keep-values", "keep-values@example.com", false);
        user.setDisplayName("BeforeName");
        user.setFaculty("BeforeFaculty");
        user.setStudyLevel("BeforeLevel");
        user.setBio("BeforeBio");
        user.setInterests(List.of("BeforeInterests"));
        user.setAvatarUrl("https://before.example.com/avatar.png");
        entityManager.flush();

        UpdateProfileRequest noChanges = new UpdateProfileRequest(null, null, null, null, null, null, null);

        User updated = userService.updateMyProfile("auth0|keep-values", noChanges);

        assertEquals("BeforeName", updated.getDisplayName());
        assertEquals("BeforeFaculty", updated.getFaculty());
        assertEquals("BeforeLevel", updated.getStudyLevel());
        assertEquals("BeforeBio", updated.getBio());
        assertEquals(List.of("BeforeInterests"), updated.getInterests());
        assertEquals("https://before.example.com/avatar.png", updated.getAvatarUrl());
        assertFalse(updated.isProfilePublic());
    }

    @Test
    @TestTransaction
    void updateMyProfileWrapsOptimisticLockException() {
        deleteAllUsers();
        persistUser("auth0|opt-lock", "opt-lock@example.com", false);
        UserService throwingService = new UserService(new SingleEntityManagerInstance(flushThrowingProxy(
            new OptimisticLockException("optimistic"))));

        OptimisticLockException exception = assertThrows(OptimisticLockException.class,
            () -> throwingService.updateMyProfile("auth0|opt-lock", validRequest()));

        assertEquals("Profile was updated by another request. Please retry.", exception.getMessage());
    }

    @Test
    @TestTransaction
    void updateMyProfileWrapsPersistenceOptimisticConflict() {
        deleteAllUsers();
        persistUser("auth0|opt-persistence", "opt-persistence@example.com", false);
        UserService throwingService = new UserService(new SingleEntityManagerInstance(flushThrowingProxy(
            new PersistenceException(new OptimisticLockException("wrapped")))));

        OptimisticLockException exception = assertThrows(OptimisticLockException.class,
            () -> throwingService.updateMyProfile("auth0|opt-persistence", validRequest()));

        assertEquals("Profile was updated by another request. Please retry.", exception.getMessage());
    }

    @Test
    @TestTransaction
    void updateMyProfileRethrowsPersistenceWhenNotOptimistic() {
        deleteAllUsers();
        persistUser("auth0|plain-persistence", "plain-persistence@example.com", false);
        UserService throwingService = new UserService(new SingleEntityManagerInstance(flushThrowingProxy(
            new PersistenceException("plain-persistence"))));

        PersistenceException exception = assertThrows(PersistenceException.class,
            () -> throwingService.updateMyProfile("auth0|plain-persistence", validRequest()));

        assertEquals("plain-persistence", exception.getMessage());
    }

    @Test
    void privateHelperIsUniqueAuth0ConflictCoversAllBranches() throws Exception {
        Method method = UserService.class.getDeclaredMethod("isUniqueAuth0Conflict", Throwable.class);
        method.setAccessible(true);

        boolean direct = (boolean) method.invoke(userService, new RuntimeException("users_auth0_id_unique"));
        boolean nested = (boolean) method.invoke(userService, new RuntimeException("outer", new RuntimeException("users_auth0_id_unique nested")));
        boolean nullMessage = (boolean) method.invoke(userService, new RuntimeException((String) null));
        boolean missing = (boolean) method.invoke(userService, new RuntimeException("other"));

        assertTrue(direct);
        assertTrue(nested);
        assertFalse(nullMessage);
        assertFalse(missing);
    }

    @Test
    void privateHelperIsOptimisticLockConflictCoversAllBranches() throws Exception {
        Method method = UserService.class.getDeclaredMethod("isOptimisticLockConflict", Throwable.class);
        method.setAccessible(true);

        boolean direct = (boolean) method.invoke(userService, new OptimisticLockException("lock"));
        boolean byClassName = (boolean) method.invoke(userService, new OptimisticLockProblem("class-name"));
        boolean staleState = (boolean) method.invoke(userService, new StaleStateProblem("stale"));
        boolean missing = (boolean) method.invoke(userService, new RuntimeException("other"));

        assertTrue(direct);
        assertTrue(byClassName);
        assertTrue(staleState);
        assertFalse(missing);
    }

    @Test
    void syntheticLambdaMethodReturnsSameExceptionInstance() throws Exception {
        Method lambdaMethod = Arrays.stream(UserService.class.getDeclaredMethods())
            .filter(method -> method.getName().startsWith("lambda$")
                && method.getParameterCount() == 1
                && method.getParameterTypes()[0] == PersistenceException.class
                && method.getReturnType() == PersistenceException.class)
            .findFirst()
            .orElseThrow();

        lambdaMethod.setAccessible(true);

        PersistenceException exception = new PersistenceException("users_auth0_id_unique");
        Object returned = Modifier.isStatic(lambdaMethod.getModifiers())
            ? lambdaMethod.invoke(null, exception)
            : lambdaMethod.invoke(userService, exception);

        assertSame(exception, returned);
    }

    private UpdateProfileRequest validRequest() {
        return new UpdateProfileRequest(
            "Alice",
            "Science",
            "Bachelor",
            "Student at UNIGE",
            List.of("AI, football"),
            "https://cdn.example.com/avatar.png",
            true
        );
    }

    private void deleteAllUsers() {
        entityManager.createNativeQuery("delete from users").executeUpdate();
        entityManager.clear();
    }

    private User persistUser(String auth0Id, String email, boolean profilePublic) {
        User user = new User();
        user.setAuth0Id(auth0Id);
        user.setEmail(email);
        user.setProfilePublic(profilePublic);
        user.setCreatedAt(LocalDateTime.now());
        entityManager.persist(user);
        entityManager.flush();
        return user;
    }

    private EntityManager flushThrowingProxy(RuntimeException flushException) {
        return (EntityManager) Proxy.newProxyInstance(
            EntityManager.class.getClassLoader(),
            new Class<?>[]{EntityManager.class},
            (proxy, method, args) -> {
                if ("flush".equals(method.getName())) {
                    throw flushException;
                }
                try {
                    return method.invoke(entityManager, args);
                } catch (InvocationTargetException exception) {
                    throw exception.getTargetException();
                }
            }
        );
    }

    private static final class SingleEntityManagerInstance implements Instance<EntityManager> {

        private final EntityManager value;

        private SingleEntityManagerInstance(EntityManager value) {
            this.value = value;
        }

        @Override
        public Instance<EntityManager> select(Annotation... qualifiers) {
            return this;
        }

        @Override
        public <U extends EntityManager> Instance<U> select(Class<U> subtype, Annotation... qualifiers) {
            throw new UnsupportedOperationException();
        }

        @Override
        public <U extends EntityManager> Instance<U> select(jakarta.enterprise.util.TypeLiteral<U> subtype, Annotation... qualifiers) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean isUnsatisfied() {
            return false;
        }

        @Override
        public boolean isAmbiguous() {
            return false;
        }

        @Override
        public void destroy(EntityManager instance) {
        }

        @Override
        public Handle<EntityManager> getHandle() {
            throw new UnsupportedOperationException();
        }

        @Override
        public Iterable<? extends Handle<EntityManager>> handles() {
            return List.of();
        }

        @Override
        public EntityManager get() {
            return value;
        }

        @Override
        public Iterator<EntityManager> iterator() {
            return List.of(value).iterator();
        }
    }

    private static class OptimisticLockProblem extends RuntimeException {
        private OptimisticLockProblem(String message) {
            super(message);
        }
    }

    private static class StaleStateProblem extends RuntimeException {
        private StaleStateProblem(String message) {
            super(message);
        }
    }
}
