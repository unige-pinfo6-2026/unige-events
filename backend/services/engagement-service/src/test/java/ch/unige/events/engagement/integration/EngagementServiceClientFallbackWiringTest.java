package ch.unige.events.engagement.integration;

import ch.unige.events.shared.client.EngagementServiceClient;
import ch.unige.events.shared.domain.dto.AttendanceSummary;

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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins the {@code @Fallback} wiring on every {@link EngagementServiceClient}
 * method. Points the rest-client
 * to a black-hole URL with sub-second timeouts so each call exercises the
 * MicroProfile FT layer; the test is red the moment {@code @Fallback} is
 * removed from the interface.
 */
@QuarkusTest
@TestProfile(EngagementServiceClientFallbackWiringTest.BlackHoleProfile.class)
class EngagementServiceClientFallbackWiringTest {

    @Inject @RestClient EngagementServiceClient client;

    public static class BlackHoleProfile implements QuarkusTestProfile {
        @Override
        public Map<String, String> getConfigOverrides() {
            return Map.of(
                "quarkus.rest-client.engagement-service.url", "http://localhost:1",
                "quarkus.rest-client.engagement-service.connect-timeout", "100",
                "quarkus.rest-client.engagement-service.read-timeout", "100"
            );
        }
    }

    @Test
    @DisplayName("getAttendanceSummary fallback wired: connection failure routes to AttendanceSummary.of(0,0)")
    void getAttendanceSummary_connectionFailure_routesToFallback() {
        AttendanceSummary result = client.getAttendanceSummary(42L);
        assertNotNull(result);
        assertEquals(0L, result.attending());
        assertEquals(0L, result.waitlisted());
    }

    @Test
    @DisplayName("getUserAttendances fallback wired: connection failure returns empty list")
    void getUserAttendances_connectionFailure_routesToFallback() {
        List<?> result = client.getUserAttendances(UUID.randomUUID(), "ATTENDING");
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("getAttendanceSummariesBulk fallback wired: connection failure returns empty map")
    void getAttendanceSummariesBulk_connectionFailure_routesToFallback() {
        var result = client.getAttendanceSummariesBulk(List.of(1L, 2L));
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }
}
