package ch.unige.events.service;

import ch.unige.events.dto.ApiErrorResponse;
import ch.unige.events.dto.user.UpdateProfileRequest;
import ch.unige.events.entity.User;
import ch.unige.events.util.UsernameGenerator;
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

import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

@ApplicationScoped
public class UserService {

    private static final Pattern USERNAME_PATTERN = Pattern.compile(User.USERNAME_PATTERN);

    @Inject FileStorageService fileStorageService;
    @Inject Instance<EntityManager> entityManager;

    /**
     * Appelé à chaque requête authentifiée.
     * Crée le profil si c'est la 1ère connexion.
     */
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
            newUser.username = generateUniqueUsername(
                    newUser.displayName, newUser.firstName, newUser.lastName);
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

    public User getPublicProfile(UUID id, String auth0Id) {
        User user = (User) User.findByIdOptional(id).orElseThrow(NotFoundException::new);
        return ensureViewable(user, auth0Id);
    }

    public User getPublicProfileByUsername(String username, String auth0Id) {
        if (username == null || username.isBlank()) {
            throw new NotFoundException();
        }
        User user = User.findByUsername(username).orElseThrow(NotFoundException::new);
        return ensureViewable(user, auth0Id);
    }

    public boolean usernameExists(String username) {
        return username != null && User.existsByUsername(username);
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

    /**
     * Updates the authenticated user's username. The candidate is normalized to
     * lowercase, validated against the public pattern and the reserved blocklist,
     * then checked for uniqueness (case-insensitive). Returns the updated User.
     */
    @Transactional
    public User updateMyUsername(String auth0Id, String rawUsername) {
        if (rawUsername == null) {
            throw badRequest("username_invalid", "Username must not be null");
        }
        String candidate = rawUsername.trim().toLowerCase();
        if (!USERNAME_PATTERN.matcher(candidate).matches()) {
            throw badRequest("username_invalid",
                    "Username must match " + User.USERNAME_PATTERN);
        }
        if (UsernameGenerator.isReserved(candidate)) {
            throw badRequest("username_reserved", "This username is reserved");
        }

        User user = User.findByAuth0Id(auth0Id).orElseThrow(NotFoundException::new);

        if (candidate.equals(user.username)) {
            return user;
        }

        if (User.existsByUsername(candidate)) {
            throw conflict("username_taken", "Username is already taken");
        }

        user.username = candidate;

        try {
            flushEntityManager();
        } catch (PersistenceException exception) {
            if (isUniqueUsernameConflict(exception)) {
                throw conflict("username_taken", "Username is already taken");
            }
            throw exception;
        }

        return user;
    }

    @Transactional
    public User uploadImage(String auth0Id, FileUpload fileUpload) {
        User user = User.findByAuth0Id(auth0Id).orElseThrow(NotFoundException::new);
        user.avatarUrl = fileStorageService.saveImage(fileUpload, "users/avatars");
        flushEntityManager();
        return user;
    }

    @Transactional
    public User uploadBanner(String auth0Id, FileUpload fileUpload) {
        User user = User.findByAuth0Id(auth0Id).orElseThrow(NotFoundException::new);
        user.bannerUrl = fileStorageService.saveImage(fileUpload, "users/banners");
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

    private User ensureViewable(User user, String auth0Id) {
        boolean isOwner = auth0Id != null && auth0Id.equals(user.auth0Id);
        if (!user.profilePublic && !isOwner) {
            throw new NotFoundException();
        }
        return user;
    }

    private String generateUniqueUsername(String displayName, String firstName, String lastName) {
        String base = UsernameGenerator.baseSlug(displayName, firstName, lastName);
        return UsernameGenerator.firstAvailable(base, User::existsByUsername);
    }

    private void flushEntityManager() {
        entityManager.get().flush();
    }

    private static WebApplicationException badRequest(String error, String message) {
        return new WebApplicationException(
                Response.status(Response.Status.BAD_REQUEST)
                        .entity(new ApiErrorResponse(error, message))
                        .type(MediaType.APPLICATION_JSON_TYPE)
                        .build());
    }

    private static WebApplicationException conflict(String error, String message) {
        return new WebApplicationException(
                Response.status(Response.Status.CONFLICT)
                        .entity(new ApiErrorResponse(error, message))
                        .type(MediaType.APPLICATION_JSON_TYPE)
                        .build());
    }

    private boolean isUniqueAuth0Conflict(Throwable throwable) {
        return matchesMessage(throwable, "users_auth0_id_unique");
    }

    private boolean isUniqueUsernameConflict(Throwable throwable) {
        return matchesMessage(throwable, "users_username_unique")
                || matchesMessage(throwable, "users_username_lower_idx");
    }

    private boolean matchesMessage(Throwable throwable, String needle) {
        Throwable current = throwable;
        while (current != null) {
            String message = current.getMessage();
            if (message != null && message.contains(needle)) {
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
