package ch.unige.events.shared.storage;

import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;

/**
 * Thrown by {@link FileStorageService#saveImage} when the declared MIME
 * is not one of the four accepted image formats <em>or</em> when the magic
 * bytes don't match the declared MIME (cf. {@link ImageFormat#matches}).
 * Mapped to {@code 400 Bad Request} by
 * {@link InvalidFileTypeExceptionMapper}.
 */
public class InvalidFileTypeException extends WebApplicationException {

    public InvalidFileTypeException(String message) {
        super(message, Response.Status.BAD_REQUEST);
    }
}
