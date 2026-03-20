package ch.unige.events.service;

import ch.unige.events.dto.UpdateProfileRequest;
import ch.unige.events.entity.User;
import io.quarkus.oidc.UserInfo;
import io.quarkus.test.Mock;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.OptimisticLockException;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.ForbiddenException;
import jakarta.ws.rs.NotAuthorizedException;
import jakarta.ws.rs.NotFoundException;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Mock
@ApplicationScoped
public class UserServiceMock extends UserService {

    private final Map<String, User> usersByAuth0Id = new ConcurrentHashMap<>();
    private final Map<UUID, User> usersById = new ConcurrentHashMap<>();
    private volatile boolean forceForbiddenOnUpdate = false;
    private volatile boolean forceConflictOnUpdate = false;

    @Inject
    public UserServiceMock(Instance<EntityManager> entityManager) {
        super(entityManager);
    }

    public UserServiceMock() {
        super(null);
    }

    public void reset() {
        usersByAuth0Id.clear();
        usersById.clear();
        forceForbiddenOnUpdate = false;
        forceConflictOnUpdate = false;
    }

    public void setForceForbiddenOnUpdate(boolean forceForbiddenOnUpdate) {
        this.forceForbiddenOnUpdate = forceForbiddenOnUpdate;
    }

    public void setForceConflictOnUpdate(boolean forceConflictOnUpdate) {
        this.forceConflictOnUpdate = forceConflictOnUpdate;
    }

    public User seedUser(String auth0Id, String email) {
        UserInfo userInfo = new UserInfo("{\"email\": \"" + email + "\"}");
        return seedUser(auth0Id, userInfo);
    }

    public User seedUser(String auth0Id, UserInfo userInfo) {
        User user = newUser(auth0Id, userInfo);
        usersByAuth0Id.put(auth0Id, user);
        usersById.put(user.getId(), user);
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
            usersById.put(user.getId(), user);
            return user;
        });
    }

    @Override
    public User getPublicProfile(UUID id) {
        User user = usersById.get(id);
        if (user == null) {
            throw new NotFoundException();
        }
        if (!user.isProfilePublic()) {
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
        return super.updateMyProfile(authenticatedAuth0Id, targetAuth0Id, req);
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

        if (req.displayName() != null) user.setDisplayName(req.displayName());
        if (req.faculty() != null) user.setFaculty(req.faculty());
        if (req.studyLevel() != null) user.setStudyLevel(req.studyLevel());
        if (req.bio() != null) user.setBio(req.bio());
        if (req.interests() != null) user.setInterests(req.interests());
        if (req.avatarUrl() != null) user.setAvatarUrl(req.avatarUrl());
        if (req.isProfilePublic() != null) user.setProfilePublic(req.isProfilePublic());

        return user;
    }

    private User newUser(String auth0Id, UserInfo userInfo) {
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setAuth0Id(auth0Id);
        user.setEmail(userInfo.getEmail());
        user.setDisplayName(userInfo.getName());
        user.setFirstName(userInfo.getString("given_name"));
        user.setLastName(userInfo.getFamilyName());
        user.setAdmin(false);
        user.setProfilePublic(false);
        user.setCreatedAt(LocalDateTime.now());
        user.setVersion(0L);
        return user;
    }
}
