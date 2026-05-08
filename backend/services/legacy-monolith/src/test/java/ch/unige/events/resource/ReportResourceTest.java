package ch.unige.events.resource;

import ch.unige.events.service.ReportServiceMock;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import io.restassured.http.ContentType;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;

@QuarkusTest
class ReportResourceTest {

    @Inject
    ReportServiceMock reportServiceMock;

    @BeforeEach
    void setUp() {
        reportServiceMock.reset();
    }

    @Test
    @TestSecurity(user = "auth0|alice")
    void report_validBody_returns201() {
        // Regression: frontend was sending French labels ("Spam") instead of enum
        // constants ("SPAM"), causing 400 — see PR for feature/s6-report-modal.
        given()
                .contentType(ContentType.JSON)
                .body("{\"reason\":\"SPAM\",\"description\":\"Faux event\"}")
                .when().post("/events/{id}/report", 1L)
                .then()
                .statusCode(201)
                .body("status", equalTo("PENDING"))
                .body("reason", equalTo("SPAM"))
                .body("description", equalTo("Faux event"))
                .body("eventId", equalTo(1));
    }

    @Test
    @TestSecurity(user = "auth0|alice")
    void report_validWithoutDescription_returns201() {
        given()
                .contentType(ContentType.JSON)
                .body("{\"reason\":\"INAPPROPRIATE\"}")
                .when().post("/events/{id}/report", 1L)
                .then()
                .statusCode(201)
                .body("reason", equalTo("INAPPROPRIATE"));
    }

    @Test
    @TestSecurity(user = "auth0|alice")
    void report_eventNotFound_returns404() {
        ReportServiceMock.forceNotFoundOnCreate = true;
        given()
                .contentType(ContentType.JSON)
                .body("{\"reason\":\"SPAM\"}")
                .when().post("/events/{id}/report", 999L)
                .then()
                .statusCode(404);
    }

    @Test
    @TestSecurity(user = "auth0|alice")
    void report_eventDraft_returns400_cannotReportDraft() {
        ReportServiceMock.forceCannotReportDraft = true;
        given()
                .contentType(ContentType.JSON)
                .body("{\"reason\":\"SPAM\"}")
                .when().post("/events/{id}/report", 1L)
                .then()
                .statusCode(400)
                .body("error", equalTo("cannot_report_draft"));
    }

    @Test
    @TestSecurity(user = "auth0|alice")
    void report_eventCancelled_returns400_cannotReportCancelled() {
        ReportServiceMock.forceCannotReportCancelled = true;
        given()
                .contentType(ContentType.JSON)
                .body("{\"reason\":\"SPAM\"}")
                .when().post("/events/{id}/report", 1L)
                .then()
                .statusCode(400)
                .body("error", equalTo("cannot_report_cancelled"));
    }

    @Test
    @TestSecurity(user = "auth0|alice")
    void report_eventBanned_returns400_cannotReportBanned() {
        ReportServiceMock.forceCannotReportBanned = true;
        given()
                .contentType(ContentType.JSON)
                .body("{\"reason\":\"SPAM\"}")
                .when().post("/events/{id}/report", 1L)
                .then()
                .statusCode(400)
                .body("error", equalTo("cannot_report_banned"));
    }

    @Test
    @TestSecurity(user = "auth0|alice")
    void report_ownEvent_returns422_cannotReportOwn() {
        ReportServiceMock.forceCannotReportOwn = true;
        given()
                .contentType(ContentType.JSON)
                .body("{\"reason\":\"SPAM\"}")
                .when().post("/events/{id}/report", 1L)
                .then()
                .statusCode(422)
                .body("error", equalTo("cannot_report_own_event"));
    }

    @Test
    @TestSecurity(user = "auth0|bob")
    void report_alreadyReported_returns409() {
        ReportServiceMock.forceAlreadyReported = true;
        given()
                .contentType(ContentType.JSON)
                .body("{\"reason\":\"SPAM\"}")
                .when().post("/events/{id}/report", 1L)
                .then()
                .statusCode(409)
                .body("error", equalTo("already_reported"));
    }

    @Test
    void report_unauthenticated_returns401() {
        given()
                .contentType(ContentType.JSON)
                .body("{\"reason\":\"SPAM\"}")
                .when().post("/events/{id}/report", 1L)
                .then()
                .statusCode(401);
    }

    @Test
    @TestSecurity(user = "auth0|alice")
    void report_missingReason_returns400() {
        given()
                .contentType(ContentType.JSON)
                .body("{}")
                .when().post("/events/{id}/report", 1L)
                .then()
                .statusCode(400);
    }

    @Test
    @TestSecurity(user = "auth0|alice")
    void report_invalidReason_returns400() {
        // Regression: frontend was sending French labels ("Spam") instead of enum
        // constants ("SPAM"), causing 400 — see PR for feature/s6-report-modal.
        given()
                .contentType(ContentType.JSON)
                .body("{\"reason\":\"FOO\"}")
                .when().post("/events/{id}/report", 1L)
                .then()
                .statusCode(400);
    }

    @Test
    @TestSecurity(user = "auth0|alice")
    void report_descriptionTooLong_returns400() {
        String longText = "A".repeat(2001);
        given()
                .contentType(ContentType.JSON)
                .body("{\"reason\":\"SPAM\",\"description\":\"" + longText + "\"}")
                .when().post("/events/{id}/report", 1L)
                .then()
                .statusCode(400);
    }

    @Test
    @TestSecurity(user = "auth0|alice")
    void report_emptyBody_returns400() {
        given()
                .contentType(ContentType.JSON)
                .when().post("/events/{id}/report", 1L)
                .then()
                .statusCode(400);
    }

    @Test
    @TestSecurity(user = "auth0|alice")
    void report_dtoIncludesAllFields() {
        given()
                .contentType(ContentType.JSON)
                .body("{\"reason\":\"FAKE\",\"description\":\"Faux profil\"}")
                .when().post("/events/{id}/report", 5L)
                .then()
                .statusCode(201)
                .body("id", notNullValue())
                .body("eventId", equalTo(5))
                .body("reporterId", notNullValue())
                .body("createdAt", notNullValue());
    }
}
