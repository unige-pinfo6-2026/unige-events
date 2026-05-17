package ch.unige.events.engagement.attendance.kafka;

import ch.unige.events.shared.kafka.events.AttendanceCreatedEvent;

import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.reactive.messaging.Channel;
import org.eclipse.microprofile.reactive.messaging.Emitter;

/**
 * Publishes {@link AttendanceCreatedEvent} payloads to Kafka topic
 * {@code attendances.created}. Pattern mirrors event-service
 * {@code EventLifecyclePublisher} : send failures are logged and
 * swallowed so a broker outage cannot roll back the user-facing
 * attendance transaction (the row in {@code attendances} is the
 * source of truth ; the topic is just a notification fan-out).
 *
 * <p>SCRUM-99 — consumed by
 * {@code notification-service.AttendanceCreatedConsumer} to push a
 * {@code NEW_ATTENDEE} notification to the event creator.
 */
@ApplicationScoped
public class AttendanceCreatedPublisher {

    private final Emitter<AttendanceCreatedEvent> emitter;

    @Inject
    public AttendanceCreatedPublisher(@Channel("attendances-created") Emitter<AttendanceCreatedEvent> emitter) {
        this.emitter = emitter;
    }

    public void send(AttendanceCreatedEvent ev) {
        try {
            emitter.send(ev);
        } catch (RuntimeException e) {
            // Post-commit: no rollback possible — this log is the only operator signal.
            Log.errorf(e,
                    "[KAFKA_PUBLISH_FAIL_attendances_created] Failed to publish attendance=%d event=%d user=%s — downstream NEW_ATTENDEE notification will not be sent",
                    ev.attendanceId(), ev.eventId(), ev.userId());
        }
    }
}
