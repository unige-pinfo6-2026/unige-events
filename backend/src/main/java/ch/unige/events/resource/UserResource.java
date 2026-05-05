package ch.unige.events.resource;

import ch.unige.events.dto.*;
import ch.unige.events.dto.attendance.AttendanceDTO;
import ch.unige.events.dto.calendar.CalendarTokenResponse;
import ch.unige.events.dto.coorganizer.CoOrganizerInvitationDTO;
import ch.unige.events.dto.event.EventDTO;
import ch.unige.events.dto.user.UpdateProfileRequest;
import ch.unige.events.dto.user.UserProfileResponse;
import ch.unige.events.dto.user.UserPublicResponse;
import ch.unige.events.entity.AttendanceStatus;
import ch.unige.events.entity.CoOrganizerStatus;
import ch.unige.events.entity.EventStatus;
import ch.unige.events.entity.User;
import ch.unige.events.service.AttendanceService;
import ch.unige.events.service.CalendarService;
import ch.unige.events.service.EventCoOrganizerService;
import ch.unige.events.service.EventService;
import ch.unige.events.service.FavoriteService;
import ch.unige.events.service.UserService;
import io.quarkus.security.Authenticated;
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.annotation.security.PermitAll;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.util.List;
import org.jboss.resteasy.reactive.RestForm;
import org.jboss.resteasy.reactive.multipart.FileUpload;
import org.eclipse.microprofile.jwt.JsonWebToken;
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
    @Inject JsonWebToken jwt;
    @Inject FavoriteService favoriteService;
    @Inject CalendarService calendarService;
    @Inject AttendanceService attendanceService;
    @Inject EventService eventService;
    @Inject EventCoOrganizerService coOrganizerService;

    /**
     * GET /api/users/{id}
     * Profil public d'un utilisateur.
     * - profilePublic=true + anon  → 200 payload réduit (id, displayName, avatarUrl)
     * - profilePublic=true + auth  → 200 payload complet
     * - profilePublic=false + self → 200 payload complet
     * - sinon                      → 404 (envelope identique à UUID inexistant, anti-oracle)
     * Hotfix pentest 2026-04-17 findings 4.1 + 4.1b.
     */
    @GET
    @Path("/{id}")
    @PermitAll
    public Response getProfile(@PathParam("id") UUID id) {
        boolean anonymous = identity.isAnonymous();
        String auth0Id = anonymous ? null : identity.getPrincipal().getName();
        User user = userService.getPublicProfile(id, auth0Id);
        UserPublicResponse body = anonymous
                ? UserPublicResponse.fromAnonymous(user)
                : UserPublicResponse.from(user);
        return Response.ok(body).build();
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
        User user = userService.getOrCreateUser(auth0Id, jwt);
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

    /**
     * POST /api/users/me/image
     * Upload de la photo de profil — met à jour avatarUrl avec le chemin /api/uploads/...
     */
    @POST
    @Path("/me/image")
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @Authenticated
    public Response uploadImage(@RestForm("file") FileUpload file) {
        String auth0Id = identity.getPrincipal().getName();
        User updated = userService.uploadImage(auth0Id, file);
        return Response.ok(UserProfileResponse.from(updated)).build();
    }

    /**
     * POST /api/users/me/banner
     * Upload de la bannière de profil — met à jour bannerUrl avec le chemin /api/uploads/...
     */
    @POST
    @Path("/me/banner")
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @Authenticated
    public Response uploadBanner(@RestForm("file") FileUpload file) {
        String auth0Id = identity.getPrincipal().getName();
        User updated = userService.uploadBanner(auth0Id, file);
        return Response.ok(UserProfileResponse.from(updated)).build();
    }

    /**
     * DELETE /api/users/me/banner
     * Supprime la bannière de profil — remet bannerUrl à null
     */
    @DELETE
    @Path("/me/banner")
    @Authenticated
    @Consumes(MediaType.WILDCARD)
    public Response deleteBanner() {
        String auth0Id = identity.getPrincipal().getName();
        User updated = userService.deleteBanner(auth0Id);
        return Response.ok(UserProfileResponse.from(updated)).build();
    }

    @GET
    @Path("/me/favorites")
    @Authenticated
    public List<EventDTO> getMyFavorites(
            @QueryParam("page") @DefaultValue("0") @Min(0) int page,
            @QueryParam("size") @DefaultValue("20") @Positive @Max(100) int size) {
        String auth0Id = identity.getPrincipal().getName();
        return favoriteService.getFavorites(auth0Id, page, size);
    }

    @GET
    @Path("/me/calendar-token")
    @Authenticated
    public CalendarTokenResponse getMyCalendarToken() {
        return calendarService.getOrCreateToken(identity.getPrincipal().getName());
    }

    @POST
    @Path("/me/calendar-token/regenerate")
    @Authenticated
    @Consumes(MediaType.WILDCARD)
    public CalendarTokenResponse regenerateMyCalendarToken() {
        return calendarService.regenerateToken(identity.getPrincipal().getName());
    }

    @GET
    @Path("/me/attendances")
    @Authenticated
    public List<AttendanceDTO> getMyAttendances() {
        return attendanceService.getMyAttendances(identity.getPrincipal().getName());
    }

    @GET
    @Path("/me/participations")
    @Authenticated
    public List<EventDTO> getMyParticipationEvents(
            @QueryParam("status") AttendanceStatus status) {
        return attendanceService.getMyParticipationEvents(identity.getPrincipal().getName(), status);
    }

    @GET
    @Path("/me/events")
    @Authenticated
    public List<EventDTO> getMyEvents(
            @QueryParam("status") EventStatus status,
            @QueryParam("page") @DefaultValue("0") @Min(0) int page,
            @QueryParam("size") @DefaultValue("20") @Positive @Max(100) int size) {
        return eventService.getMyEvents(identity.getPrincipal().getName(), status, page, size);
    }

    @GET
    @Path("/me/co-organizer-invitations")
    @Authenticated
    public List<CoOrganizerInvitationDTO> getMyCoOrganizerInvitations(
            @QueryParam("status") CoOrganizerStatus status,
            @QueryParam("page") @DefaultValue("0") @Min(0) int page,
            @QueryParam("size") @DefaultValue("20") @Positive @Max(100) int size) {
        return coOrganizerService.getMyInvitations(
                identity.getPrincipal().getName(), status, page, size);
    }
}
