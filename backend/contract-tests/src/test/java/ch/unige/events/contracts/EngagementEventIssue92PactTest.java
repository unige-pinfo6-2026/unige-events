package ch.unige.events.contracts;

import au.com.dius.pact.consumer.MockServer;
import au.com.dius.pact.consumer.dsl.PactDslJsonBody;
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
 * Étape 6.1 of finalization spec.
 *
 * <p>Consumer-driven Pact contract: engagement-service relies on
 * event-service applying the ISSUE-92 anti-oracle on
 * {@code GET /events/{id}}: a DRAFT event must surface as 404 to
 * non-creators (same envelope as a missing event), so the cascade for
 * post-comment / RSVP is closed without leaking existence.
 *
 * <p>This pact pins both happy path (PUBLISHED → 200 + EventDTO payload)
 * and the anti-oracle (DRAFT non-creator → 404 + canonical error envelope).
 */
@ExtendWith(PactConsumerTestExt.class)
@PactTestFor(providerName = "event-service", pactVersion = PactSpecVersion.V3)
@SuppressWarnings("java:S100")
class EngagementEventIssue92PactTest {

    @Pact(consumer = "engagement-service", provider = "event-service")
    public RequestResponsePact getEvent_published_returns200(PactDslWithProvider builder) {
        return builder
                .given("event 42 is PUBLISHED")
                .uponReceiving("a GET /events/42 from engagement-service")
                    .path("/events/42")
                    .method("GET")
                .willRespondWith()
                    .status(200)
                    .headers(java.util.Map.of("Content-Type", "application/json"))
                    .body(new PactDslJsonBody()
                            .integerType("id", 42L)
                            .stringType("title", "Hackathon")
                            .stringValue("status", "PUBLISHED")
                            .uuid("creatorId"))
                .toPact();
    }

    @Pact(consumer = "engagement-service", provider = "event-service")
    public RequestResponsePact getEvent_draftByNonCreator_returns404(PactDslWithProvider builder) {
        return builder
                .given("event 99 is DRAFT and caller is not creator")
                .uponReceiving("a GET /events/99 from engagement-service")
                    .path("/events/99")
                    .method("GET")
                .willRespondWith()
                    .status(404)
                    .headers(java.util.Map.of("Content-Type", "application/json"))
                    .body(new PactDslJsonBody()
                            .stringType("error", "not_found"))
                .toPact();
    }

    @Test
    @PactTestFor(pactMethod = "getEvent_published_returns200")
    void published_event_returns_200(MockServer mockServer) {
        RestAssured.given()
                .accept(ContentType.JSON)
                .when()
                    .get(mockServer.getUrl() + "/events/42")
                .then()
                    .statusCode(200)
                    .body("id", equalTo(42))
                    .body("status", equalTo("PUBLISHED"));
    }

    @Test
    @PactTestFor(pactMethod = "getEvent_draftByNonCreator_returns404")
    void draft_by_non_creator_returns_404_antiOracle(MockServer mockServer) {
        RestAssured.given()
                .accept(ContentType.JSON)
                .when()
                    .get(mockServer.getUrl() + "/events/99")
                .then()
                    .statusCode(404)
                    .body("error", equalTo("not_found"));
    }
}
