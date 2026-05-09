package ch.unige.events.user.service;

import ch.unige.events.user.dto.PublicProfileView;
import ch.unige.events.user.dto.UpdateProfileRequest;
import ch.unige.events.user.entity.FollowStatus;
import ch.unige.events.user.entity.FollowStub;
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

import java.util.Objects;
import java.util.UUID;

/**
 * Same contract as the legacy UserService. Image / banner upload methods
 * carry their own S3 wiring via {@link FileStorageService} (carbon-copied
 * from legacy and re-rooted in this module's package — same code lives
 * in event-service for the event banner upload). The follower /
 * followStatus enrichment uses the local FollowStub ; will become a REST
 * call to follow-service in a follow-up cleanup.
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

    public PublicProfileView getPublicProfile(UUID id, String auth0Id) {
        User user = User.<User>findByIdOptional(id).orElseThrow(NotFoundException::new);

        boolean isOwner = auth0Id != null && auth0Id.equals(user.auth0Id);
        if (!user.profilePublic && !isOwner) {
            throw new NotFoundException();
        }

        if (auth0Id == null) {
            return PublicProfileView.anonymous(user);
        }

        long followerCount = FollowStub.countFollowersOf(user.id);
        long followingCount = FollowStub.countFollowingOf(user.id);

        FollowStatus followStatus = null;
        if (!isOwner) {
            UUID callerId = User.findByAuth0Id(auth0Id).map(u -> u.id).orElse(null);
            if (callerId != null && !callerId.equals(user.id)) {
                followStatus = FollowStub.findByFollowerAndFollowed(callerId, user.id)
                        .map(f -> f.status)
                        .orElse(null);
            }
        }
        return new PublicProfileView(user, followerCount, followingCount, followStatus);
    }

    @Transactional
    public User updateMyProfile(String authenticatedAuth0Id, String targetAuth0Id, UpdateProfileRequest req) {
        if (!Objects.equals(authenticatedAuth0Id, targetAuth0Id)) {
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

    @Transactional
    public User uploadImage(String auth0Id, FileUpload fileUpload) {
        User user = User.findByAuth0Id(auth0Id).orElseThrow(NotFoundException::new);
        user.avatarUrl = fileStorageService.saveImage(fileUpload, "users/avatars",
                FileStorageService.MAX_AVATAR_BYTES, user.avatarUrl);
        flushEntityManager();
        return user;
    }

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
