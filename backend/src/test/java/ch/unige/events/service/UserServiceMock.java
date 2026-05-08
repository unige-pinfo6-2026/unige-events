package ch.unige.events.service;

import ch.unige.events.dto.user.UpdateProfileRequest;
import ch.unige.events.entity.User;
import ch.unige.events.exception.FileTooLargeException;
import ch.unige.events.exception.InvalidFileTypeException;
import ch.unige.events.util.ImageFormat;
import io.quarkus.security.identity.SecurityIdentity;
import io.quarkus.test.Mock;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.OptimisticLockException;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.ForbiddenException;
import jakarta.ws.rs.NotAuthorizedException;
import jakarta.ws.rs.NotFoundException;
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
    public static volatile boolean forceFileTooLargeOnUpload = false;

    public void reset() {
        usersByAuth0Id.clear();
        usersById.clear();
        forceForbiddenOnUpdate = false;
        forceConflictOnUpdate = false;
        forceBadMimeOnUpload = false;
        forceBadMimeOnBannerUpload = false;
        forceFileTooLargeOnUpload = false;
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
        // Same rule as prod — hotfix pentest 4.1 (404 anti-oracle, self-case bypass).
        boolean isOwner = auth0Id != null && auth0Id.equals(user.auth0Id);
        if (!user.profilePublic && !isOwner) {
            throw new NotFoundException();
        }
        return user;
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
        if (forceFileTooLargeOnUpload) {
            throw new FileTooLargeException("File exceeds 2 MB limit");
        }
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
    public User deleteAvatar(String auth0Id) {
        User user = usersByAuth0Id.get(auth0Id);
        if (user == null) {
            throw new NotFoundException();
        }
        user.avatarUrl = null;
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
        return user;
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
