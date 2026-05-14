package ch.unige.events.shared.client;

import ch.unige.events.shared.domain.dto.AttendeeProjection;
import ch.unige.events.shared.domain.dto.UserPublicResponse;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Direct unit test of the @Fallback default methods on UserServiceClient.
 * Same rationale as EventServiceClientFallbackTest.
 */
class UserServiceClientFallbackTest {

    private static final UserServiceClient CLIENT = new UserServiceClient() {
        @Override public UserPublicResponse getById(UUID id) { throw new UnsupportedOperationException(); }
        @Override public List<AttendeeProjection> getAttendeeProjections(List<UUID> ids) {
            throw new UnsupportedOperationException();
        }
    };

    @Test
    void getByIdFallback_returnsNull() {
        assertNull(CLIENT.getByIdFallback(UUID.randomUUID()));
    }

    @Test
    void getAttendeeProjectionsFallback_returnsEmptyList() {
        // Degraded enrichment: caller should treat every row as anonymous
        // rather than fail the whole request. An empty list satisfies that.
        List<AttendeeProjection> fallback = CLIENT.getAttendeeProjectionsFallback(
                List.of(UUID.randomUUID(), UUID.randomUUID()));
        assertEquals(0, fallback.size());
    }

    @Test
    void getAttendeeProjectionsFallback_acceptsNullIdsWithoutNpe() {
        // Defensive: the fallback should never NPE on the size() log statement
        // even if a degenerate caller hands it a null list.
        assertTrue(CLIENT.getAttendeeProjectionsFallback(null).isEmpty());
    }
}
