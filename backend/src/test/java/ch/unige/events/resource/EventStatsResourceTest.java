package ch.unige.events.resource;

import ch.unige.events.service.EventStatsServiceMock;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import io.restassured.http.ContentType;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

@QuarkusTest
class EventStatsResourceTest {

    @Inject
    EventStatsServiceMock eventStatsServiceMock;

    @BeforeEach
    void setUp() {
        eventStatsServiceMock.reset();
    }

    @Test
    @TestSecurity(user = "auth0|alice")
    void getStats_asCreator_returns200WithCounts() {
        eventStatsServiceMock.seed(5L, 3L, 12L);

        given()
                .when().get("/events/{id}/stats", 1L)
                .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .body("attendingCount", equalTo(5))
                .body("interestedCount", equalTo(3))
                .body("viewCount", equalTo(12));
    }

    @Test
    @TestSecurity(user = "auth0|alice")
    void getStats_zeroCounts_returns200() {
        eventStatsServiceMock.seed(0L, 0L, 0L);

        given()
                .when().get("/events/{id}/stats", 1L)
                .then()
                .statusCode(200)
                .body("attendingCount", equalTo(0))
                .body("interestedCount", equalTo(0))
                .body("viewCount", equalTo(0));
    }

    @Test
    void getStats_unauthenticated_returns401() {
        given()
                .when().get("/events/{id}/stats", 1L)
                .then()
                .statusCode(401);
    }

    @Test
    @TestSecurity(user = "auth0|alice")
    void getStats_unknownEvent_returns404() {
        EventStatsServiceMock.forceNotFound = true;

        given()
                .when().get("/events/{id}/stats", 999L)
                .then()
                .statusCode(404)
                .body("error", is("not_found"));
    }

    @Test
    @TestSecurity(user = "auth0|alice")
    void getStats_notCreator_returns403() {
        EventStatsServiceMock.forceForbidden = true;

        given()
                .when().get("/events/{id}/stats", 1L)
                .then()
                .statusCode(403)
                .body("error", is("forbidden"));
    }
}
