package ch.unige.events.service;

import ch.unige.events.dto.UpdateProfileRequest;
import ch.unige.events.entity.User;
import io.quarkus.oidc.UserInfo;
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
import java.util.Objects;
import java.util.UUID;

@ApplicationScoped
public class UserService {

    private final Instance<EntityManager> entityManager;

    @Inject
    public UserService(Instance<EntityManager> entityManager) {
        this.entityManager = entityManager;
    }

    /**
     * Appelé à chaque requête authentifiée.
     * Crée le profil si c'est la 1ère connexion.
     */
    @Transactional
    public User getOrCreateUser(String auth0Id, UserInfo userInfo) {
        User existing = User.findByAuth0Id(auth0Id).orElse(null);
        if (existing != null) {
            return existing;
        }

        if (userInfo == null || userInfo.getEmail() == null) {
            throw new NotAuthorizedException("Email claim is required");
        }

        try {
            User newUser = User.builder()
                    .auth0Id(auth0Id)
                    .displayName(userInfo.getName())
                    .firstName(userInfo.getString("given_name"))
                    .lastName(userInfo.getFamilyName())
                    .email(userInfo.getEmail())
                    .avatarUrl(userInfo.getString("picture"))
                    .profilePublic(false)
                    .admin(false)
                    .build();

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

    public User getPublicProfile(UUID id) {
        User user = (User) User.findByIdOptional(id)
            .orElseThrow(NotFoundException::new);

        if (!user.isProfilePublic()) {
            throw new ForbiddenException("This profile is private");
        }
        return user;
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

        User user = User.findByAuth0Id(auth0Id)
            .orElseThrow(NotFoundException::new);

        if (req.displayName()    != null) user.setDisplayName(req.displayName());
        if (req.faculty()        != null) user.setFaculty(req.faculty());
        if (req.studyLevel()     != null) user.setStudyLevel(req.studyLevel());
        if (req.bio()            != null) user.setBio(req.bio());
        if (req.interests()      != null) user.setInterests(req.interests());
        if (req.avatarUrl()      != null) user.setAvatarUrl(req.avatarUrl());
        if (req.isProfilePublic()!= null) user.setProfilePublic(req.isProfilePublic());

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
