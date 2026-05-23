package ch.unige.events.engagement.attendance.resource;

import ch.unige.events.engagement.attendance.entity.Attendance;
import ch.unige.events.engagement.test.JwtTestContext;
import ch.unige.events.engagement.test.JwtTestHelper;
import ch.unige.events.shared.client.EventServiceClient;
import ch.unige.events.shared.client.UserServiceClient;
import ch.unige.events.shared.domain.dto.UserPublicResponse;

import io.quarkus.panache.mock.PanacheMock;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@QuarkusTest
@TestSecurity(user = "auth0|test-my-att")
class MyAttendancesResourceTest {

    @InjectMock
    @RestClient
    EventServiceClient eventClient;

    @InjectMock
    @RestClient
    UserServiceClient userClient;

    private final UUID userId = UUID.randomUUID();

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

    @Test
    void get_myAttendances_emptyList() {
        PanacheMock.mock(Attendance.class);
        when(Attendance.findAllByUser(userId)).thenReturn(List.of());

        given()
            .when().get("/users/me/attendances")
            .then().statusCode(200)
            .body("$", org.hamcrest.Matchers.empty());
    }

    @Test
    void get_myParticipations_emptyList() {
        PanacheMock.mock(Attendance.class);
        when(Attendance.findAllByUser(userId)).thenReturn(List.of());

        given()
            .when().get("/users/me/participations")
            .then().statusCode(200)
            .body("$", org.hamcrest.Matchers.empty());
    }

    @Test
    void get_myParticipations_validTimeframe_parsedAndAccepted() {
        // Exercises the Timeframe.valueOf(...) success branch of parseTimeframe
        // (case-insensitive upper-casing) — distinct from the blank→null and
        // invalid→400 branches already covered below.
        PanacheMock.mock(Attendance.class);
        when(Attendance.findAllByUser(userId)).thenReturn(List.of());

        given()
            .when().get("/users/me/participations?timeframe=upcoming")
            .then().statusCode(200);
    }

    @Test
    void get_myParticipations_invalidTimeframe_returns400() {
        given()
            .when().get("/users/me/participations?timeframe=NOT_A_VALUE")
            .then().statusCode(400);
    }

    @Test
    void get_myParticipations_noTimeframeParam_acceptedAsNull() {
        // No ?timeframe query param at all — parseTimeframe receives raw==null
        // and returns null via the first operand of the line-61 guard (distinct
        // from the blank arm exercised below).
        PanacheMock.mock(Attendance.class);
        when(Attendance.findAllByUser(userId)).thenReturn(List.of());

        given()
            .when().get("/users/me/participations")
            .then().statusCode(200)
            .body("$", org.hamcrest.Matchers.empty());
    }

    @Test
    void get_myParticipations_emptyTimeframe_acceptedAsNull() {
        PanacheMock.mock(Attendance.class);
        when(Attendance.findAllByUser(userId)).thenReturn(List.of());

        given()
            .when().get("/users/me/participations?timeframe=")
            .then().statusCode(200);
    }
}
