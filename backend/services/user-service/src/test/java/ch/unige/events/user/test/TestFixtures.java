package ch.unige.events.user.test;

import ch.unige.events.shared.domain.enums.FollowStatus;
import ch.unige.events.user.entity.User;
import ch.unige.events.user.follow.entity.Follow;

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
        User user = new User();
        user.auth0Id = auth0Id;
        user.email = email;
        user.profilePublic = profilePublic;
        user.createdAt = LocalDateTime.now();
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
        f.createdAt = LocalDateTime.now();
        em.persist(f);
        em.flush();
        return f;
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
