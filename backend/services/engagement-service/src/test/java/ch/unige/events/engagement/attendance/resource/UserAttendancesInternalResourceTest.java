package ch.unige.events.engagement.attendance.resource;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.empty;

@QuarkusTest
class UserAttendancesInternalResourceTest {

    @Test
    void getUserAttendances_unknownUser_returnsEmptyList() {
        UUID unknown = UUID.randomUUID();
        given()
            .when().get("/users/" + unknown + "/attendances?status=ATTENDING")
            .then()
            .statusCode(200)
            .body("$", empty());
    }
}
