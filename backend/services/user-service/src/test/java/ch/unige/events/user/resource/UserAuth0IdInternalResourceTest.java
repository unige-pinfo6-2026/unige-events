package ch.unige.events.user.resource;

import ch.unige.events.user.entity.User;
import ch.unige.events.user.test.TestFixtures;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.ConfigProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;

/**
 * REST-layer tests for {@link UserAuth0IdInternalResource} (SCRUM-99 Decision E).
 *
 * <p>Sentinels:
 * <ul>
 *   <li>Token + known auth0Id → 200 + {@code { id: UUID }}.</li>
 *   <li>Token + unknown auth0Id → 404 (NotFoundException).</li>
 *   <li>Wrong token → 404 (anti-oracle, same envelope as unknown auth0Id —
 *       no way for an attacker to tell which is which).</li>
 *   <li>No token → 404 (same envelope).</li>
 * </ul>
 */
@QuarkusTest
class UserAuth0IdInternalResourceTest {

    @Inject TestFixtures fixtures;

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
    void knownAuth0Id_returnsIdProjection() {
        User u = fixtures.persistUser("auth0|abi-known", "abi-known@example.com", true);

        given()
            .header("X-Internal-Token", token())
            .when().get("/users/_internal-by-auth0-id/{auth0Id}", "auth0|abi-known")
            .then()
            .statusCode(200)
            .body("id", equalTo(u.id.toString()))
            .body("id", notNullValue());
    }

    @Test
    void unknownAuth0Id_returns404() {
        given()
            .header("X-Internal-Token", token())
            .when().get("/users/_internal-by-auth0-id/{auth0Id}", "auth0|abi-missing")
            .then()
            .statusCode(404);
    }

    @Test
    void wrongInternalToken_returns404_antiOracle() {
        // When the token is configured, a wrong header value must produce
        // the same envelope as an unknown auth0Id.
        if (token().isEmpty()) {
            return;
        }
        User u = fixtures.persistUser("auth0|abi-otoken", "abi-otoken@example.com", true);
        given()
            .header("X-Internal-Token", "wrong-token-value")
            .when().get("/users/_internal-by-auth0-id/{auth0Id}", u.auth0Id)
            .then()
            .statusCode(404);
    }

    @Test
    void missingInternalToken_returns404() {
        if (token().isEmpty()) {
            return;
        }
        User u = fixtures.persistUser("auth0|abi-notoken", "abi-notoken@example.com", true);
        given()
            .when().get("/users/_internal-by-auth0-id/{auth0Id}", u.auth0Id)
            .then()
            .statusCode(404);
    }
}
