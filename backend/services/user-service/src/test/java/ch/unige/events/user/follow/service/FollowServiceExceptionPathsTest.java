package ch.unige.events.user.follow.service;

import ch.unige.events.shared.kafka.events.FollowLifecycleEvent;
import ch.unige.events.user.entity.User;
import ch.unige.events.user.follow.entity.Follow;

import io.quarkus.panache.mock.PanacheMock;
import io.quarkus.test.TestTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.enterprise.event.Event;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceException;
import jakarta.ws.rs.WebApplicationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Coverage suite for the flush-time {@link PersistenceException} branch of
 * {@link FollowService#follow} (the race-on-create that trips
 * {@code uq_follow_follower_followed}) and the {@code isUniqueFollowConflict}
 * helper it drives. The pre-check normally wins this race against the real
 * datasource, so the service is wired by hand with a mocked
 * {@link EntityManager} whose {@code flush()} throws, and the Panache statics
 * are stubbed via {@link PanacheMock}. {@code @QuarkusTest} so the executed
 * service bytecode is captured by quarkus-jacoco.
 */
@QuarkusTest
class FollowServiceExceptionPathsTest {

    private FollowService service;
    private EntityManager em;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        service = new FollowService();
        em = mock(EntityManager.class);
        service.entityManager = em;
        service.lifecycleEvent = mock(Event.class);
    }

    private UUID stage(String followerAuth0, boolean followedPublic) {
        UUID followerId = UUID.randomUUID();
        UUID followedId = UUID.randomUUID();
        User follower = new User();
        follower.id = followerId;
        follower.auth0Id = followerAuth0;
        User followed = new User();
        followed.id = followedId;
        followed.profilePublic = followedPublic;
        when(User.findByAuth0Id(followerAuth0)).thenReturn(Optional.of(follower));
        when(User.<User>findByIdOptional(followedId)).thenReturn(Optional.of(followed));
        when(Follow.findByFollowerAndFollowed(followerId, followedId)).thenReturn(Optional.empty());
        return followedId;
    }

    @Test
    @TestTransaction
    void follow_uniqueConstraintRace_throws409_alreadyFollowing() {
        PanacheMock.mock(User.class);
        PanacheMock.mock(Follow.class);
        UUID followedId = stage("auth0|fe-race", true);
        doThrow(new PersistenceException(
                "duplicate key value violates unique constraint \"uq_follow_follower_followed\""))
                .when(em).flush();

        WebApplicationException ex = assertThrows(WebApplicationException.class,
                () -> service.follow("auth0|fe-race", followedId));
        assertEquals(409, ex.getResponse().getStatus());
        // The Kafka fire is reached only after a successful flush — never here.
        verify(service.lifecycleEvent, never()).fire(any(FollowLifecycleEvent.class));
    }

    @Test
    @TestTransaction
    void follow_unrelatedPersistenceException_rethrows() {
        PanacheMock.mock(User.class);
        PanacheMock.mock(Follow.class);
        UUID followedId = stage("auth0|fe-boom", false);
        PersistenceException boom = new PersistenceException("statement timeout");
        doThrow(boom).when(em).flush();

        PersistenceException thrown = assertThrows(PersistenceException.class,
                () -> service.follow("auth0|fe-boom", followedId));
        assertSame(boom, thrown);
    }
}
