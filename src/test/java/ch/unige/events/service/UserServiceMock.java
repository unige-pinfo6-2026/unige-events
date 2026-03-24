package ch.unige.events.service;

import ch.unige.events.dto.user.UpdateProfileRequest;
import ch.unige.events.entity.User;
import io.quarkus.oidc.UserInfo;
import io.quarkus.test.Mock;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.OptimisticLockException;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.ForbiddenException;
import jakarta.ws.rs.NotAuthorizedException;
import jakarta.ws.rs.NotFoundException;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Mock
@ApplicationScoped
public class UserServiceMock extends UserService {

    private final Map<String, User> usersByAuth0Id = new ConcurrentHashMap<>();
    private final Map<UUID, User> usersById = new ConcurrentHashMap<>();
    public static volatile boolean forceForbiddenOnUpdate = false;
    public static volatile boolean forceConflictOnUpdate = false;

    public void reset() {
        usersByAuth0Id.clear();
        usersById.clear();
        forceForbiddenOnUpdate = false;
        forceConflictOnUpdate = false;
    }

    public User seedUser(String auth0Id, String email) {
        UserInfo userInfo = new UserInfo("{\"email\": \"" + email + "\"}");
        return seedUser(auth0Id, userInfo);
    }

    public User seedUser(String auth0Id, UserInfo userInfo) {
        User user = newUser(auth0Id, userInfo);
        usersByAuth0Id.put(auth0Id, user);
        usersById.put(user.id, user);
        return user;
    }

    @Override
    public User getOrCreateUser(String auth0Id, UserInfo userInfo) {
        User existing = usersByAuth0Id.get(auth0Id);
        if (existing != null) {
            return existing;
        }

        if (userInfo == null || userInfo.getEmail() == null) {
            throw new NotAuthorizedException("Email claim is required");
        }

        return usersByAuth0Id.computeIfAbsent(auth0Id, key -> {
            User user = newUser(key, userInfo);
            usersById.put(user.id, user);
            return user;
        });
    }

    @Override
    public User getPublicProfile(UUID id) {
        User user = usersById.get(id);
        if (user == null) {
            throw new NotFoundException();
        }
        if (!user.profilePublic) {
            throw new ForbiddenException("This profile is private");
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

    private User newUser(String auth0Id, UserInfo userInfo) {
        User user = new User();
        user.id = UUID.randomUUID();
        user.auth0Id = auth0Id;
        user.email = userInfo.getEmail();
        user.displayName = userInfo.getName();
        user.firstName = userInfo.getString("given_name");
        user.lastName = userInfo.getFamilyName();
        user.profilePublic = false;
        return user;
    }
}
