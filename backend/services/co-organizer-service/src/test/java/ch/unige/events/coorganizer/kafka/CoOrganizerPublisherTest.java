package ch.unige.events.coorganizer.kafka;

import ch.unige.events.shared.kafka.events.CoOrganizerEvent;
import org.eclipse.microprofile.reactive.messaging.Emitter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class CoOrganizerPublisherTest {

    @SuppressWarnings("unchecked")
    private final Emitter<CoOrganizerEvent> invitedEmitter = mock(Emitter.class);
    @SuppressWarnings("unchecked")
    private final Emitter<CoOrganizerEvent> acceptedEmitter = mock(Emitter.class);

    private CoOrganizerPublisher publisher;

    @BeforeEach
    void setUp() {
        publisher = new CoOrganizerPublisher(invitedEmitter, acceptedEmitter);
    }

    @Test
    void send_invited_routesToInvitedEmitter() {
        CoOrganizerEvent ev = CoOrganizerEvent.invited(42L, UUID.randomUUID());
        publisher.send(ev);
        verify(invitedEmitter).send(ev);
        verify(acceptedEmitter, never()).send(ev);
    }

    @Test
    void send_accepted_routesToAcceptedEmitter() {
        CoOrganizerEvent ev = CoOrganizerEvent.accepted(99L, UUID.randomUUID());
        publisher.send(ev);
        verify(acceptedEmitter).send(ev);
        verify(invitedEmitter, never()).send(ev);
    }

    @Test
    void send_emitterFails_swallows() {
        doThrow(new RuntimeException("kafka down")).when(invitedEmitter).send(org.mockito.ArgumentMatchers.any(CoOrganizerEvent.class));
        CoOrganizerEvent ev = CoOrganizerEvent.invited(42L, UUID.randomUUID());
        assertDoesNotThrow(() -> publisher.send(ev));
    }
}
