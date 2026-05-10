package ch.unige.events.user.service;

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
import org.eclipse.microprofile.jwt.JsonWebToken;
import org.jboss.resteasy.reactive.multipart.FileUpload;

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

    @Transactional
    public User getOrCreateUser(String auth0Id, JsonWebToken jwt) {
        User existing = User.findByAuth0Id(auth0Id).orElse(null);
        if (existing != null) {
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

    @Transactional
    public PublicProfileView getPublicProfile(UUID id, String auth0Id) {
        return getPublicProfile(id, auth0Id, false);
    }

    @Transactional
    public PublicProfileView getPublicProfile(UUID id, String auth0Id, boolean isAdmin) {
        User user = User.<User>findByIdOptional(id).orElseThrow(NotFoundException::new);

        boolean isOwner = auth0Id != null && auth0Id.equals(user.auth0Id);
        // Admin bypass aligns with UserServiceClient javadoc (Étape 2.4
        // finalization-ultimate REST-003 / ISSUE-93): admins read private
        // profiles for moderation/support. Anti-oracle ISSUE-93 stays for
        // anonymous + non-admin non-self callers.
        if (!user.profilePublic && !isOwner && !isAdmin) {
            throw new NotFoundException();
        }

        if (auth0Id == null) {
            return PublicProfileView.anonymous(user);
        }

        long followerCount = Follow.countFollowersOf(user.id);
        long followingCount = Follow.countFollowingOf(user.id);

        FollowStatus followStatus = null;
        if (!isOwner) {
            UUID callerId = User.findByAuth0Id(auth0Id).map(u -> u.id).orElse(null);
            if (callerId != null && !callerId.equals(user.id)) {
                followStatus = Follow.findByFollowerAndFollowed(callerId, user.id)
                        .map((Follow f) -> f.status)
                        .orElse(null);
            }
        }
        return new PublicProfileView(user, followerCount, followingCount, followStatus);
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
        if (req.faculty()        != null) user.faculty = req.faculty();
        if (req.studyLevel()     != null) user.studyLevel = req.studyLevel();
        if (req.bio()            != null) user.bio = req.bio();
        if (req.interests()      != null) user.interests = req.interests();
        if (req.avatarUrl()      != null) user.avatarUrl = req.avatarUrl();
        if (req.profilePublic()  != null) user.profilePublic = req.profilePublic();

        try {
            flushEntityManager();
        } catch (OptimisticLockException exception) {
            throw new OptimisticLockException("Profile was updated by another request. Please retry.");
        } catch (PersistenceException exception) {
            if (isOptimisticLockConflict(exception)) {
                throw new OptimisticLockException("Profile was updated by another request. Please retry.");
            }
            throw exception;
        }

        return user;
    }

    /**
     * MINOR-011 (Étape 5.5 finalization-complete): {@code saveImage} performs
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

    private void flushEntityManager() {
        entityManager.get().flush();
    }

    private boolean isUniqueAuth0Conflict(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            String message = current.getMessage();
            if (message != null && message.contains("users_auth0_id_unique")) {
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
}
