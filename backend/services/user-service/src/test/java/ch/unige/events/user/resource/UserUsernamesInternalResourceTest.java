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
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;

/**
 * REST-layer tests for {@link UserUsernamesInternalResource} (SCRUM-145).
 *
 * <p>Sentinels :
 * <ul>
 *   <li>Token + known handles → 200 + slim {@code [{id, username}, …]}.</li>
 *   <li>Token + mix of known/unknown handles → 200 + only the resolved
 *       rows (silent drop of unknowns, consumer treats them as
 *       <em>no notification</em>).</li>
 *   <li>Case-insensitive normalisation (input {@code Alice.Dosh} resolves
 *       the persisted lowercase row).</li>
 *   <li>Duplicate handles in the query collapse to one row.</li>
 *   <li>Empty / blank CSV → 200 + empty list (no DB hit).</li>
 *   <li>Anti-DoS cap : a 60-handle CSV silently truncates to 50.</li>
 *   <li>Missing / wrong X-Internal-Token → 404 (anti-oracle).</li>
 * </ul>
 */
@QuarkusTest
class UserUsernamesInternalResourceTest {

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
    void knownHandles_returnsIdProjections() {
        User alice = fixtures.persistUser("auth0|alice-ibu", "alice-ibu@example.com", true,
                "alice.ibu");
        User bob = fixtures.persistUser("auth0|bob-ibu", "bob-ibu@example.com", true,
                "bob.ibu");

        given()
            .header("X-Internal-Token", token())
            .queryParam("usernames", "alice.ibu,bob.ibu")
            .when().get("/users/_internal-by-usernames")
            .then()
            .statusCode(200)
            .body("$", hasSize(2))
            .body("username", hasItem("alice.ibu"))
            .body("username", hasItem("bob.ibu"))
            .body("id", hasItem(alice.id.toString()))
            .body("id", hasItem(bob.id.toString()));
    }

    @Test
    void missingHandlesSilentlyDropped() {
        fixtures.persistUser("auth0|alice-md", "alice-md@example.com", true, "alice.md");

        given()
            .header("X-Internal-Token", token())
            .queryParam("usernames", "alice.md,ghost.user")
            .when().get("/users/_internal-by-usernames")
            .then()
            .statusCode(200)
            .body("$", hasSize(1))
            .body("[0].username", equalTo("alice.md"));
    }

    @Test
    void caseInsensitive_inputMatchesLowercaseRow() {
        fixtures.persistUser("auth0|alice-ci", "alice-ci@example.com", true, "alice.ci");

        given()
            .header("X-Internal-Token", token())
            .queryParam("usernames", "Alice.CI")
            .when().get("/users/_internal-by-usernames")
            .then()
            .statusCode(200)
            .body("$", hasSize(1))
            .body("[0].username", equalTo("alice.ci"));
    }

    @Test
    void duplicatesInQuery_collapseToOneRow() {
        fixtures.persistUser("auth0|alice-dup", "alice-dup@example.com", true, "alice.dup");

        given()
            .header("X-Internal-Token", token())
            .queryParam("usernames", "alice.dup,alice.dup,alice.dup")
            .when().get("/users/_internal-by-usernames")
            .then()
            .statusCode(200)
            .body("$", hasSize(1));
    }

    @Test
    void emptyCsv_returnsEmptyList() {
        given()
            .header("X-Internal-Token", token())
            .queryParam("usernames", "")
            .when().get("/users/_internal-by-usernames")
            .then()
            .statusCode(200)
            .body("$", hasSize(0));
    }

    @Test
    void noUsernamesParam_returnsEmptyList() {
        given()
            .header("X-Internal-Token", token())
            .when().get("/users/_internal-by-usernames")
            .then()
            .statusCode(200)
            .body("$", hasSize(0));
    }

    @Test
    void blankCsvEntries_dropped() {
        fixtures.persistUser("auth0|alice-bl", "alice-bl@example.com", true, "alice.bl");

        given()
            .header("X-Internal-Token", token())
            .queryParam("usernames", " , alice.bl ,  ")
            .when().get("/users/_internal-by-usernames")
            .then()
            .statusCode(200)
            .body("$", hasSize(1))
            .body("[0].username", equalTo("alice.bl"));
    }

    @Test
    void cap_truncatesTo50() {
        // Persist 51 users, request all 51 + bogus padding ; expect at most
        // 50 rows back (the cap is silent, not a 400).
        StringBuilder csv = new StringBuilder();
        for (int i = 0; i < 51; i++) {
            String handle = "cap.user" + i;
            fixtures.persistUser("auth0|cap-user-" + i, "cap-user-" + i + "@example.com", true, handle);
            if (i > 0) csv.append(',');
            csv.append(handle);
        }

        given()
            .header("X-Internal-Token", token())
            .queryParam("usernames", csv.toString())
            .when().get("/users/_internal-by-usernames")
            .then()
            .statusCode(200)
            .body("$", hasSize(50));
    }

    @Test
    void wrongInternalToken_returns404_antiOracle() {
        if (token().isEmpty()) {
            return;
        }
        fixtures.persistUser("auth0|alice-wt", "alice-wt@example.com", true, "alice.wt");

        given()
            .header("X-Internal-Token", "wrong-token-value")
            .queryParam("usernames", "alice.wt")
            .when().get("/users/_internal-by-usernames")
            .then()
            .statusCode(404);
    }

    @Test
    void missingInternalToken_returns404() {
        if (token().isEmpty()) {
            return;
        }
        fixtures.persistUser("auth0|alice-nt", "alice-nt@example.com", true, "alice.nt");

        given()
            .queryParam("usernames", "alice.nt")
            .when().get("/users/_internal-by-usernames")
            .then()
            .statusCode(404);
    }

    @Test
    void onlyBlankCsvEntries_returnEmptyList() {
        // CSV of only blanks → filter drops everything → requested.isEmpty()
        // short-circuit kicks in BEFORE the JPQL IN clause runs. Distinct
        // from the empty / no-param cases handled above.
        given()
            .header("X-Internal-Token", token())
            .queryParam("usernames", " , ,  \t ")
            .when().get("/users/_internal-by-usernames")
            .then()
            .statusCode(200)
            .body("$", hasSize(0));
    }
}
