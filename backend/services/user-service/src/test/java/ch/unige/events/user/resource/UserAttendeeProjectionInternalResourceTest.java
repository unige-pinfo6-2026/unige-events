package ch.unige.events.user.resource;

import ch.unige.events.user.entity.User;
import ch.unige.events.user.test.TestFixtures;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.ConfigProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * REST-layer tests for {@link UserAttendeeProjectionInternalResource}.
 *
 * <p>Sentinels:
 * <ul>
 *   <li>Returns every requested id regardless of {@code profilePublic} (bypasses
 *       ISSUE-93 — this is the whole point of the internal endpoint).</li>
 *   <li>Silently drops unknown ids (used by engagement-service to render
 *       deleted users as anonymous rows).</li>
 *   <li>Empty / missing {@code ids} param → empty list (no DB call).</li>
 *   <li>Wrong {@code X-Internal-Token} → 404 anti-oracle (same body as a missing
 *       endpoint), since the resource carries {@link
 *       ch.unige.events.shared.jaxrs.Internal}.</li>
 * </ul>
 */
@QuarkusTest
class UserAttendeeProjectionInternalResourceTest {

    @Inject TestFixtures fixtures;
    @Inject UserAttendeeProjectionInternalResource resource;

    private static String token() {
        return ConfigProvider.getConfig()
                .getOptionalValue("unige.internal-token", String.class)
                .orElse("");
    }

    @BeforeEach
    void resetState() {
        fixtures.truncateAll();
    }

    @AfterEach
    void cleanRows() {
        fixtures.truncateAll();
    }

    @Test
    void returnsBothPublicAndPrivateProfiles_bypassingIssue93() {
        User pub = fixtures.persistUser("auth0|aiap-pub", "aiap-pub@example.com", true);
        User priv = fixtures.persistUser("auth0|aiap-priv", "aiap-priv@example.com", false);

        given()
            .header("X-Internal-Token", token())
            .queryParam("ids", pub.id, priv.id)
            .when().get("/users/_internal-attendee-projections")
            .then()
            .statusCode(200)
            .body("$", hasSize(2))
            .body("find { it.id == '" + pub.id + "' }.profilePublic", equalTo(true))
            .body("find { it.id == '" + priv.id + "' }.profilePublic", equalTo(false));
    }

    @Test
    void dropsUnknownIdsSilently() {
        User pub = fixtures.persistUser("auth0|aiap-known", "aiap-known@example.com", true);
        UUID ghost = UUID.randomUUID();

        given()
            .header("X-Internal-Token", token())
            .queryParam("ids", pub.id, ghost)
            .when().get("/users/_internal-attendee-projections")
            .then()
            .statusCode(200)
            .body("$", hasSize(1))
            .body("[0].id", equalTo(pub.id.toString()));
    }

    @Test
    void emptyIds_returnsEmptyList() {
        given()
            .header("X-Internal-Token", token())
            .when().get("/users/_internal-attendee-projections")
            .then()
            .statusCode(200)
            .body("$", hasSize(0));
    }

    @Test
    void getAttendeeProjections_nullIds_returnsEmptyList_directCall() {
        // JAX-RS never injects null for a @QueryParam List, so the
        // `ids == null` half of the L45 guard is only reachable via a direct
        // bean call. The null path touches no injected dependency.
        assertTrue(resource.getAttendeeProjections(null).isEmpty());
    }

    @Test
    void wrongInternalTokenReturns404() {
        // Only assert when the token is actually configured in this test run;
        // otherwise the InternalTokenFilter is a no-op and accepts the request.
        if (token().isEmpty()) {
            return;
        }
        given()
            .header("X-Internal-Token", "wrong-token-value")
            .when().get("/users/_internal-attendee-projections")
            .then()
            .statusCode(404);
    }
}
