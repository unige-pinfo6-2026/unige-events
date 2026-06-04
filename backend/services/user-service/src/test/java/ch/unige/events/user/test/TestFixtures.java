package ch.unige.events.user.test;

import ch.unige.events.shared.domain.enums.Faculty;
import ch.unige.events.shared.domain.enums.FollowStatus;
import ch.unige.events.user.entity.User;
import ch.unige.events.user.follow.entity.Follow;
import ch.unige.events.user.service.UsernameGenerator;

import io.quarkus.arc.Unremovable;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.transaction.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Test-only CDI bean that persists fixtures in a {@code @Transactional}
 * boundary that COMMITS — required for REST-Assured-driven resource
 * tests where the HTTP request runs in a separate transaction (the
 * outer test method does NOT use {@code @TestTransaction} since data
 * needs to be visible to the request handler).
 *
 * <p>Cleanup is best-effort via {@link #truncateAll()} — call it from
 * {@code @AfterEach} to keep tests isolated.
 */
@ApplicationScoped
@Unremovable
public class TestFixtures {

    @PersistenceContext
    EntityManager em;

    @Transactional
    public User persistUser(String auth0Id, String email, boolean profilePublic) {
        // SCRUM-169 — derive a unique username from the email local-part so
        // existing tests that don't care about username keep working out of
        // the box. Tests that DO care can call the
        // {@link #persistUser(String, String, boolean, String)} overload.
        String localPart = email.contains("@") ? email.substring(0, email.indexOf('@')) : email;
        String base = UsernameGenerator.slugify(null, localPart, null);
        String username = UsernameGenerator.resolveAvailable(
                base,
                candidate -> User.findByUsername(candidate).isPresent()
        );
        return persistUser(auth0Id, email, profilePublic, username);
    }

    @Transactional
    public User persistUser(String auth0Id, String email, boolean profilePublic, String username) {
        User user = new User();
        user.auth0Id = auth0Id;
        user.email = email;
        user.username = username;
        user.profilePublic = profilePublic;
        user.createdAt = LocalDateTime.of(2025, 1, 1, 12, 0);
        em.persist(user);
        em.flush();
        return user;
    }

    @Transactional
    public Follow persistFollow(UUID followerId, UUID followedId, FollowStatus status) {
        Follow f = new Follow();
        f.followerId = followerId;
        f.followedId = followedId;
        f.status = status;
        f.createdAt = LocalDateTime.of(2025, 1, 1, 12, 0);
        em.persist(f);
        em.flush();
        return f;
    }

    /**
     * Sets the cover/profile detail fields on an already-persisted user so REST
     * tests can assert which fields each projection exposes (e.g. the locked
     * projection keeps {@code bannerUrl} but strips {@code bio} / {@code
     * faculty}). Commits in its own transaction like the other fixtures.
     */
    @Transactional
    public void setProfileDetails(UUID userId, String bannerUrl, String bio, Faculty faculty) {
        User user = em.find(User.class, userId);
        if (user != null) {
            user.bannerUrl = bannerUrl;
            user.bio = bio;
            user.faculty = faculty;
            em.flush();
        }
    }

    @Transactional
    public void setCalendarToken(UUID userId, UUID token) {
        User user = em.find(User.class, userId);
        if (user != null) {
            user.calendarToken = token;
            em.flush();
        }
    }

    @Transactional
    public void truncateAll() {
        // Cascade-friendly order: follows first (FK refs to users), then users.
        em.createQuery("delete from Follow").executeUpdate();
        em.createQuery("delete from User").executeUpdate();
    }
}
