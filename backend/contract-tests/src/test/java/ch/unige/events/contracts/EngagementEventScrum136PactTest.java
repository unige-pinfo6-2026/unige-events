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
 * Étape 6.2 of finalization spec.
 *
 * <p>Pins the cascade SCRUM-136 contract: when engagement-service calls
 * {@code GET /events/{id}?check-co-org-of=&lt;UUID&gt;}, the response
 * body MUST include {@code coOrganizerOf: bool} so the consumer doesn't
 * inline the cascade rule. Post Étape 2.2.4 the {@code event_co_organizers}
 * table moved into event-service, so the cascade is a single local SELECT.
 */
@ExtendWith(PactConsumerTestExt.class)
@PactTestFor(providerName = "event-service", pactVersion = PactSpecVersion.V3)
@SuppressWarnings("java:S100")
class EngagementEventScrum136PactTest {

    private static final String CALLER_UUID = "11111111-1111-1111-1111-111111111111";

    @Pact(consumer = "engagement-service", provider = "event-service")
    public RequestResponsePact getEvent_callerIsAcceptedCoOrganizer_returnsCoOrganizerOfTrue(PactDslWithProvider builder) {
        return builder
                .given("event 42 has caller " + CALLER_UUID + " as ACCEPTED co-organizer")
                .uponReceiving("a GET /events/42?check-co-org-of=" + CALLER_UUID + " from engagement-service")
                    .path("/events/42")
                    .method("GET")
                    .query("check-co-org-of=" + CALLER_UUID)
                .willRespondWith()
                    .status(200)
                    .headers(java.util.Map.of("Content-Type", "application/json"))
                    .body(new PactDslJsonBody()
                            .integerType("id", 42L)
                            .stringValue("status", "PUBLISHED")
                            .uuid("creatorId")
                            .booleanValue("coOrganizerOf", true))
                .toPact();
    }

    @Pact(consumer = "engagement-service", provider = "event-service")
    public RequestResponsePact getEvent_callerIsRandomUser_returnsCoOrganizerOfFalse(PactDslWithProvider builder) {
        return builder
                .given("event 42 has caller " + CALLER_UUID + " neither creator nor co-organizer")
                .uponReceiving("a GET /events/42?check-co-org-of=" + CALLER_UUID + " from engagement-service for non-organizer")
                    .path("/events/42")
                    .method("GET")
                    .query("check-co-org-of=" + CALLER_UUID)
                .willRespondWith()
                    .status(200)
                    .headers(java.util.Map.of("Content-Type", "application/json"))
                    .body(new PactDslJsonBody()
                            .integerType("id", 42L)
                            .stringValue("status", "PUBLISHED")
                            .uuid("creatorId")
                            .booleanValue("coOrganizerOf", false))
                .toPact();
    }

    @Test
    @PactTestFor(pactMethod = "getEvent_callerIsAcceptedCoOrganizer_returnsCoOrganizerOfTrue")
    void cascade_acceptedCoOrganizer_returnsTrue(MockServer mockServer) {
        RestAssured.given()
                .accept(ContentType.JSON)
                .when()
                    .get(mockServer.getUrl() + "/events/42?check-co-org-of=" + CALLER_UUID)
                .then()
                    .statusCode(200)
                    .body("coOrganizerOf", equalTo(true));
    }

    @Test
    @PactTestFor(pactMethod = "getEvent_callerIsRandomUser_returnsCoOrganizerOfFalse")
    void cascade_randomUser_returnsFalse(MockServer mockServer) {
        RestAssured.given()
                .accept(ContentType.JSON)
                .when()
                    .get(mockServer.getUrl() + "/events/42?check-co-org-of=" + CALLER_UUID)
                .then()
                    .statusCode(200)
                    .body("coOrganizerOf", equalTo(false));
    }
}
