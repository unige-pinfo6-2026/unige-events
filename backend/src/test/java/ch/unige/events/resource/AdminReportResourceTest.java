package ch.unige.events.resource;

import ch.unige.events.dto.report.ReportDTO;
import ch.unige.events.entity.ReportReason;
import ch.unige.events.entity.ReportStatus;
import ch.unige.events.service.ReportServiceMock;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import io.restassured.http.ContentType;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;

@QuarkusTest
class AdminReportResourceTest {

    @Inject
    ReportServiceMock reportServiceMock;

    @BeforeEach
    void setUp() {
        reportServiceMock.reset();
    }

    private ReportDTO seedFixture(Long id) {
        ReportDTO dto = new ReportDTO(
                id, 10L, "Event " + id, UUID.randomUUID(), "Reporter " + id,
                ReportReason.SPAM, "desc", ReportStatus.PENDING, null,
                LocalDateTime.now(), null, null);
        reportServiceMock.seedReport(dto);
        return dto;
    }

    // ── GET /admin/reports ──────────────────────────────────────────────────

    @Test
    @TestSecurity(user = "auth0|admin", roles = {"ADMIN"})
    void list_admin_returns200() {
        seedFixture(1L);
        seedFixture(2L);

        given()
                .when().get("/admin/reports")
                .then()
                .statusCode(200)
                .body("size()", equalTo(2))
                .body("[0].eventTitle", equalTo("Event 1"))
                .body("[0].reporterDisplayName", equalTo("Reporter 1"));
    }

    @Test
    @TestSecurity(user = "auth0|admin", roles = {"ADMIN"})
    void list_defaultStatusIsPending() {
        // Mock returns the fixture regardless of status; we just check no 4xx on default call.
        given()
                .when().get("/admin/reports")
                .then()
                .statusCode(200);
    }

    @Test
    @TestSecurity(user = "auth0|admin", roles = {"ADMIN"})
    void list_filterStatusDismissed_returns200() {
        given()
                .when().get("/admin/reports?status=DISMISSED")
                .then()
                .statusCode(200);
    }

    @Test
    @TestSecurity(user = "auth0|admin", roles = {"ADMIN"})
    void list_invalidStatus_returns404() {
        given()
                .when().get("/admin/reports?status=FOO")
                .then()
                .statusCode(404);
    }

    @Test
    @TestSecurity(user = "auth0|admin", roles = {"ADMIN"})
    void list_pagination_returns200() {
        given()
                .when().get("/admin/reports?page=1&size=5")
                .then()
                .statusCode(200);
    }

    @Test
    @TestSecurity(user = "auth0|admin", roles = {"ADMIN"})
    void list_sizeOver100_returns400() {
        given()
                .when().get("/admin/reports?size=101")
                .then()
                .statusCode(400);
    }

    @Test
    @TestSecurity(user = "auth0|admin", roles = {"ADMIN"})
    void list_negativePage_returns400() {
        given()
                .when().get("/admin/reports?page=-1")
                .then()
                .statusCode(400);
    }

    @Test
    void list_unauthenticated_returns401() {
        given()
                .when().get("/admin/reports")
                .then()
                .statusCode(401);
    }

    @Test
    @TestSecurity(user = "auth0|alice")
    void list_authenticatedNotAdmin_returns403() {
        given()
                .when().get("/admin/reports")
                .then()
                .statusCode(403);
    }

    // ── PATCH /admin/reports/{id} ───────────────────────────────────────────

    @Test
    @TestSecurity(user = "auth0|admin", roles = {"ADMIN"})
    void handle_validReviewed_returns200() {
        given()
                .contentType(ContentType.JSON)
                .body("{\"status\":\"REVIEWED\"}")
                .when().patch("/admin/reports/{id}", 1L)
                .then()
                .statusCode(200)
                .body("status", equalTo("REVIEWED"));
    }

    @Test
    @TestSecurity(user = "auth0|admin", roles = {"ADMIN"})
    void handle_validDismissed_returns200() {
        given()
                .contentType(ContentType.JSON)
                .body("{\"status\":\"DISMISSED\",\"moderationNote\":\"OK\"}")
                .when().patch("/admin/reports/{id}", 1L)
                .then()
                .statusCode(200)
                .body("status", equalTo("DISMISSED"));
    }

    @Test
    @TestSecurity(user = "auth0|admin", roles = {"ADMIN"})
    void handle_pendingStatus_returns400_invalidStatus() {
        ReportServiceMock.forceInvalidStatus = true;
        given()
                .contentType(ContentType.JSON)
                .body("{\"status\":\"PENDING\"}")
                .when().patch("/admin/reports/{id}", 1L)
                .then()
                .statusCode(400)
                .body("error", equalTo("invalid_status"));
    }

    @Test
    @TestSecurity(user = "auth0|admin", roles = {"ADMIN"})
    void handle_reportNotFound_returns404() {
        ReportServiceMock.forceNotFoundOnHandle = true;
        given()
                .contentType(ContentType.JSON)
                .body("{\"status\":\"REVIEWED\"}")
                .when().patch("/admin/reports/{id}", 999L)
                .then()
                .statusCode(404);
    }

    @Test
    @TestSecurity(user = "auth0|admin", roles = {"ADMIN"})
    void handle_alreadyHandled_returns409_invalidTransition() {
        ReportServiceMock.forceInvalidTransition = true;
        given()
                .contentType(ContentType.JSON)
                .body("{\"status\":\"REVIEWED\"}")
                .when().patch("/admin/reports/{id}", 1L)
                .then()
                .statusCode(409)
                .body("error", equalTo("invalid_transition"));
    }

    @Test
    void handle_unauthenticated_returns401() {
        given()
                .contentType(ContentType.JSON)
                .body("{\"status\":\"REVIEWED\"}")
                .when().patch("/admin/reports/{id}", 1L)
                .then()
                .statusCode(401);
    }

    @Test
    @TestSecurity(user = "auth0|alice")
    void handle_authenticatedNotAdmin_returns403() {
        given()
                .contentType(ContentType.JSON)
                .body("{\"status\":\"REVIEWED\"}")
                .when().patch("/admin/reports/{id}", 1L)
                .then()
                .statusCode(403);
    }

    @Test
    @TestSecurity(user = "auth0|admin", roles = {"ADMIN"})
    void handle_emptyBody_returns400() {
        given()
                .contentType(ContentType.JSON)
                .when().patch("/admin/reports/{id}", 1L)
                .then()
                .statusCode(400);
    }

    @Test
    @TestSecurity(user = "auth0|admin", roles = {"ADMIN"})
    void handle_moderationNoteTooLong_returns400() {
        String longText = "A".repeat(2001);
        given()
                .contentType(ContentType.JSON)
                .body("{\"status\":\"REVIEWED\",\"moderationNote\":\"" + longText + "\"}")
                .when().patch("/admin/reports/{id}", 1L)
                .then()
                .statusCode(400);
    }

    @Test
    @TestSecurity(user = "auth0|admin", roles = {"ADMIN"})
    void handle_returnsReviewedAtAndBy() {
        given()
                .contentType(ContentType.JSON)
                .body("{\"status\":\"DISMISSED\",\"moderationNote\":\"OK\"}")
                .when().patch("/admin/reports/{id}", 1L)
                .then()
                .statusCode(200)
                .body("reviewedAt", notNullValue())
                .body("reviewedBy", notNullValue())
                .body("moderationNote", equalTo("OK"));
    }
}
