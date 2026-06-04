package ch.unige.events.event.share.resource;

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
class ShareResourceTest {

    @InjectMock @RestClient EngagementServiceClient engagementClient;

    private final UUID userUuid = UUID.randomUUID();

    @BeforeEach
    void setup() {
        JwtTestContext.set(JwtTestHelper.jwtFor(userUuid));
        lenient().when(engagementClient.getAttendanceSummary(anyLong()))
                .thenReturn(AttendanceSummary.of(0L, 0L));
    }

    @AfterEach
    void clear() {
        JwtTestContext.clear();
    }

    private long createEvent() {
        String start = LocalDateTime.of(2999, Month.JANUARY, 1, 0, 0).plusDays(2).withNano(0).toString();
        String end = LocalDateTime.of(2999, Month.JANUARY, 1, 0, 0).plusDays(2).plusHours(2).withNano(0).toString();
        String body = "{\"title\":\"T\",\"description\":\"d\",\"location\":\"l\","
                + "\"startDate\":\"" + start + "\",\"endDate\":\"" + end + "\","
                + "\"category\":\"ACADEMIC\"}";
        return given()
            .contentType("application/json").body(body)
            .when().post("/events")
            .then().statusCode(201)
            .extract().jsonPath().getLong("id");
    }

    @Test
    @TestSecurity(user = "auth0|share")
    void getShareInfo_authenticated_returns200() {
        long id = createEvent();
        given().when().get("/events/" + id + "/share").then().statusCode(200);
    }

    @Test
    void getShareInfo_anonymous_returns401() {
        given().when().get("/events/1/share").then().statusCode(401);
    }

    @Test
    @TestSecurity(user = "auth0|share2")
    void redirect_validShortCode_returns302() {
        long id = createEvent();
        String shortCode = given().when().get("/events/" + id + "/share")
            .then().statusCode(200)
            .extract().jsonPath().getString("shortCode");

        given().redirects().follow(false)
            .when().get("/s/" + shortCode)
            .then().statusCode(302);
    }

    @Test
    void redirect_unknownShortCode_returns404() {
        given().redirects().follow(false)
            .when().get("/s/notfound")
            .then().statusCode(404);
    }
}
