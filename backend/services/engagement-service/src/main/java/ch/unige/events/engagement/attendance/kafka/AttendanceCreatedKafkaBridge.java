package ch.unige.events.engagement.attendance.kafka;

import ch.unige.events.shared.kafka.events.AttendanceCreatedEvent;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.event.TransactionPhase;
import jakarta.inject.Inject;

/**
 * Bridges CDI-fired {@link AttendanceCreatedEvent}s to the Kafka
 * {@link AttendanceCreatedPublisher} <em>after</em> the JDBC transaction
 * commits (Décision A of the completion spec — fixes BUG-001 / BUG-002
 * fantôme events on rollback).
 *
 * <p>{@code AttendanceService.attend} fires the CDI event from inside its
 * {@code @Transactional} method only when the effective status is
 * {@code ATTENDING} (SCRUM-99 Décision M). CDI delivers it to this observer
 * only if the transaction commits successfully — a rollback aborts the
 * delivery and no Kafka message is sent.
 */
@ApplicationScoped
public class AttendanceCreatedKafkaBridge {

    private final AttendanceCreatedPublisher publisher;

    @Inject
    public AttendanceCreatedKafkaBridge(AttendanceCreatedPublisher publisher) {
        this.publisher = publisher;
    }

    void onAfterCommit(@Observes(during = TransactionPhase.AFTER_SUCCESS) AttendanceCreatedEvent ev) {
        publisher.send(ev);
    }
}
