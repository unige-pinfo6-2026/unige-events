package ch.unige.events.service;

import ch.unige.events.dto.ApiErrorResponse;
import ch.unige.events.entity.Follow;
import ch.unige.events.entity.FollowStatus;
import ch.unige.events.entity.User;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.util.List;
import java.util.UUID;

@ApplicationScoped
public class FollowService {

    @Inject EntityManager entityManager;

    // ── Lectures (non-transactional, cf. spec décision 23) ─────────────────────

    public long countFollowers(UUID userId) {
        return Follow.countFollowersOf(userId);
    }

    public long countFollowing(UUID userId) {
        return Follow.countFollowingOf(userId);
    }

    /**
     * État du follow (callerId → targetId), ou null si aucune row.
     * Retourne null pour caller == target (cf. spec décision 13).
     */
    public FollowStatus getStatusBetween(UUID callerId, UUID targetId) {
        if (callerId == null || callerId.equals(targetId)) {
            return null;
        }
        return Follow.findByFollowerAndFollowed(callerId, targetId)
                .map(f -> f.status)
                .orElse(null);
    }

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

    // ── Mutations (toutes @Transactional) ──────────────────────────────────────

    @Transactional
    public Follow follow(String followerAuth0Id, UUID followedId) {
        UUID followerId = resolveUserId(followerAuth0Id);
        if (followerId.equals(followedId)) {
            throw unprocessable("cannot_follow_self", "You cannot follow yourself.");
        }

        User followed = (User) User.findByIdOptional(followedId)
                .orElseThrow(() -> new NotFoundException("Target user not found"));

        if (Follow.findByFollowerAndFollowed(followerId, followedId).isPresent()) {
            throw conflict("already_following", "You are already following this user.");
        }

        Follow row = new Follow();
        row.followerId = followerId;
        row.followedId = followedId;
        row.status = followed.profilePublic ? FollowStatus.ACCEPTED : FollowStatus.PENDING;
        row.persist();
        entityManager.flush();
        return row;
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

    private UUID resolveUserId(String auth0Id) {
        return User.<User>find("auth0Id", auth0Id)
                .firstResultOptional()
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
