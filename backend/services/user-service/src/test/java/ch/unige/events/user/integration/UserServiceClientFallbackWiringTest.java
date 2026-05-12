package ch.unige.events.user.integration;

import ch.unige.events.shared.client.UserServiceClient;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import jakarta.inject.Inject;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Pins the {@code @Fallback} wiring on every {@link UserServiceClient}
 * method. Points the rest-client
 * to a black-hole URL with sub-second timeouts so each call exercises the
 * MicroProfile FT layer; the test is red the moment {@code @Fallback} is
 * removed from the interface.
 */
@QuarkusTest
@TestProfile(UserServiceClientFallbackWiringTest.BlackHoleProfile.class)
class UserServiceClientFallbackWiringTest {

    @Inject @RestClient UserServiceClient client;

    public static class BlackHoleProfile implements QuarkusTestProfile {
        @Override
        public Map<String, String> getConfigOverrides() {
            return Map.of(
                "quarkus.rest-client.user-service.url", "http://localhost:1",
                "quarkus.rest-client.user-service.connect-timeout", "100",
                "quarkus.rest-client.user-service.read-timeout", "100"
            );
        }
    }

    @Test
    @DisplayName("getById fallback wired: connection failure routes to fallback method, not exception")
    void getById_connectionFailure_routesToFallback() {
        assertNull(client.getById(UUID.randomUUID()));
    }
}
