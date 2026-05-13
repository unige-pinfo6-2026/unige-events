package ch.unige.events.event.resource;

import ch.unige.events.shared.client.EngagementServiceClient;

import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;

@QuarkusTest
class EventSearchResourceTest {

    @InjectMock @RestClient EngagementServiceClient engagementClient;

    @BeforeEach
    void setup() {
        lenient().when(engagementClient.getAttendanceSummariesBulk(any())).thenReturn(Map.of());
    }

    @Test
    void search_anonymous_returns200() {
        given()
            .queryParam("q", "yoga")
            .when().get("/events/search")
            .then().statusCode(200);
    }

    @Test
    void search_invalidSize_returns400() {
        given()
            .queryParam("size", 200)
            .when().get("/events/search")
            .then().statusCode(400);
    }

    @Test
    void search_byCategory() {
        given()
            .queryParam("category", "SPORTS")
            .when().get("/events/search")
            .then().statusCode(200);
    }

    @Test
    void search_byTags() {
        given()
            .queryParam("tags", "foo")
            .when().get("/events/search")
            .then().statusCode(200);
    }

    @Test
    void search_byDateRange() {
        given()
            .queryParam("dateFrom", "2099-01-01")
            .queryParam("dateTo", "2099-12-31")
            .when().get("/events/search")
            .then().statusCode(200);
    }
}
