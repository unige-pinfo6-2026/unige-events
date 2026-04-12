package ch.unige.events.resource;

import ch.unige.events.entity.Event;
import ch.unige.events.entity.AttendanceStatus;
import ch.unige.events.service.AttendanceServiceMock;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import io.restassured.http.ContentType;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

@QuarkusTest
class AttendanceResourceTest {

    @Inject
    AttendanceServiceMock attendanceServiceMock;

    @BeforeEach
    void setUp() {
        attendanceServiceMock.reset();
    }

    // =========================================================
    // POST /events/{id}/attend
    // =========================================================

    @Test
    @TestSecurity(user = "auth0|alice")
    void attend_authenticated_returns200() {
        Event event = attendanceServiceMock.seedEvent("Conférence UNIGE");

        given()
                .contentType(ContentType.JSON)
                .body("{\"status\":\"ATTENDING\"}")
                .when().post("/events/{id}/attend", event.id)
                .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .body("status", equalTo("ATTENDING"))
                .body("eventId", equalTo(event.id.intValue()));
    }

    @Test
    void attend_unauthenticated_returns401() {
        given()
                .contentType(ContentType.JSON)
                .body("{\"status\":\"ATTENDING\"}")
                .when().post("/events/{id}/attend", 1L)
                .then()
                .statusCode(401);
    }

    @Test
    @TestSecurity(user = "auth0|alice")
    void attend_unknownEvent_returns404() {
        AttendanceServiceMock.forceNotFoundOnAttend = true;

        given()
                .contentType(ContentType.JSON)
                .body("{\"status\":\"ATTENDING\"}")
                .when().post("/events/{id}/attend", 999L)
                .then()
                .statusCode(404)
                .body("error", is("not_found"));
    }

    @Test
    @TestSecurity(user = "auth0|alice")
    void attend_capacityReached_returns409() {
        Event event = attendanceServiceMock.seedEvent("Full Event");
        AttendanceServiceMock.forceCapacityConflict = true;

        given()
                .contentType(ContentType.JSON)
                .body("{\"status\":\"ATTENDING\"}")
                .when().post("/events/{id}/attend", event.id)
                .then()
                .statusCode(409);
    }

    @Test
    @TestSecurity(user = "auth0|alice")
    void attend_missingStatus_returns400() {
        Event event = attendanceServiceMock.seedEvent("Conférence UNIGE 3");

        given()
                .contentType(ContentType.JSON)
                .body("{}")
                .when().post("/events/{id}/attend", event.id)
                .then()
                .statusCode(400);
    }

    @Test
    @TestSecurity(user = "auth0|alice")
    void attend_nullBody_returns400() {
        Event event = attendanceServiceMock.seedEvent("Conférence UNIGE NPE");

        given()
                .contentType(ContentType.JSON)
                // Pas de .body() — body absent → null côté JAX-RS
                .when().post("/events/{id}/attend", event.id)
                .then()
                .statusCode(400);
    }

    @Test
    @TestSecurity(user = "auth0|alice")
    void attend_draftEvent_returns400() {
        Event event = attendanceServiceMock.seedEvent("Draft Event");
        AttendanceServiceMock.forceDraftConflict = true;

        given()
                .contentType(ContentType.JSON)
                .body("{\"status\":\"ATTENDING\"}")
                .when().post("/events/{id}/attend", event.id)
                .then()
                .statusCode(400);
    }

    // =========================================================
    // DELETE /events/{id}/attend
    // =========================================================

    @Test
    @TestSecurity(user = "auth0|alice")
    void removeAttendance_existingAttendance_returns204() {
        Event event = attendanceServiceMock.seedEvent("Conférence UNIGE");
        attendanceServiceMock.seedAttendance(event.id, AttendanceStatus.ATTENDING);

        given()
                .when().delete("/events/{id}/attend", event.id)
                .then()
                .statusCode(204);
    }

    @Test
    void removeAttendance_unauthenticated_returns401() {
        given()
                .when().delete("/events/{id}/attend", 1L)
                .then()
                .statusCode(401);
    }

    @Test
    @TestSecurity(user = "auth0|alice")
    void removeAttendance_notAttending_returns404() {
        AttendanceServiceMock.forceNotFoundOnRemove = true;

        given()
                .when().delete("/events/{id}/attend", 999L)
                .then()
                .statusCode(404)
                .body("error", is("not_found"));
    }

    // =========================================================
    // GET /events/{id}/attendees
    // =========================================================

    @Test
    @TestSecurity(user = "auth0|alice")
    void getAttendees_byCreator_returns200() {
        Event event = attendanceServiceMock.seedEvent("Conférence UNIGE");
        attendanceServiceMock.seedAttendance(event.id, AttendanceStatus.ATTENDING);

        given()
                .when().get("/events/{id}/attendees", event.id)
                .then()
                .statusCode(200)
                .contentType(ContentType.JSON);
    }

    @Test
    void getAttendees_unauthenticated_returns401() {
        given()
                .when().get("/events/{id}/attendees", 1L)
                .then()
                .statusCode(401);
    }

    @Test
    @TestSecurity(user = "auth0|alice")
    void getAttendees_forbidden_returns403() {
        Event event = attendanceServiceMock.seedEvent("Conférence UNIGE");
        AttendanceServiceMock.forceForbiddenOnGetAttendees = true;

        given()
                .when().get("/events/{id}/attendees", event.id)
                .then()
                .statusCode(403)
                .body("error", is("forbidden"));
    }

    @Test
    @TestSecurity(user = "auth0|alice")
    void getAttendees_unknownEvent_returns404() {
        AttendanceServiceMock.forceNotFoundOnAttend = true;

        given()
                .when().get("/events/{id}/attendees", 999L)
                .then()
                .statusCode(404)
                .body("error", is("not_found"));
    }
}
