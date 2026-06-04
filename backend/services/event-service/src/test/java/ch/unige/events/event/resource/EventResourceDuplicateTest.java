package ch.unige.events.event.resource;

import ch.unige.events.event.entity.Event;
import ch.unige.events.event.test.JwtTestContext;
import ch.unige.events.event.test.JwtTestHelper;
import ch.unige.events.shared.client.EngagementServiceClient;
import ch.unige.events.shared.client.UserServiceClient;
import ch.unige.events.shared.domain.dto.AttendanceSummary;
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
import java.time.Month;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.startsWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.lenient;

/**
 * REST-layer tests for {@code POST /events/{id}/duplicate} (SCRUM-99 Bloc G).
 * Covers happy path, all variants of the permission check, status-driven
 * branches (DRAFT / CANCELLED source allowed, BANNED forbidden), the title
 * collision dedup loop, the +7 days date shift, and the reset matrix.
 */
@QuarkusTest
class EventResourceDuplicateTest {

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

    private UUID stage(UUID userId) {
        JwtTestContext.set(JwtTestHelper.jwtFor(userId));
        return userId;
    }

    private UUID stageFreshUser() {
        return stage(UUID.randomUUID());
    }

    private UUID stageAdmin() {
        UUID id = UUID.randomUUID();
        JwtTestContext.set(JwtTestHelper.adminJwt(id));
        return id;
    }

    private Long persistEvent(UUID creatorId, String title, EventStatus status) {
        Event e = new Event();
        e.title = title;
        e.description = "desc";
        e.location = "loc";
        e.startDate = LocalDateTime.of(2999, Month.JANUARY, 1, 0, 0).plusDays(2).withNano(0);
        e.endDate = LocalDateTime.of(2999, Month.JANUARY, 1, 0, 0).plusDays(2).plusHours(2).withNano(0);
        e.category = ch.unige.events.shared.domain.enums.EventCategory.ACADEMIC;
        e.creatorId = creatorId;
        e.status = status;
        e.allDay = false;
        e.featured = false;
        e.tags = new java.util.ArrayList<>(List.of("foo", "bar"));
        QuarkusTransaction.requiringNew().run(e::persist);
        return e.id;
    }

    @Test
    void duplicate_unauthenticated_returns401() {
        given()
            .contentType("application/json")
            .when().post("/events/1/duplicate")
            .then().statusCode(401);
    }

    @Test
    @TestSecurity(user = "auth0|erd-not-found")
    void duplicate_sourceNotFound_returns404() {
        stageFreshUser();
        given()
            .contentType("application/json")
            .when().post("/events/9999999/duplicate")
            .then().statusCode(404);
    }

    @Test
    @TestSecurity(user = "auth0|erd-creator-happy")
    void duplicate_creator_returns201WithDraftPlus7DaysAndCopiedFields() {
        UUID caller = stageFreshUser();
        Long sourceId = persistEvent(caller, "My Event", EventStatus.PUBLISHED);

        given()
            .contentType("application/json")
            .when().post("/events/" + sourceId + "/duplicate")
            .then()
            .statusCode(201)
            .body("id", notNullValue())
            .body("title", equalTo("Copie de My Event"))
            .body("status", equalTo("DRAFT"))
            .body("creatorId", equalTo(caller.toString()))
            .body("attendingCount", equalTo(0))
            .body("waitlistedCount", equalTo(0))
            .body("tags", equalTo(List.of("foo", "bar")));
    }

    @Test
    @TestSecurity(user = "auth0|erd-stranger")
    void duplicate_nonOrganizer_returns403() {
        UUID owner = UUID.randomUUID();
        stageFreshUser();
        Long sourceId = persistEvent(owner, "Restricted", EventStatus.PUBLISHED);

        given()
            .contentType("application/json")
            .when().post("/events/" + sourceId + "/duplicate")
            .then().statusCode(403);
    }

    @Test
    @TestSecurity(user = "auth0|erd-admin", roles = "ADMIN")
    void duplicate_admin_canDuplicateAnyEvent() {
        UUID owner = UUID.randomUUID();
        UUID adminId = stageAdmin();
        Long sourceId = persistEvent(owner, "Owner Event", EventStatus.PUBLISHED);

        given()
            .contentType("application/json")
            .when().post("/events/" + sourceId + "/duplicate")
            .then()
            .statusCode(201)
            // Décision N: the duplicator (admin here) becomes the new creator.
            .body("creatorId", equalTo(adminId.toString()));
    }

    @Test
    @TestSecurity(user = "auth0|erd-draft", roles = "ADMIN")
    void duplicate_sourceDraft_allowed() {
        UUID caller = stageAdmin();
        Long sourceId = persistEvent(caller, "Draft Source", EventStatus.DRAFT);

        given()
            .contentType("application/json")
            .when().post("/events/" + sourceId + "/duplicate")
            .then()
            .statusCode(201)
            .body("status", equalTo("DRAFT"));
    }

    @Test
    @TestSecurity(user = "auth0|erd-cancelled", roles = "ADMIN")
    void duplicate_sourceCancelled_allowed() {
        UUID caller = stageAdmin();
        Long sourceId = persistEvent(caller, "Cancelled Source", EventStatus.CANCELLED);

        given()
            .contentType("application/json")
            .when().post("/events/" + sourceId + "/duplicate")
            .then()
            .statusCode(201)
            .body("status", equalTo("DRAFT"))
            .body("title", startsWith("Copie de "));
    }

    @Test
    @TestSecurity(user = "auth0|erd-banned", roles = "ADMIN")
    void duplicate_sourceBanned_returns403_evenForAdmin() {
        UUID caller = stageAdmin();
        Long sourceId = persistEvent(caller, "Banned Source", EventStatus.BANNED);

        given()
            .contentType("application/json")
            .when().post("/events/" + sourceId + "/duplicate")
            .then().statusCode(403);
    }

    @Test
    @TestSecurity(user = "auth0|erd-title-collision")
    void duplicate_titleCollision_suffixesWithParenN() {
        UUID caller = stageFreshUser();
        Long sourceId = persistEvent(caller, "Workshop", EventStatus.PUBLISHED);
        persistEvent(caller, "Copie de Workshop", EventStatus.DRAFT);

        given()
            .contentType("application/json")
            .when().post("/events/" + sourceId + "/duplicate")
            .then()
            .statusCode(201)
            .body("title", equalTo("Copie de Workshop (2)"));
    }

    @Test
    @TestSecurity(user = "auth0|erd-shift-7d")
    void duplicate_startEndDates_shifted7Days() {
        UUID caller = stageFreshUser();
        Long sourceId = persistEvent(caller, "Concert", EventStatus.PUBLISHED);

        Event source = QuarkusTransaction.requiringNew().call(
                () -> Event.<Event>findById(sourceId));

        String responseStart = given()
            .contentType("application/json")
            .when().post("/events/" + sourceId + "/duplicate")
            .then().statusCode(201)
            .extract().jsonPath().getString("startDate");

        // Compare as LocalDateTime values, not strings: LocalDateTime.toString()
        // omits the seconds field when it is zero (e.g. "...T07:11"), whereas the
        // JSON response always serialises it ("...T07:11:00"). Asserting on the
        // string form made this test flaky (~1 run in 60) whenever the persisted
        // startDate happened to land on a :00 second. Parsing both sides and
        // comparing the temporal values is form-insensitive yet still exact.
        LocalDateTime expected = source.startDate.plusDays(7);
        LocalDateTime actual = LocalDateTime.parse(responseStart);
        org.junit.jupiter.api.Assertions.assertEquals(
                expected, actual,
                "startDate should be shifted exactly +7 days");
    }

    /** Persist a row with optional dates/tags set to null (bypasses persistEvent helper). */
    private Long persistEventWithNulls(UUID creatorId, String title, EventStatus status,
                                       boolean nullDates, boolean nullTags) {
        Event e = new Event();
        e.title = title;
        e.description = "desc";
        e.location = "loc";
        if (!nullDates) {
            e.startDate = LocalDateTime.of(2999, Month.JANUARY, 1, 0, 0).plusDays(2).withNano(0);
            e.endDate = LocalDateTime.of(2999, Month.JANUARY, 1, 0, 0).plusDays(2).plusHours(2).withNano(0);
        }
        e.category = ch.unige.events.shared.domain.enums.EventCategory.ACADEMIC;
        e.creatorId = creatorId;
        e.status = status;
        e.allDay = false;
        e.featured = false;
        e.tags = nullTags ? null : new java.util.ArrayList<>(List.of("a"));
        QuarkusTransaction.requiringNew().run(e::persist);
        return e.id;
    }

    @Test
    @TestSecurity(user = "auth0|erd-null-dates")
    void duplicate_sourceWithNullDates_clonePreservesNullDates() {
        UUID caller = stageFreshUser();
        Long sourceId = persistEventWithNulls(caller, "NoDates", EventStatus.DRAFT,
                /* nullDates */ true, /* nullTags */ false);

        given()
            .contentType("application/json")
            .when().post("/events/" + sourceId + "/duplicate")
            .then()
            .statusCode(201)
            .body("startDate", equalTo(null))
            .body("endDate", equalTo(null));
    }

    @Test
    @TestSecurity(user = "auth0|erd-null-tags")
    void duplicate_sourceWithNullTags_cloneGetsEmptyTags() {
        UUID caller = stageFreshUser();
        Long sourceId = persistEventWithNulls(caller, "NoTags", EventStatus.PUBLISHED,
                /* nullDates */ false, /* nullTags */ true);

        given()
            .contentType("application/json")
            .when().post("/events/" + sourceId + "/duplicate")
            .then()
            .statusCode(201)
            .body("tags", equalTo(List.of()));
    }

    @Test
    @TestSecurity(user = "auth0|erd-long-title")
    void duplicate_sourceWithLongTitle_truncatesBeforeAppendingSuffixCap() {
        UUID caller = stageFreshUser();
        // 120-char title (the @Size max). "Copie de " + 120 chars = 129 → truncated to 114.
        String longTitle = "T".repeat(120);
        Long sourceId = persistEvent(caller, longTitle, EventStatus.PUBLISHED);

        given()
            .contentType("application/json")
            .when().post("/events/" + sourceId + "/duplicate")
            .then()
            .statusCode(201)
            // Title length must remain ≤ 120 chars (validation limit).
            .body("title.length()", org.hamcrest.Matchers.lessThanOrEqualTo(120));
    }

    @Test
    @TestSecurity(user = "auth0|erd-coorganizer")
    void duplicate_coOrganizerAccepted_canDuplicate() {
        // The owner creates the event; the co-organizer (the caller) accepts
        // the invitation and then duplicates. Exercises the
        // isCreatorOrAcceptedCoOrganizer branch of the authorization check.
        UUID owner = UUID.randomUUID();
        UUID coOrg = stageFreshUser();
        Long sourceId = persistEvent(owner, "Shared event", EventStatus.PUBLISHED);

        QuarkusTransaction.requiringNew().run(() -> {
            ch.unige.events.event.coorganizer.entity.EventCoOrganizer eco =
                    new ch.unige.events.event.coorganizer.entity.EventCoOrganizer();
            eco.eventId = sourceId;
            eco.userId = coOrg;
            eco.status = ch.unige.events.shared.domain.enums.CoOrganizerStatus.ACCEPTED;
            eco.persist();
        });

        given()
            .contentType("application/json")
            .when().post("/events/" + sourceId + "/duplicate")
            .then()
            .statusCode(201)
            // Décision N: caller (the co-organizer) becomes the new creator.
            .body("creatorId", equalTo(coOrg.toString()));
    }
}
