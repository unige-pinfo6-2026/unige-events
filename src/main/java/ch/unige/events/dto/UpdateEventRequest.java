package ch.unige.events.dto;

import ch.unige.events.entity.EventStatus;

public class UpdateEventRequest extends EventRequestBase {
    public EventStatus status;
}
