package ch.unige.events.user.resource;

import ch.unige.events.user.dto.PublicProfileView;
import ch.unige.events.user.dto.UpdateProfileRequest;
import ch.unige.events.user.dto.UserProfileResponse;
import ch.unige.events.user.dto.UserPublicResponse;
import ch.unige.events.user.entity.User;
import ch.unige.events.user.service.UserService;
import io.quarkus.security.Authenticated;
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.annotation.security.PermitAll;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.jwt.JsonWebToken;

import java.util.UUID;

/**
 * In S8 soft-extraction mode this resource exposes only :
 *   - GET  /users/me        — self profile (auto-creates from JWT claims)
 *   - PUT  /users/me        — partial self update
 *   - GET  /users/{id}      — public profile (anti-oracle 404)
 *
 * <p>The image / banner upload endpoints (POST/DELETE /users/me/image,
 * /users/me/banner) stay on legacy-monolith because their
 * FileStorageService dep + S3 wiring lives there. A follow-up cleanup
 * after PR 13 (event-service ships FileStorageService) will migrate
 * them here.
 *
 * <p>The legacy {@code @PerUserRateLimit("users.updateMe", max=10)} on
 * PUT is intentionally not duplicated in S8 (cf. PR 3 commit message).
 */
@Path("/users")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class UserResource {

    @Inject SecurityIdentity identity;
    @Inject UserService userService;
    /**
     * Lazy via {@link Instance} so the sentinel {@code @QuarkusTest}
     * (running with {@code quarkus.oidc.enabled=false}) doesn't have to
     * resolve the JsonWebToken bean at startup. legacy-monolith pulls
     * {@code quarkus-junit-mockito} which provides a JWT mock at test
     * time ; we don't add that dep to the scaffolds, so we lazy-inject
     * via Instance.
     */
    @Inject Instance<JsonWebToken> jwt;

    @GET
    @Path("/{id}")
    @PermitAll
    public Response getProfile(@PathParam("id") UUID id) {
        boolean anonymous = identity.isAnonymous();
        String auth0Id = anonymous ? null : identity.getPrincipal().getName();
        PublicProfileView view = userService.getPublicProfile(id, auth0Id);
        UserPublicResponse body = anonymous
                ? UserPublicResponse.fromAnonymous(view.user())
                : UserPublicResponse.from(
                        view.user(),
                        view.followerCount(),
                        view.followingCount(),
                        view.followStatus()
                  );
        return Response.ok(body).build();
    }

    @GET
    @Path("/me")
    @Authenticated
    public UserProfileResponse me() {
        String auth0Id = identity.getPrincipal().getName();
        User user = userService.getOrCreateUser(auth0Id, jwt.get());
        return UserProfileResponse.from(user);
    }

    @PUT
    @Path("/me")
    @Authenticated
    public Response updateMe(@Valid UpdateProfileRequest req) {
        String auth0Id = identity.getPrincipal().getName();
        User updated = userService.updateMyProfile(auth0Id, auth0Id, req);
        return Response.ok(UserProfileResponse.from(updated)).build();
    }
}
