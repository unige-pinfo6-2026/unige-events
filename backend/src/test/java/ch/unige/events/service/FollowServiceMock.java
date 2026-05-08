package ch.unige.events.service;

import ch.unige.events.entity.Follow;
import ch.unige.events.entity.FollowStatus;
import io.quarkus.test.Mock;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.NotFoundException;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Test mock for {@link FollowService} — pattern aligné sur {@code FavoriteServiceMock}
 * avec des flags volatile pour piloter les rejets attendus dans les tests Resource.
 */
@Mock
@ApplicationScoped
public class FollowServiceMock extends FollowService {

    private final Map<Long, Follow> rowsById = new ConcurrentHashMap<>();
    private final AtomicLong idSequence = new AtomicLong(1);

    public static volatile boolean forceConflictOnFollow = false;
    public static volatile boolean forceUnprocessableOnFollow = false;
    public static volatile boolean forceForbiddenOnAccept = false;
    public static volatile boolean forceInvalidTransitionOnAccept = false;
    public static volatile boolean forceForbiddenOnReject = false;
    public static volatile boolean forceInvalidTransitionOnReject = false;
    public static volatile boolean forceNotFoundOnTarget = false;
    public static volatile boolean forceNotFoundOnAccept = false;
    public static volatile boolean forceNotFoundOnReject = false;
    public static volatile FollowStatus nextCreateStatus = FollowStatus.ACCEPTED;
    public static volatile UUID nextCreateFollowerId = null;

    public void reset() {
        rowsById.clear();
        idSequence.set(1);
        forceConflictOnFollow = false;
        forceUnprocessableOnFollow = false;
        forceForbiddenOnAccept = false;
        forceInvalidTransitionOnAccept = false;
        forceForbiddenOnReject = false;
        forceInvalidTransitionOnReject = false;
        forceNotFoundOnTarget = false;
        forceNotFoundOnAccept = false;
        forceNotFoundOnReject = false;
        nextCreateStatus = FollowStatus.ACCEPTED;
        nextCreateFollowerId = null;
    }

    public Follow seedFollow(UUID followerId, UUID followedId, FollowStatus status) {
        Follow f = new Follow();
        f.id = idSequence.getAndIncrement();
        f.followerId = followerId;
        f.followedId = followedId;
        f.status = status;
        f.createdAt = LocalDateTime.now();
        rowsById.put(f.id, f);
        return f;
    }

    @Override
    public Follow follow(String followerAuth0Id, UUID followedId) {
        if (forceUnprocessableOnFollow) {
            throw FollowService.unprocessable("cannot_follow_self", "You cannot follow yourself.");
        }
        if (forceConflictOnFollow) {
            throw FollowService.conflict("already_following", "You are already following this user.");
        }
        if (forceNotFoundOnTarget) {
            throw new NotFoundException("Target user not found");
        }
        UUID followerId = nextCreateFollowerId != null ? nextCreateFollowerId : UUID.randomUUID();
        return seedFollow(followerId, followedId, nextCreateStatus);
    }

    @Override
    public void unfollow(String followerAuth0Id, UUID followedId) {
        // Idempotent — no-op even if no row matches.
    }

    @Override
    public Follow acceptRequest(String targetAuth0Id, Long followId) {
        if (forceForbiddenOnAccept) {
            throw FollowService.forbidden("forbidden",
                    "Only the target of the follow request can accept it.");
        }
        if (forceNotFoundOnAccept) {
            throw new NotFoundException("Follow request not found");
        }
        Follow f = rowsById.get(followId);
        if (f == null) {
            throw new NotFoundException("Follow request not found");
        }
        if (forceInvalidTransitionOnAccept) {
            throw FollowService.conflict("invalid_transition",
                    "Follow is already in status ACCEPTED — only PENDING follows can be accepted.");
        }
        f.status = FollowStatus.ACCEPTED;
        return f;
    }

    @Override
    public void rejectRequest(String targetAuth0Id, Long followId) {
        if (forceForbiddenOnReject) {
            throw FollowService.forbidden("forbidden",
                    "Only the target of the follow request can reject it.");
        }
        if (forceNotFoundOnReject) {
            throw new NotFoundException("Follow request not found");
        }
        Follow f = rowsById.get(followId);
        if (f == null) {
            throw new NotFoundException("Follow request not found");
        }
        if (forceInvalidTransitionOnReject) {
            throw FollowService.conflict("invalid_transition",
                    "Follow is already in status ACCEPTED — only PENDING follows can be rejected.");
        }
        rowsById.remove(followId);
    }

    @Override
    public long countFollowers(UUID userId) {
        return rowsById.values().stream()
                .filter(f -> f.followedId.equals(userId) && f.status == FollowStatus.ACCEPTED)
                .count();
    }

    @Override
    public long countFollowing(UUID userId) {
        return rowsById.values().stream()
                .filter(f -> f.followerId.equals(userId) && f.status == FollowStatus.ACCEPTED)
                .count();
    }

    @Override
    public FollowStatus getStatusBetween(UUID callerId, UUID targetId) {
        if (callerId == null || callerId.equals(targetId)) {
            return null;
        }
        return rowsById.values().stream()
                .filter(f -> f.followerId.equals(callerId) && f.followedId.equals(targetId))
                .map(f -> f.status)
                .findFirst()
                .orElse(null);
    }

    @Override
    public List<Follow> getFollowers(UUID userId, int page, int size) {
        return paginated(
                rowsById.values().stream()
                        .filter(f -> f.followedId.equals(userId) && f.status == FollowStatus.ACCEPTED)
                        .toList(),
                page, size);
    }

    @Override
    public List<Follow> getFollowing(UUID userId, int page, int size) {
        return paginated(
                rowsById.values().stream()
                        .filter(f -> f.followerId.equals(userId) && f.status == FollowStatus.ACCEPTED)
                        .toList(),
                page, size);
    }

    @Override
    public List<Follow> getPendingRequests(String auth0Id, int page, int size) {
        // The mock cannot resolve auth0Id → UUID — tests that call this endpoint must
        // seed fixtures that match. For now: return all PENDING rows paginated.
        return paginated(
                rowsById.values().stream()
                        .filter(f -> f.status == FollowStatus.PENDING)
                        .toList(),
                page, size);
    }

    private List<Follow> paginated(List<Follow> rows, int page, int size) {
        List<Follow> out = new ArrayList<>(rows);
        int from = Math.min(page * size, out.size());
        int to = Math.min(from + size, out.size());
        return out.subList(from, to);
    }
}
