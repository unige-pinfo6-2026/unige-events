package ch.unige.events.event.favorite.resource;

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
import java.util.Map;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.lenient;

@QuarkusTest
@TestSecurity(user = "auth0|fav-rs")
class FavoriteResourceTest {

    @InjectMock @RestClient EngagementServiceClient engagementClient;

    private final UUID userUuid = UUID.randomUUID();

    @BeforeEach
    void setup() {
        JwtTestContext.set(JwtTestHelper.jwtFor(userUuid));
        lenient().when(engagementClient.getAttendanceSummariesBulk(any())).thenReturn(Map.of());
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
    void addFavorite_returns200() {
        long id = createEvent();
        given()
            .when().post("/events/" + id + "/favorite")
            .then().statusCode(200);
    }

    @Test
    void removeFavorite_existing_returns204() {
        long id = createEvent();
        given().when().post("/events/" + id + "/favorite").then().statusCode(200);
        given().when().delete("/events/" + id + "/favorite").then().statusCode(204);
    }

    @Test
    void getMyFavorites_returns200() {
        given().when().get("/users/me/favorites").then().statusCode(200);
    }

    @Test
    void addFavorite_unknownEvent_returns404() {
        given()
            .when().post("/events/9999986/favorite")
            .then().statusCode(404);
    }
}
