package ch.unige.events.e2e;

import io.restassured.RestAssured;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;

/**
 * Étape 6.5 of finalization spec.
 *
 * <p>Cross-service happy path against the deployed preview cluster
 * (Kong ingress + Kafka + 4 services + db + minio). Drives the full
 * UNIGE Events flow that combines event-service (CRUD + cascade) and
 * the strict cross-service contracts pinned by the contract-tests
 * module.
 *
 * <p>Activation: only enabled when {@code UNIGE_EVENTS_E2E_BASE_URL}
 * env var is set (typically to a preview cluster URL like
 * {@code https://pr-158.unige-events.example}). The full flow requires
 * a valid Auth0 JWT — passed via {@code UNIGE_EVENTS_E2E_TOKEN} (oauth2
 * Bearer). When the env vars are absent, the test is skipped — so
 * unit-style {@code mvn test} runs locally without trying to hit the
 * cluster.
 *
 * <p>Tagged {@code e2e} so a plain {@code mvn -Dtest=E2EHappyPathTest}
 * still works on a configured machine.
 */
@Tag("e2e")
@EnabledIfEnvironmentVariable(named = "UNIGE_EVENTS_E2E_BASE_URL", matches = ".+")
class E2EHappyPathTest {

    private static final String BASE_URL = System.getenv("UNIGE_EVENTS_E2E_BASE_URL");
    private static final String TOKEN = System.getenv("UNIGE_EVENTS_E2E_TOKEN");

    @Test
    void user_can_create_publish_get_event() {
        RestAssured.baseURI = BASE_URL;

        // Step 1: POST /api/users/me — auto-create user from JWT claims.
        given()
                .auth().oauth2(TOKEN)
                .when().post("/api/users/me")
                .then().statusCode(200);

        // Step 2: POST /api/events — create event in DRAFT.
        Long eventId = given()
                .auth().oauth2(TOKEN)
                .contentType("application/json")
                .body("""
                        {
                          "title": "E2E happy path event",
                          "description": "auto-created by E2EHappyPathTest",
                          "location": "Uni-Mail",
                          "startDate": "2026-06-01T10:00:00",
                          "endDate":   "2026-06-01T12:00:00",
                          "category":  "ACADEMIC",
                          "faculty":   "SCIENCES"
                        }
                        """)
                .when().post("/api/events")
                .then().statusCode(201)
                .extract().jsonPath().getLong("id");

        // Step 3: PATCH /api/events/{id}/publish — DRAFT → PUBLISHED.
        given()
                .auth().oauth2(TOKEN)
                .when().patch("/api/events/" + eventId + "/publish")
                .then().statusCode(200)
                .body("status", equalTo("PUBLISHED"));

        // Step 4: GET /api/events/{id} — public read, creatorId enriched.
        given()
                .auth().oauth2(TOKEN)
                .when().get("/api/events/" + eventId)
                .then().statusCode(200)
                .body("status", equalTo("PUBLISHED"))
                .body("creatorId", notNullValue());
    }
}
