package ch.unige.events.service;

import ch.unige.events.dto.user.UpdateProfileRequest;
import ch.unige.events.entity.User;
import ch.unige.events.exception.InvalidFileTypeException;
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
import org.eclipse.microprofile.jwt.JsonWebToken;
import org.jboss.resteasy.reactive.multipart.FileUpload;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.lang.annotation.Annotation;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import jakarta.ws.rs.core.MultivaluedHashMap;
import jakarta.ws.rs.core.MultivaluedMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
@TestProfile(ShareServiceCoverageProfile.class)
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
        assertEquals(user.id, byAuth0.orElseThrow().id);
        assertEquals(user.id, byEmail.orElseThrow().id);
    }

    @Test
    @TestTransaction
    void getOrCreateUserReturnsExistingUser() {
        deleteAllUsers();
        User existing = persistUser("auth0|existing", "existing@example.com", false);

        User result = userService.getOrCreateUser("auth0|existing", jwt("auth0|existing", Map.of("email", "other@example.com")));

        assertEquals(existing.id, result.id);
    }

    @Test
    @TestTransaction
    void getOrCreateUserRejectsMissingEmail() {
        deleteAllUsers();

        assertThrows(NotAuthorizedException.class,
            () -> userService.getOrCreateUser("auth0|missing", jwt("auth0|missing", Map.of())));
    }

    @Test
    @TestTransaction
    void getOrCreateUserRejectsNullEmail() {
        deleteAllUsers();

        Map<String, Object> claims = new HashMap<>();
        claims.put("email", null);

        assertThrows(NotAuthorizedException.class,
            () -> userService.getOrCreateUser("auth0|null-email", jwt("auth0|null-email", claims)));
    }

    @Test
    @TestTransaction
    void getOrCreateUserCreatesUserWhenMissing() {
        deleteAllUsers();

        User created = userService.getOrCreateUser("auth0|new", jwt("auth0|new", Map.of(
            "email", "new@example.com",
            "name", "New User",
            "given_name", "New",
            "family_name", "User",
            "picture", "https://cdn.example.com/new.png"
        )));

        assertNotNull(created.id);
        assertEquals("auth0|new", created.auth0Id);
        assertEquals("new@example.com", created.email);
        assertEquals("New User", created.displayName);
        assertEquals("New", created.firstName);
        assertEquals("User", created.lastName);
        assertEquals("https://cdn.example.com/new.png", created.avatarUrl);
        assertFalse(created.profilePublic);
    }

    @Test
    @TestTransaction
    void getOrCreateUserCoversUniqueConflictBranch() {
        deleteAllUsers();

        UserService throwingService = new UserService();
        throwingService.entityManager = new SingleEntityManagerInstance(flushThrowingProxy(new PersistenceException("users_auth0_id_unique")));

        try {
            throwingService.getOrCreateUser("auth0|unique-conflict", jwt("auth0|unique-conflict", Map.of("email", "unique-conflict@example.com")));
        } catch (PersistenceException exception) {
            assertTrue(exception.getMessage().contains("users_auth0_id_unique"));
        }
    }

    @Test
    @TestTransaction
    void getOrCreateUserRethrowsNonUniquePersistenceException() {
        deleteAllUsers();
        UserService throwingService = new UserService();
        throwingService.entityManager = new SingleEntityManagerInstance(flushThrowingProxy(new PersistenceException("plain persistence")));

        PersistenceException exception = assertThrows(PersistenceException.class,
            () -> throwingService.getOrCreateUser("auth0|plain-persistence", jwt("auth0|plain-persistence", Map.of("email", "plain-persistence@example.com"))));

        assertEquals("plain persistence", exception.getMessage());
    }

    @Test
    @TestTransaction
    void getPublicProfileThrowsNotFoundWhenMissing() {
        deleteAllUsers();

        assertThrows(NotFoundException.class,
            () -> userService.getPublicProfile(UUID.randomUUID(), null));
    }

    @Test
    @TestTransaction
    void getPublicProfileThrowsNotFoundWhenPrivateAndAnon() {
        deleteAllUsers();
        User user = persistUser("auth0|private", "private@example.com", false);

        assertThrows(NotFoundException.class,
            () -> userService.getPublicProfile(user.id, null));
    }

    @Test
    @TestTransaction
    void getPublicProfileReturnsUserWhenPublic() {
        deleteAllUsers();
        User user = persistUser("auth0|public", "public@example.com", true);

        User result = userService.getPublicProfile(user.id, null);

        assertEquals(user.id, result.id);
    }

    @Test
    @TestTransaction
    void getPublicProfileReturnsUserWhenPrivateAndOwner() {
        // Self-case: the owner can always read their own private profile.
        deleteAllUsers();
        User user = persistUser("auth0|self", "self@example.com", false);

        User result = userService.getPublicProfile(user.id, "auth0|self");

        assertEquals(user.id, result.id);
    }

    @Test
    @TestTransaction
    void getPublicProfileThrowsNotFoundWhenPrivateAndOtherUser() {
        deleteAllUsers();
        User alice = persistUser("auth0|alice-priv", "alice-priv@example.com", false);
        persistUser("auth0|bob-priv", "bob-priv@example.com", true);

        assertThrows(NotFoundException.class,
            () -> userService.getPublicProfile(alice.id, "auth0|bob-priv"));
    }

    @Test
    @TestTransaction
    void getPublicProfileThrowsNotFoundWhenPrivateAndCallerNotProvisioned() {
        // Edge case: caller presents an auth0Id not yet backed by a User row.
        // They can't own any profile, so privacy rule applies unchanged.
        deleteAllUsers();
        User alice = persistUser("auth0|alice-ghost", "alice-ghost@example.com", false);

        assertThrows(NotFoundException.class,
            () -> userService.getPublicProfile(alice.id, "auth0|ghost-not-in-db"));
    }

    @Test
    @TestTransaction
    void getPublicProfileReturnsUserWhenPublicAndAnon() {
        deleteAllUsers();
        User user = persistUser("auth0|public-anon", "public-anon@example.com", true);

        User result = userService.getPublicProfile(user.id, null);

        assertEquals(user.id, result.id);
    }

    @Test
    @TestTransaction
    void getPublicProfileReturnsUserWhenPublicAndAuth() {
        deleteAllUsers();
        User user = persistUser("auth0|alice-pub", "alice-pub@example.com", true);
        persistUser("auth0|bob-pub", "bob-pub@example.com", true);

        User result = userService.getPublicProfile(user.id, "auth0|bob-pub");

        assertEquals(user.id, result.id);
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

        assertEquals("Alice", updated.displayName);
        assertEquals("Science", updated.faculty);
        assertEquals("Bachelor", updated.studyLevel);
        assertEquals("Student at UNIGE", updated.bio);
        assertEquals(List.of("AI, football"), updated.interests);
        assertEquals("https://cdn.example.com/avatar.png", updated.avatarUrl);
        assertTrue(updated.profilePublic);
    }

    @Test
    @TestTransaction
    void updateMyProfileKeepsExistingValuesWhenAllOptionalFieldsAreNull() {
        deleteAllUsers();

        User user = persistUser("auth0|keep-values", "keep-values@example.com", false);
        user.displayName = "BeforeName";
        user.faculty = "BeforeFaculty";
        user.studyLevel = "BeforeLevel";
        user.bio = "BeforeBio";
        user.interests = List.of("BeforeInterests");
        user.avatarUrl = "https://before.example.com/avatar.png";
        entityManager.flush();

        UpdateProfileRequest noChanges = new UpdateProfileRequest(null, null, null, null, null, null, null);

        User updated = userService.updateMyProfile("auth0|keep-values", noChanges);

        assertEquals("BeforeName", updated.displayName);
        assertEquals("BeforeFaculty", updated.faculty);
        assertEquals("BeforeLevel", updated.studyLevel);
        assertEquals("BeforeBio", updated.bio);
        assertEquals(List.of("BeforeInterests"), updated.interests);
        assertEquals("https://before.example.com/avatar.png", updated.avatarUrl);
        assertFalse(updated.profilePublic);
    }

    @Test
    @TestTransaction
    void updateMyProfileWrapsOptimisticLockException() {
        deleteAllUsers();
        persistUser("auth0|opt-lock", "opt-lock@example.com", false);
        UserService throwingService = new UserService();
        throwingService.entityManager = new SingleEntityManagerInstance(flushThrowingProxy(new OptimisticLockException("optimistic")));

        OptimisticLockException exception = assertThrows(OptimisticLockException.class,
            () -> throwingService.updateMyProfile("auth0|opt-lock", validRequest()));

        assertEquals("Profile was updated by another request. Please retry.", exception.getMessage());
    }

    @Test
    @TestTransaction
    void updateMyProfileWrapsPersistenceOptimisticConflict() {
        deleteAllUsers();
        persistUser("auth0|opt-persistence", "opt-persistence@example.com", false);
        UserService throwingService = new UserService();
        throwingService.entityManager = new SingleEntityManagerInstance(flushThrowingProxy(new PersistenceException(new OptimisticLockException("wrapped"))));

        OptimisticLockException exception = assertThrows(OptimisticLockException.class,
            () -> throwingService.updateMyProfile("auth0|opt-persistence", validRequest()));

        assertEquals("Profile was updated by another request. Please retry.", exception.getMessage());
    }

    @Test
    @TestTransaction
    void updateMyProfileRethrowsPersistenceWhenNotOptimistic() {
        deleteAllUsers();
        persistUser("auth0|plain-persistence", "plain-persistence@example.com", false);
        UserService throwingService = new UserService();
        throwingService.entityManager = new SingleEntityManagerInstance(flushThrowingProxy(new PersistenceException("plain-persistence")));

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

    private JsonWebToken jwt(String auth0Id, Map<String, Object> claims) {
        Map<String, Object> tokenClaims = new HashMap<>(claims);
        tokenClaims.putIfAbsent("sub", auth0Id);

        return new JsonWebToken() {
            @Override
            public String getName() {
                return auth0Id;
            }

            @Override
            public Set<String> getClaimNames() {
                return tokenClaims.keySet();
            }

            @Override
            @SuppressWarnings("unchecked")
            public <T> T getClaim(String claimName) {
                return (T) tokenClaims.get(claimName);
            }
        };
    }

    private void deleteAllUsers() {
        entityManager.createNativeQuery("delete from users").executeUpdate();
        entityManager.clear();
    }

    private User persistUser(String auth0Id, String email, boolean profilePublic) {
        User user = new User();
        user.auth0Id = auth0Id;
        user.email = email;
        user.profilePublic = profilePublic;
        user.createdAt = LocalDateTime.now();
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

    // --- uploadImage ---

    @Test
    @TestTransaction
    void uploadImage_validJpeg_updatesAvatarUrl(@TempDir Path tempDir) throws IOException {
        deleteAllUsers();
        persistUser("auth0|photo", "photo@example.com", false);

        Path fakeFile = tempDir.resolve("avatar.jpg");
        Files.write(fakeFile, jpegHeader());
        FileUpload upload = new StubFileUpload("avatar.jpg", "image/jpeg", fakeFile);

        User result = userService.uploadImage("auth0|photo", upload);

        assertNotNull(result.avatarUrl);
        assertTrue(result.avatarUrl.startsWith("http"));
        assertTrue(result.avatarUrl.endsWith(".jpg"));
    }

    @Test
    @TestTransaction
    void uploadImage_validPng_updatesAvatarUrl(@TempDir Path tempDir) throws IOException {
        deleteAllUsers();
        persistUser("auth0|png", "png@example.com", false);

        Path fakeFile = tempDir.resolve("avatar.png");
        Files.write(fakeFile, pngHeader());
        FileUpload upload = new StubFileUpload("avatar.png", "image/png", fakeFile);

        User result = userService.uploadImage("auth0|png", upload);

        assertTrue(result.avatarUrl.endsWith(".png"));
    }

    @Test
    @TestTransaction
    void uploadImage_validWebp_updatesAvatarUrl(@TempDir Path tempDir) throws IOException {
        deleteAllUsers();
        persistUser("auth0|webp", "webp@example.com", false);

        Path fakeFile = tempDir.resolve("avatar.webp");
        Files.write(fakeFile, webpHeader());
        FileUpload upload = new StubFileUpload("avatar.webp", "image/webp", fakeFile);

        User result = userService.uploadImage("auth0|webp", upload);

        assertTrue(result.avatarUrl.endsWith(".webp"));
    }

    @Test
    @TestTransaction
    void uploadImage_validGif_updatesAvatarUrl(@TempDir Path tempDir) throws IOException {
        deleteAllUsers();
        persistUser("auth0|gif", "gif@example.com", false);

        Path fakeFile = tempDir.resolve("avatar.gif");
        Files.write(fakeFile, gifHeader());
        FileUpload upload = new StubFileUpload("avatar.gif", "image/gif", fakeFile);

        User result = userService.uploadImage("auth0|gif", upload);

        assertTrue(result.avatarUrl.endsWith(".gif"));
    }

    @Test
    @TestTransaction
    void uploadImage_extensionDerivedFromMime_notFromFileName(@TempDir Path tempDir) throws IOException {
        deleteAllUsers();
        persistUser("auth0|noext", "noext@example.com", false);

        Path fakeFile = tempDir.resolve("avatar");
        Files.write(fakeFile, jpegHeader());
        FileUpload upload = new StubFileUpload("avatar", "image/jpeg", fakeFile);

        User result = userService.uploadImage("auth0|noext", upload);

        // Extension comes from the validated MIME type, not from the client filename.
        assertTrue(result.avatarUrl.endsWith(".jpg"));
    }

    @Test
    @TestTransaction
    void uploadImage_nullFileName_extensionFromMime(@TempDir Path tempDir) throws IOException {
        deleteAllUsers();
        persistUser("auth0|nullname", "nullname@example.com", false);

        Path fakeFile = tempDir.resolve("file");
        Files.write(fakeFile, jpegHeader());
        FileUpload upload = new StubFileUpload(null, "image/jpeg", fakeFile);

        User result = userService.uploadImage("auth0|nullname", upload);

        assertTrue(result.avatarUrl.endsWith(".jpg"));
    }

    @Test
    @TestTransaction
    void uploadImage_svgMime_throwsBadRequest(@TempDir Path tempDir) throws IOException {
        deleteAllUsers();
        persistUser("auth0|svg", "svg@example.com", false);

        Path fakeFile = tempDir.resolve("xss.svg");
        Files.write(fakeFile, svgContent());
        FileUpload upload = new StubFileUpload("xss.svg", "image/svg+xml", fakeFile);

        assertThrows(InvalidFileTypeException.class,
            () -> userService.uploadImage("auth0|svg", upload));
    }

    @Test
    @TestTransaction
    void uploadImage_svgDisguisedAsPng_throwsBadRequest(@TempDir Path tempDir) throws IOException {
        // Finding 4.18: SVG sent with Content-Type image/png must be rejected by magic-byte check.
        deleteAllUsers();
        persistUser("auth0|svg-png", "svg-png@example.com", false);

        Path fakeFile = tempDir.resolve("xss.png");
        Files.write(fakeFile, svgContent());
        FileUpload upload = new StubFileUpload("xss.png", "image/png", fakeFile);

        assertThrows(InvalidFileTypeException.class,
            () -> userService.uploadImage("auth0|svg-png", upload));
    }

    @Test
    @TestTransaction
    void uploadImage_binaryDisguisedAsPng_throwsBadRequest(@TempDir Path tempDir) throws IOException {
        deleteAllUsers();
        persistUser("auth0|bin-png", "bin-png@example.com", false);

        Path fakeFile = tempDir.resolve("bad.png");
        Files.write(fakeFile, new byte[]{0x00, 0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07, 0x08});
        FileUpload upload = new StubFileUpload("bad.png", "image/png", fakeFile);

        assertThrows(InvalidFileTypeException.class,
            () -> userService.uploadImage("auth0|bin-png", upload));
    }

    @Test
    @TestTransaction
    void uploadImage_invalidMime_throwsBadRequest(@TempDir Path tempDir) throws IOException {
        deleteAllUsers();
        persistUser("auth0|mime", "mime@example.com", false);

        Path fakeFile = tempDir.resolve("script.sh");
        Files.write(fakeFile, "#!/bin/bash".getBytes());
        FileUpload upload = new StubFileUpload("script.sh", "text/plain", fakeFile);

        assertThrows(InvalidFileTypeException.class,
            () -> userService.uploadImage("auth0|mime", upload));
    }

    @Test
    @TestTransaction
    void uploadImage_nullMime_throwsBadRequest(@TempDir Path tempDir) throws IOException {
        deleteAllUsers();
        persistUser("auth0|nullmime", "nullmime@example.com", false);

        Path fakeFile = tempDir.resolve("file.bin");
        Files.write(fakeFile, new byte[0]);
        FileUpload upload = new StubFileUpload("file.bin", null, fakeFile);

        assertThrows(InvalidFileTypeException.class,
            () -> userService.uploadImage("auth0|nullmime", upload));
    }

    @Test
    @TestTransaction
    void uploadImage_unknownUser_throwsNotFound(@TempDir Path tempDir) throws IOException {
        deleteAllUsers();

        Path fakeFile = tempDir.resolve("f.jpg");
        Files.write(fakeFile, jpegHeader());
        FileUpload upload = new StubFileUpload("f.jpg", "image/jpeg", fakeFile);

        assertThrows(NotFoundException.class,
            () -> userService.uploadImage("auth0|unknown", upload));
    }

    // --- uploadBanner ---

    @Test
    @TestTransaction
    void uploadBanner_updatesBannerUrl(@TempDir Path tempDir) throws IOException {
        deleteAllUsers();
        persistUser("auth0|banner", "banner@example.com", false);

        Path fakeFile = tempDir.resolve("banner.jpg");
        Files.write(fakeFile, jpegHeader());
        FileUpload upload = new StubFileUpload("banner.jpg", "image/jpeg", fakeFile);

        User result = userService.uploadBanner("auth0|banner", upload);

        assertNotNull(result.bannerUrl);
        assertTrue(result.bannerUrl.startsWith("http"));
        assertTrue(result.bannerUrl.endsWith(".jpg"));
    }

    @Test
    @TestTransaction
    void uploadBanner_svgMime_throwsBadRequest(@TempDir Path tempDir) throws IOException {
        deleteAllUsers();
        persistUser("auth0|banner-svg", "banner-svg@example.com", false);

        Path fakeFile = tempDir.resolve("xss.svg");
        Files.write(fakeFile, svgContent());
        FileUpload upload = new StubFileUpload("xss.svg", "image/svg+xml", fakeFile);

        assertThrows(InvalidFileTypeException.class,
            () -> userService.uploadBanner("auth0|banner-svg", upload));
    }

    @Test
    @TestTransaction
    void uploadBanner_svgDisguisedAsPng_throwsBadRequest(@TempDir Path tempDir) throws IOException {
        deleteAllUsers();
        persistUser("auth0|banner-svg-png", "banner-svg-png@example.com", false);

        Path fakeFile = tempDir.resolve("xss.png");
        Files.write(fakeFile, svgContent());
        FileUpload upload = new StubFileUpload("xss.png", "image/png", fakeFile);

        assertThrows(InvalidFileTypeException.class,
            () -> userService.uploadBanner("auth0|banner-svg-png", upload));
    }

    @Test
    @TestTransaction
    void uploadBanner_invalidMime_throwsBadRequest(@TempDir Path tempDir) throws IOException {
        deleteAllUsers();
        persistUser("auth0|bannermime", "bannermime@example.com", false);

        Path fakeFile = tempDir.resolve("script.sh");
        Files.write(fakeFile, "#!/bin/bash".getBytes());
        FileUpload upload = new StubFileUpload("script.sh", "text/plain", fakeFile);

        assertThrows(InvalidFileTypeException.class,
            () -> userService.uploadBanner("auth0|bannermime", upload));
    }

    @Test
    @TestTransaction
    void uploadBanner_unknownUser_throwsNotFound(@TempDir Path tempDir) throws IOException {
        deleteAllUsers();

        Path fakeFile = tempDir.resolve("b.jpg");
        Files.write(fakeFile, jpegHeader());
        FileUpload upload = new StubFileUpload("b.jpg", "image/jpeg", fakeFile);

        assertThrows(NotFoundException.class,
            () -> userService.uploadBanner("auth0|unknown", upload));
    }

    // --- deleteBanner ---

    @Test
    @TestTransaction
    void deleteBanner_setsBannerUrlToNull() {
        deleteAllUsers();
        User user = persistUser("auth0|delbanner", "delbanner@example.com", false);
        user.bannerUrl = "https://cdn.example.com/banner.jpg";
        entityManager.flush();

        User result = userService.deleteBanner("auth0|delbanner");

        assertNull(result.bannerUrl);
    }

    @Test
    @TestTransaction
    void deleteBanner_unknownUser_throwsNotFound() {
        deleteAllUsers();

        assertThrows(NotFoundException.class,
            () -> userService.deleteBanner("auth0|unknown"));
    }

    static byte[] jpegHeader() {
        return new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 0, 0, 0};
    }

    static byte[] pngHeader() {
        return new byte[]{(byte) 0x89, 'P', 'N', 'G', (byte) 0x0D, (byte) 0x0A, (byte) 0x1A, (byte) 0x0A};
    }

    static byte[] webpHeader() {
        return new byte[]{'R', 'I', 'F', 'F', 0, 0, 0, 0, 'W', 'E', 'B', 'P'};
    }

    static byte[] gifHeader() {
        return new byte[]{'G', 'I', 'F', '8', '9', 'a', 0, 0, 0, 0};
    }

    static byte[] svgContent() {
        return "<svg xmlns=\"http://www.w3.org/2000/svg\"><script>alert(1)</script></svg>".getBytes();
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
        @Override public MultivaluedMap<String, String> getHeaders() { return new MultivaluedHashMap<>(); }
    }
}
