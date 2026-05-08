package ch.unige.events.dto.event;

import ch.unige.events.entity.EventStatus;
import jakarta.validation.Valid;

public class CreateEventRequest extends EventRequestBase {
    // Tous les champs et contraintes de validation sont hérités de EventRequestBase.
    private EventStatus status;

    /**
     * SCRUM-147 — bloc optionnel de récurrence. Si renseigné,
     * {@code EventService.create} délègue à {@code createRecurring(...)}.
     * {@code @Valid} déclenche la validation imbriquée.
     */
    @Valid
    public RecurrenceRequest recurrence;

    public EventStatus getStatus() { return status; }
    public void setStatus(EventStatus status) { this.status = status; }
}
