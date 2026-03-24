package ch.unige.events.resource;

import ch.unige.events.dto.*;
import ch.unige.events.dto.user.UpdateProfileRequest;
import ch.unige.events.dto.user.UserProfileResponse;
import ch.unige.events.dto.user.UserPublicResponse;
import ch.unige.events.entity.User;
import ch.unige.events.service.UserService;
import io.quarkus.oidc.UserInfo;
import io.quarkus.security.Authenticated;
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.annotation.security.PermitAll;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.ExampleObject;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.parameters.RequestBody;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponses;
import org.eclipse.microprofile.openapi.annotations.security.SecurityRequirement;

import java.util.UUID;

@Path("/users")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class UserResource {

    @Inject SecurityIdentity identity;
    @Inject UserService userService;
    @Inject Instance<UserInfo> userInfo;

    /**
     * GET /api/users/{id}
     * Public si profilePublic=true, sinon 403
     */
    @GET
    @Path("/{id}")
    @PermitAll
    public Response getProfile(@PathParam("id") UUID id) {
        User user = userService.getPublicProfile(id);
        return Response.ok(UserPublicResponse.from(user)).build();
    }

    @GET
    @Path("/me")
    @Authenticated
    @Operation(
        summary = "Get authenticated user profile",
        description = "Auth REQUIRED: valid Bearer JWT. On first login, a profile is created from JWT `sub` and required `email` claim, then the full profile is returned."
    )
    @SecurityRequirement(name = "bearerAuth")
    @APIResponses(value = {
        @APIResponse(
            responseCode = "200",
            description = "Full authenticated user profile",
            content = @Content(
                mediaType = MediaType.APPLICATION_JSON,
                schema = @Schema(implementation = UserProfileResponse.class),
                examples = @ExampleObject(
                    name = "success",
                    value = "{\"id\":\"8efcb891-df3b-48cc-8f3e-a37853fbe2f4\",\"auth0_id\":\"auth0|abc123\",\"email\":\"alice@example.com\",\"display_name\":\"Alice\",\"faculty\":\"Science\",\"study_level\":\"Bachelor\",\"bio\":\"Student at UNIGE\",\"interests\":\"AI, football\",\"avatar_url\":\"https://cdn.example.com/avatar.png\",\"profile_public\":false,\"created_at\":\"2026-03-19T10:30:00\"}"
                )
            )
        ),
        @APIResponse(
            responseCode = "401",
            description = "Not authenticated",
            content = @Content(
                mediaType = MediaType.APPLICATION_JSON,
                schema = @Schema(implementation = ApiErrorResponse.class),
                examples = @ExampleObject(
                    name = "unauthorized",
                    value = "{\"error\":\"unauthorized\",\"message\":\"Authentication is required.\"}"
                )
            )
        )
    })
    public UserProfileResponse me() {
        String auth0Id = identity.getPrincipal().getName();
        User user = userService.getOrCreateUser(auth0Id, userInfo.get());
        return UserProfileResponse.from(user);
    }

    /**
     * PUT /api/users/me
     * Modifie son propre profil (crée si 1ère connexion)
     */
    @PUT
    @Path("/me")
    @Authenticated
    @Operation(
        summary = "Update authenticated user profile",
        description = "Auth REQUIRED: valid Bearer JWT. Updates only the authenticated user's profile (JWT `sub`) with partial editable fields."
    )
    @SecurityRequirement(name = "bearerAuth")
    @RequestBody(
        required = true,
        content = @Content(
            mediaType = MediaType.APPLICATION_JSON,
            schema = @Schema(implementation = UpdateProfileRequest.class),
            examples = @ExampleObject(
                name = "partial-update",
                value = "{\"display_name\":\"Alice\",\"bio\":\"Student at UNIGE\",\"profile_public\":true}"
            )
        )
    )
    @APIResponses(value = {
        @APIResponse(
            responseCode = "200",
            description = "Updated full user profile",
            content = @Content(
                mediaType = MediaType.APPLICATION_JSON,
                schema = @Schema(implementation = UserProfileResponse.class),
                examples = @ExampleObject(
                    name = "updated",
                    value = "{\"id\":\"8efcb891-df3b-48cc-8f3e-a37853fbe2f4\",\"auth0_id\":\"auth0|abc123\",\"email\":\"alice@example.com\",\"display_name\":\"Alice\",\"faculty\":\"Science\",\"study_level\":\"Bachelor\",\"bio\":\"Student at UNIGE\",\"interests\":\"AI, football\",\"avatar_url\":\"https://cdn.example.com/avatar.png\",\"profile_public\":true,\"created_at\":\"2026-03-19T10:30:00\"}"
                )
            )
        ),
        @APIResponse(
            responseCode = "400",
            description = "Validation error",
            content = @Content(
                mediaType = MediaType.APPLICATION_JSON,
                schema = @Schema(implementation = ValidationErrorResponse.class),
                examples = @ExampleObject(
                    name = "validation-error",
                    value = "{\"error\":\"validation_error\",\"message\":\"Request validation failed\",\"details\":[{\"field\":\"display_name\",\"message\":\"must not be blank\"}]}"
                )
            )
        ),
        @APIResponse(
            responseCode = "401",
            description = "Not authenticated",
            content = @Content(
                mediaType = MediaType.APPLICATION_JSON,
                schema = @Schema(implementation = ApiErrorResponse.class),
                examples = @ExampleObject(
                    name = "unauthorized",
                    value = "{\"error\":\"unauthorized\",\"message\":\"Authentication is required.\"}"
                )
            )
        ),
        @APIResponse(
            responseCode = "403",
            description = "Forbidden",
            content = @Content(
                mediaType = MediaType.APPLICATION_JSON,
                schema = @Schema(implementation = ApiErrorResponse.class),
                examples = @ExampleObject(
                    name = "forbidden",
                    value = "{\"error\":\"forbidden\",\"message\":\"Cannot modify another user's profile\"}"
                )
            )
        ),
        @APIResponse(
            responseCode = "404",
            description = "Profile not found",
            content = @Content(
                mediaType = MediaType.APPLICATION_JSON,
                schema = @Schema(implementation = ApiErrorResponse.class),
                examples = @ExampleObject(
                    name = "not-found",
                    value = "{\"error\":\"not_found\",\"message\":\"Profile not found\"}"
                )
            )
        ),
        @APIResponse(
            responseCode = "409",
            description = "Profile update conflict",
            content = @Content(
                mediaType = MediaType.APPLICATION_JSON,
                schema = @Schema(implementation = ApiErrorResponse.class),
                examples = @ExampleObject(
                    name = "conflict",
                    value = "{\"error\":\"conflict\",\"message\":\"Profile was updated by another request. Please retry.\"}"
                )
            )
        )
    })
    public Response updateMe(@Valid UpdateProfileRequest req) {
        String auth0Id = identity.getPrincipal().getName();
        User updated = userService.updateMyProfile(auth0Id, auth0Id, req);
        return Response.ok(UserProfileResponse.from(updated)).build();
    }
}
