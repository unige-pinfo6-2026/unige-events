package ch.unige.events.event.me.resource;

import ch.unige.events.event.test.JwtTestContext;
import ch.unige.events.event.test.JwtTestHelper;
import ch.unige.events.shared.client.EngagementServiceClient;

import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;

@QuarkusTest
class MyEventsResourceTest {

    @InjectMock @RestClient EngagementServiceClient engagementClient;

    private final UUID userUuid = UUID.randomUUID();

    @BeforeEach
    void setup() {
        JwtTestContext.set(JwtTestHelper.jwtFor(userUuid));
        lenient().when(engagementClient.getAttendanceSummariesBulk(any())).thenReturn(Map.of());
    }

    @AfterEach
    void clear() {
        JwtTestContext.clear();
    }

    @Test
    @TestSecurity(user = "auth0|me")
    void getMyEvents_authenticated_returns200() {
        given().when().get("/users/me/events").then().statusCode(200);
    }

    @Test
    @TestSecurity(user = "auth0|me")
    void getMyEvents_filterByStatus_returns200() {
        given()
            .queryParam("status", "DRAFT")
            .when().get("/users/me/events")
            .then().statusCode(200);
    }

    @Test
    void getMyEvents_anonymous_returns401() {
        given().when().get("/users/me/events").then().statusCode(401);
    }
}
