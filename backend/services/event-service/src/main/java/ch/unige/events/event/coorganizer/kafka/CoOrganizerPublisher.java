package ch.unige.events.event.coorganizer.kafka;

import ch.unige.events.shared.kafka.events.CoOrganizerEvent;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.reactive.messaging.Channel;
import org.eclipse.microprofile.reactive.messaging.Emitter;

/**
 * Producer for {@code co-organizers.{invited,accepted}} (Décision F).
 */
@ApplicationScoped
public class CoOrganizerPublisher {

    private final Emitter<CoOrganizerEvent> invitedEmitter;
    private final Emitter<CoOrganizerEvent> acceptedEmitter;

    @Inject
    public CoOrganizerPublisher(
            @Channel("co-organizers-invited") Emitter<CoOrganizerEvent> invitedEmitter,
            @Channel("co-organizers-accepted") Emitter<CoOrganizerEvent> acceptedEmitter) {
        this.invitedEmitter = invitedEmitter;
        this.acceptedEmitter = acceptedEmitter;
    }

    public void send(CoOrganizerEvent ev) {
        Emitter<CoOrganizerEvent> emitter = switch (ev.type()) {
            case INVITED -> invitedEmitter;
            case ACCEPTED -> acceptedEmitter;
        };
        try {
            emitter.send(ev);
        } catch (RuntimeException e) {
            // Tightened from blanket Exception so checked failures still surface.
            // Post-commit: no rollback possible — this log is the only operator signal.
            Log.errorf(e,
                    "[KAFKA_PUBLISH_FAIL_events_co_organizer] Failed to publish co-organizers.%s for event %d / user %s — downstream consumers will not see this transition",
                    ev.type(), ev.eventId(), ev.userId());
        }
    }
}
