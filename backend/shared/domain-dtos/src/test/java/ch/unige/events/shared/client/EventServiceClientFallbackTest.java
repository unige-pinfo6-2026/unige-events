package ch.unige.events.shared.client;

import ch.unige.events.shared.domain.dto.EventDTO;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Direct unit test of the @Fallback default methods on EventServiceClient.
 * The abstract REST methods are proxy-only and thus invisible to jacoco;
 * the default fallbacks carry instrumented bytecode that would otherwise
 * stay 0%-covered. This test exercises each default branch.
 */
class EventServiceClientFallbackTest {

    private static final EventServiceClient CLIENT = new EventServiceClient() {
        @Override public EventDTO getById(long id) { throw new UnsupportedOperationException(); }
        @Override public EventDTO getByIdWithCoOrgCheck(long id, UUID userId) { throw new UnsupportedOperationException(); }
        @Override public List<EventDTO> findByIds(List<Long> ids, String status) { throw new UnsupportedOperationException(); }
        @Override public List<UUID> getOrganizerUuids(long id) { throw new UnsupportedOperationException(); }
    };

    @Test
    void getByIdFallback_returnsNull() {
        assertNull(CLIENT.getByIdFallback(42L));
    }

    @Test
    void getByIdWithCoOrgCheckFallback_returnsNull() {
        assertNull(CLIENT.getByIdWithCoOrgCheckFallback(42L, UUID.randomUUID()));
    }

    @Test
    void findByIdsFallback_returnsEmptyImmutableList() {
        List<EventDTO> result = CLIENT.findByIdsFallback(List.of(1L, 2L), "PUBLISHED");
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    void getOrganizerUuidsFallback_returnsEmptyImmutableList() {
        List<UUID> result = CLIENT.getOrganizerUuidsFallback(42L);
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }
}
