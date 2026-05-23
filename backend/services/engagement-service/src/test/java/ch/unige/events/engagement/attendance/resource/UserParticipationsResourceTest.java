package ch.unige.events.engagement.attendance.resource;

import ch.unige.events.engagement.attendance.entity.Attendance;
import ch.unige.events.engagement.test.JwtTestContext;
import ch.unige.events.engagement.test.JwtTestHelper;
import ch.unige.events.shared.client.EventServiceClient;
import ch.unige.events.shared.client.UserServiceClient;
import ch.unige.events.shared.domain.dto.AttendeeProjection;
import ch.unige.events.shared.domain.dto.EventDTO;
import ch.unige.events.shared.domain.enums.AttendanceStatus;
import ch.unige.events.shared.domain.enums.EventStatus;

import io.quarkus.panache.mock.PanacheMock;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * Endpoint coverage for {@code GET /api/users/{id}/participations}
 * ({@link UserParticipationsResource}). Mirrors {@code MyAttendancesResourceTest}:
 * Attendance static finders are mocked via {@link PanacheMock}, the cross-service
 * REST clients via {@link InjectMock}, and the caller UUID is staged through
 * {@link JwtTestContext}.
 */
@QuarkusTest
@TestSecurity(user = "auth0|test-user-part")
class UserParticipationsResourceTest {

    @InjectMock
    @RestClient
    EventServiceClient eventClient;

    @InjectMock
    @RestClient
    UserServiceClient userClient;

    private final UUID callerId = UUID.randomUUID();
    private final UUID otherId = UUID.randomUUID();

    @BeforeEach
    void stageJwt() {
        JwtTestContext.set(JwtTestHelper.jwtFor(callerId));
    }

    @AfterEach
    void clearJwt() {
        JwtTestContext.clear();
    }

    private static EventDTO publishedEvent(long id) {
        return new EventDTO(id, "Title-" + id, "desc", "loc",
                LocalDateTime.now().plusDays(1), LocalDateTime.now().plusDays(2),
                null, null, null, UUID.randomUUID(),
                EventStatus.PUBLISHED, 10, false, false, null,
                0L, 10L, 0L, 0L, 0L, null, null, null,
                List.of(), LocalDateTime.now(), LocalDateTime.now(),
                null, null, null);
    }

    private static Attendance attending(UUID userId, long eventId) {
        Attendance a = new Attendance();
        a.id = eventId * 10;
        a.userId = userId;
        a.eventId = eventId;
        a.status = AttendanceStatus.ATTENDING;
        a.createdAt = LocalDateTime.now();
        return a;
    }

    @SuppressWarnings("unchecked")
    @Test
    void getUserParticipations_self_returns200WithEvents() {
        PanacheMock.mock(Attendance.class);
        when(Attendance.findAllByUser(callerId)).thenReturn(List.of(attending(callerId, 90L)));
        when(Attendance.countGroupedByStatus(any(List.class), any(), any())).thenReturn(Map.of());
        when(eventClient.findByIds(any(List.class), eq("PUBLISHED")))
                .thenReturn(List.of(publishedEvent(90L)));

        given()
            .when().get("/users/" + callerId + "/participations")
            .then().statusCode(200)
            .body("size()", Matchers.is(1))
            .body("[0].id", Matchers.is(90));
    }

    @SuppressWarnings("unchecked")
    @Test
    void getUserParticipations_selfWithUpcomingTimeframe_returns200() {
        PanacheMock.mock(Attendance.class);
        when(Attendance.findAllByUser(callerId)).thenReturn(List.of(attending(callerId, 95L)));
        when(Attendance.countGroupedByStatus(any(List.class), any(), any())).thenReturn(Map.of());
        when(eventClient.findByIds(any(List.class), eq("PUBLISHED")))
                .thenReturn(List.of(publishedEvent(95L)));

        given()
            .when().get("/users/" + callerId + "/participations?timeframe=upcoming")
            .then().statusCode(200)
            .body("size()", Matchers.is(1));
    }

    @SuppressWarnings("unchecked")
    @Test
    void getUserParticipations_publicTarget_returns200() {
        PanacheMock.mock(Attendance.class);
        when(Attendance.findAllByUser(otherId)).thenReturn(List.of(attending(otherId, 91L)));
        when(Attendance.countGroupedByStatus(any(List.class), any(), any())).thenReturn(Map.of());
        when(eventClient.findByIds(any(List.class), eq("PUBLISHED")))
                .thenReturn(List.of(publishedEvent(91L)));
        when(userClient.getAttendeeProjections(any())).thenReturn(List.of(
                new AttendeeProjection(otherId, "Other", null, true)));

        given()
            .when().get("/users/" + otherId + "/participations")
            .then().statusCode(200)
            .body("size()", Matchers.is(1));
    }

    @Test
    void getUserParticipations_privateTarget_returns200EmptyList() {
        PanacheMock.mock(Attendance.class);
        when(userClient.getAttendeeProjections(any())).thenReturn(List.of(
                new AttendeeProjection(otherId, "Other", null, false)));

        given()
            .when().get("/users/" + otherId + "/participations")
            .then().statusCode(200)
            .body("$", Matchers.empty());
    }

    @SuppressWarnings("unchecked")
    @Test
    void getUserParticipations_selfWithBlankTimeframe_returns200() {
        // Empty ?timeframe= on the self path — parseTimeframe's `raw.isBlank()`
        // arm (line 64) returns null (no 400, no filtering).
        PanacheMock.mock(Attendance.class);
        when(Attendance.findAllByUser(callerId)).thenReturn(List.of(attending(callerId, 96L)));
        when(Attendance.countGroupedByStatus(any(List.class), any(), any())).thenReturn(Map.of());
        when(eventClient.findByIds(any(List.class), eq("PUBLISHED")))
                .thenReturn(List.of(publishedEvent(96L)));

        given()
            .when().get("/users/" + callerId + "/participations?timeframe=")
            .then().statusCode(200)
            .body("size()", Matchers.is(1));
    }

    @SuppressWarnings("unchecked")
    @Test
    void getUserParticipations_selfWithWhitespaceTimeframe_returns200() {
        // A whitespace-only (URL-encoded space) ?timeframe= binds to a NON-null
        // but blank String, so parseTimeframe (line 64) evaluates the first
        // operand `raw == null` to FALSE and the second operand `raw.isBlank()`
        // to TRUE → returns null. This covers the raw.isBlank()==true arm, which
        // an absent param (raw==null short-circuit) leaves unexercised. Mirrors
        // the structure of getUserParticipations_selfWithUpcomingTimeframe.
        PanacheMock.mock(Attendance.class);
        when(Attendance.findAllByUser(callerId)).thenReturn(List.of(attending(callerId, 97L)));
        when(Attendance.countGroupedByStatus(any(List.class), any(), any())).thenReturn(Map.of());
        when(eventClient.findByIds(any(List.class), eq("PUBLISHED")))
                .thenReturn(List.of(publishedEvent(97L)));

        given()
            .when().get("/users/" + callerId + "/participations?timeframe=%20")
            .then().statusCode(200)
            .body("size()", Matchers.is(1));
    }

    @Test
    void getUserParticipations_invalidTimeframe_returns400() {
        given()
            .when().get("/users/" + callerId + "/participations?timeframe=bogus")
            .then().statusCode(400);
    }
}
