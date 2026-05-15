package ch.unige.events.shared.storage;

import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;

/**
 * Thrown by {@link FileStorageService#saveImage} when the uploaded payload
 * exceeds the per-folder size cap (avatar = 2 MiB, banner = 5 MiB).
 * Mapped to {@code 413 Payload Too Large} by
 * {@link FileTooLargeExceptionMapper}.
 */
public class FileTooLargeException extends WebApplicationException {

    private static final long serialVersionUID = 1L;

    public FileTooLargeException(String message) {
        super(message, Response.Status.REQUEST_ENTITY_TOO_LARGE);
    }
}
