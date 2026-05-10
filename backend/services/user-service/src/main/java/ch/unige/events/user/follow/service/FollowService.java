package ch.unige.events.user.follow.service;

import ch.unige.events.shared.error.ApiErrorResponse;
import ch.unige.events.user.follow.entity.Follow;
import ch.unige.events.shared.domain.enums.FollowStatus;
import ch.unige.events.user.entity.User;
import ch.unige.events.shared.kafka.events.FollowLifecycleEvent;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceException;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.util.List;
import java.util.UUID;

/**
 * Same contract as the legacy monolith's FollowService — read methods
 * non-transactional (cf. spec décision 23), mutations all
 * {@code @Transactional}, race-on-create translated to 409
 * {@code already_following}.
 */
@ApplicationScoped
public class FollowService {

    @Inject EntityManager entityManager;
    @Inject jakarta.enterprise.event.Event<FollowLifecycleEvent> lifecycleEvent;

    public List<Follow> getFollowers(UUID userId, int page, int size) {
        return Follow.findFollowersOf(userId, page, size);
    }

    public List<Follow> getFollowing(UUID userId, int page, int size) {
        return Follow.findFollowingOf(userId, page, size);
    }

    public List<Follow> getPendingRequests(String auth0Id, int page, int size) {
        UUID me = resolveUserId(auth0Id);
        return Follow.findPendingRequestsFor(me, page, size);
    }

    @Transactional
    public Follow follow(String followerAuth0Id, UUID followedId) {
        UUID followerId = resolveUserId(followerAuth0Id);
        if (followerId.equals(followedId)) {
            throw unprocessable("cannot_follow_self", "You cannot follow yourself.");
        }

        User followed = User.<User>findByIdOptional(followedId)
                .orElseThrow(() -> new NotFoundException("Target user not found"));

        if (Follow.findByFollowerAndFollowed(followerId, followedId).isPresent()) {
            throw conflict("already_following", "You are already following this user.");
        }

        Follow row = new Follow();
        row.followerId = followerId;
        row.followedId = followedId;
        row.status = followed.profilePublic ? FollowStatus.ACCEPTED : FollowStatus.PENDING;
        try {
            row.persist();
            entityManager.flush();
        } catch (PersistenceException exception) {
            // Race-case: another concurrent request inserted the same
            // (followerId, followedId) row between our pre-check and our flush,
            // tripping uq_follow_follower_followed. Translate to 409 already_following
            // for a coherent envelope.
            if (isUniqueFollowConflict(exception)) {
                throw conflict("already_following", "You are already following this user.");
            }
            throw exception;
        }
        // CDI fire — bridge publishes to Kafka AFTER_SUCCESS.
        FollowLifecycleEvent ev = (row.status == FollowStatus.ACCEPTED)
                ? FollowLifecycleEvent.followed(followerId, followedId)
                : FollowLifecycleEvent.followRequested(followerId, followedId);
        lifecycleEvent.fire(ev);
        return row;
    }

    private static boolean isUniqueFollowConflict(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            String message = current.getMessage();
            if (message != null && message.contains("uq_follow_follower_followed")) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    @Transactional
    public void unfollow(String followerAuth0Id, UUID followedId) {
        UUID followerId = resolveUserId(followerAuth0Id);
        Follow.findByFollowerAndFollowed(followerId, followedId)
                .ifPresent(Follow::delete);
    }

    @Transactional
    public Follow acceptRequest(String targetAuth0Id, Long followId) {
        UUID targetUserId = resolveUserId(targetAuth0Id);
        Follow row = Follow.<Follow>findByIdOptional(followId)
                .orElseThrow(() -> new NotFoundException("Follow request not found"));
        if (!row.followedId.equals(targetUserId)) {
            throw forbidden("forbidden", "Only the target of the follow request can accept it.");
        }
        if (row.status != FollowStatus.PENDING) {
            throw conflict("invalid_transition",
                    "Follow is already in status " + row.status + " — only PENDING follows can be accepted.");
        }
        row.status = FollowStatus.ACCEPTED;
        // CDI fire — bridge publishes users.follow-accepted AFTER_SUCCESS.
        lifecycleEvent.fire(FollowLifecycleEvent.followAccepted(row.followerId, row.followedId));
        return row;
    }

    @Transactional
    public void rejectRequest(String targetAuth0Id, Long followId) {
        UUID targetUserId = resolveUserId(targetAuth0Id);
        Follow row = Follow.<Follow>findByIdOptional(followId)
                .orElseThrow(() -> new NotFoundException("Follow request not found"));
        if (!row.followedId.equals(targetUserId)) {
            throw forbidden("forbidden", "Only the target of the follow request can reject it.");
        }
        if (row.status != FollowStatus.PENDING) {
            throw conflict("invalid_transition",
                    "Follow is already in status " + row.status + " — only PENDING follows can be rejected.");
        }
        row.delete();
    }

    /**
     * ISSUE-93 anti-oracle visibility check : 404 when the target profile
     * is private and the caller isn't the owner. Same envelope as
     * "user does not exist" — closes the existence oracle.
     *
     * <p>ISSUE-93 anti-oracle check inline: user-service hosts both Follow
     * and User entities post-finalization (Étape 2.3 — cf.
     * sprint-context.md § Étape 23), so this lookup is a local query, not
     * a REST call.
     */
    public void assertProfileVisible(UUID targetId, String callerAuth0Id) {
        User target = User.<User>findByIdOptional(targetId)
                .orElseThrow(NotFoundException::new);
        boolean isOwner = callerAuth0Id != null && callerAuth0Id.equals(target.auth0Id);
        if (!target.profilePublic && !isOwner) {
            throw new NotFoundException();
        }
    }

    private UUID resolveUserId(String auth0Id) {
        return User.findByAuth0Id(auth0Id)
                .orElseThrow(() -> new NotFoundException("User profile not found"))
                .id;
    }

    protected static WebApplicationException conflict(String error, String message) {
        return new WebApplicationException(
                Response.status(Response.Status.CONFLICT)
                        .entity(new ApiErrorResponse(error, message))
                        .type(MediaType.APPLICATION_JSON_TYPE)
                        .build());
    }

    protected static WebApplicationException unprocessable(String error, String message) {
        return new WebApplicationException(
                Response.status(422)
                        .entity(new ApiErrorResponse(error, message))
                        .type(MediaType.APPLICATION_JSON_TYPE)
                        .build());
    }

    protected static WebApplicationException forbidden(String error, String message) {
        return new WebApplicationException(
                Response.status(Response.Status.FORBIDDEN)
                        .entity(new ApiErrorResponse(error, message))
                        .type(MediaType.APPLICATION_JSON_TYPE)
                        .build());
    }
}
