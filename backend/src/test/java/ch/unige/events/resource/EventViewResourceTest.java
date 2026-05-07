package ch.unige.events.resource;

import ch.unige.events.service.EventViewServiceMock;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

@QuarkusTest
class EventViewResourceTest {

    @Inject
    EventViewServiceMock eventViewServiceMock;

    @BeforeEach
    void setUp() {
        eventViewServiceMock.reset();
    }

    @Test
    @TestSecurity(user = "auth0|alice")
    void recordView_firstTime_returns204() {
        given()
                .when().post("/events/{id}/view", 1L)
                .then()
                .statusCode(204);
    }

    @Test
    @TestSecurity(user = "auth0|alice")
    void recordView_secondTime_returns204Idempotent() {
        // Regression: duplicate key on uq_event_view_user_event when re-viewing event
        // (commit 2837585 reverted prior fix 3591f37). Repeat POST must stay 204.
        given()
                .when().post("/events/{id}/view", 1L)
                .then()
                .statusCode(204);

        given()
                .when().post("/events/{id}/view", 1L)
                .then()
                .statusCode(204);
    }

    @Test
    void recordView_unauthenticated_returns401() {
        given()
                .when().post("/events/{id}/view", 1L)
                .then()
                .statusCode(401);
    }

    @Test
    @TestSecurity(user = "auth0|alice")
    void recordView_unknownEvent_returns404() {
        EventViewServiceMock.forceNotFound = true;

        given()
                .when().post("/events/{id}/view", 999L)
                .then()
                .statusCode(404)
                .body("error", is("not_found"));
    }
}
