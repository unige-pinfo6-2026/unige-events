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

    @Test
    void getUserAttendances_tokenMismatch_returns404SameBodyAsMissing() {
        // Anti-oracle: wrong X-Internal-Token returns the same 404 body
        // as a missing one — otherwise "endpoint exists, token wrong" leaks.
        UUID unknown = UUID.randomUUID();
        io.restassured.response.Response missing = given()
                .when().get("/users/" + unknown + "/attendances?status=ATTENDING");
        io.restassured.response.Response wrong = given()
                .header("X-Internal-Token", "wrong-token-value")
                .when().get("/users/" + unknown + "/attendances?status=ATTENDING");

        org.junit.jupiter.api.Assertions.assertEquals(404, missing.getStatusCode());
        org.junit.jupiter.api.Assertions.assertEquals(404, wrong.getStatusCode());
        org.junit.jupiter.api.Assertions.assertEquals(
                missing.getBody().asString(), wrong.getBody().asString(),
                "Token-mismatch body must equal missing-route body (anti-oracle)");
    }
}
