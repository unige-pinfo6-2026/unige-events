package ch.unige.events.exception;

import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;

public class FileTooLargeException extends WebApplicationException {

    private static final long serialVersionUID = 1L;

    public FileTooLargeException(String message) {
        super(message, Response.Status.REQUEST_ENTITY_TOO_LARGE);
    }
}
