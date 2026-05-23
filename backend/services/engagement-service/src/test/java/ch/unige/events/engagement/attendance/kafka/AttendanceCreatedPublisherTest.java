package ch.unige.events.engagement.attendance.kafka;

import ch.unige.events.shared.kafka.events.AttendanceCreatedEvent;

import io.quarkus.test.junit.QuarkusTest;
import org.eclipse.microprofile.reactive.messaging.Emitter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * {@code @QuarkusTest} so the executed {@link AttendanceCreatedPublisher}
 * bytecode (including the swallow {@code catch}) is captured by
 * quarkus-jacoco — plain Mockito unit tests do not contribute coverage.
 */
@QuarkusTest
class AttendanceCreatedPublisherTest {

    @SuppressWarnings("unchecked")
    private final Emitter<AttendanceCreatedEvent> emitter = mock(Emitter.class);
    private AttendanceCreatedPublisher publisher;

    @BeforeEach
    void setUp() {
        publisher = new AttendanceCreatedPublisher(emitter);
    }

    @Test
    void send_forwardsEventToEmitter() {
        UUID user = UUID.randomUUID();
        AttendanceCreatedEvent ev = AttendanceCreatedEvent.of(1L, 42L, user);

        publisher.send(ev);

        ArgumentCaptor<AttendanceCreatedEvent> captor = ArgumentCaptor.forClass(AttendanceCreatedEvent.class);
        verify(emitter).send(captor.capture());
        assertEquals(1L, captor.getValue().attendanceId());
        assertEquals(42L, captor.getValue().eventId());
        assertEquals(user, captor.getValue().userId());
    }

    @Test
    void emitFailure_isSwallowed() {
        doThrow(new RuntimeException("broker down"))
                .when(emitter).send(org.mockito.ArgumentMatchers.any(AttendanceCreatedEvent.class));

        // Post-commit transaction must not be rolled back by a Kafka outage.
        assertDoesNotThrow(() -> publisher.send(AttendanceCreatedEvent.of(1L, 1L, UUID.randomUUID())));
    }
}
