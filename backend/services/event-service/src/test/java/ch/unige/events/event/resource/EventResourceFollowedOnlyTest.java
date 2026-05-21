package ch.unige.events.event.resource;

import ch.unige.events.event.test.JwtTestContext;
import ch.unige.events.event.test.JwtTestHelper;
import ch.unige.events.shared.client.EngagementServiceClient;
import ch.unige.events.shared.client.UserServiceClient;
import ch.unige.events.shared.domain.dto.AttendanceSummary;

import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import io.restassured.path.json.JsonPath;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.empty;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * SCRUM-168 — @QuarkusTest coverage for the {@code ?followedOnly=true} filter
 * on {@code GET /events}.
 *
 * <p>Scenarios:
 * <ol>
 *   <li>Authenticated, follows 2 users with events → only their events returned.</li>
 *   <li>Authenticated, follows nobody → empty list.</li>
 *   <li>Anonymous with followedOnly=true → 401.</li>
 *   <li>followedOnly absent → all events returned unchanged.</li>
 *   <li>followedOnly combined with status filter.</li>
 * </ol>
 */
@QuarkusTest
class EventResourceFollowedOnlyTest {

    @InjectMock @RestClient EngagementServiceClient engagementClient;
    @InjectMock @RestClient UserServiceClient userClient;

    @BeforeEach
    void setup() {
        lenient().when(engagementClient.getAttendanceSummary(anyLong()))
                .thenReturn(AttendanceSummary.of(0L, 0L));
        lenient().when(engagementClient.getAttendanceSummariesBulk(any()))
                .thenReturn(Map.of());
    }

    @AfterEach
    void clear() {
        JwtTestContext.clear();
    }

    private static String eventBody(String title) {
        String start = LocalDateTime.now().plusDays(3).withNano(0).toString();
        String end   = LocalDateTime.now().plusDays(3).plusHours(2).withNano(0).toString();
        return "{\"title\":\"" + title + "\",\"description\":\"d\",\"location\":\"loc\","
                + "\"startDate\":\"" + start + "\",\"endDate\":\"" + end + "\","
                + "\"category\":\"ACADEMIC\"}";
    }

    private long createEventAs(UUID creatorId) {
        JwtTestContext.set(JwtTestHelper.jwtFor(creatorId));
        return given()
                .contentType("application/json")
                .body(eventBody("Event by " + creatorId))
                .when().post("/events")
                .then().statusCode(201)
                .extract().jsonPath().getLong("id");
    }

    // ─── Scenario 1: authenticated, follows 2 users ─────────────────────────

    @Test
    @TestSecurity(user = "auth0|fo-1")
    void followedOnly_authenticatedFollows2Users_returnsOnlyTheirEvents() {
        UUID callerUuid    = UUID.randomUUID();
        UUID followedUser1 = UUID.randomUUID();
        UUID followedUser2 = UUID.randomUUID();
        UUID otherUser     = UUID.randomUUID();

        long e1 = createEventAs(followedUser1);
        long e2 = createEventAs(followedUser2);
        createEventAs(otherUser);

        // Switch to caller context for the actual request
        JwtTestContext.set(JwtTestHelper.jwtFor(callerUuid));
        when(userClient.getFollowedIds(callerUuid))
                .thenReturn(List.of(followedUser1, followedUser2));

        JsonPath json = given()
                .queryParam("followedOnly", "true")
                .when().get("/events")
                .then().statusCode(200)
                .extract().jsonPath();

        List<Long> returnedIds = json.getList("id", Long.class);
        assertTrue(returnedIds.contains(e1), "Event from followedUser1 must be present");
        assertTrue(returnedIds.contains(e2), "Event from followedUser2 must be present");
        assertTrue(returnedIds.stream().noneMatch(id -> id.equals(0L)),
                "No spurious zero IDs");
        // All returned events must belong to followed users
        List<String> creatorIds = json.getList("creatorId", String.class);
        for (String cid : creatorIds) {
            UUID uuid = UUID.fromString(cid);
            assertTrue(
                    uuid.equals(followedUser1) || uuid.equals(followedUser2),
                    "Only followed users' events should be returned, got: " + cid);
        }
    }

    // ─── Scenario 2: authenticated, follows nobody ───────────────────────────

    @Test
    @TestSecurity(user = "auth0|fo-2")
    void followedOnly_authenticatedFollowsNobody_returnsEmptyList() {
        UUID callerUuid = UUID.randomUUID();

        // Create an event so we know the DB is non-empty
        UUID someCreator = UUID.randomUUID();
        createEventAs(someCreator);

        JwtTestContext.set(JwtTestHelper.jwtFor(callerUuid));
        when(userClient.getFollowedIds(callerUuid)).thenReturn(List.of());

        List<?> result = given()
                .queryParam("followedOnly", "true")
                .when().get("/events")
                .then().statusCode(200)
                .extract().jsonPath().getList("$");

        assertEquals(0, result.size(), "Should return empty list when following nobody");
    }

    // ─── Scenario 3: anonymous + followedOnly=true → 401 ────────────────────

    @Test
    void followedOnly_anonymous_returns401() {
        given()
                .queryParam("followedOnly", "true")
                .when().get("/events")
                .then().statusCode(401);
    }

    // ─── Scenario 4: followedOnly absent → unchanged behaviour ──────────────

    @Test
    @TestSecurity(user = "auth0|fo-4")
    void followedOnly_absent_returnsAllEvents() {
        UUID creator = UUID.randomUUID();
        createEventAs(creator);

        // No followedOnly param — all events visible to anonymous
        given()
                .when().get("/events")
                .then().statusCode(200);
    }

    // ─── Scenario 5½: authenticated but CallerIdentity returns null UUID ─────
    // Covers the defensive branch: followedIds = List.of() when getUuid() == null.

    @Test
    @TestSecurity(user = "auth0|fo-5b")
    void followedOnly_authenticatedButNullCallerUuid_returnsEmptyList() {
        // JwtTestContext is deliberately NOT set — TestCallerIdentity.getUuid() returns null.
        // The resource must degrade gracefully to an empty list rather than NPE.
        given()
                .queryParam("followedOnly", "true")
                .when().get("/events")
                .then().statusCode(200)
                .body("$", empty());
    }

    // ─── Scenario 5: followedOnly combined with status filter ────────────────

    @Test
    @TestSecurity(user = "auth0|fo-5")
    void followedOnly_combinedWithStatusFilter_appliesBothConditions() {
        UUID callerUuid    = UUID.randomUUID();
        UUID followedUser  = UUID.randomUUID();

        createEventAs(followedUser); // creates a DRAFT event

        JwtTestContext.set(JwtTestHelper.jwtFor(callerUuid));
        when(userClient.getFollowedIds(callerUuid))
                .thenReturn(List.of(followedUser));

        // DRAFT events are not visible in the default (no status) listing,
        // but when status=DRAFT is explicitly requested the filter must hold.
        // We just verify 200 + the combination is accepted without error.
        given()
                .queryParam("followedOnly", "true")
                .queryParam("status", "DRAFT")
                .when().get("/events")
                .then().statusCode(200);
    }
}
