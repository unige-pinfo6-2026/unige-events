package ch.unige.events.exception;

import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;

public class InvalidFileTypeException extends WebApplicationException {

    public InvalidFileTypeException(String message) {
        super(message, Response.Status.BAD_REQUEST);
    }
}
