package ch.unige.events.exception.mapper;

import ch.unige.events.dto.ApiErrorResponse;
import jakarta.ws.rs.ForbiddenException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

@Provider
public class ForbiddenExceptionMapper implements ExceptionMapper<ForbiddenException> {

    /**
     * Public-facing 403 message — uniform across every authorization failure.
     *
     * <p>Pentest 2026-04-17 finding 4.20: service-layer messages such as
     * {@code "Only the event creator or an admin can publish this event"} leak
     * the existence of the {@code admin} role and the creator/co-organizer
     * authorization model. The mapper enforces a single sanitized response —
     * the original detailed message remains attached to the exception object
     * so it still reaches the application logs (where ops needs the context).
     */
    static final String SANITIZED_MESSAGE = "You are not allowed to perform this action.";

    @Override
    public Response toResponse(ForbiddenException exception) {
        return Response.status(Response.Status.FORBIDDEN)
            .type(MediaType.APPLICATION_JSON)
            .entity(new ApiErrorResponse("forbidden", SANITIZED_MESSAGE))
            .build();
    }
}
