package ch.unige.events.engagement.attendance.resource;

import ch.unige.events.engagement.attendance.entity.Attendance;
import ch.unige.events.shared.domain.enums.AttendanceStatus;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import org.eclipse.microprofile.config.ConfigProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;

/**
 * REST-layer tests for {@link AttendeeIdsInternalResource} (SCRUM-99 Bloc F).
 *
 * <p>Sentinels:
 * <ul>
 *   <li>{@code ATTENDING} status returns only attending user ids (WAITLISTED
 *       must be filtered out — important for {@code NEW_ATTENDEE} semantics).</li>
 *   <li>{@code WAITLISTED} status returns only waitlisted user ids.</li>
 *   <li>Unknown event id returns 200 + empty list (no oracle on event existence).</li>
 *   <li>Wrong / missing {@code X-Internal-Token} → 404 anti-oracle (same envelope).</li>
 * </ul>
 */
@QuarkusTest
class AttendeeIdsInternalResourceTest {

    private static final Long ATTEND_ONLY_EVENT = 800_001L;
    private static final Long MIXED_EVENT = 800_002L;
    private static final Long UNKNOWN_EVENT = 9_999_999L;

    private static String token() {
        return ConfigProvider.getConfig()
                .getOptionalValue("unige.internal-token", String.class)
                .orElse("");
    }

    @AfterEach
    void cleanRows() {
        QuarkusTransaction.requiringNew().run(() -> Attendance.delete("eventId in ?1",
                List.of(ATTEND_ONLY_EVENT, MIXED_EVENT)));
    }

    private static void persist(UUID userId, Long eventId, AttendanceStatus status) {
        Attendance a = new Attendance();
        a.userId = userId;
        a.eventId = eventId;
        a.status = status;
        a.persist();
    }

    @Test
    void attendingStatus_returnsOnlyAttendingUserIds() {
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        UUID w = UUID.randomUUID();
        QuarkusTransaction.requiringNew().run(() -> {
            persist(a, MIXED_EVENT, AttendanceStatus.ATTENDING);
            persist(b, MIXED_EVENT, AttendanceStatus.ATTENDING);
            persist(w, MIXED_EVENT, AttendanceStatus.WAITLISTED);
        });

        given()
            .header("X-Internal-Token", token())
            .when().get("/events/" + MIXED_EVENT + "/_internal-attendee-ids?status=ATTENDING")
            .then()
            .statusCode(200)
            .body("$", hasSize(2))
            .body("$", hasItem(a.toString()))
            .body("$", hasItem(b.toString()))
            .body("$", not(hasItem(w.toString())));
    }

    @Test
    void waitlistedStatus_returnsOnlyWaitlistedUserIds() {
        UUID a = UUID.randomUUID();
        UUID w = UUID.randomUUID();
        QuarkusTransaction.requiringNew().run(() -> {
            persist(a, MIXED_EVENT, AttendanceStatus.ATTENDING);
            persist(w, MIXED_EVENT, AttendanceStatus.WAITLISTED);
        });

        given()
            .header("X-Internal-Token", token())
            .when().get("/events/" + MIXED_EVENT + "/_internal-attendee-ids?status=WAITLISTED")
            .then()
            .statusCode(200)
            .body("$", hasSize(1))
            .body("$", hasItem(w.toString()));
    }

    @Test
    void defaultStatus_isAttending() {
        // No status query param → default ATTENDING (cf. @DefaultValue).
        UUID a = UUID.randomUUID();
        QuarkusTransaction.requiringNew().run(() -> persist(a, ATTEND_ONLY_EVENT, AttendanceStatus.ATTENDING));

        given()
            .header("X-Internal-Token", token())
            .when().get("/events/" + ATTEND_ONLY_EVENT + "/_internal-attendee-ids")
            .then()
            .statusCode(200)
            .body("$", hasSize(1))
            .body("$", hasItem(a.toString()));
    }

    @Test
    void unknownEvent_returns200WithEmptyList() {
        given()
            .header("X-Internal-Token", token())
            .when().get("/events/" + UNKNOWN_EVENT + "/_internal-attendee-ids")
            .then()
            .statusCode(200)
            .body("$", hasSize(0));
    }

    @Test
    void missingInternalToken_returns404() {
        if (token().isEmpty()) {
            return;
        }
        given()
            .when().get("/events/" + UNKNOWN_EVENT + "/_internal-attendee-ids")
            .then()
            .statusCode(404);
    }

    @Test
    void wrongInternalToken_returns404SameBodyAsMissing() {
        if (token().isEmpty()) {
            return;
        }
        // Anti-oracle: wrong token + missing token must produce the same body.
        io.restassured.response.Response missing = given()
                .when().get("/events/" + UNKNOWN_EVENT + "/_internal-attendee-ids");
        io.restassured.response.Response wrong = given()
                .header("X-Internal-Token", "wrong-token-value")
                .when().get("/events/" + UNKNOWN_EVENT + "/_internal-attendee-ids");

        org.junit.jupiter.api.Assertions.assertEquals(404, missing.getStatusCode());
        org.junit.jupiter.api.Assertions.assertEquals(404, wrong.getStatusCode());
        org.junit.jupiter.api.Assertions.assertEquals(
                missing.getBody().asString(), wrong.getBody().asString(),
                "Token-mismatch body must equal missing-token body (anti-oracle)");
    }
}
