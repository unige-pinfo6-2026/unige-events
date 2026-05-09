package ch.unige.events.user.resource;

import ch.unige.events.user.dto.PublicProfileView;
import ch.unige.events.user.dto.UpdateProfileRequest;
import ch.unige.events.user.dto.UserProfileResponse;
import ch.unige.events.user.dto.UserPublicResponse;
import ch.unige.events.user.entity.User;
import ch.unige.events.user.service.UserService;
import ch.unige.events.shared.ratelimit.PerUserRateLimit;
import io.quarkus.security.Authenticated;
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.annotation.security.PermitAll;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.jwt.JsonWebToken;
import org.jboss.resteasy.reactive.RestForm;
import org.jboss.resteasy.reactive.multipart.FileUpload;

import java.util.UUID;

/**
 * Endpoints :
 *   - GET    /users/me           — self profile (auto-creates from JWT claims)
 *   - PUT    /users/me           — partial self update
 *   - GET    /users/{id}         — public profile (anti-oracle 404)
 *   - POST   /users/me/image     — multipart upload, S3-backed avatar
 *   - DELETE /users/me/image     — clear avatar + S3 object
 *   - POST   /users/me/banner    — multipart upload, S3-backed banner
 *   - DELETE /users/me/banner    — clear banner (S3 object kept ; legacy parity)
 *
 * <p>PUT, POST /me/image and POST /me/banner carry {@link PerUserRateLimit}
 * decorations (issue #98) wired to the shared interceptor lib — same
 * names/budgets as the legacy monolith.
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
    @PerUserRateLimit(name = "users.updateMe", max = 10)
    public Response updateMe(@Valid UpdateProfileRequest req) {
        String auth0Id = identity.getPrincipal().getName();
        User updated = userService.updateMyProfile(auth0Id, auth0Id, req);
        return Response.ok(UserProfileResponse.from(updated)).build();
    }

    @POST
    @Path("/me/image")
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @Authenticated
    @PerUserRateLimit(name = "users.uploadImage", max = 5)
    public Response uploadImage(@RestForm("file") FileUpload file) {
        if (file == null) {
            throw new BadRequestException("Missing required form field: file");
        }
        String auth0Id = identity.getPrincipal().getName();
        User updated = userService.uploadImage(auth0Id, file);
        return Response.ok(UserProfileResponse.from(updated)).build();
    }

    @DELETE
    @Path("/me/image")
    @Authenticated
    @Consumes(MediaType.WILDCARD)
    public Response deleteImage() {
        String auth0Id = identity.getPrincipal().getName();
        User updated = userService.deleteAvatar(auth0Id);
        return Response.ok(UserProfileResponse.from(updated)).build();
    }

    @POST
    @Path("/me/banner")
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @Authenticated
    @PerUserRateLimit(name = "users.uploadBanner", max = 5)
    public Response uploadBanner(@RestForm("file") FileUpload file) {
        if (file == null) {
            throw new BadRequestException("Missing required form field: file");
        }
        String auth0Id = identity.getPrincipal().getName();
        User updated = userService.uploadBanner(auth0Id, file);
        return Response.ok(UserProfileResponse.from(updated)).build();
    }

    @DELETE
    @Path("/me/banner")
    @Authenticated
    @Consumes(MediaType.WILDCARD)
    public Response deleteBanner() {
        String auth0Id = identity.getPrincipal().getName();
        User updated = userService.deleteBanner(auth0Id);
        return Response.ok(UserProfileResponse.from(updated)).build();
    }
}
