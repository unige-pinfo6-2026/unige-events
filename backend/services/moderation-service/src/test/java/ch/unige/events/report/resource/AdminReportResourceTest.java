package ch.unige.events.report.resource;

import ch.unige.events.report.entity.Report;
import ch.unige.events.report.test.JwtTestContext;
import ch.unige.events.report.test.JwtTestHelper;
import ch.unige.events.shared.client.EventServiceClient;
import ch.unige.events.shared.client.UserServiceClient;
import ch.unige.events.shared.domain.dto.EventDTO;
import ch.unige.events.shared.domain.dto.UserPublicResponse;
import ch.unige.events.shared.domain.enums.EventStatus;
import ch.unige.events.shared.domain.enums.ReportReason;
import ch.unige.events.shared.domain.enums.ReportStatus;

import io.quarkus.hibernate.orm.panache.PanacheQuery;
import io.quarkus.panache.mock.PanacheMock;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.time.Month;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@QuarkusTest
@TestSecurity(user = "auth0|test-admin-resource", roles = "ADMIN")
class AdminReportResourceTest {

    @InjectMock
    @RestClient
    EventServiceClient eventClient;

    @InjectMock
    @RestClient
    UserServiceClient userClient;

    private final UUID adminId = UUID.randomUUID();
    private final UUID reporterId = UUID.randomUUID();
    private final UUID creatorId = UUID.randomUUID();

    @BeforeEach
    void stageJwt() {
        JwtTestContext.set(JwtTestHelper.adminJwt(adminId));
        lenient().when(userClient.getById(any(UUID.class))).thenAnswer(inv -> {
            UUID id = inv.getArgument(0);
            return new UserPublicResponse(id, "User-" + id, null, null, null,
                    null, null, null, 0L, 0L, null);
        });
    }

    @AfterEach
    void clearJwt() {
        JwtTestContext.clear();
    }

    private static EventDTO event(Long id, EventStatus status, UUID creator) {
        return new EventDTO(id, "T-" + id, "d", "l",
                LocalDateTime.of(2025, Month.JANUARY, 1, 12, 0), LocalDateTime.of(2999, Month.JANUARY, 1, 0, 0).plusDays(1),
                null, null, null, creator,
                status, null, false, false, null,
                0L, null, 0L, 0L, 0L,
                null, null, null,
                List.of(), LocalDateTime.of(2025, Month.JANUARY, 1, 12, 0), LocalDateTime.of(2025, Month.JANUARY, 1, 12, 0),
                null, null, null);
    }

    private static Report buildReport(Long id, Long eventId, UUID reporterId, ReportStatus status) {
        Report r = new Report();
        r.id = id;
        r.eventId = eventId;
        r.reporterId = reporterId;
        r.reason = ReportReason.SPAM;
        r.status = status;
        r.createdAt = LocalDateTime.of(2025, Month.JANUARY, 1, 12, 0);
        return r;
    }

    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    void list_pendingReports_returns200() {
        Report r = buildReport(3001L, 4001L, reporterId, ReportStatus.PENDING);
        PanacheMock.mock(Report.class);
        PanacheQuery q = mock(PanacheQuery.class);
        when(q.page(0, 20)).thenReturn(q);
        when(q.list()).thenReturn(List.of(r));
        when(Report.find(anyString(), any(Object[].class))).thenReturn(q);
        when(eventClient.findByIds(any(List.class), eq(null)))
                .thenReturn(List.of(event(4001L, EventStatus.PUBLISHED, creatorId)));

        given()
            .when().get("/admin/reports")
            .then().statusCode(200);
    }

    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    void list_emptyResult_returns200() {
        PanacheMock.mock(Report.class);
        PanacheQuery q = mock(PanacheQuery.class);
        when(q.page(0, 20)).thenReturn(q);
        when(q.list()).thenReturn(List.of());
        when(Report.find(anyString(), any(Object[].class))).thenReturn(q);

        given()
            .when().get("/admin/reports?status=DISMISSED")
            .then().statusCode(200);
    }

    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    void patch_dismissReport_returns200() {
        Report r = buildReport(3002L, 4002L, reporterId, ReportStatus.PENDING);
        PanacheMock.mock(Report.class);
        when(Report.findByIdOptional(3002L)).thenReturn(Optional.of(r));

        when(eventClient.getById(4002L)).thenReturn(event(4002L, EventStatus.PUBLISHED, creatorId));

        given()
            .contentType("application/json")
            .body("{\"status\":\"DISMISSED\",\"moderationNote\":\"false alarm\"}")
            .when().patch("/admin/reports/3002")
            .then().statusCode(200);
    }

    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    void patch_reviewedReport_returns200() {
        Report r = buildReport(3003L, 4003L, reporterId, ReportStatus.PENDING);
        PanacheMock.mock(Report.class);
        when(Report.findByIdOptional(3003L)).thenReturn(Optional.of(r));

        // Sibling cascade query returns empty.
        PanacheQuery siblingQ = mock(PanacheQuery.class);
        when(siblingQ.list()).thenReturn(List.of());
        when(Report.find(anyString(), any(Object[].class))).thenReturn(siblingQ);

        when(eventClient.getById(4003L)).thenReturn(event(4003L, EventStatus.PUBLISHED, creatorId));

        given()
            .contentType("application/json")
            .body("{\"status\":\"REVIEWED\",\"moderationNote\":\"spam confirmed\"}")
            .when().patch("/admin/reports/3003")
            .then().statusCode(200);
    }

    @Test
    void patch_unknownReport_returns404() {
        PanacheMock.mock(Report.class);
        when(Report.findByIdOptional(anyLong())).thenReturn(Optional.empty());

        given()
            .contentType("application/json")
            .body("{\"status\":\"DISMISSED\"}")
            .when().patch("/admin/reports/9999")
            .then().statusCode(404);
    }

    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    void patch_alreadyReviewed_returns409() {
        Report r = buildReport(3004L, 4004L, reporterId, ReportStatus.REVIEWED);
        PanacheMock.mock(Report.class);
        when(Report.findByIdOptional(3004L)).thenReturn(Optional.of(r));

        given()
            .contentType("application/json")
            .body("{\"status\":\"DISMISSED\"}")
            .when().patch("/admin/reports/3004")
            .then().statusCode(409);
    }

    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    void patch_invalidStatus_returns400() {
        // Bean validation does not catch ReportStatus values, but the
        // service rejects PENDING as a target status.
        Report r = buildReport(3005L, 4005L, reporterId, ReportStatus.PENDING);
        PanacheMock.mock(Report.class);
        when(Report.findByIdOptional(3005L)).thenReturn(Optional.of(r));

        given()
            .contentType("application/json")
            .body("{\"status\":\"PENDING\"}")
            .when().patch("/admin/reports/3005")
            .then().statusCode(400);
    }

    /**
     * Without ADMIN role on the security identity, the resource is
     * forbidden — proves @RolesAllowed is wired on AdminReportResource.
     */
    @Test
    @TestSecurity(user = "auth0|non-admin", roles = "USER")
    void list_byNonAdmin_returns403() {
        given()
            .when().get("/admin/reports")
            .then().statusCode(403);
    }
}
