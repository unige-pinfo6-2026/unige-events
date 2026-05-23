package ch.unige.events.event.resource;

import ch.unige.events.event.entity.Event;
import ch.unige.events.event.test.JwtTestContext;
import ch.unige.events.event.test.JwtTestHelper;
import ch.unige.events.shared.client.EngagementServiceClient;
import ch.unige.events.shared.client.UserServiceClient;
import ch.unige.events.shared.domain.dto.AttendanceSummary;
import ch.unige.events.shared.domain.enums.EventCategory;
import ch.unige.events.shared.domain.enums.EventStatus;
import ch.unige.events.shared.storage.FileStorageService;

import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.jboss.resteasy.reactive.multipart.FileUpload;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * Resource-layer tests use REST-Assured against a real DB. Each test
 * creates its own UUID to dodge the per-user rate-limit buckets
 * (PerUserRateLimit max=10 across the test suite).
 */
@QuarkusTest
class EventResourceTest {

    @InjectMock @RestClient EngagementServiceClient engagementClient;
    @InjectMock @RestClient UserServiceClient userClient;
    @InjectMock FileStorageService fileStorageService;

    @BeforeEach
    void setup() {
        lenient().when(engagementClient.getAttendanceSummary(anyLong()))
                .thenReturn(AttendanceSummary.of(0L, 0L));
        lenient().when(engagementClient.getAttendanceSummariesBulk(any())).thenReturn(Map.of());
        lenient().when(fileStorageService.saveImage(any(FileUpload.class), anyString(), anyLong(), any()))
                .thenReturn("https://cdn/banner.png");
    }

    private static Path tinyPng(Path tmp) throws IOException {
        Path file = tmp.resolve("banner.png");
        Files.write(file, new byte[]{(byte) 0x89, 'P', 'N', 'G'});
        return file;
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

    /**
     * Seed a PUBLISHED event by direct persistence (committed in its own
     * transaction) — needed for anonymous GETs whose method has no
     * {@code @TestSecurity}, so the authenticated {@code postEvent()} would
     * 401. Returns the committed id. Always pair with {@link #deleteEvent}
     * so these committed rows don't pollute the shared drop-and-create DB
     * (the featured-ranking tests assert on the global published-event set).
     */
    private long persistPublishedEvent(UUID creator) {
        long[] holder = new long[1];
        QuarkusTransaction.requiringNew().run(() -> {
            Event e = new Event();
            e.title = "anon-" + UUID.randomUUID();
            e.description = "d";
            e.location = "l";
            e.startDate = LocalDateTime.now().plusDays(2);
            e.endDate = e.startDate.plusHours(2);
            e.category = EventCategory.ACADEMIC;
            e.creatorId = creator;
            e.status = EventStatus.PUBLISHED;
            e.persist();
            holder[0] = e.id;
        });
        return holder[0];
    }

    private void deleteEvent(long id) {
        QuarkusTransaction.requiringNew().run(() -> Event.deleteById(id));
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

    @Test
    @TestSecurity(user = "auth0|er-15", roles = "ADMIN")
    void getById_admin_returns200() {
        // Authenticated + ADMIN role → exercises the isAdmin branch.
        stageFreshUser();
        long id = postEvent();
        given().when().get("/events/" + id).then().statusCode(200);
    }

    @Test
    @TestSecurity(user = "auth0|er-16")
    void getById_checkCoOrgOf_notMatchingCaller_silentlyIgnored() {
        // check-co-org-of set but != caller UUID → effectiveCheck stays null
        // (membership oracle closed). Still a 200, field simply not honored.
        stageFreshUser();
        long id = postEvent();
        given()
            .queryParam("check-co-org-of", UUID.randomUUID().toString())
            .when().get("/events/" + id)
            .then().statusCode(200);
    }

    @Test
    @TestSecurity(user = "auth0|er-17")
    void getAll_organizerWithPublishedStatus_returns200() {
        // organizerId set AND status=PUBLISHED → the else-if (non-PUBLISHED)
        // branch is skipped, effectiveStatus kept as PUBLISHED.
        stageFreshUser();
        given()
            .queryParam("organizerId", UUID.randomUUID().toString())
            .queryParam("status", "PUBLISHED")
            .when().get("/events")
            .then().statusCode(200);
    }

    @Test
    @TestSecurity(user = "auth0|er-18")
    void getOccurrences_authenticated_returns200() {
        // Authenticated caller → exercises the non-anonymous auth0Id / isAdmin
        // resolution in getOccurrences (the body that follows the branches).
        stageFreshUser();
        long id = postEvent();
        given()
            .when().get("/events/" + id + "/occurrences")
            .then().statusCode(200);
    }

    @Test
    @TestSecurity(user = "auth0|er-19")
    void uploadImage_withFile_returns200(@TempDir Path tmp) throws IOException {
        // Non-null file → the BadRequestException guard is skipped and the
        // success path (saveImage + 200) runs.
        stageFreshUser();
        long id = postEvent();
        given()
            .multiPart("file", tinyPng(tmp).toFile(), "image/png")
            .when().post("/events/" + id + "/image")
            .then().statusCode(200);
    }

    @Test
    void getOrganizerUuids_publishedEvent_returns200WithCreator() {
        // L184 happy path — read the organizer UUIDs of a published event; the
        // creator's UUID must be present. @PermitAll endpoint, so no auth is
        // needed. Seed by direct persist + clean up to stay net-zero on the
        // shared DB.
        UUID creator = UUID.randomUUID();
        long id = persistPublishedEvent(creator);
        try {
            given()
                .when().get("/events/" + id + "/organizer-uuids")
                .then().statusCode(200)
                .body("", org.hamcrest.Matchers.hasItem(creator.toString()));
        } finally {
            deleteEvent(id);
        }
    }

    @Test
    void getById_anonymousWithCheckCoOrgOf_returns200() {
        // branch L155 — checkCoOrgOf set but caller is anonymous (auth0Id ==
        // null) → effectiveCheck stays null, the event is still served (200).
        // The GET must be truly anonymous (no @TestSecurity), so the event is
        // seeded by direct persist rather than the authenticated POST.
        long id = persistPublishedEvent(UUID.randomUUID());
        try {
            given()
                .queryParam("check-co-org-of", UUID.randomUUID().toString())
                .when().get("/events/" + id)
                .then().statusCode(200);
        } finally {
            deleteEvent(id);
        }
    }

    @Test
    void getOccurrences_anonymous_returns200() {
        // branch L195 — anonymous caller resolves auth0Id=null / isAdmin=false
        // in getOccurrences and still serves a published parent (200).
        long id = persistPublishedEvent(UUID.randomUUID());
        try {
            given()
                .when().get("/events/" + id + "/occurrences")
                .then().statusCode(200);
        } finally {
            deleteEvent(id);
        }
    }

    @Test
    @TestSecurity(user = "auth0|er-22", roles = "ADMIN")
    void getOccurrences_admin_returns200() {
        // branch L195 — authenticated ADMIN caller: !identity.isAnonymous() is
        // true AND identity.hasRole(ROLE_ADMIN) is true, so the
        // `identity.hasRole(ADMIN)` operand evaluates its true leg and
        // isAdmin=true is forwarded to getOccurrences. Still serves a published
        // parent (200).
        long id = persistPublishedEvent(UUID.randomUUID());
        try {
            given()
                .when().get("/events/" + id + "/occurrences")
                .then().statusCode(200);
        } finally {
            deleteEvent(id);
        }
    }

    @Test
    @TestSecurity(user = "auth0|er-21")
    void getById_checkCoOrgOf_unresolvedCallerUuid_returns200() {
        // branch L157 — authenticated (auth0Id != null) but the caller's
        // profile is unresolved: TestCallerIdentity.getUuid() returns null when
        // JwtTestContext is not set. effectiveCheck stays null (callerUuid ==
        // null short-circuits the equals), the event is still served (200).
        long id = persistPublishedEvent(UUID.randomUUID());
        try {
            given()
                .queryParam("check-co-org-of", UUID.randomUUID().toString())
                .when().get("/events/" + id)
                .then().statusCode(200);
        } finally {
            deleteEvent(id);
        }
    }
}
