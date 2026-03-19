package ch.unige.events.resource;

import ch.unige.events.dto.ApiErrorResponse;
import jakarta.persistence.OptimisticLockException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

@Provider
public class ConflictExceptionMapper implements ExceptionMapper<OptimisticLockException> {

    @Override
    public Response toResponse(OptimisticLockException exception) {
        String message = exception.getMessage() == null || exception.getMessage().isBlank()
            ? "Profile update conflict. Please retry."
            : exception.getMessage();

        return Response.status(Response.Status.CONFLICT)
            .type(MediaType.APPLICATION_JSON)
            .entity(new ApiErrorResponse("conflict", message))
            .build();
    }
}
