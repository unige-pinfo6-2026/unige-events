package ch.unige.events.resource;

import ch.unige.events.dto.ApiErrorResponse;
import jakarta.ws.rs.NotAuthorizedException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

@Provider
public class UnauthorizedExceptionMapper implements ExceptionMapper<NotAuthorizedException> {

    @Override
    public Response toResponse(NotAuthorizedException exception) {
        String message = exception.getMessage() == null || exception.getMessage().isBlank()
            ? "Authentication is required."
            : exception.getMessage();

        return Response.status(Response.Status.UNAUTHORIZED)
            .type(MediaType.APPLICATION_JSON)
            .entity(new ApiErrorResponse("unauthorized", message))
            .build();
    }
}
