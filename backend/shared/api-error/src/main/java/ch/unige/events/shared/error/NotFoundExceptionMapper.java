package ch.unige.events.shared.error;

import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

/**
 * Universal 404 envelope. Maps any {@link NotFoundException} thrown by
 * a JAX-RS resource to a canonical {@link ApiErrorResponse} with
 * {@code error="not_found"} so cross-service contracts (cf.
 * EngagementEventIssue92PactTest, ModerationEventPactTest) can rely on
 * a stable shape regardless of which service threw the exception.
 *
 * <p>Auto-discovered via Jandex when shared-api-error is on the
 * classpath ; no per-service registration needed.
 */
@Provider
public class NotFoundExceptionMapper implements ExceptionMapper<NotFoundException> {

    @Override
    public Response toResponse(NotFoundException ex) {
        String message = (ex.getMessage() != null && !ex.getMessage().isBlank())
                ? ex.getMessage()
                : "Resource not found";
        return Response.status(Response.Status.NOT_FOUND)
                .entity(new ApiErrorResponse("not_found", message))
                .type(MediaType.APPLICATION_JSON)
                .build();
    }
}
