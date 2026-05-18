package ch.unige.events.report.resource;

import ch.unige.events.report.dto.CreateReportRequest;
import ch.unige.events.report.dto.ReportDTO;
import ch.unige.events.report.service.ReportService;
import ch.unige.events.shared.domain.enums.ReportReason;
import ch.unige.events.shared.domain.enums.ReportStatus;
import ch.unige.events.shared.error.ApiErrorResponse;

import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * SCRUM-144 — CommentReportResource REST tests (Décision N).
 *
 * <p>Mock-based : the {@link ReportService#createForComment} chain is fully
 * tested separately in {@code ReportServiceCreateForCommentTest}. Here we
 * verify the HTTP contract only — status codes, body, auth gates, rate-limit
 * trip.
 *
 * <p>The rate-limit bucket {@code reports.commentCreate} (max=5/60 s, Décision U)
 * is per-principal — each test uses a distinct {@code @TestSecurity} user so
 * the burst test doesn't cross-contaminate the happy-path tests. The
 * {@link org.junit.jupiter.api.MethodOrderer.OrderAnnotation} pins the burst
 * last in case the same principal ever gets reused.
 */
@QuarkusTest
@org.junit.jupiter.api.TestMethodOrder(org.junit.jupiter.api.MethodOrderer.OrderAnnotation.class)
class CommentReportResourceTest {

    @InjectMock
    ReportService reportService;

    private static ReportDTO sampleDto(Long commentId) {
        return new ReportDTO(
                1L, null, commentId, null,
                UUID.randomUUID(), "Reporter-1",
                ReportReason.SPAM, "spammy comment",
                ReportStatus.PENDING, null,
                LocalDateTime.now(), null, null
        );
    }

    private static WebApplicationException apiError(int status, String error, String message) {
        return new WebApplicationException(
                Response.status(status)
                        .entity(new ApiErrorResponse(error, message))
                        .type(MediaType.APPLICATION_JSON_TYPE)
                        .build());
    }

    @Test
    @TestSecurity(user = "auth0|comment-report-happy")
    void postReport_happyPath_returns201() {
        when(reportService.createForComment(eq(42L), anyString(), any(CreateReportRequest.class)))
                .thenReturn(sampleDto(42L));

        given()
            .contentType("application/json")
            .body("{\"reason\":\"SPAM\",\"description\":\"spam content\"}")
            .when().post("/comments/42/report")
            .then().statusCode(201);
    }

    @Test
    @TestSecurity(user = "auth0|comment-report-404")
    void postReport_visibilityFails_returns404AntiOracle() {
        when(reportService.createForComment(eq(43L), anyString(), any(CreateReportRequest.class)))
                .thenThrow(new NotFoundException("Comment not found"));

        given()
            .contentType("application/json")
            .body("{\"reason\":\"SPAM\"}")
            .when().post("/comments/43/report")
            .then().statusCode(404);
    }

    @Test
    @TestSecurity(user = "auth0|comment-report-own")
    void postReport_ownComment_returns422() {
        when(reportService.createForComment(eq(44L), anyString(), any(CreateReportRequest.class)))
                .thenThrow(apiError(422, "cannot_report_own_comment",
                        "You cannot report your own comment."));

        given()
            .contentType("application/json")
            .body("{\"reason\":\"SPAM\"}")
            .when().post("/comments/44/report")
            .then().statusCode(422);
    }

    @Test
    @TestSecurity(user = "auth0|comment-report-dup")
    void postReport_alreadyReported_returns409() {
        when(reportService.createForComment(eq(45L), anyString(), any(CreateReportRequest.class)))
                .thenThrow(apiError(409, "already_reported",
                        "You have already reported this comment."));

        given()
            .contentType("application/json")
            .body("{\"reason\":\"SPAM\"}")
            .when().post("/comments/45/report")
            .then().statusCode(409);
    }

    @Test
    @TestSecurity(user = "auth0|comment-report-503")
    void postReport_engagementServiceDown_returns503() {
        when(reportService.createForComment(eq(46L), anyString(), any(CreateReportRequest.class)))
                .thenThrow(new WebApplicationException(
                        "Comment visibility check unavailable",
                        Response.Status.SERVICE_UNAVAILABLE));

        given()
            .contentType("application/json")
            .body("{\"reason\":\"SPAM\"}")
            .when().post("/comments/46/report")
            .then().statusCode(503);
    }

    @Test
    void postReport_unauthenticated_returns401() {
        // No @TestSecurity — caller is anonymous.
        given()
            .contentType("application/json")
            .body("{\"reason\":\"SPAM\"}")
            .when().post("/comments/47/report")
            .then().statusCode(401);
    }

    @Test
    @TestSecurity(user = "auth0|comment-report-validation")
    void postReport_missingReason_returns400() {
        // Bean validation triggers @NotNull on reason before the service is hit.
        given()
            .contentType("application/json")
            .body("{}")
            .when().post("/comments/48/report")
            .then().statusCode(400);
    }

    /**
     * Bursts the {@code reports.commentCreate} bucket (max=5/60 s, Décision U)
     * to assert the rate-limit interceptor + exception mapper combine into a
     * {@code 429}. Pinned last via {@link org.junit.jupiter.api.Order} since
     * the bucket is principal-scoped and survives across {@code @Test} methods
     * — re-using this caller before the test would invalidate the burst.
     */
    @org.junit.jupiter.api.Order(Integer.MAX_VALUE)
    @Test
    @TestSecurity(user = "auth0|comment-report-burst")
    void postReport_rateLimit_eventually_triggers429() {
        when(reportService.createForComment(eq(49L), anyString(), any(CreateReportRequest.class)))
                .thenReturn(sampleDto(49L));

        boolean saw429 = false;
        for (int i = 0; i < 10; i++) {
            int code = given()
                .contentType("application/json")
                .body("{\"reason\":\"SPAM\",\"description\":\"burst-" + i + "\"}")
                .when().post("/comments/49/report")
                .then().extract().statusCode();
            if (code == 429) {
                saw429 = true;
                break;
            }
        }
        org.junit.jupiter.api.Assertions.assertTrue(saw429,
                "Expected a 429 after 5 successful POSTs (bucket max=5/60s)");
    }
}
