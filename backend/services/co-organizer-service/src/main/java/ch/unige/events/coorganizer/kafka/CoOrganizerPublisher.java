package ch.unige.events.coorganizer.kafka;

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
        } catch (Exception e) {
            Log.warnf(e, "Failed to publish co-organizers.%s for event %d / user %s", ev.type(), ev.eventId(), ev.userId());
        }
    }
}
