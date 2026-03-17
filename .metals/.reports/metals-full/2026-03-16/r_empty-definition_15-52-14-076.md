error id: file://<WORKSPACE>/src/main/java/ch/unige/events/service/UserService.java:_empty_/User#findByEmail#orElseThrow#
file://<WORKSPACE>/src/main/java/ch/unige/events/service/UserService.java
empty definition using pc, found symbol in pc: _empty_/User#findByEmail#orElseThrow#
empty definition using semanticdb
empty definition using fallback
non-local guesses:

offset: 1275
uri: file://<WORKSPACE>/src/main/java/ch/unige/events/service/UserService.java
text:
```scala
package ch.unige.events.service;

import ch.unige.events.dto.UpdateProfileRequest;
import ch.unige.events.entity.User;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.ForbiddenException;
import jakarta.ws.rs.NotFoundException;
import java.util.UUID;

@ApplicationScoped
public class UserService {

    /**
     * Appelé à chaque requête authentifiée.
     * Crée le profil si c'est la 1ère connexion.
     */
    @Transactional
    public User getOrCreateUser(String email) {
        return User.findByEmail(email).orElseGet(() -> {
            User newUser = new User();
            newUser.email = email;
            newUser.displayName = email.split("@")[0]; // valeur par défaut
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
    public User updateMyProfile(String email, UpdateProfileRequest req) {
        User user = User.findByEmail(email)
            .orEl@@seThrow(NotFoundException::new);

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

```


#### Short summary: 

empty definition using pc, found symbol in pc: _empty_/User#findByEmail#orElseThrow#