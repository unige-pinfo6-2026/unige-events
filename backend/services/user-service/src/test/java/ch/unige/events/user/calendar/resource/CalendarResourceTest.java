package ch.unige.events.user.calendar.resource;

import ch.unige.events.shared.client.EngagementServiceClient;
import ch.unige.events.shared.client.EventServiceClient;
import ch.unige.events.user.entity.User;
import ch.unige.events.user.test.JwtTestContext;
import ch.unige.events.user.test.JwtTestHelper;
import ch.unige.events.user.test.TestFixtures;

import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import jakarta.inject.Inject;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.notNullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * REST tests for {@link CalendarResource} and
 * {@link UserCalendarTokenResource}. Cross-service REST clients are
 * mocked via {@link InjectMock}.
 */
@QuarkusTest
class CalendarResourceTest {

    @Inject TestFixtures fixtures;

    @InjectMock @RestClient EngagementServiceClient engagementClient;
    @InjectMock @RestClient EventServiceClient eventClient;

    @BeforeEach
    void clearJwt() {
        JwtTestContext.clear();
        fixtures.truncateAll();
        // Default empty stubs so fallbacks don't kick in.
        lenient().when(engagementClient.getUserAttendances(any(UUID.class), anyString()))
                .thenReturn(List.of());
        lenient().when(eventClient.findByIds(anyList(), anyString()))
                .thenReturn(List.of());
    }

    @AfterEach
    void resetJwt() {
        JwtTestContext.clear();
        fixtures.truncateAll();
    }

    @Test
    @TestSecurity(user = "auth0|cr-token-get")
    void getCalendarToken_authedUser_returns200WithToken() {
        fixtures.persistUser("auth0|cr-token-get", "cr-token-get@example.com", true);
        JwtTestContext.set(JwtTestHelper.jwtFor("auth0|cr-token-get"));

        given()
            .when().get("/users/me/calendar-token")
            .then()
            .statusCode(200)
            .body("calendarToken", notNullValue())
            .body("webcalUrl", containsString("webcal://"))
            .body("httpsUrl", notNullValue());
    }

    @Test
    @TestSecurity(user = "auth0|cr-token-regen")
    void regenerateCalendarToken_authedUser_returns200() {
        fixtures.persistUser("auth0|cr-token-regen", "cr-token-regen@example.com", true);
        JwtTestContext.set(JwtTestHelper.jwtFor("auth0|cr-token-regen"));

        given()
            .when().post("/users/me/calendar-token/regenerate")
            .then()
            .statusCode(200)
            .body("calendarToken", notNullValue());
    }

    @Test
    void getCalendarToken_unauthenticated_returns401() {
        given()
            .when().get("/users/me/calendar-token")
            .then()
            .statusCode(401);
    }

    @Test
    void getIcsFeed_validToken_returns200() {
        UUID token = UUID.randomUUID();
        User user = fixtures.persistUser("auth0|cr-ics", "cr-ics@example.com", true);
        fixtures.setCalendarToken(user.id, token);

        given()
            .when().get("/calendar/" + token + ".ics")
            .then()
            .statusCode(200)
            .header("Content-Disposition", containsString("unige-events.ics"));
    }

    @Test
    void getIcsFeed_unknownToken_returns404() {
        given()
            .when().get("/calendar/" + UUID.randomUUID() + ".ics")
            .then()
            .statusCode(404);
    }
}
