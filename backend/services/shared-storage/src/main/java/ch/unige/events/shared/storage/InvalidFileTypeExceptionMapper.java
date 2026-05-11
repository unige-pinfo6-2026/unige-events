package ch.unige.events.shared.storage;

import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

/**
 * {@link InvalidFileTypeException} → {@code 400 Bad Request} +
 * {@code {"error": "invalid_file_type", "message": ...}}.
 */
@Provider
public class InvalidFileTypeExceptionMapper implements ExceptionMapper<InvalidFileTypeException> {

    @Override
    public Response toResponse(InvalidFileTypeException exception) {
        return Response.status(Response.Status.BAD_REQUEST)
                .type(MediaType.APPLICATION_JSON)
                .entity(new StorageErrorBody("invalid_file_type", exception.getMessage()))
                .build();
    }
}
