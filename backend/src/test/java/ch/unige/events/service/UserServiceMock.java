package ch.unige.events.service;

import ch.unige.events.dto.ApiErrorResponse;
import ch.unige.events.dto.user.UpdateProfileRequest;
import ch.unige.events.entity.User;
import ch.unige.events.exception.InvalidFileTypeException;
import ch.unige.events.util.ImageFormat;
import ch.unige.events.util.UsernameGenerator;
import io.quarkus.security.identity.SecurityIdentity;
import io.quarkus.test.Mock;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.OptimisticLockException;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.ForbiddenException;
import jakarta.ws.rs.NotAuthorizedException;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.jwt.JsonWebToken;
import org.jboss.resteasy.reactive.multipart.FileUpload;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Mock
@ApplicationScoped
public class UserServiceMock extends UserService {

    @Inject SecurityIdentity securityIdentity;

    private final Map<String, User> usersByAuth0Id = new ConcurrentHashMap<>();
    private final Map<UUID, User> usersById = new ConcurrentHashMap<>();
    public static volatile boolean forceForbiddenOnUpdate = false;
    public static volatile boolean forceConflictOnUpdate = false;
    public static volatile boolean forceBadMimeOnUpload = false;
    public static volatile boolean forceBadMimeOnBannerUpload = false;

    public void reset() {
        usersByAuth0Id.clear();
        usersById.clear();
        forceForbiddenOnUpdate = false;
        forceConflictOnUpdate = false;
        forceBadMimeOnUpload = false;
        forceBadMimeOnBannerUpload = false;
    }

    public User seedUser(String auth0Id, String email) {
        return seedUser(auth0Id, newJwt(auth0Id, Map.of("email", email)));
    }

    public User seedUser(String auth0Id, JsonWebToken jwt) {
        User user = newUser(auth0Id, jwt);
        usersByAuth0Id.put(auth0Id, user);
        usersById.put(user.id, user);
        return user;
    }

    @Override
    public User getOrCreateUser(String auth0Id, JsonWebToken jwt) {
        User existing = usersByAuth0Id.get(auth0Id);
        if (existing != null) {
            return existing;
        }

        String email = claim(jwt, "email");
        if (email == null) {
            throw new NotAuthorizedException("Email claim is required");
        }

        return usersByAuth0Id.computeIfAbsent(auth0Id, key -> {
            User user = newUser(key, jwt);
            usersById.put(user.id, user);
            return user;
        });
    }

    @Override
    public User getPublicProfile(UUID id, String auth0Id) {
        User user = usersById.get(id);
        if (user == null) {
            throw new NotFoundException();
        }
        return ensureViewableMock(user, auth0Id);
    }

    @Override
    public User getPublicProfileByUsername(String username, String auth0Id) {
        if (username == null || username.isBlank()) {
            throw new NotFoundException();
        }
        User user = findUserByUsername(username);
        if (user == null) {
            throw new NotFoundException();
        }
        return ensureViewableMock(user, auth0Id);
    }

    @Override
    public boolean usernameExists(String username) {
        return findUserByUsername(username) != null;
    }

    @Override
    public User updateMyUsername(String auth0Id, String rawUsername) {
        if (rawUsername == null) {
            throw badRequestError("username_invalid", "Username must not be null");
        }
        String candidate = rawUsername.trim().toLowerCase();
        if (!candidate.matches(User.USERNAME_PATTERN)) {
            throw badRequestError("username_invalid",
                    "Username must match " + User.USERNAME_PATTERN);
        }
        if (UsernameGenerator.isReserved(candidate)) {
            throw badRequestError("username_reserved", "This username is reserved");
        }
        User user = usersByAuth0Id.get(auth0Id);
        if (user == null) {
            throw new NotFoundException();
        }
        if (candidate.equals(user.username)) {
            return user;
        }
        User existing = findUserByUsername(candidate);
        if (existing != null && !existing.id.equals(user.id)) {
            throw conflictError("username_taken", "Username is already taken");
        }
        user.username = candidate;
        return user;
    }

    private User ensureViewableMock(User user, String auth0Id) {
        boolean isOwner = auth0Id != null && auth0Id.equals(user.auth0Id);
        if (!user.profilePublic && !isOwner) {
            throw new NotFoundException();
        }
        return user;
    }

    private User findUserByUsername(String username) {
        if (username == null) return null;
        String needle = username.toLowerCase();
        return usersById.values().stream()
                .filter(u -> u.username != null && u.username.equalsIgnoreCase(needle))
                .findFirst()
                .orElse(null);
    }

    private static WebApplicationException badRequestError(String error, String message) {
        return new WebApplicationException(
                Response.status(Response.Status.BAD_REQUEST)
                        .entity(new ApiErrorResponse(error, message))
                        .type(MediaType.APPLICATION_JSON_TYPE)
                        .build());
    }

    private static WebApplicationException conflictError(String error, String message) {
        return new WebApplicationException(
                Response.status(Response.Status.CONFLICT)
                        .entity(new ApiErrorResponse(error, message))
                        .type(MediaType.APPLICATION_JSON_TYPE)
                        .build());
    }

    @Override
    public User updateMyProfile(String authenticatedAuth0Id, String targetAuth0Id, UpdateProfileRequest req) {
        if (forceForbiddenOnUpdate) {
            throw new ForbiddenException("Cannot modify another user's profile");
        }
        if (forceConflictOnUpdate) {
            throw new OptimisticLockException("Profile was updated by another request. Please retry.");
        }
        if (!Objects.equals(authenticatedAuth0Id, targetAuth0Id)) {
            throw new ForbiddenException("Cannot modify another user's profile");
        }
        return updateMyProfile(targetAuth0Id, req);
    }

    @Override
    public User updateMyProfile(String auth0Id, UpdateProfileRequest req) {
        if (req == null) {
            throw new BadRequestException("Request body must not be null");
        }

        if (forceConflictOnUpdate) {
            throw new OptimisticLockException("Profile was updated by another request. Please retry.");
        }

        User user = usersByAuth0Id.get(auth0Id);
        if (user == null) {
            throw new NotFoundException();
        }

        if (req.displayName() != null) user.displayName = req.displayName();
        if (req.faculty() != null) user.faculty = req.faculty();
        if (req.studyLevel() != null) user.studyLevel = req.studyLevel();
        if (req.bio() != null) user.bio = req.bio();
        if (req.interests() != null) user.interests = req.interests();
        if (req.avatarUrl() != null) user.avatarUrl = req.avatarUrl();
        if (req.profilePublic() != null) user.profilePublic = req.profilePublic();

        return user;
    }

    @Override
    public User uploadImage(String auth0Id, FileUpload fileUpload) {
        if (forceBadMimeOnUpload) {
            throw new InvalidFileTypeException("File must be a JPEG, PNG, WebP or GIF image");
        }
        String ct = fileUpload.contentType();
        if (ct == null || !ImageFormat.MIME_TO_EXTENSION.containsKey(ct)) {
            throw new InvalidFileTypeException("File must be a JPEG, PNG, WebP or GIF image");
        }
        User user = usersByAuth0Id.get(auth0Id);
        if (user == null) {
            throw new NotFoundException();
        }
        user.avatarUrl = "/api/uploads/test-photo.jpg";
        return user;
    }

    @Override
    public User uploadBanner(String auth0Id, FileUpload fileUpload) {
        if (forceBadMimeOnBannerUpload) {
            throw new InvalidFileTypeException("File must be a JPEG, PNG, WebP or GIF image");
        }
        String ct = fileUpload.contentType();
        if (ct == null || !ImageFormat.MIME_TO_EXTENSION.containsKey(ct)) {
            throw new InvalidFileTypeException("File must be a JPEG, PNG, WebP or GIF image");
        }
        User user = usersByAuth0Id.get(auth0Id);
        if (user == null) {
            throw new NotFoundException();
        }
        user.bannerUrl = "/api/uploads/test-banner.jpg";
        return user;
    }

    @Override
    public User deleteBanner(String auth0Id) {
        User user = usersByAuth0Id.get(auth0Id);
        if (user == null) {
            throw new NotFoundException();
        }
        user.bannerUrl = null;
        return user;
    }

    private User newUser(String auth0Id, JsonWebToken jwt) {
        User user = new User();
        user.id = UUID.randomUUID();
        user.auth0Id = auth0Id;
        user.email = claim(jwt, "email");
        user.displayName = claim(jwt, "name");
        user.firstName = claim(jwt, "given_name");
        user.lastName = claim(jwt, "family_name");
        user.avatarUrl = claim(jwt, "picture");
        user.profilePublic = false;
        user.username = generateUsernameFromContext(user);
        return user;
    }

    private String generateUsernameFromContext(User user) {
        String base = UsernameGenerator.baseSlug(user.displayName, user.firstName, user.lastName);
        return UsernameGenerator.firstAvailable(base, this::usernameExists);
    }

    private String claim(JsonWebToken jwt, String claimName) {
        Object value = jwt == null ? null : jwt.getClaim(claimName);
        if (value == null && securityIdentity != null) {
            value = securityIdentity.getAttribute(claimName);
        }
        return value == null ? null : value.toString();
    }

    private JsonWebToken newJwt(String auth0Id, Map<String, Object> claims) {
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
}
