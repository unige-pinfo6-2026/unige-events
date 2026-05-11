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
 * Étape 6.4 of finalization spec.
 *
 * <p>Pins the user-service ↔ event-service bulk-lookup contract used
 * by the calendar ICS feed: GET /events?ids=42,7,1&amp;status=PUBLISHED
 * returns a List&lt;EventDTO&gt; filtered to the listed ids and the
 * given status. The user-service builds the ICS feed by combining
 * favorites + attendances → distinct event ids → bulk fetch.
 */
@ExtendWith(PactConsumerTestExt.class)
@PactTestFor(providerName = "event-service", pactVersion = PactSpecVersion.V3)
@SuppressWarnings("java:S100")
class UserEventBulkPactTest {

    @Pact(consumer = "user-service", provider = "event-service")
    public RequestResponsePact getEvents_byIdsAndStatus_returnsFilteredList(PactDslWithProvider builder) {
        // Body literal — both elements share the same shape (PUBLISHED filter
        // applied server-side, so DRAFT id=1 is filtered out at source).
        String body = """
                [
                  {"id": 42, "title": "Hackathon", "status": "PUBLISHED"},
                  {"id": 7,  "title": "Stand-up",  "status": "PUBLISHED"}
                ]
                """;
        return builder
                .given("events 42, 7, 1 exist; 42 + 7 are PUBLISHED; 1 is DRAFT")
                .uponReceiving("a GET /events?ids=42,7,1&status=PUBLISHED from user-service")
                    .path("/events")
                    .method("GET")
                    .query("ids=42&ids=7&ids=1&status=PUBLISHED")
                .willRespondWith()
                    .status(200)
                    .headers(java.util.Map.of("Content-Type", "application/json"))
                    .body(body, "application/json")
                .toPact();
    }

    @Test
    @PactTestFor(pactMethod = "getEvents_byIdsAndStatus_returnsFilteredList")
    void calendar_bulk_lookup_returns_only_published(MockServer mockServer) {
        RestAssured.given()
                .accept(ContentType.JSON)
                .when()
                    .get(mockServer.getUrl() + "/events?ids=42&ids=7&ids=1&status=PUBLISHED")
                .then()
                    .statusCode(200)
                    .body("size()", equalTo(2))
                    .body("[0].status", equalTo("PUBLISHED"))
                    .body("[1].status", equalTo("PUBLISHED"));
    }
}
