package ch.unige.events.event.dto;

import ch.unige.events.shared.domain.enums.EventStatus;
import jakarta.validation.Valid;

public class CreateEventRequest extends EventRequestBase {
    private EventStatus status;

    @Valid
    public RecurrenceRequest recurrence;

    public EventStatus getStatus() { return status; }
    public void setStatus(EventStatus status) { this.status = status; }
}
