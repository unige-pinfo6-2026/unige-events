package ch.unige.events.user.service;

import ch.unige.events.shared.domain.enums.Faculty;
import ch.unige.events.shared.domain.enums.FollowStatus;
import ch.unige.events.shared.storage.FileStorageService;
import ch.unige.events.user.dto.PublicProfileView;
import ch.unige.events.user.dto.UpdateProfileRequest;
import ch.unige.events.user.entity.User;
import ch.unige.events.user.follow.entity.Follow;
import ch.unige.events.user.test.JwtTestHelper;

import io.quarkus.test.InjectMock;
import io.quarkus.test.TestTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.ForbiddenException;
import jakarta.ws.rs.NotAuthorizedException;
import jakarta.ws.rs.NotFoundException;
import org.eclipse.microprofile.jwt.JsonWebToken;
import org.jboss.resteasy.reactive.multipart.FileUpload;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Coverage-oriented test suite for {@link UserService}. Drives the
 * service through the real DevServices Postgres datasource — keeps the
 * coverage contract honest on flush / OptimisticLock / unique-constraint
 * paths (which require a real DB to surface).
 */
@QuarkusTest
class UserServiceTest {

    @Inject UserService userService;
    @Inject EntityManager entityManager;

    @InjectMock FileStorageService fileStorageService;

    private static JsonWebToken jwt(String auth0Id, Map<String, Object> claims) {
        return JwtTestHelper.jwtFor(auth0Id, claims);
    }

    // ── getOrCreateUser ────────────────────────────────────────────────────

    @Test
    @TestTransaction
    void getOrCreateUser_existingUser_returnsExisting() {
        User existing = persistUser("auth0|us-existing", "us-existing@example.com", false);

        User result = userService.getOrCreateUser("auth0|us-existing",
                jwt("auth0|us-existing", Map.of("email", "ignored@example.com")));

        assertEquals(existing.id, result.id);
    }

    @Test
    @TestTransaction
    void getOrCreateUser_missingEmailClaim_throwsNotAuthorized() {
        assertThrows(NotAuthorizedException.class,
                () -> userService.getOrCreateUser("auth0|us-no-email",
                        jwt("auth0|us-no-email", Map.of())));
    }

    @Test
    @TestTransaction
    void getOrCreateUser_nullEmailClaim_throwsNotAuthorized() {
        Map<String, Object> claims = new HashMap<>();
        claims.put("email", null);
        assertThrows(NotAuthorizedException.class,
                () -> userService.getOrCreateUser("auth0|us-null-email",
                        jwt("auth0|us-null-email", claims)));
    }

    @Test
    @TestTransaction
    void getOrCreateUser_newUser_createsAndPopulatesClaims() {
        User created = userService.getOrCreateUser("auth0|us-new",
                jwt("auth0|us-new", Map.of(
                        "email", "us-new@example.com",
                        "name", "Some User",
                        "given_name", "Some",
                        "family_name", "User",
                        "picture", "https://cdn.example.com/avatar.png")));

        assertNotNull(created.id);
        assertEquals("auth0|us-new", created.auth0Id);
        assertEquals("us-new@example.com", created.email);
        assertEquals("Some User", created.displayName);
        assertEquals("Some", created.firstName);
        assertEquals("User", created.lastName);
        assertEquals("https://cdn.example.com/avatar.png", created.avatarUrl);
        assertFalse(created.profilePublic, "newly-created users default to private profile");
    }

    // ── getPublicProfile ───────────────────────────────────────────────────

    @Test
    @TestTransaction
    void getPublicProfile_unknownId_throwsNotFound() {
        assertThrows(NotFoundException.class,
                () -> userService.getPublicProfile(UUID.randomUUID(), "auth0|us-anyone"));
    }

    @Test
    @TestTransaction
    void getPublicProfile_privateProfile_anonymousCaller_returnsRestrictedView() {
        // SCRUM-169 revision — private profile seen by a non-self non-admin
        // caller (here anonymous) no longer 404s; it returns a restricted
        // PublicProfileView so the resource layer can surface the
        // public-facing identifier (id, username, displayName, avatarUrl)
        // through the anonymous projection.
        User user = persistUser("auth0|us-priv-anon", "us-priv-anon@example.com", false);

        PublicProfileView view = userService.getPublicProfile(user.id, null);
        assertEquals(user.id, view.user().id);
        assertTrue(view.restricted(), "restricted flag must be set so the resource strips the payload");
        assertEquals(0L, view.followerCount());
        assertEquals(0L, view.followingCount());
        assertNull(view.followStatus());
    }

    @Test
    @TestTransaction
    void getPublicProfile_privateProfile_otherCaller_returnsRestrictedView() {
        // SCRUM-169 revision — authenticated non-self non-admin caller of
        // a private profile gets the restricted projection instead of 404.
        User user = persistUser("auth0|us-priv-tgt", "us-priv-tgt@example.com", false);
        persistUser("auth0|us-priv-other", "us-priv-other@example.com", true);

        PublicProfileView view = userService.getPublicProfile(user.id, "auth0|us-priv-other");
        assertEquals(user.id, view.user().id);
        assertTrue(view.restricted());
        assertEquals(0L, view.followerCount());
        assertEquals(0L, view.followingCount());
        assertNull(view.followStatus());
    }

    @Test
    @TestTransaction
    void getPublicProfile_privateProfile_adminCallerBypassesAntiOracle() {
        User user = persistUser("auth0|us-priv-admin-tgt", "us-priv-admin-tgt@example.com", false);
        persistUser("auth0|us-priv-admin-caller", "us-priv-admin-caller@example.com", true);

        PublicProfileView view = userService.getPublicProfile(user.id, "auth0|us-priv-admin-caller", true);
        assertEquals(user.id, view.user().id);
    }

    @Test
    @TestTransaction
    void getPublicProfile_anonymousCaller_publicProfile_returnsAnonymousView() {
        User user = persistUser("auth0|us-anon-pub", "us-anon-pub@example.com", true);

        PublicProfileView view = userService.getPublicProfile(user.id, null);
        assertEquals(user.id, view.user().id);
        assertEquals(0L, view.followerCount());
        assertEquals(0L, view.followingCount());
        assertNull(view.followStatus());
    }

    @Test
    @TestTransaction
    void getPublicProfile_authedSelf_followStatusIsNull() {
        User user = persistUser("auth0|us-self", "us-self@example.com", true);

        PublicProfileView view = userService.getPublicProfile(user.id, "auth0|us-self");
        assertEquals(user.id, view.user().id);
        assertNull(view.followStatus());
    }

    @Test
    @TestTransaction
    void getPublicProfile_authedOtherWithAcceptedFollow_returnsAccepted() {
        User target = persistUser("auth0|us-target-acc", "us-target-acc@example.com", true);
        User caller = persistUser("auth0|us-caller-acc", "us-caller-acc@example.com", true);
        persistFollow(caller.id, target.id, FollowStatus.ACCEPTED);

        PublicProfileView view = userService.getPublicProfile(target.id, "auth0|us-caller-acc");
        assertEquals(FollowStatus.ACCEPTED, view.followStatus());
    }

    @Test
    @TestTransaction
    void getPublicProfile_authedOther_noFollowRow_returnsNullStatus() {
        User target = persistUser("auth0|us-target-nf", "us-target-nf@example.com", true);
        persistUser("auth0|us-caller-nf", "us-caller-nf@example.com", true);

        PublicProfileView view = userService.getPublicProfile(target.id, "auth0|us-caller-nf");
        assertNull(view.followStatus());
    }

    @Test
    @TestTransaction
    void getPublicProfile_authedOtherButCallerNotProvisioned_returnsNullStatus() {
        User target = persistUser("auth0|us-target-cnp", "us-target-cnp@example.com", true);

        // caller has no User row → callerId resolves to null → followStatus stays null.
        PublicProfileView view = userService.getPublicProfile(target.id, "auth0|us-ghost-caller");
        assertNull(view.followStatus());
    }

    // ── updateMyProfile ────────────────────────────────────────────────────

    @Test
    @TestTransaction
    void updateMyProfile_authMismatch_throwsForbidden() {
        persistUser("auth0|us-upd-x", "us-upd-x@example.com", true);

        assertThrows(ForbiddenException.class,
                () -> userService.updateMyProfile("auth0|us-upd-x", "auth0|us-upd-y",
                        new UpdateProfileRequest(null, null, null, null, null, null, null, null, null)));
    }

    @Test
    @TestTransaction
    void updateMyProfile_nullRequest_throwsBadRequest() {
        persistUser("auth0|us-upd-null", "us-upd-null@example.com", true);

        assertThrows(BadRequestException.class,
                () -> userService.updateMyProfile("auth0|us-upd-null", null));
    }

    @Test
    @TestTransaction
    void updateMyProfile_unknownUser_throwsNotFound() {
        assertThrows(NotFoundException.class,
                () -> userService.updateMyProfile("auth0|us-upd-unknown",
                        new UpdateProfileRequest("New Name", null, null, null, null, null, null, null, null)));
    }

    @Test
    @TestTransaction
    void updateMyProfile_partialUpdate_appliesOnlyNonNullFields() {
        User user = persistUser("auth0|us-upd-partial", "us-upd-partial@example.com", false);
        user.displayName = "Old";
        user.faculty = Faculty.SCIENCES;
        entityManager.flush();

        UpdateProfileRequest req = new UpdateProfileRequest(
                "New Name", "First", "Last", null, "Master", "New bio",
                List.of("AI"), "https://cdn/avatar.png", true);

        User updated = userService.updateMyProfile("auth0|us-upd-partial", "auth0|us-upd-partial", req);

        assertEquals("New Name", updated.displayName);
        assertEquals("First", updated.firstName);
        assertEquals("Last", updated.lastName);
        // faculty was null in request → unchanged
        assertEquals(Faculty.SCIENCES, updated.faculty);
        assertEquals("Master", updated.studyLevel);
        assertEquals("New bio", updated.bio);
        assertEquals(List.of("AI"), updated.interests);
        assertEquals("https://cdn/avatar.png", updated.avatarUrl);
        assertTrue(updated.profilePublic);
    }

    // ── uploadImage / uploadBanner ─────────────────────────────────────────

    @Test
    @TestTransaction
    void uploadImage_existingUser_setsAvatarUrl() {
        persistUser("auth0|us-up-img", "us-up-img@example.com", false);
        FileUpload mockFile = mock(FileUpload.class);
        when(fileStorageService.saveImage(any(FileUpload.class), anyString(), anyLong(), any()))
                .thenReturn("https://cdn/users/avatars/abc.png");

        User updated = userService.uploadImage("auth0|us-up-img", mockFile);
        assertEquals("https://cdn/users/avatars/abc.png", updated.avatarUrl);
    }

    @Test
    @TestTransaction
    void uploadImage_unknownUser_throwsNotFound() {
        FileUpload mockFile = mock(FileUpload.class);
        assertThrows(NotFoundException.class,
                () -> userService.uploadImage("auth0|us-up-img-unknown", mockFile));
    }

    @Test
    @TestTransaction
    void uploadBanner_existingUser_setsBannerUrl() {
        persistUser("auth0|us-up-banner", "us-up-banner@example.com", false);
        FileUpload mockFile = mock(FileUpload.class);
        when(fileStorageService.saveImage(any(FileUpload.class), anyString(), anyLong(), any()))
                .thenReturn("https://cdn/users/banners/def.png");

        User updated = userService.uploadBanner("auth0|us-up-banner", mockFile);
        assertEquals("https://cdn/users/banners/def.png", updated.bannerUrl);
    }

    @Test
    @TestTransaction
    void uploadBanner_unknownUser_throwsNotFound() {
        FileUpload mockFile = mock(FileUpload.class);
        assertThrows(NotFoundException.class,
                () -> userService.uploadBanner("auth0|us-up-banner-unknown", mockFile));
    }

    @Test
    @TestTransaction
    void deleteAvatar_invokesFileStorage() {
        User user = persistUser("auth0|us-del-avatar-mock", "us-del-avatar-mock@example.com", true);
        user.avatarUrl = "https://cdn.example.com/users/avatars/x.png";
        entityManager.flush();
        lenient().doNothing().when(fileStorageService).deleteObject(anyString(), anyString());

        User updated = userService.deleteAvatar("auth0|us-del-avatar-mock");
        assertNull(updated.avatarUrl);
    }

    // ── deleteAvatar / deleteBanner ────────────────────────────────────────

    @Test
    @TestTransaction
    void deleteAvatar_unknownUser_throwsNotFound() {
        assertThrows(NotFoundException.class,
                () -> userService.deleteAvatar("auth0|us-del-avatar-unknown"));
    }

    @Test
    @TestTransaction
    void deleteAvatar_existingUser_clearsAvatarField() {
        User user = persistUser("auth0|us-del-avatar", "us-del-avatar@example.com", true);
        user.avatarUrl = "https://no-such-host/users/avatars/x.png";
        entityManager.flush();

        // tryDeleteObject swallows S3 errors (S3 endpoint unreachable in tests),
        // so the avatar field still gets cleared.
        User updated = userService.deleteAvatar("auth0|us-del-avatar");
        assertNull(updated.avatarUrl);
    }

    @Test
    @TestTransaction
    void deleteBanner_existingUser_clearsBannerField() {
        User user = persistUser("auth0|us-del-banner", "us-del-banner@example.com", true);
        user.bannerUrl = "https://no-such-host/users/banners/x.png";
        entityManager.flush();

        User updated = userService.deleteBanner("auth0|us-del-banner");
        assertNull(updated.bannerUrl);
    }

    @Test
    @TestTransaction
    void deleteBanner_unknownUser_throwsNotFound() {
        assertThrows(NotFoundException.class,
                () -> userService.deleteBanner("auth0|us-del-banner-unknown"));
    }

    // ── User static finders ───────────────────────────────────────────────

    @Test
    @TestTransaction
    void userStaticFinders_findByAuth0IdAndEmailAndCalendarToken() {
        User user = persistUser("auth0|us-find", "us-find@example.com", true);
        UUID token = UUID.randomUUID();
        user.calendarToken = token;
        entityManager.flush();

        assertTrue(User.findByAuth0Id("auth0|us-find").isPresent());
        assertEquals(user.id, User.findByAuth0Id("auth0|us-find").orElseThrow().id);
        assertEquals(user.id, User.findByEmail("us-find@example.com").orElseThrow().id);
        assertEquals(user.id, User.findByCalendarToken(token).orElseThrow().id);
        assertTrue(User.findByAuth0Id("auth0|us-no-such").isEmpty());
    }

    // ─── SCRUM-169 — updateUsername / getByUsername / existsByUsername ──

    @Test
    @TestTransaction
    void updateUsername_happyPath_persistsLowercase() {
        User user = persistUser("auth0|us-uname-happy", "us-uname-happy@example.com", false);
        User updated = userService.updateUsername(user.auth0Id, "  New.Handle  ");
        // Normalised : trim + toLowerCase before persist.
        assertEquals("new.handle", updated.username);
    }

    @Test
    @TestTransaction
    void updateUsername_sameValue_isIdempotent_returnsUser() {
        User user = persistUser("auth0|us-uname-same", "us-uname-same@example.com", false);
        String current = user.username;

        User result = userService.updateUsername(user.auth0Id, current);
        assertEquals(current, result.username);
    }

    @Test
    @TestTransaction
    void updateUsername_invalidPattern_throws400_usernameInvalid() {
        User user = persistUser("auth0|us-uname-inv", "us-uname-inv@example.com", false);
        jakarta.ws.rs.WebApplicationException ex = assertThrows(
                jakarta.ws.rs.WebApplicationException.class,
                () -> userService.updateUsername(user.auth0Id, "AB")); // < 3
        assertEquals(400, ex.getResponse().getStatus());
        assertEquals("username_invalid", ex.getMessage());
    }

    @Test
    @TestTransaction
    void updateUsername_reservedWord_throws400_usernameReserved() {
        User user = persistUser("auth0|us-uname-res", "us-uname-res@example.com", false);
        jakarta.ws.rs.WebApplicationException ex = assertThrows(
                jakarta.ws.rs.WebApplicationException.class,
                () -> userService.updateUsername(user.auth0Id, "admin"));
        assertEquals(400, ex.getResponse().getStatus());
        assertEquals("username_reserved", ex.getMessage());
    }

    @Test
    @TestTransaction
    void updateUsername_alreadyTakenByOther_throws409_usernameTaken() {
        User existing = persistUser("auth0|us-uname-other", "us-uname-other@example.com", false);
        existing.username = "claimed.handle";
        entityManager.flush();

        User caller = persistUser("auth0|us-uname-self", "us-uname-self@example.com", false);

        jakarta.ws.rs.WebApplicationException ex = assertThrows(
                jakarta.ws.rs.WebApplicationException.class,
                () -> userService.updateUsername(caller.auth0Id, "claimed.handle"));
        assertEquals(409, ex.getResponse().getStatus());
        assertEquals("username_taken", ex.getMessage());
    }

    @Test
    @TestTransaction
    void updateUsername_unknownAuth0Id_throwsNotFound() {
        assertThrows(NotFoundException.class,
                () -> userService.updateUsername("auth0|us-uname-ghost", "any.handle"));
    }

    @Test
    @TestTransaction
    void getByUsername_publicProfile_anonymous_returnsView() {
        User user = persistUser("auth0|us-gbun-pub", "us-gbun-pub@example.com", true);
        user.username = "public.bun";
        entityManager.flush();

        PublicProfileView view = userService.getByUsername("public.bun", null, false);
        assertEquals(user.id, view.user().id);
    }

    @Test
    @TestTransaction
    void getByUsername_caseInsensitive_normalisesLowercase() {
        User user = persistUser("auth0|us-gbun-case", "us-gbun-case@example.com", true);
        user.username = "case.bun";
        entityManager.flush();

        PublicProfileView view = userService.getByUsername("CASE.BUN", null, false);
        assertEquals(user.id, view.user().id);
    }

    @Test
    @TestTransaction
    void getByUsername_privateProfile_otherCaller_returnsRestrictedView() {
        // SCRUM-169 revision — username lookup of a private profile by a
        // non-self non-admin caller no longer 404s; it returns the
        // restricted projection (id+username+displayName+avatarUrl).
        User target = persistUser("auth0|us-gbun-priv", "us-gbun-priv@example.com", false);
        target.username = "priv.bun";
        entityManager.flush();
        persistUser("auth0|us-gbun-other", "us-gbun-other@example.com", false);

        PublicProfileView view = userService.getByUsername("priv.bun", "auth0|us-gbun-other", false);
        assertEquals(target.id, view.user().id);
        assertTrue(view.restricted());
        assertNull(view.followStatus());
    }

    @Test
    @TestTransaction
    void getByUsername_privateProfile_adminCaller_bypasses() {
        User target = persistUser("auth0|us-gbun-admin", "us-gbun-admin@example.com", false);
        target.username = "admin.target";
        entityManager.flush();
        persistUser("auth0|us-gbun-admincaller", "admincaller@example.com", false);

        PublicProfileView view = userService.getByUsername(
                "admin.target", "auth0|us-gbun-admincaller", true);
        assertEquals(target.id, view.user().id);
    }

    @Test
    @TestTransaction
    void getByUsername_notFound_throws() {
        assertThrows(NotFoundException.class,
                () -> userService.getByUsername("ghost.handle", null, false));
    }

    @Test
    @TestTransaction
    void existsByUsername_takenAndAvailable() {
        User user = persistUser("auth0|us-exists", "us-exists@example.com", true);
        user.username = "exist.handle";
        entityManager.flush();

        assertTrue(userService.existsByUsername("exist.handle"));
        assertTrue(userService.existsByUsername("Exist.Handle")); // case-insensitive
        assertFalse(userService.existsByUsername("never.exists.xyz"));
        assertFalse(userService.existsByUsername(null));
    }

    @Test
    @TestTransaction
    void getOrCreateUser_newUser_generatesUsername() {
        Map<String, Object> claims = new HashMap<>();
        claims.put("email", "alice.dupont@example.com");
        claims.put("name", "Alice Dupont");
        JsonWebToken jwt = jwt("auth0|us-gen-new", claims);

        User created = userService.getOrCreateUser("auth0|us-gen-new", jwt);
        assertEquals("alice.dupont", created.username);
    }

    @Test
    @TestTransaction
    void getOrCreateUser_newUser_collisionResolvedBySuffix() {
        // Pre-populate one user with the slug we expect.
        User existing = persistUser("auth0|us-gen-pre", "alice@example.com", false);
        existing.username = "alice.dupont";
        entityManager.flush();

        Map<String, Object> claims = new HashMap<>();
        claims.put("email", "alice2@example.com");
        claims.put("name", "Alice Dupont");
        JsonWebToken jwt = jwt("auth0|us-gen-collide", claims);

        User created = userService.getOrCreateUser("auth0|us-gen-collide", jwt);
        assertEquals("alice.dupont2", created.username);
    }

    // ── helpers ───────────────────────────────────────────────────────────

    private User persistUser(String auth0Id, String email, boolean profilePublic) {
        User user = new User();
        user.auth0Id = auth0Id;
        user.email = email;
        // SCRUM-169 — derive a unique username so the entity satisfies the
        // NOT NULL + UNIQUE constraint. Tests that exercise username
        // explicitly mutate the value after persist.
        user.username = UsernameGenerator.resolveAvailable(
                UsernameGenerator.slugify(null,
                        email.contains("@") ? email.substring(0, email.indexOf('@')) : email,
                        null),
                candidate -> User.findByUsername(candidate).isPresent()
        );
        user.profilePublic = profilePublic;
        user.createdAt = LocalDateTime.now();
        entityManager.persist(user);
        entityManager.flush();
        return user;
    }

    private Follow persistFollow(UUID followerId, UUID followedId, FollowStatus status) {
        Follow f = new Follow();
        f.followerId = followerId;
        f.followedId = followedId;
        f.status = status;
        f.createdAt = LocalDateTime.now();
        entityManager.persist(f);
        entityManager.flush();
        return f;
    }
}
