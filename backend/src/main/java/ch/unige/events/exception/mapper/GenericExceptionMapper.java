package ch.unige.events.exception.mapper;

import ch.unige.events.dto.ApiErrorResponse;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import org.jboss.logging.Logger;

/**
 * Catch-all mapper for unhandled exceptions.
 *
 * <p>Pentest 2026-04-17 finding 4.22 — RESTEasy Reactive's default behaviour
 * for an unhandled {@link RuntimeException} is to render a plain-text error
 * page (e.g. {@code "Error id aca02b92-…"}). That's both ugly and a contract
 * leak: the frontend can't parse it, and external clients see Quarkus internals.
 *
 * <p>This mapper emits the project-standard JSON envelope for every otherwise
 * unhandled exception. JAX-RS picks the most specific {@code ExceptionMapper}
 * available, so the dedicated mappers in this package (BadRequest, Forbidden,
 * NotFound, ConstraintViolation, …) all continue to fire first. Only when
 * nothing more specific matches does this one engage.
 *
 * <p>{@link WebApplicationException} carries its own response — the framework
 * already renders that correctly, but we still pass through this mapper because
 * the JAX-RS resolution algorithm doesn't differentiate between
 * {@code WebApplicationException} and a generic {@code Exception} when both
 * could match. We hand back the built-in response so we don't disturb the
 * specific mappers' work.
 */
@Provider
public class GenericExceptionMapper implements ExceptionMapper<Exception> {

    private static final Logger LOG = Logger.getLogger(GenericExceptionMapper.class);

    @Override
    public Response toResponse(Exception exception) {
        if (exception instanceof WebApplicationException webExc) {
            // Defer to the framework / specific mapper that already built the
            // response on the exception object. Don't double-wrap.
            return webExc.getResponse();
        }
        // Genuinely unexpected — log with full stack so ops can diagnose, then
        // emit a sanitized 500 so the client never sees the default RESTEasy
        // text page.
        LOG.errorf(exception, "Unhandled exception (%s) — returning 500",
                exception.getClass().getName());
        return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                .type(MediaType.APPLICATION_JSON)
                .entity(new ApiErrorResponse(
                        "internal_error",
                        "An unexpected error occurred. Please retry later."))
                .build();
    }
}
