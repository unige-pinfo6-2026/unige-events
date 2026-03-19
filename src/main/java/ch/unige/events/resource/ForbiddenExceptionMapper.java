package ch.unige.events.resource;

import ch.unige.events.dto.ApiErrorResponse;
import jakarta.ws.rs.ForbiddenException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

@Provider
public class ForbiddenExceptionMapper implements ExceptionMapper<ForbiddenException> {

    @Override
    public Response toResponse(ForbiddenException exception) {
        String message = exception.getMessage() == null || exception.getMessage().isBlank()
            ? "Forbidden"
            : exception.getMessage();

        return Response.status(Response.Status.FORBIDDEN)
            .type(MediaType.APPLICATION_JSON)
            .entity(new ApiErrorResponse("forbidden", message))
            .build();
    }
}
