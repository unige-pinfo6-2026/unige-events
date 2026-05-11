package ch.unige.events.contracts;

import au.com.dius.pact.consumer.MockServer;
import au.com.dius.pact.consumer.dsl.PactDslWithProvider;
import au.com.dius.pact.consumer.junit5.PactConsumerTestExt;
import au.com.dius.pact.consumer.junit5.PactTestFor;
import au.com.dius.pact.core.model.PactSpecVersion;
import au.com.dius.pact.core.model.RequestResponsePact;
import au.com.dius.pact.core.model.annotations.Pact;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import static org.hamcrest.Matchers.equalTo;

/**
 * Étape 5.1.a of finalization-ultimate spec (TEST-002).
 *
 * <p>Pins the event-service ↔ engagement-service bulk attendance
 * summary contract — Décision I. Replaces the legacy
 * {@code AttendanceStub.countGroupedByStatus(ids, ATTENDING/WAITLISTED)}
 * cross-schema query with a single REST hop:
 * {@code GET /events/_bulk-attendance-summary?ids=42&ids=7} returning
 * {@code Map<Long, AttendanceSummary>}.
 */
@ExtendWith(PactConsumerTestExt.class)
@PactTestFor(providerName = "engagement-service", pactVersion = PactSpecVersion.V3)
@SuppressWarnings("java:S100")
class EventEngagementBulkAttendancePactTest {

    @Pact(consumer = "event-service", provider = "engagement-service")
    public RequestResponsePact bulkAttendanceSummary_returnsMap(PactDslWithProvider builder) {
        String body = """
                {
                  "42": {"attending": 5, "waitlisted": 2, "interested": 0},
                  "7":  {"attending": 1, "waitlisted": 0, "interested": 0}
                }
                """;
        return builder
                .given("events 42 (5 ATTENDING, 2 WAITLISTED) and 7 (1 ATTENDING)")
                .uponReceiving("a GET /events/_bulk-attendance-summary?ids=42&ids=7 from event-service")
                    .path("/events/_bulk-attendance-summary")
                    .method("GET")
                    .query("ids=42&ids=7")
                .willRespondWith()
                    .status(200)
                    .headers(java.util.Map.of("Content-Type", "application/json"))
                    .body(body, "application/json")
                .toPact();
    }

    @Test
    @PactTestFor(pactMethod = "bulkAttendanceSummary_returnsMap")
    void bulk_attendance_summary_returns_map_by_event_id(MockServer mockServer) {
        RestAssured.given()
                .accept(ContentType.JSON)
                .when()
                    .get(mockServer.getUrl() + "/events/_bulk-attendance-summary?ids=42&ids=7")
                .then()
                    .statusCode(200)
                    .body("'42'.attending", equalTo(5))
                    .body("'7'.attending", equalTo(1));
    }
}
