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
}
