package ch.unige.events.dto.event;

import ch.unige.events.entity.EventStatus;

public class CreateEventRequest extends EventRequestBase {
    // Tous les champs et contraintes de validation sont hérités de EventRequestBase.
    private EventStatus status;

    public EventStatus getStatus() { return status; }
    public void setStatus(EventStatus status) { this.status = status; }
}
