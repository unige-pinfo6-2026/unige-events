package ch.unige.events.report.resource;

import ch.unige.events.report.entity.Report;
import ch.unige.events.report.test.JwtTestContext;
import ch.unige.events.report.test.JwtTestHelper;
import ch.unige.events.shared.client.EventServiceClient;
import ch.unige.events.shared.client.UserServiceClient;
import ch.unige.events.shared.domain.dto.EventDTO;
import ch.unige.events.shared.domain.dto.UserPublicResponse;
import ch.unige.events.shared.domain.enums.EventStatus;

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
import java.util.List;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * REST-layer tests for {@link ReportResource} (POST /events/{id}/report).
 */
@QuarkusTest
@TestSecurity(user = "auth0|test-report-resource")
class ReportResourceTest {

    @InjectMock
    @RestClient
    EventServiceClient eventClient;

    @InjectMock
    @RestClient
    UserServiceClient userClient;

    private final UUID userId = UUID.randomUUID();
    private final UUID creatorId = UUID.randomUUID();

    @BeforeEach
    void stageJwt() {
        JwtTestContext.set(JwtTestHelper.jwtFor(userId));
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
                LocalDateTime.now(), LocalDateTime.now().plusDays(1),
                null, null, null, creator,
                status, null, false, false, null,
                0L, null, 0L, 0L, 0L,
                null, null, null,
                List.of(), LocalDateTime.now(), LocalDateTime.now(),
                null, null, null);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static PanacheQuery makeCountQuery(long count) {
        PanacheQuery q = mock(PanacheQuery.class);
        org.mockito.Mockito.doReturn(count).when(q).count();
        return q;
    }

    @Test
    void post_report_publishedEvent_returns201() {
        EventDTO ev = event(2001L, EventStatus.PUBLISHED, creatorId);
        when(eventClient.getByIdWithCoOrgCheck(eq(2001L), any(UUID.class))).thenReturn(ev);

        PanacheMock.mock(Report.class);
        PanacheQuery cq0 = makeCountQuery(0L);
        when(Report.find(anyString(), any(Object[].class))).thenReturn(cq0);

        given()
            .contentType("application/json")
            .body("{\"reason\":\"SPAM\",\"description\":\"spam content\"}")
            .when().post("/events/2001/report")
            .then().statusCode(201);
    }

    @Test
    void post_report_unknownEvent_returns404() {
        when(eventClient.getByIdWithCoOrgCheck(eq(2002L), any(UUID.class))).thenReturn(null);
        given()
            .contentType("application/json")
            .body("{\"reason\":\"SPAM\"}")
            .when().post("/events/2002/report")
            .then().statusCode(404);
    }

    @Test
    void post_report_draftEvent_returns400() {
        EventDTO ev = event(2003L, EventStatus.DRAFT, creatorId);
        when(eventClient.getByIdWithCoOrgCheck(eq(2003L), any(UUID.class))).thenReturn(ev);

        given()
            .contentType("application/json")
            .body("{\"reason\":\"SPAM\"}")
            .when().post("/events/2003/report")
            .then().statusCode(400);
    }

    @Test
    void post_report_missingReason_returns400() {
        // Bean validation triggers @NotNull on reason.
        given()
            .contentType("application/json")
            .body("{}")
            .when().post("/events/2004/report")
            .then().statusCode(400);
    }
}
