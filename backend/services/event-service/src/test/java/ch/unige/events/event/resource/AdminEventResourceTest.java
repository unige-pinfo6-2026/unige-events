package ch.unige.events.event.resource;

import ch.unige.events.event.test.JwtTestContext;
import ch.unige.events.event.test.JwtTestHelper;
import ch.unige.events.shared.client.EngagementServiceClient;
import ch.unige.events.shared.domain.dto.AttendanceSummary;

import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.time.Month;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.lenient;

@QuarkusTest
class AdminEventResourceTest {

    @InjectMock @RestClient EngagementServiceClient engagementClient;

    private final UUID adminUuid = UUID.randomUUID();

    @BeforeEach
    void setup() {
        JwtTestContext.set(JwtTestHelper.adminJwt(adminUuid));
        lenient().when(engagementClient.getAttendanceSummary(anyLong()))
                .thenReturn(AttendanceSummary.of(0L, 0L));
    }

    @AfterEach
    void clear() {
        JwtTestContext.clear();
    }

    private static String createBody() {
        String start = LocalDateTime.of(2999, Month.JANUARY, 1, 0, 0).plusDays(2).withNano(0).toString();
        String end = LocalDateTime.of(2999, Month.JANUARY, 1, 0, 0).plusDays(2).plusHours(2).withNano(0).toString();
        return "{\"title\":\"AdmT\",\"description\":\"d\",\"location\":\"l\","
                + "\"startDate\":\"" + start + "\",\"endDate\":\"" + end + "\","
                + "\"category\":\"ACADEMIC\"}";
    }

    @Test
    @TestSecurity(user = "auth0|admin", roles = "ADMIN")
    void feature_byAdmin_returns200() {
        long id = given()
            .contentType("application/json").body(createBody())
            .when().post("/events")
            .then().statusCode(201)
            .extract().jsonPath().getLong("id");

        given()
            .when().patch("/admin/events/" + id + "/feature")
            .then().statusCode(200);
    }

    @Test
    @TestSecurity(user = "auth0|admin", roles = "ADMIN")
    void unfeature_byAdmin_returns200() {
        long id = given()
            .contentType("application/json").body(createBody())
            .when().post("/events")
            .then().statusCode(201)
            .extract().jsonPath().getLong("id");

        given()
            .when().patch("/admin/events/" + id + "/unfeature")
            .then().statusCode(200);
    }

    @Test
    @TestSecurity(user = "auth0|user", roles = "USER")
    void feature_byNonAdmin_returns403() {
        given()
            .when().patch("/admin/events/1/feature")
            .then().statusCode(403);
    }

    @Test
    void feature_anonymous_returns401() {
        given()
            .when().patch("/admin/events/1/feature")
            .then().statusCode(401);
    }
}
