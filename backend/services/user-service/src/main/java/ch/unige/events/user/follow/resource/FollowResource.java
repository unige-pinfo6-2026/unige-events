package ch.unige.events.user.follow.resource;

import ch.unige.events.user.follow.dto.FollowDTO;
import ch.unige.events.user.dto.UserPublicResponse;
import ch.unige.events.user.follow.entity.Follow;
import ch.unige.events.user.entity.User;
import ch.unige.events.user.follow.service.FollowService;
import ch.unige.events.shared.ratelimit.PerUserRateLimit;

import io.quarkus.security.Authenticated;
import io.quarkus.security.identity.SecurityIdentity;

import jakarta.inject.Inject;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Follow endpoints rooted under {@code /users}. Accept/reject endpoints
 * live in {@link FollowRequestResource} (separate {@code /follow-requests}
 * root).
 *
 * <p>POST is rate-limited via {@link PerUserRateLimit} (issue #98) — same
 * name/budget as the legacy monolith.
 */
@Path("/users")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class FollowResource {

    @Inject SecurityIdentity identity;
    @Inject FollowService followService;

    @POST
    @Path("/{id}/follow")
    @Authenticated
    @Consumes(MediaType.WILDCARD)
    @PerUserRateLimit(name = "follows.follow", max = 30)
    public Response follow(@PathParam("id") UUID followedId) {
        String auth0Id = identity.getPrincipal().getName();
        Follow row = followService.follow(auth0Id, followedId);
        return Response.status(Response.Status.CREATED)
                .entity(FollowDTO.from(row))
                .build();
    }

    @DELETE
    @Path("/{id}/follow")
    @Authenticated
    @Consumes(MediaType.WILDCARD)
    public Response unfollow(@PathParam("id") UUID followedId) {
        String auth0Id = identity.getPrincipal().getName();
        followService.unfollow(auth0Id, followedId);
        return Response.noContent().build();
    }

    @DELETE
    @Path("/me/followers/{followerId}")
    @Authenticated
    @Consumes(MediaType.WILDCARD)
    public Response removeFollower(@PathParam("followerId") UUID followerId) {
        String auth0Id = identity.getPrincipal().getName();
        followService.removeFollower(auth0Id, followerId);
        return Response.noContent().build();
    }

    @GET
    @Path("/{id}/followers")
    @Authenticated
    public List<UserPublicResponse> getFollowers(
            @PathParam("id") UUID userId,
            @QueryParam("page") @DefaultValue("0") @Min(0) int page,
            @QueryParam("size") @DefaultValue("20") @Positive @Max(100) int size) {
        String auth0Id = identity.getPrincipal().getName();
        followService.assertProfileVisible(userId, auth0Id);

        List<Follow> rows = followService.getFollowers(userId, page, size);
        return resolveUsers(rows.stream().map(f -> f.followerId).toList(), auth0Id);
    }

    @GET
    @Path("/{id}/following")
    @Authenticated
    public List<UserPublicResponse> getFollowing(
            @PathParam("id") UUID userId,
            @QueryParam("page") @DefaultValue("0") @Min(0) int page,
            @QueryParam("size") @DefaultValue("20") @Positive @Max(100) int size) {
        String auth0Id = identity.getPrincipal().getName();
        followService.assertProfileVisible(userId, auth0Id);

        List<Follow> rows = followService.getFollowing(userId, page, size);
        return resolveUsers(rows.stream().map(f -> f.followedId).toList(), auth0Id);
    }

    @GET
    @Path("/me/follow-requests")
    @Authenticated
    public List<FollowDTO> getMyFollowRequests(
            @QueryParam("page") @DefaultValue("0") @Min(0) int page,
            @QueryParam("size") @DefaultValue("20") @Positive @Max(100) int size) {
        String auth0Id = identity.getPrincipal().getName();
        return followService.getPendingRequests(auth0Id, page, size).stream()
                .map(FollowDTO::from)
                .toList();
    }

    /**
     * Bulk-resolves the User rows matching {@code ids} (single DB query)
     * and projects them preserving arrival order. Mirror of the legacy
     * {@code FollowResource#resolveUsers} — pentest 4.1b anti-harvest
     * preserved (private profiles seen by non-self callers project via
     * {@link UserPublicResponse#fromAnonymous(User)}).
     */
    private List<UserPublicResponse> resolveUsers(List<UUID> ids, String callerAuth0Id) {
        if (ids.isEmpty()) {
            return List.of();
        }
        List<User> users = User.list("id in ?1", ids);
        Map<UUID, User> byId = new HashMap<>(users.size());
        users.forEach(u -> byId.put(u.id, u));
        return ids.stream()
                .map(byId::get)
                .filter(Objects::nonNull)
                .map(u -> projectListItem(u, callerAuth0Id))
                .toList();
    }

    private UserPublicResponse projectListItem(User user, String callerAuth0Id) {
        boolean isSelf = callerAuth0Id != null && callerAuth0Id.equals(user.auth0Id);
        if (user.profilePublic || isSelf) {
            return UserPublicResponse.from(user);
        }
        return UserPublicResponse.fromAnonymous(user);
    }
}
