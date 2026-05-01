package ch.unige.events.exception;

import jakarta.ws.rs.BadRequestException;

public class InvalidFileTypeException extends BadRequestException {

    public InvalidFileTypeException(String message) {
        super(message);
    }
}
