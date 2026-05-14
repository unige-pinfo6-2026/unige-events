package ch.unige.events.event.view.resource;

import ch.unige.events.event.test.JwtTestContext;
import ch.unige.events.event.test.JwtTestHelper;
import ch.unige.events.shared.client.EngagementServiceClient;
import ch.unige.events.shared.domain.dto.AttendanceSummary;

import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.lenient;

/**
 * Resource-layer tests for {@link EventViewResource}. Class-level @TestSecurity
 * is omitted intentionally — methods opt into auth via @TestSecurity(user=...)
 * + stageFreshUser(). This is the same pattern as EventResourceTest and is
 * required to test the anonymous branch (the {@code @PermitAll} resource
 * accepts callers without a JWT — Axe 4 / SCRUM-137 PR).
 */
@QuarkusTest
class EventViewResourceTest {

    @InjectMock @RestClient EngagementServiceClient engagementClient;

    @BeforeEach
    void setup() {
        lenient().when(engagementClient.getAttendanceSummary(anyLong()))
                .thenReturn(AttendanceSummary.of(0L, 0L));
    }

    @AfterEach
    void clear() {
        JwtTestContext.clear();
    }

    /** Stages a fresh JWT for the test (needed for any auth'd call). */
    private UUID stageFreshUser() {
        UUID id = UUID.randomUUID();
        JwtTestContext.set(JwtTestHelper.jwtFor(id));
        return id;
    }

    /** Helper: creates an event as an authenticated user, returns its id. */
    private long createEventAuthd() {
        stageFreshUser();
        String start = LocalDateTime.now().plusDays(2).withNano(0).toString();
        String end = LocalDateTime.now().plusDays(2).plusHours(2).withNano(0).toString();
        String body = "{\"title\":\"T\",\"description\":\"d\",\"location\":\"l\","
                + "\"startDate\":\"" + start + "\",\"endDate\":\"" + end + "\","
                + "\"category\":\"ACADEMIC\"}";
        long id = given()
            .contentType("application/json").body(body)
            .when().post("/events")
            .then().statusCode(201)
            .extract().jsonPath().getLong("id");
        JwtTestContext.clear();
        return id;
    }

    // ─── Authenticated branch ─────────────────────────────────────────────

    @Test
    @TestSecurity(user = "auth0|view-rs-auth")
    void recordView_authenticated_noBody_returns204() {
        long id = createEventAuthd();
        stageFreshUser();
        given().when().post("/events/" + id + "/view").then().statusCode(204);
    }

    @Test
    @TestSecurity(user = "auth0|view-rs-auth-body")
    void recordView_authenticated_withSessionIdBody_returns204() {
        long id = createEventAuthd();
        stageFreshUser();
        String body = "{\"sessionId\":\"" + UUID.randomUUID() + "\"}";
        given()
            .contentType("application/json").body(body)
            .when().post("/events/" + id + "/view")
            .then().statusCode(204);
    }

    // ─── Anonymous branch (Axe 4 PR — view anon + dedup session) ────────

    @Test
    void recordView_anonymousWithSessionId_returns204() {
        long id = createEventAuthd();
        String body = "{\"sessionId\":\"" + UUID.randomUUID() + "\"}";
        given()
            .contentType("application/json").body(body)
            .when().post("/events/" + id + "/view")
            .then().statusCode(204);
    }

    @Test
    void recordView_anonymousWithoutBody_returns204_silentNoOp() {
        long id = createEventAuthd();
        // No JWT, no body — backend silently no-ops, still returns 204.
        given().when().post("/events/" + id + "/view").then().statusCode(204);
    }

    @Test
    void recordView_anonymousWithNullSessionId_returns204() {
        long id = createEventAuthd();
        given()
            .contentType("application/json").body("{\"sessionId\":null}")
            .when().post("/events/" + id + "/view")
            .then().statusCode(204);
    }

    // ─── Event existence check ────────────────────────────────────────────

    @Test
    void recordView_unknown_anonymous_returns404() {
        given().when().post("/events/9999984/view").then().statusCode(404);
    }

    @Test
    @TestSecurity(user = "auth0|view-rs-unknown")
    void recordView_unknown_authenticated_returns404() {
        stageFreshUser();
        given().when().post("/events/9999985/view").then().statusCode(404);
    }
}
