package ch.unige.events.report.integration;

import ch.unige.events.shared.client.EngagementServiceClient;
import ch.unige.events.shared.domain.dto.AttendanceSummary;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import jakarta.inject.Inject;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Regression guard for the production incident where every
 * {@code POST /events/{id}/report} returned 500.
 *
 * <p>Root cause : {@link ch.unige.events.report.service.ReportService} injects
 * {@code @RestClient EngagementServiceClient} as a field (used by the
 * comment-report path, SCRUM-144), but moderation-service's
 * {@code application.properties} was missing
 * {@code quarkus.rest-client.engagement-service.url}. Field injection of a
 * REST client is resolved at bean creation, so the missing base URL made the
 * whole {@code ReportService} bean fail to instantiate
 * (« Unable to determine the proper baseUrl/baseUri ») — 500-ing <em>every</em>
 * ReportService call, including event reports that never touch
 * engagement-service.
 *
 * <p>This test deliberately <strong>does not</strong> override the rest-client
 * URL : it must resolve from {@code application.properties} so the test goes
 * RED the moment that default line is removed again. Only the timeouts are
 * shortened so the call to the (CI-unresolvable) {@code engagement-service}
 * host fails fast and lands on the {@code @Fallback}.
 *
 * <p>Mirrors the {@code *ClientFallbackWiringTest} pattern already present in
 * event-service / user-service / engagement-service — moderation-service was
 * the only consumer without one, which is why this slipped to prod.
 */
@QuarkusTest
@TestProfile(EngagementServiceClientWiringTest.FastTimeoutProfile.class)
class EngagementServiceClientWiringTest {

    @Inject
    @RestClient
    EngagementServiceClient client;

    public static class FastTimeoutProfile implements QuarkusTestProfile {
        @Override
        public Map<String, String> getConfigOverrides() {
            // URL intentionally NOT overridden — it comes from
            // application.properties (the config whose presence this test
            // guards). Only shrink timeouts so the unresolved-host call falls
            // through to @Fallback quickly.
            return Map.of(
                    "quarkus.rest-client.engagement-service.connect-timeout", "100",
                    "quarkus.rest-client.engagement-service.read-timeout", "100");
        }
    }

    @Test
    @DisplayName("EngagementServiceClient is injectable (base URL configured) and falls back when the host is unreachable")
    void engagementClient_isWiredAndFallsBack() {
        // The injection itself is the regression assertion : the prod bug was a
        // bean-creation failure caused by the missing base URL. If the URL
        // config is absent, this @QuarkusTest fails to start.
        assertNotNull(client, "EngagementServiceClient should be injectable");

        // The call then exercises the @Fallback (engagement-service host is not
        // resolvable in CI) → AttendanceSummary.of(0, 0).
        AttendanceSummary summary = client.getAttendanceSummary(42L);
        assertNotNull(summary);
        assertEquals(0L, summary.attending());
    }
}
