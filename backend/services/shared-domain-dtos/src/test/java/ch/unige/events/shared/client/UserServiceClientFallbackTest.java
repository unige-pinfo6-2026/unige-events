package ch.unige.events.shared.client;

import ch.unige.events.shared.domain.dto.UserPublicResponse;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Direct unit test of the @Fallback default method on UserServiceClient.
 * Same rationale as EventServiceClientFallbackTest.
 */
class UserServiceClientFallbackTest {

    private static final UserServiceClient CLIENT = new UserServiceClient() {
        @Override public UserPublicResponse getById(UUID id) { throw new UnsupportedOperationException(); }
    };

    @Test
    void getByIdFallback_returnsNull() {
        assertNull(CLIENT.getByIdFallback(UUID.randomUUID()));
    }
}
