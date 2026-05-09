package ch.unige.events.engagement.attendance.resource;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;

@QuarkusTest
class AttendanceSummaryInternalResourceTest {

    @Test
    void getAttendanceSummary_unknownEvent_returnsZeros() {
        given()
            .when().get("/events/9999999/attendance-summary")
            .then()
            .statusCode(200)
            .body("attending", equalTo(0))
            .body("waitlisted", equalTo(0))
            .body("interested", equalTo(0));
    }
}
