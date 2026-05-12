package ch.unige.events.shared.kafka.events;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CoOrganizerEventTest {

    @Test
    void invited_factory() {
        UUID user = UUID.randomUUID();
        CoOrganizerEvent ev = CoOrganizerEvent.invited(42L, user);
        assertEquals(CoOrganizerEvent.Type.INVITED, ev.type());
        assertEquals(42L, ev.eventId());
        assertEquals(user, ev.userId());
    }

    @Test
    void accepted_factory() {
        CoOrganizerEvent ev = CoOrganizerEvent.accepted(99L, UUID.randomUUID());
        assertEquals(CoOrganizerEvent.Type.ACCEPTED, ev.type());
        assertEquals(99L, ev.eventId());
    }

    @Test
    void typeEnum_twoValues() {
        assertEquals(2, CoOrganizerEvent.Type.values().length);
        assertEquals(CoOrganizerEvent.Type.INVITED, CoOrganizerEvent.Type.values()[0]);
        assertEquals(CoOrganizerEvent.Type.ACCEPTED, CoOrganizerEvent.Type.values()[1]);
    }
}
