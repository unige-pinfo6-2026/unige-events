package ch.unige.events.event.dto;

import ch.unige.events.event.entity.EventStatus;

public class UpdateEventRequest extends EventRequestBase {
    public EventStatus status;
}
