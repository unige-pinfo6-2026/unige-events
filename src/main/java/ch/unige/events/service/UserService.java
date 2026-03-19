package ch.unige.events.service;

import ch.unige.events.dto.UpdateProfileRequest;
import ch.unige.events.entity.User;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.ForbiddenException;
import jakarta.ws.rs.NotFoundException;
import java.util.Objects;
import java.util.UUID;

@ApplicationScoped
public class UserService {

    /**
     * Appelé à chaque requête authentifiée.
     * Crée le profil si c'est la 1ère connexion.
     */
    @Transactional
    public User getOrCreateUser(String auth0Id, String email) {
        return User.findByAuth0Id(auth0Id).orElseGet(() -> {
            User newUser = new User();
            newUser.auth0Id = auth0Id;
            newUser.email = email;
            newUser.isProfilePublic = false;
            newUser.isAdmin = false;
            newUser.persist();
            return newUser;
        });
    }

    public User getPublicProfile(UUID id) {
        User user = (User) User.findByIdOptional(id)
            .orElseThrow(NotFoundException::new);

        if (!user.isProfilePublic) {
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

        if (req.displayName()    != null) user.displayName    = req.displayName();
        if (req.faculty()        != null) user.faculty        = req.faculty();
        if (req.studyLevel()     != null) user.studyLevel     = req.studyLevel();
        if (req.bio()            != null) user.bio            = req.bio();
        if (req.interests()      != null) user.interests      = req.interests();
        if (req.avatarUrl()      != null) user.avatarUrl      = req.avatarUrl();
        if (req.isProfilePublic()!= null) user.isProfilePublic= req.isProfilePublic();

        return user;
    }
}
