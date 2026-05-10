package ch.unige.events.engagement.attendance.resource;

import ch.unige.events.engagement.attendance.entity.Attendance;
import ch.unige.events.shared.domain.enums.AttendanceStatus;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import org.eclipse.microprofile.config.ConfigProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;

@QuarkusTest
class AttendanceSummaryInternalResourceTest {

    private static String token() {
        return ConfigProvider.getConfig()
                .getOptionalValue("unige.internal-token", String.class)
                .orElse("");
    }

    @Test
    void getAttendanceSummary_unknownEvent_returnsZeros() {
        given()
            .header("X-Internal-Token", token())
            .when().get("/events/9999999/attendance-summary")
            .then()
            .statusCode(200)
            .body("attending", equalTo(0))
            .body("waitlisted", equalTo(0))
            .body("interested", equalTo(0));
    }

    @Test
    void getBulkAttendanceSummary_emptyIds_returnsEmptyMap() {
        given()
            .header("X-Internal-Token", token())
            .when().get("/events/_bulk-attendance-summary")
            .then()
            .statusCode(200)
            .body("$", equalTo(new java.util.HashMap<>()));
    }

    @AfterEach
    void cleanRows() {
        // Clean any rows persisted by the committed-tx tests below.
        QuarkusTransaction.requiringNew().run(() -> Attendance.delete("eventId in ?1",
                List.of(700_001L, 700_002L, 700_003L)));
    }

    @Test
    void getAttendanceSummary_withRows_returnsCounts() {
        Long eventId = 700_001L;
        QuarkusTransaction.requiringNew().run(() -> {
            persist(UUID.randomUUID(), eventId, AttendanceStatus.ATTENDING);
            persist(UUID.randomUUID(), eventId, AttendanceStatus.ATTENDING);
            persist(UUID.randomUUID(), eventId, AttendanceStatus.WAITLISTED);
        });

        given()
            .header("X-Internal-Token", token())
            .when().get("/events/" + eventId + "/attendance-summary")
            .then()
            .statusCode(200)
            .body("attending", equalTo(2))
            .body("waitlisted", equalTo(1));
    }

    @Test
    void getBulkAttendanceSummary_withIds_returnsAggregate() {
        Long e1 = 700_002L;
        Long e2 = 700_003L;
        QuarkusTransaction.requiringNew().run(() -> {
            persist(UUID.randomUUID(), e1, AttendanceStatus.ATTENDING);
            persist(UUID.randomUUID(), e2, AttendanceStatus.WAITLISTED);
        });

        given()
            .header("X-Internal-Token", token())
            .when().get("/events/_bulk-attendance-summary?ids=" + e1 + "&ids=" + e2)
            .then()
            .statusCode(200)
            .body("'" + e1 + "'.attending", equalTo(1))
            .body("'" + e2 + "'.waitlisted", equalTo(1));
    }

    @Test
    void getAttendanceSummary_missingInternalToken_returns404() {
        given()
            .when().get("/events/9999999/attendance-summary")
            .then()
            .statusCode(404);
    }

    @Test
    void getBulkAttendanceSummary_missingInternalToken_returns404() {
        given()
            .when().get("/events/_bulk-attendance-summary?ids=1&ids=2")
            .then()
            .statusCode(404);
    }

    private static void persist(UUID userId, Long eventId, AttendanceStatus status) {
        Attendance a = new Attendance();
        a.userId = userId;
        a.eventId = eventId;
        a.status = status;
        a.persist();
    }
}
