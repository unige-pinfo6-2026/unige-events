package ch.unige.events.shared.error;

import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

/**
 * Factory helpers for the common 4xx WebApplicationExceptions. Each
 * helper builds a {@link Response} with status, JSON content-type, and
 * an {@link ApiErrorResponse} entity, then wraps it in a
 * {@link WebApplicationException} ready to throw.
 */
public final class ApiErrors {

    private ApiErrors() {
    }

    public static WebApplicationException badRequest(String error, String message) {
        return build(Response.Status.BAD_REQUEST, error, message);
    }

    public static WebApplicationException conflict(String error, String message) {
        return build(Response.Status.CONFLICT, error, message);
    }

    public static WebApplicationException unprocessable(String error, String message) {
        return build(422, error, message);
    }

    public static WebApplicationException forbidden(String error, String message) {
        return build(Response.Status.FORBIDDEN, error, message);
    }

    public static WebApplicationException notFound(String error, String message) {
        return build(Response.Status.NOT_FOUND, error, message);
    }

    private static WebApplicationException build(Response.Status status, String error, String message) {
        return new WebApplicationException(
                Response.status(status)
                        .type(MediaType.APPLICATION_JSON)
                        .entity(new ApiErrorResponse(error, message))
                        .build());
    }

    private static WebApplicationException build(int status, String error, String message) {
        return new WebApplicationException(
                Response.status(status)
                        .type(MediaType.APPLICATION_JSON)
                        .entity(new ApiErrorResponse(error, message))
                        .build());
    }
}
