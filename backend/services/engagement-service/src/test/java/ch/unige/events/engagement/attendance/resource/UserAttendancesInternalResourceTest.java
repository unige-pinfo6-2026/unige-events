package ch.unige.events.engagement.attendance.resource;

import io.quarkus.test.junit.QuarkusTest;
import org.eclipse.microprofile.config.ConfigProvider;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.empty;

@QuarkusTest
class UserAttendancesInternalResourceTest {

    private static String token() {
        return ConfigProvider.getConfig()
                .getOptionalValue("unige.internal-token", String.class)
                .orElse("");
    }

    @Test
    void getUserAttendances_unknownUser_returnsEmptyList() {
        UUID unknown = UUID.randomUUID();
        given()
            .header("X-Internal-Token", token())
            .when().get("/users/" + unknown + "/attendances?status=ATTENDING")
            .then()
            .statusCode(200)
            .body("$", empty());
    }

    @Test
    void getUserAttendances_missingInternalToken_returns404() {
        UUID unknown = UUID.randomUUID();
        given()
            .when().get("/users/" + unknown + "/attendances?status=ATTENDING")
            .then()
            .statusCode(404);
    }
}
