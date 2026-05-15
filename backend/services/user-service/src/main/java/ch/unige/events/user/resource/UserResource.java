package ch.unige.events.user.resource;

import ch.unige.events.user.dto.PublicProfileView;
import ch.unige.events.user.dto.UpdateProfileRequest;
import ch.unige.events.user.dto.UpdateUsernameRequest;
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
import jakarta.ws.rs.HEAD;
import jakarta.ws.rs.PATCH;
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

    private static final String ROLE_ADMIN = "ADMIN";

    @GET
    @Path("/{id}")
    @PermitAll
    public Response getProfile(@PathParam("id") UUID id) {
        boolean anonymous = identity.isAnonymous();
        String auth0Id = anonymous ? null : identity.getPrincipal().getName();
        // REST-003 / ISSUE-93: admins read private profiles for
        // moderation/support. SCRUM-169 revision: non-self non-admin
        // callers of a private profile receive a restricted projection
        // (id + username + displayName + avatarUrl) instead of the
        // historical 404, so cross-service enrichment (comments,
        // organizer team) keeps the public-facing identifier visible.
        boolean isAdmin = !anonymous && identity.hasRole(ROLE_ADMIN);
        PublicProfileView view = userService.getPublicProfile(id, auth0Id, isAdmin);
        UserPublicResponse body = (anonymous || view.restricted())
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

    /**
     * SCRUM-169 — update the caller's public-facing username. Endpoint
     * separate from {@code PUT /users/me} so the live-check debounce in
     * {@code ProfileEditPage} can race against this submit without dragging
     * the rest of the profile form down. Returns the full
     * {@link UserProfileResponse} so {@code useAuth.updateUser(...)} can
     * refresh global state without a refetch.
     */
    @PATCH
    @Path("/me/username")
    @Authenticated
    @PerUserRateLimit(name = "users.updateUsername", max = 5)
    public Response updateMyUsername(@Valid UpdateUsernameRequest req) {
        String auth0Id = identity.getPrincipal().getName();
        User updated = userService.updateUsername(auth0Id, req.username());
        return Response.ok(UserProfileResponse.from(updated)).build();
    }

    /**
     * SCRUM-169 — public profile lookup by username. Mirrors the security
     * model of {@link #getProfile(UUID)} : {@code @PermitAll} entry point,
     * stripped payload for anonymous callers (with {@code username} kept —
     * cf. spec Décision E). SCRUM-169 revision: private profiles viewed by
     * non-self non-admin callers now return the same stripped payload via
     * the {@code restricted} flag, instead of the historical 404.
     */
    @GET
    @Path("/by-username/{username}")
    @PermitAll
    public Response getByUsername(@PathParam("username") String username) {
        boolean anonymous = identity.isAnonymous();
        String auth0Id = anonymous ? null : identity.getPrincipal().getName();
        boolean isAdmin = !anonymous && identity.hasRole(ROLE_ADMIN);
        PublicProfileView view = userService.getByUsername(username, auth0Id, isAdmin);
        UserPublicResponse body = (anonymous || view.restricted())
                ? UserPublicResponse.fromAnonymous(view.user())
                : UserPublicResponse.from(
                        view.user(),
                        view.followerCount(),
                        view.followingCount(),
                        view.followStatus()
                  );
        return Response.ok(body).build();
    }

    /**
     * SCRUM-169 — light existence check backing the debounced uniqueness
     * probe in {@code ProfileEditPage}. <strong>Inverted semantics —
     * intentional</strong> : {@code 200} means the username is already
     * taken (resource exists), {@code 404} means it is free. The frontend
     * helper {@code checkUsernameAvailable} translates this back to a
     * boolean. No body is returned (per HTTP HEAD spec).
     */
    @HEAD
    @Path("/by-username/{username}")
    @PermitAll
    public Response existsByUsername(@PathParam("username") String username) {
        boolean exists = userService.existsByUsername(username);
        return Response.status(exists ? Response.Status.OK : Response.Status.NOT_FOUND).build();
    }
}
