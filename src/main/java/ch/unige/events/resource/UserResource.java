package ch.unige.events.resource;

import ch.unige.events.dto.UpdateProfileRequest;
import ch.unige.events.dto.UserPublicResponse;
import ch.unige.events.entity.User;
import ch.unige.events.service.UserService;
import jakarta.annotation.security.PermitAll;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.jwt.JsonWebToken;
import java.util.UUID;

@Path("/users")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class UserResource {

    @Inject JsonWebToken jwt;
    @Inject UserService  userService;

    /**
     * GET /api/users/{id}
     * Public si isProfilePublic=true, sinon 403
     */
    @GET
    @Path("/{id}")
    @PermitAll
    public Response getProfile(@PathParam("id") UUID id) {
        User user = userService.getPublicProfile(id);
        return Response.ok(UserPublicResponse.from(user)).build();
    }

    /**
     * PUT /api/users/me
     * Modifie son propre profil (crée si 1ère connexion)
     */
    @PUT
    @Path("/me")
    @RolesAllowed({"user", "admin"}) // token Auth0 valide requis
    public Response updateMe(UpdateProfileRequest req) {
        String email = jwt.getClaim("email"); // claim Auth0
        User updated = userService.updateMyProfile(email, req);
        return Response.ok(UserPublicResponse.from(updated)).build();
    }

    /**
     * GET /api/users/me
     * Retourne son propre profil (crée si 1ère connexion)
     */
    @GET
    @Path("/me")
    @RolesAllowed({"user", "admin"})
    public Response getMe() {
        String email = jwt.getClaim("email");
        User user = userService.getOrCreateUser(email); // ← création auto ici
        return Response.ok(UserPublicResponse.from(user)).build();
    }
}
