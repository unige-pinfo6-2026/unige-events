package ch.unige.events.event.integration;

import ch.unige.events.shared.client.EventServiceClient;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import jakarta.inject.Inject;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins the {@code @Fallback} wiring on every {@link EventServiceClient}
 * method (Étape 24.7.2, C5 — pr-test-analyzer I-2). Points the rest-client
 * to a black-hole URL with sub-second timeouts so each call exercises the
 * MicroProfile FT layer; the test is red the moment {@code @Fallback} is
 * removed from the interface (the FT exception bubbles up instead of being
 * absorbed by the fallback method).
 */
@QuarkusTest
@TestProfile(EventServiceClientFallbackWiringTest.BlackHoleProfile.class)
class EventServiceClientFallbackWiringTest {

    @Inject @RestClient EventServiceClient client;

    public static class BlackHoleProfile implements QuarkusTestProfile {
        @Override
        public Map<String, String> getConfigOverrides() {
            return Map.of(
                "quarkus.rest-client.event-service.url", "http://localhost:1",
                "quarkus.rest-client.event-service.connect-timeout", "100",
                "quarkus.rest-client.event-service.read-timeout", "100"
            );
        }
    }

    @Test
    @DisplayName("getById fallback wired: connection failure routes to fallback method, not exception")
    void getById_connectionFailure_routesToFallback() {
        assertNull(client.getById(42L));
    }

    @Test
    @DisplayName("getByIdWithCoOrgCheck fallback wired: connection failure routes to fallback method")
    void getByIdWithCoOrgCheck_connectionFailure_routesToFallback() {
        assertNull(client.getByIdWithCoOrgCheck(42L, UUID.randomUUID()));
    }

    @Test
    @DisplayName("findByIds fallback wired: connection failure returns empty list")
    void findByIds_connectionFailure_routesToFallback() {
        List<?> result = client.findByIds(List.of(1L, 2L), "PUBLISHED");
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("getOrganizerUuids fallback wired: connection failure returns empty list")
    void getOrganizerUuids_connectionFailure_routesToFallback() {
        List<?> result = client.getOrganizerUuids(42L);
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }
}
