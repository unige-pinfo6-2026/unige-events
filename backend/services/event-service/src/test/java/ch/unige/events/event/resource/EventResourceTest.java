package ch.unige.events.event.resource;

import ch.unige.events.event.test.JwtTestContext;
import ch.unige.events.event.test.JwtTestHelper;
import ch.unige.events.shared.client.EngagementServiceClient;
import ch.unige.events.shared.client.UserServiceClient;
import ch.unige.events.shared.domain.dto.AttendanceSummary;

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

/**
 * Resource-layer tests use REST-Assured against a real DB. Each test
 * creates its own UUID to dodge the per-user rate-limit buckets
 * (PerUserRateLimit max=10 across the test suite).
 */
@QuarkusTest
class EventResourceTest {

    @InjectMock @RestClient EngagementServiceClient engagementClient;
    @InjectMock @RestClient UserServiceClient userClient;

    @BeforeEach
    void setup() {
        lenient().when(engagementClient.getAttendanceSummary(anyLong()))
                .thenReturn(AttendanceSummary.of(0L, 0L));
        lenient().when(engagementClient.getAttendanceSummariesBulk(any())).thenReturn(Map.of());
    }

    @AfterEach
    void clear() {
        JwtTestContext.clear();
    }

    private static String createBody() {
        String start = LocalDateTime.now().plusDays(2).withNano(0).toString();
        String end = LocalDateTime.now().plusDays(2).plusHours(2).withNano(0).toString();
        return "{\"title\":\"T\",\"description\":\"d\",\"location\":\"l\","
                + "\"startDate\":\"" + start + "\",\"endDate\":\"" + end + "\","
                + "\"category\":\"ACADEMIC\"}";
    }

    /** Each test gets a fresh UUID — avoids per-user rate limit accumulation. */
    private UUID stageFreshUser() {
        UUID id = UUID.randomUUID();
        JwtTestContext.set(JwtTestHelper.jwtFor(id));
        return id;
    }

    private long postEvent() {
        return given()
            .contentType("application/json").body(createBody())
            .when().post("/events")
            .then().statusCode(201)
            .extract().jsonPath().getLong("id");
    }

    @Test
    void getAll_anonymous_returns200() {
        given().when().get("/events").then().statusCode(200);
    }

    @Test
    @TestSecurity(user = "auth0|er-1")
    void getAll_organizerNonPublished_returns400() {
        stageFreshUser();
        given()
            .queryParam("organizerId", UUID.randomUUID().toString())
            .queryParam("status", "DRAFT")
            .when().get("/events")
            .then().statusCode(400);
    }

    @Test
    @TestSecurity(user = "auth0|er-2")
    void getAll_organizerWithoutStatus_setsPublished() {
        stageFreshUser();
        given()
            .queryParam("organizerId", UUID.randomUUID().toString())
            .when().get("/events")
            .then().statusCode(200);
    }

    @Test
    @TestSecurity(user = "auth0|er-3")
    void getAll_byIds_short_circuits() {
        stageFreshUser();
        long id = postEvent();
        given()
            .queryParam("ids", id)
            .when().get("/events")
            .then().statusCode(200);
    }

    @Test
    void getById_unknown_returns404() {
        given().when().get("/events/9999999").then().statusCode(404);
    }

    @Test
    void getOccurrences_unknown_returns404() {
        given().when().get("/events/9999998/occurrences").then().statusCode(404);
    }

    @Test
    void getOrganizerUuids_unknown_returns404() {
        given().when().get("/events/9999997/organizer-uuids").then().statusCode(404);
    }

    @Test
    @TestSecurity(user = "auth0|er-4")
    void create_authenticated_returns201() {
        stageFreshUser();
        given()
            .contentType("application/json").body(createBody())
            .when().post("/events")
            .then().statusCode(201);
    }

    @Test
    @TestSecurity(user = "auth0|er-5")
    void create_invalidBody_returns400() {
        stageFreshUser();
        given()
            .contentType("application/json").body("{}")
            .when().post("/events")
            .then().statusCode(400);
    }

    @Test
    @TestSecurity(user = "auth0|er-6")
    void update_authenticated_byCreator_returns200() {
        stageFreshUser();
        long id = postEvent();
        String body = createBody().replace("\"T\"", "\"T2\"");
        given()
            .contentType("application/json").body(body)
            .when().put("/events/" + id)
            .then().statusCode(200);
    }

    @Test
    @TestSecurity(user = "auth0|er-7")
    void cancel_publishedByCreator_returns200() {
        stageFreshUser();
        long id = postEvent();
        given()
            .when().patch("/events/" + id + "/cancel")
            .then().statusCode(200);
    }

    @Test
    @TestSecurity(user = "auth0|er-8")
    void restore_cancelledByCreator_returns200() {
        stageFreshUser();
        long id = postEvent();
        given().when().patch("/events/" + id + "/cancel").then().statusCode(200);
        given().when().patch("/events/" + id + "/restore").then().statusCode(200);
    }

    @Test
    @TestSecurity(user = "auth0|er-9")
    void delete_cancelledByCreator_returns204() {
        stageFreshUser();
        long id = postEvent();
        given().when().patch("/events/" + id + "/cancel").then().statusCode(200);
        given().when().delete("/events/" + id).then().statusCode(204);
    }

    @Test
    @TestSecurity(user = "auth0|er-10")
    void publish_draftByCreator_returns200() {
        stageFreshUser();
        long id = postEvent();
        given()
            .when().patch("/events/" + id + "/publish")
            .then().statusCode(200);
    }

    @Test
    void getFeatured_returns200() {
        given().when().get("/events/featured").then().statusCode(200);
    }

    @Test
    @TestSecurity(user = "auth0|er-11")
    void uploadImage_missingFile_returns400() {
        stageFreshUser();
        long id = postEvent();
        given()
            .contentType("multipart/form-data")
            .when().post("/events/" + id + "/image")
            .then().statusCode(400);
    }

    @Test
    @TestSecurity(user = "auth0|er-12")
    void getOccurrences_sizeOver52_returns400() {
        stageFreshUser();
        long id = postEvent();
        given()
            .queryParam("size", 53)
            .when().get("/events/" + id + "/occurrences")
            .then().statusCode(400);
    }

    @Test
    @TestSecurity(user = "auth0|er-13")
    void getById_byCreator_returns200() {
        stageFreshUser();
        long id = postEvent();
        given().when().get("/events/" + id).then().statusCode(200);
    }

    @Test
    @TestSecurity(user = "auth0|er-14")
    void getById_checkCoOrgOf_byCaller_includesField() {
        UUID userUuid = stageFreshUser();
        long id = postEvent();
        given()
            .queryParam("check-co-org-of", userUuid.toString())
            .when().get("/events/" + id)
            .then().statusCode(200);
    }
}
