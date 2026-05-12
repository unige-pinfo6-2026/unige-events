package ch.unige.events.shared.storage;

import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

/**
 * {@link FileTooLargeException} → {@code 413 Payload Too Large} +
 * {@code {"error": "payload_too_large", "message": ...}}.
 *
 * <p>Wire-compatible with each service's {@code ApiErrorResponse} record ;
 * see {@link StorageErrorBody} for the rationale (avoid coupling this lib
 * to any specific service's DTO package).
 */
@Provider
public class FileTooLargeExceptionMapper implements ExceptionMapper<FileTooLargeException> {

    @Override
    public Response toResponse(FileTooLargeException exception) {
        return Response.status(Response.Status.REQUEST_ENTITY_TOO_LARGE)
                .type(MediaType.APPLICATION_JSON)
                .entity(new StorageErrorBody("payload_too_large", exception.getMessage()))
                .build();
    }
}
