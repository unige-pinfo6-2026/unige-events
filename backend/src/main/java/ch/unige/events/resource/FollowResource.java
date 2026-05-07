package ch.unige.events.resource;

import ch.unige.events.config.PerUserRateLimit;
import ch.unige.events.dto.follow.FollowDTO;
import ch.unige.events.dto.user.UserPublicResponse;
import ch.unige.events.entity.Follow;
import ch.unige.events.entity.User;
import ch.unige.events.service.FollowService;
import ch.unige.events.service.UserService;

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
import jakarta.ws.rs.PATCH;
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

@Path("/")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class FollowResource {

    @Inject SecurityIdentity identity;
    @Inject FollowService followService;
    @Inject UserService userService;

    // ── POST /users/{id}/follow ────────────────────────────────────────────────

    @POST
    @Path("/users/{id}/follow")
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
    @Path("/users/{id}/follow")
    @Authenticated
    @Consumes(MediaType.WILDCARD)
    public Response unfollow(@PathParam("id") UUID followedId) {
        String auth0Id = identity.getPrincipal().getName();
        followService.unfollow(auth0Id, followedId);
        return Response.noContent().build();
    }

    // ── GET /users/{id}/followers | /following ─────────────────────────────────

    @GET
    @Path("/users/{id}/followers")
    @Authenticated
    public List<UserPublicResponse> getFollowers(
            @PathParam("id") UUID userId,
            @QueryParam("page") @DefaultValue("0") @Min(0) int page,
            @QueryParam("size") @DefaultValue("20") @Positive @Max(100) int size) {
        // Garde 404 anti-oracle alignée ISSUE-93 : NotFoundException si profil non visible.
        String auth0Id = identity.getPrincipal().getName();
        userService.getPublicProfile(userId, auth0Id);

        List<Follow> rows = followService.getFollowers(userId, page, size);
        return resolveUsers(rows.stream().map(f -> f.followerId).toList());
    }

    @GET
    @Path("/users/{id}/following")
    @Authenticated
    public List<UserPublicResponse> getFollowing(
            @PathParam("id") UUID userId,
            @QueryParam("page") @DefaultValue("0") @Min(0) int page,
            @QueryParam("size") @DefaultValue("20") @Positive @Max(100) int size) {
        String auth0Id = identity.getPrincipal().getName();
        userService.getPublicProfile(userId, auth0Id);

        List<Follow> rows = followService.getFollowing(userId, page, size);
        return resolveUsers(rows.stream().map(f -> f.followedId).toList());
    }

    // ── GET /users/me/follow-requests ──────────────────────────────────────────

    @GET
    @Path("/users/me/follow-requests")
    @Authenticated
    public List<FollowDTO> getMyFollowRequests(
            @QueryParam("page") @DefaultValue("0") @Min(0) int page,
            @QueryParam("size") @DefaultValue("20") @Positive @Max(100) int size) {
        String auth0Id = identity.getPrincipal().getName();
        return followService.getPendingRequests(auth0Id, page, size).stream()
                .map(FollowDTO::from)
                .toList();
    }

    // ── PATCH /follow-requests/{followId}/accept | /reject ─────────────────────

    @PATCH
    @Path("/follow-requests/{followId}/accept")
    @Authenticated
    @Consumes(MediaType.WILDCARD)
    public FollowDTO acceptFollowRequest(@PathParam("followId") Long followId) {
        String auth0Id = identity.getPrincipal().getName();
        Follow row = followService.acceptRequest(auth0Id, followId);
        return FollowDTO.from(row);
    }

    @PATCH
    @Path("/follow-requests/{followId}/reject")
    @Authenticated
    @Consumes(MediaType.WILDCARD)
    public Response rejectFollowRequest(@PathParam("followId") Long followId) {
        String auth0Id = identity.getPrincipal().getName();
        followService.rejectRequest(auth0Id, followId);
        return Response.noContent().build();
    }

    // ── Helper interne ─────────────────────────────────────────────────────────

    /**
     * Résout en bulk les `User` correspondant aux UUIDs reçus (1 seule requête DB),
     * puis projette via {@code UserPublicResponse.from(User)} en préservant l'ordre
     * d'arrivée. Pattern aligné sur AttendanceService.getAttendees.
     */
    private List<UserPublicResponse> resolveUsers(List<UUID> ids) {
        if (ids.isEmpty()) {
            return List.of();
        }
        List<User> users = User.list("id in ?1", ids);
        Map<UUID, User> byId = new HashMap<>(users.size());
        users.forEach(u -> byId.put(u.id, u));
        return ids.stream()
                .map(byId::get)
                .filter(Objects::nonNull)
                .map(UserPublicResponse::from)
                .toList();
    }
}
