package ch.unige.events.event.dto;

import ch.unige.events.shared.domain.enums.EventStatus;

public class UpdateEventRequest extends EventRequestBase {
    public EventStatus status;
}
