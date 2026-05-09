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
 * Étape 6.3 of finalization spec.
 *
 * <p>Pins the moderation-service ↔ event-service contract: moderation
 * needs to read the current Event status before processing a report
 * (so the auto-ban Kafka message is fired idempotently — if the event
 * is already BANNED, the action is a no-op). The contract guarantees
 * the {@code status} field is always present on the response.
 */
@ExtendWith(PactConsumerTestExt.class)
@PactTestFor(providerName = "event-service", pactVersion = PactSpecVersion.V3)
@SuppressWarnings("java:S100")
class ModerationEventPactTest {

    @Pact(consumer = "moderation-service", provider = "event-service")
    public RequestResponsePact getEvent_returnsStatus(PactDslWithProvider builder) {
        return builder
                .given("event 7 exists and is PUBLISHED")
                .uponReceiving("a GET /events/7 from moderation-service")
                    .path("/events/7")
                    .method("GET")
                .willRespondWith()
                    .status(200)
                    .headers(java.util.Map.of("Content-Type", "application/json"))
                    .body(new PactDslJsonBody()
                            .integerType("id", 7L)
                            .stringType("title", "Stand-up")
                            .stringValue("status", "PUBLISHED")
                            .uuid("creatorId"))
                .toPact();
    }

    @Test
    @PactTestFor(pactMethod = "getEvent_returnsStatus")
    void moderation_reads_event_status(MockServer mockServer) {
        RestAssured.given()
                .accept(ContentType.JSON)
                .when()
                    .get(mockServer.getUrl() + "/events/7")
                .then()
                    .statusCode(200)
                    .body("id", equalTo(7))
                    .body("status", equalTo("PUBLISHED"));
    }
}
