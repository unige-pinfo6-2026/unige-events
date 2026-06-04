package ch.unige.events.event.coorganizer.resource;

import ch.unige.events.event.test.JwtTestContext;
import ch.unige.events.event.test.JwtTestHelper;
import ch.unige.events.shared.client.EngagementServiceClient;
import ch.unige.events.shared.client.UserServiceClient;
import ch.unige.events.shared.domain.dto.AttendanceSummary;
import ch.unige.events.shared.domain.dto.UserPublicResponse;

import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.lenient;

@QuarkusTest
@TestSecurity(user = "auth0|coorg-rs")
class EventCoOrganizerResourceTest {

    @InjectMock @RestClient UserServiceClient userClient;
    @InjectMock @RestClient EngagementServiceClient engagementClient;

    private final UUID callerUuid = UUID.randomUUID();
    private final UUID inviteeUuid = UUID.randomUUID();

    @BeforeEach
    void setup() {
        JwtTestContext.set(JwtTestHelper.jwtFor(callerUuid));
        lenient().when(userClient.getById(any(UUID.class))).thenAnswer(inv -> {
            UUID id = inv.getArgument(0);
            return new UserPublicResponse(id, "User-" + id, null, null, null,
                    null, null, null, 0L, 0L, null);
        });
        lenient().when(engagementClient.getAttendanceSummariesBulk(any())).thenReturn(Map.of());
        lenient().when(engagementClient.getAttendanceSummary(anyLong()))
                .thenReturn(AttendanceSummary.of(0L, 0L));
    }

    @AfterEach
    void clear() {
        JwtTestContext.clear();
    }

    private long createEvent() {
        String start = LocalDateTime.of(2999, 1, 1, 0, 0).plusDays(2).withNano(0).toString();
        String end = LocalDateTime.of(2999, 1, 1, 0, 0).plusDays(2).plusHours(2).withNano(0).toString();
        String body = "{\"title\":\"T\",\"description\":\"d\",\"location\":\"l\","
                + "\"startDate\":\"" + start + "\",\"endDate\":\"" + end + "\","
                + "\"category\":\"ACADEMIC\"}";
        return given()
            .contentType("application/json").body(body)
            .when().post("/events")
            .then().statusCode(201)
            .extract().jsonPath().getLong("id");
    }

    @Test
    void invite_byCreator_returns201() {
        long id = createEvent();
        String body = "{\"userId\":\"" + inviteeUuid + "\"}";
        given()
            .contentType("application/json").body(body)
            .when().post("/events/" + id + "/co-organizers")
            .then().statusCode(201);
    }

    @Test
    void invite_unknownEvent_returns404() {
        String body = "{\"userId\":\"" + inviteeUuid + "\"}";
        given()
            .contentType("application/json").body(body)
            .when().post("/events/9999987/co-organizers")
            .then().statusCode(404);
    }

    @Test
    void list_returnsCoOrganizers() {
        long id = createEvent();
        given()
            .when().get("/events/" + id + "/co-organizers")
            .then().statusCode(200);
    }

    @Test
    void remove_byCreator_returns204() {
        long id = createEvent();
        String body = "{\"userId\":\"" + inviteeUuid + "\"}";
        given()
            .contentType("application/json").body(body)
            .when().post("/events/" + id + "/co-organizers")
            .then().statusCode(201);

        given()
            .when().delete("/events/" + id + "/co-organizers/" + inviteeUuid)
            .then().statusCode(204);
    }

    @Test
    void getMyInvitations_returns200() {
        given()
            .when().get("/users/me/co-organizer-invitations")
            .then().statusCode(200);
    }

    @Test
    void accept_noInvitation_returns422() {
        long id = createEvent();
        given()
            .when().patch("/events/" + id + "/co-organizers/me/accept")
            .then().statusCode(422);
    }

    @Test
    void decline_noInvitation_returns422() {
        long id = createEvent();
        given()
            .when().patch("/events/" + id + "/co-organizers/me/decline")
            .then().statusCode(422);
    }

    /** Invite {@code target} (as the event creator) and return the event id. */
    private long createEventAndInvite(UUID target) {
        long id = createEvent();
        given()
            .contentType("application/json").body("{\"userId\":\"" + target + "\"}")
            .when().post("/events/" + id + "/co-organizers")
            .then().statusCode(201);
        return id;
    }

    @Test
    void accept_pendingInvitation_returns200() {
        // Creator (callerUuid) invites inviteeUuid → PENDING. Then the
        // invitee accepts, exercising the resource's success branch
        // (Response.ok(accepted)).
        long id = createEventAndInvite(inviteeUuid);
        JwtTestContext.set(JwtTestHelper.jwtFor(inviteeUuid));
        given()
            .when().patch("/events/" + id + "/co-organizers/me/accept")
            .then().statusCode(200);
    }

    @Test
    void decline_pendingInvitation_returns204() {
        // Same setup, but the invitee declines — exercises the resource's
        // success branch (Response.noContent()).
        long id = createEventAndInvite(inviteeUuid);
        JwtTestContext.set(JwtTestHelper.jwtFor(inviteeUuid));
        given()
            .when().patch("/events/" + id + "/co-organizers/me/decline")
            .then().statusCode(204);
    }
}
