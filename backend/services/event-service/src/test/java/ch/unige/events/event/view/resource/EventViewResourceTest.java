package ch.unige.events.event.view.resource;

import ch.unige.events.event.entity.Event;
import ch.unige.events.event.test.JwtTestContext;
import ch.unige.events.event.test.JwtTestHelper;
import ch.unige.events.shared.client.EngagementServiceClient;
import ch.unige.events.shared.domain.dto.AttendanceSummary;
import ch.unige.events.shared.domain.enums.EventCategory;
import ch.unige.events.shared.domain.enums.EventStatus;

import io.quarkus.narayana.jta.QuarkusTransaction;
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
 * accepts callers without a JWT).
 *
 * <p>Events are persisted directly via JPA (not via {@code POST /events})
 * because the create endpoint requires auth — going through it would force
 * every anonymous test to declare {@code @TestSecurity} just to seed data.
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

    /**
     * Persists a minimal published Event directly via JPA. Returns the id.
     * Uses a programmatic transaction so we don't need {@code @Transactional}
     * on the test method (which would interact poorly with REST-Assured
     * spinning up its own transaction).
     */
    private long persistEvent() {
        return QuarkusTransaction.requiringNew().call(() -> {
            Event e = new Event();
            e.title = "T";
            e.description = "d";
            e.location = "l";
            e.startDate = LocalDateTime.now().plusDays(2);
            e.endDate = e.startDate.plusHours(2);
            e.category = EventCategory.ACADEMIC;
            e.creatorId = UUID.randomUUID();
            e.status = EventStatus.PUBLISHED;
            e.persist();
            return e.id;
        });
    }

    // ─── Authenticated branch ─────────────────────────────────────────────

    @Test
    @TestSecurity(user = "auth0|view-rs-auth")
    void recordView_authenticated_noBody_returns204() {
        long id = persistEvent();
        stageFreshUser();
        given().when().post("/events/" + id + "/view").then().statusCode(204);
    }

    @Test
    @TestSecurity(user = "auth0|view-rs-auth-body")
    void recordView_authenticated_withSessionIdBody_returns204() {
        long id = persistEvent();
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
        long id = persistEvent();
        String body = "{\"sessionId\":\"" + UUID.randomUUID() + "\"}";
        given()
            .contentType("application/json").body(body)
            .when().post("/events/" + id + "/view")
            .then().statusCode(204);
    }

    @Test
    void recordView_anonymousWithoutBody_returns204_silentNoOp() {
        long id = persistEvent();
        // No JWT, no body — backend silently no-ops, still returns 204.
        given().when().post("/events/" + id + "/view").then().statusCode(204);
    }

    @Test
    void recordView_anonymousWithNullSessionId_returns204() {
        long id = persistEvent();
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
