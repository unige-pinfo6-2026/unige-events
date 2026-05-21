package ch.unige.events.shared.domain.dto;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class IdProjectionTest {

    @Test
    void recordExposesUuid() {
        UUID id = UUID.randomUUID();
        IdProjection p = new IdProjection(id);
        assertEquals(id, p.id());
    }

    @Test
    void nullId_isLegalAtRecordLevel() {
        // The record itself has no validation — the producer (user-service
        // internal endpoint) guarantees a non-null id by throwing 404 if the
        // user is unknown. Verify the record accepts null defensively.
        IdProjection p = new IdProjection(null);
        assertNull(p.id());
    }

    @Test
    void singleArgConstructor_leavesUsernameNull() {
        // SCRUM-145 backward-compat — pre-existing callers (SCRUM-99
        // notification-service.resolveUserId) use new IdProjection(uuid)
        // and never look at the username. Pin the contract.
        UUID id = UUID.randomUUID();
        IdProjection p = new IdProjection(id);
        assertNull(p.username());
    }

    @Test
    void twoArgConstructor_exposesBothFields() {
        // SCRUM-145 new shape — the /users/_internal-by-usernames endpoint
        // populates both fields so notification-service can build the
        // handle→UUID map in one pass.
        UUID id = UUID.randomUUID();
        IdProjection p = new IdProjection(id, "alice.dosh");
        assertEquals(id, p.id());
        assertEquals("alice.dosh", p.username());
    }
}
