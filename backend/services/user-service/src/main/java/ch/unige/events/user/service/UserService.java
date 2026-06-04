package ch.unige.events.user.service;

import ch.unige.events.shared.error.ApiErrorResponse;
import ch.unige.events.shared.storage.FileStorageService;
import ch.unige.events.user.dto.PublicProfileView;
import ch.unige.events.user.dto.UpdateProfileRequest;
import ch.unige.events.shared.domain.enums.FollowStatus;
import ch.unige.events.user.follow.entity.Follow;
import ch.unige.events.user.entity.User;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.OptimisticLockException;
import jakarta.persistence.PersistenceException;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.ForbiddenException;
import jakarta.ws.rs.NotAuthorizedException;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.jwt.JsonWebToken;
import org.jboss.resteasy.reactive.multipart.FileUpload;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Same contract as the legacy UserService. Image / banner upload methods
 * delegate S3 wiring to the shared {@link FileStorageService}. The
 * follower / followStatus enrichment uses the local Follow ; will
 * become a REST call to user-service (replaced by REST client post-finalization) in a follow-up cleanup.
 */
@ApplicationScoped
public class UserService {

    @Inject FileStorageService fileStorageService;
    @Inject Instance<EntityManager> entityManager;

    /**
     * Legacy 2-arg overload — kept for callers that don't have access to
     * the security identity (e.g. background hydration). Does NOT sync
     * roles : passing {@code null} for the role set is the explicit
     * opt-out signal, distinct from "sync to empty set" (which would
     * wipe legitimate admin badges if a background job happened to
     * touch the row).
     */
    @Transactional
    public User getOrCreateUser(String auth0Id, JsonWebToken jwt) {
        return getOrCreateUser(auth0Id, jwt, null);
    }

    /**
     * Variant called from {@code UserResource.me()} that also syncs the
     * Auth0 roles from the caller's {@code SecurityIdentity} into the
     * persisted {@code User.roles} list. Roles are mirrored so that
     * {@code UserPublicResponse} can expose them on any profile (not
     * just the viewer's own), which is what drives the "Staff" badge on
     * the frontend profile page.
     *
     * <p>{@code rolesFromToken} semantics:
     * <ul>
     *   <li>{@code null} — opt out of sync entirely (leave whatever is
     *       persisted untouched). Used by the 2-arg overload.</li>
     *   <li>empty set — sync to empty (revoke all roles).</li>
     *   <li>non-empty set — sync to this exact set.</li>
     * </ul>
     *
     * <p>The sync is intentionally only wired on {@code GET /users/me} —
     * the frontend bootstraps from this endpoint on every page load, so
     * an Auth0 role change is picked up at the next session refresh.
     * Wiring sync into every authenticated endpoint would amplify the
     * write load with no real freshness benefit for this product.
     */
    @Transactional
    public User getOrCreateUser(String auth0Id, JsonWebToken jwt, Set<String> rolesFromToken) {
        User existing = User.findByAuth0Id(auth0Id).orElse(null);
        if (existing != null) {
            if (rolesFromToken != null) {
                syncRolesIfChanged(existing, rolesFromToken);
            }
            return existing;
        }

        String email = jwt.getClaim("email");
        if (email == null) {
            throw new NotAuthorizedException("Email claim is required");
        }

        try {
            User newUser = new User();
            newUser.auth0Id = auth0Id;
            newUser.email = email;
            newUser.displayName = jwt.getClaim("name");
            newUser.firstName = jwt.getClaim("given_name");
            newUser.lastName = jwt.getClaim("family_name");
            newUser.avatarUrl = jwt.getClaim("picture");
            // `newUser.roles` is initialised to an empty list by the entity
            // declaration ; only seed it from the token when the caller
            // actually wanted to sync (3-arg overload with a non-null set).
            if (rolesFromToken != null) {
                newUser.roles = new ArrayList<>(rolesFromToken);
            }
            // SCRUM-169 — auto-generate a public-facing username from the
            // JWT identity claims at signup. The slug logic mirrors the V3
            // migration back-fill so legacy and new accounts share the same
            // shape. Collision resolution probes the DB in the same
            // transaction.
            newUser.username = UsernameGenerator.generate(
                    newUser.displayName,
                    newUser.firstName,
                    newUser.lastName,
                    candidate -> User.findByUsername(candidate).isPresent()
            );
            newUser.persist();

            flushEntityManager();

            return newUser;
        } catch (PersistenceException exception) {
            if (isUniqueAuth0Conflict(exception)) {
                return User.findByAuth0Id(auth0Id).orElseThrow(() -> exception);
            }
            throw exception;
        }
    }

    /**
     * Mirrors the JWT roles claim onto {@code user.roles} when it has drifted
     * from what's persisted. Comparison is set-based (order doesn't matter, the
     * sorted serialisation is just for stable diff in tests) so the {@code
     * UPDATE} is skipped when nothing changed — the common case for repeat
     * /me hits within the same session.
     */
    private void syncRolesIfChanged(User user, Set<String> rolesFromToken) {
        // No null guard on `user.roles` — the entity initialises the field
        // to an empty list and Hibernate hydrates rows with no `user_roles`
        // entries as empty collections, so the field is always non-null.
        Set<String> current = new HashSet<>(user.roles);
        if (current.equals(rolesFromToken)) {
            return;
        }
        List<String> sorted = new ArrayList<>(rolesFromToken);
        Collections.sort(sorted);
        user.roles = sorted;
    }

    @Transactional
    public PublicProfileView getPublicProfile(UUID id, String auth0Id) {
        return getPublicProfile(id, auth0Id, false);
    }

    @Transactional
    public PublicProfileView getPublicProfile(UUID id, String auth0Id, boolean isAdmin) {
        User user = User.<User>findByIdOptional(id).orElseThrow(NotFoundException::new);
        return enrichPublicProfile(user, auth0Id, isAdmin);
    }

    @Transactional
    public User updateMyProfile(String authenticatedAuth0Id, String targetAuth0Id, UpdateProfileRequest req) {
        if (authenticatedAuth0Id == null || targetAuth0Id == null
                || !authenticatedAuth0Id.equals(targetAuth0Id)) {
            throw new ForbiddenException("Cannot modify another user's profile");
        }
        return updateMyProfile(targetAuth0Id, req);
    }

    @Transactional
    public User updateMyProfile(String auth0Id, UpdateProfileRequest req) {
        if (req == null) {
            throw new BadRequestException("Request body must not be null");
        }

        User user = User.findByAuth0Id(auth0Id).orElseThrow(NotFoundException::new);

        if (req.displayName()    != null) user.displayName = req.displayName();
        if (req.firstName()      != null) user.firstName = req.firstName();
        if (req.lastName()       != null) user.lastName = req.lastName();
        if (req.faculty()        != null) user.faculty = req.faculty();
        if (req.studyLevel()     != null) user.studyLevel = req.studyLevel();
        if (req.bio()            != null) user.bio = req.bio();
        if (req.interests()      != null) user.interests = req.interests();
        if (req.avatarUrl()      != null) user.avatarUrl = req.avatarUrl();
        if (req.profilePublic()  != null) user.profilePublic = req.profilePublic();

        try {
            flushEntityManager();
        } catch (OptimisticLockException exception) {
            throw optimisticLockConflict(exception);
        } catch (PersistenceException exception) {
            if (isOptimisticLockConflict(exception)) {
                throw optimisticLockConflict(exception);
            }
            throw exception;
        }

        return user;
    }

    /**
     * MINOR-011: {@code saveImage} performs
     * an S3 delete-then-upload of the previous object before the JPA flush
     * commits the new {@code avatarUrl}. If the JPA flush fails after the
     * S3 upload succeeded, the old S3 object is already deleted and the new
     * one is left orphaned (URL never persisted on the entity). Known
     * limitation inherited from the legacy monolith — acceptable for a
     * pinfo6 project. A proper outbox / two-phase commit pattern is
     * deferred to S9+ (would require a dedicated cleanup job scanning S3
     * for objects without a referencing entity).
     */
    @Transactional
    public User uploadImage(String auth0Id, FileUpload fileUpload) {
        User user = User.findByAuth0Id(auth0Id).orElseThrow(NotFoundException::new);
        user.avatarUrl = fileStorageService.saveImage(fileUpload, "users/avatars",
                FileStorageService.MAX_AVATAR_BYTES, user.avatarUrl);
        flushEntityManager();
        return user;
    }

    /** Same S3 cleanup limitation as {@link #uploadImage} — see its JavaDoc. */
    @Transactional
    public User uploadBanner(String auth0Id, FileUpload fileUpload) {
        User user = User.findByAuth0Id(auth0Id).orElseThrow(NotFoundException::new);
        user.bannerUrl = fileStorageService.saveImage(fileUpload, "users/banners",
                FileStorageService.MAX_BANNER_BYTES, user.bannerUrl);
        flushEntityManager();
        return user;
    }

    @Transactional
    public User deleteAvatar(String auth0Id) {
        User user = User.findByAuth0Id(auth0Id).orElseThrow(NotFoundException::new);
        fileStorageService.deleteObject(user.avatarUrl, "users/avatars");
        user.avatarUrl = null;
        flushEntityManager();
        return user;
    }

    @Transactional
    public User deleteBanner(String auth0Id) {
        User user = User.findByAuth0Id(auth0Id).orElseThrow(NotFoundException::new);
        user.bannerUrl = null;
        flushEntityManager();
        return user;
    }

    /**
     * SCRUM-169 — change the caller's username. Normalises the input, applies
     * pattern + blocklist validation, then attempts the persist. The race
     * between the debounced {@code HEAD /by-username/{u}} check and this
     * update is handled here : a pre-check probes for an existing row with
     * the same username (different user), and the unique constraint
     * {@code uq_users_username} acts as a last line of defence — a
     * {@link PersistenceException} containing the constraint name is
     * translated into the canonical {@code 409 username_taken} envelope.
     */
    @Transactional
    public User updateUsername(String auth0Id, String requestedUsername) {
        if (requestedUsername == null) {
            throw usernameInvalid();
        }
        String normalised = requestedUsername.trim().toLowerCase();

        if (!UsernameGenerator.isValid(normalised)) {
            throw usernameInvalid();
        }
        if (UsernameGenerator.isReserved(normalised)) {
            throw usernameReserved();
        }

        User user = User.findByAuth0Id(auth0Id).orElseThrow(NotFoundException::new);

        // No-op if unchanged (avoids touching @Version + the unique-check round-trip).
        if (normalised.equals(user.username)) {
            return user;
        }

        // Pre-check : explicit conflict surface before letting Hibernate
        // raise a generic PersistenceException.
        User existing = User.findByUsername(normalised).orElse(null);
        if (existing != null && !existing.id.equals(user.id)) {
            throw usernameTaken();
        }

        user.username = normalised;
        try {
            flushEntityManager();
        } catch (PersistenceException exception) {
            if (isUniqueUsernameConflict(exception)) {
                throw usernameTaken();
            }
            throw exception;
        }
        return user;
    }

    /**
     * SCRUM-169 — public profile lookup by username (case-insensitive).
     * Mirrors the authorization rules of {@link #getPublicProfile(UUID,
     * String, boolean)} : anti-oracle 404 on private profiles requested by
     * non-owner non-admin callers, admin bypass, follow-status enrichment.
     */
    @Transactional
    public PublicProfileView getByUsername(String username, String callerAuth0Id, boolean isAdmin) {
        if (username == null) {
            throw new NotFoundException();
        }
        String normalised = username.trim().toLowerCase();
        User user = User.findByUsername(normalised).orElseThrow(NotFoundException::new);
        return enrichPublicProfile(user, callerAuth0Id, isAdmin);
    }

    /**
     * SCRUM-137 polish — autocomplete-friendly prefix scan on {@code username}.
     * Returns at most {@code limit} matching users sorted alphabetically,
     * optionally excluding the caller so the invitation field never proposes
     * inviting yourself. Returns an empty list if {@code limit <= 0}.
     *
     * <p>Validation of the prefix (length, charset) is the caller's
     * responsibility — this method assumes a sanitised, lowercase string. The
     * service layer keeps it minimal: the resource is the validation choke
     * point ({@code @Pattern} / {@code @Size}) and the entity finder owns the
     * LIKE escape so the raw HQL never sees an unescaped wildcard.
     */
    public List<User> searchByUsernamePrefix(String prefix, int limit, String excludeAuth0Id) {
        return User.searchByUsernamePrefix(prefix, limit, excludeAuth0Id);
    }

    /**
     * SCRUM-169 — light existence check backing
     * {@code HEAD /users/by-username/{username}}. Case-insensitive. Does not
     * apply anti-oracle semantics (existence is the contract, and the field
     * is public-facing).
     */
    public boolean existsByUsername(String username) {
        if (username == null) {
            return false;
        }
        return User.findByUsername(username.trim().toLowerCase()).isPresent();
    }

    /**
     * Factored projection used by both {@link #getPublicProfile} and
     * {@link #getByUsername}.
     *
     * <p>A profile is served as a <strong>full</strong> projection (every field +
     * real follower/following counts) when it is public, or the caller is its
     * owner, an admin, or an accepted follower — <em>including anonymous callers
     * of a public profile</em>: a public profile is meant to be visible to all.
     *
     * <p>Otherwise (a private profile viewed by a non-owner / non-admin /
     * non-accepted-follower, anonymous or authenticated) it is served as a
     * <strong>locked</strong> projection ({@code restricted=true}): the resource
     * layer keeps the public-facing identity (id, username, displayName,
     * avatarUrl), the cover banner and the counts, and strips faculty,
     * studyLevel, bio and interests. The caller-relative {@code followStatus} is
     * preserved so the FollowButton renders "Demande envoyée" for a PENDING
     * request.
     *
     * <p>The {@code 404} is reserved for a genuinely missing user — never used as
     * a privacy gate (cf. SCRUM-169 revision of ISSUE-93, which had broken
     * cross-service enrichment by dropping the {@code authorUsername}).
     */
    private PublicProfileView enrichPublicProfile(User user, String callerAuth0Id, boolean isAdmin) {
        boolean isOwner = callerAuth0Id != null && callerAuth0Id.equals(user.auth0Id);

        // Counts are exposed on every projection now: a public profile shows them
        // to anonymous viewers (a public profile is meant to be fully visible) and
        // the locked "private" card shows them too (Instagram-style). The two
        // COUNT queries therefore run on every profile read.
        long followerCount = Follow.countFollowersOf(user.id);
        long followingCount = Follow.countFollowingOf(user.id);

        // Caller-relative follow status — null for an anonymous caller and for the
        // owner's self-view; otherwise the status of the (caller -> target) row.
        FollowStatus followStatus = resolveFollowStatus(user, callerAuth0Id, isOwner);

        // A private profile stays locked for everyone except its owner, an admin,
        // or an accepted follower. The locked projection still carries the counts
        // and the real followStatus so the frontend renders the correct counters
        // and FollowButton state ("Demande envoyée" for PENDING).
        boolean fullAccess = user.profilePublic || isOwner || isAdmin
                || followStatus == FollowStatus.ACCEPTED;

        return new PublicProfileView(user, followerCount, followingCount, followStatus, !fullAccess);
    }

    /**
     * Resolves the caller-relative {@link FollowStatus} for the {@code (caller ->
     * target)} couple. Returns {@code null} when the caller is anonymous, is the
     * target themselves, or has no {@code Follow} row — the cases where the
     * frontend renders a plain "Suivre" button (or routes an anonymous click to
     * login).
     */
    private static FollowStatus resolveFollowStatus(User target, String callerAuth0Id, boolean isOwner) {
        if (callerAuth0Id == null || isOwner) {
            return null;
        }
        // Self (caller == target) is already short-circuited by isOwner above, so
        // a resolved callerId can never equal target.id here.
        UUID callerId = User.findByAuth0Id(callerAuth0Id).map(u -> u.id).orElse(null);
        if (callerId == null) {
            return null;
        }
        return Follow.findByFollowerAndFollowed(callerId, target.id)
                .map((Follow f) -> f.status)
                .orElse(null);
    }

    private void flushEntityManager() {
        entityManager.get().flush();
    }

    private boolean isUniqueAuth0Conflict(Throwable throwable) {
        return containsMessage(throwable, "users_auth0_id_unique");
    }

    private boolean isUniqueUsernameConflict(Throwable throwable) {
        return containsMessage(throwable, "uq_users_username");
    }

    private boolean containsMessage(Throwable throwable, String marker) {
        Throwable current = throwable;
        while (current != null) {
            String message = current.getMessage();
            if (message != null && message.contains(marker)) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private boolean isOptimisticLockConflict(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof OptimisticLockException) {
                return true;
            }
            String className = current.getClass().getName();
            if (className.contains("OptimisticLock") || className.contains("StaleState")) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    /**
     * D20 — wrap an {@link OptimisticLockException} in a
     * 409 {@link WebApplicationException} carrying the canonical
     * {@link ApiErrorResponse} envelope. The original exception is
     * preserved as the cause so observability tooling (Sentry, Quarkus
     * stack traces) can still trace back to the JPA layer.
     */
    private static WebApplicationException optimisticLockConflict(Throwable cause) {
        Response response = Response.status(Response.Status.CONFLICT)
                .entity(new ApiErrorResponse("optimistic_lock_conflict",
                        "Profile was updated by another request. Please retry."))
                .type(MediaType.APPLICATION_JSON)
                .build();
        return new WebApplicationException("optimistic_lock_conflict", cause, response);
    }

    /** SCRUM-169 — canonical 400 envelope for a pattern violation. */
    private static WebApplicationException usernameInvalid() {
        Response response = Response.status(Response.Status.BAD_REQUEST)
                .entity(new ApiErrorResponse("username_invalid",
                        "Username must match ^[a-z0-9._-]{3,30}$ (3-30 lowercase letters, digits, '.', '_', '-')."))
                .type(MediaType.APPLICATION_JSON)
                .build();
        return new WebApplicationException("username_invalid", response);
    }

    /** SCRUM-169 — canonical 400 envelope for a blocklist hit. */
    private static WebApplicationException usernameReserved() {
        Response response = Response.status(Response.Status.BAD_REQUEST)
                .entity(new ApiErrorResponse("username_reserved",
                        "This username is reserved and cannot be claimed."))
                .type(MediaType.APPLICATION_JSON)
                .build();
        return new WebApplicationException("username_reserved", response);
    }

    /** SCRUM-169 — canonical 409 envelope for a unique-constraint hit. */
    private static WebApplicationException usernameTaken() {
        Response response = Response.status(Response.Status.CONFLICT)
                .entity(new ApiErrorResponse("username_taken",
                        "This username is already taken by another user."))
                .type(MediaType.APPLICATION_JSON)
                .build();
        return new WebApplicationException("username_taken", response);
    }
}
