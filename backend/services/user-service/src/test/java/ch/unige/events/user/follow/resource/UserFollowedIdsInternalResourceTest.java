package ch.unige.events.user.follow.resource;

import ch.unige.events.user.test.TestFixtures;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.ConfigProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;

/**
 * REST-layer tests for {@link UserFollowedIdsInternalResource}.
 *
 * <p>Sentinels:
 * <ul>
 *   <li>Returns the UUIDs of ACCEPTED followed users for a given followerId.</li>
 *   <li>Excludes PENDING rows.</li>
 *   <li>Null / missing followerId → empty list.</li>
 *   <li>Unknown followerId (no rows) → empty list.</li>
 *   <li>Wrong {@code X-Internal-Token} → 404 anti-oracle.</li>
 * </ul>
 */
@QuarkusTest
class UserFollowedIdsInternalResourceTest {

    @Inject TestFixtures fixtures;

    private static String token() {
        return ConfigProvider.getConfig()
                .getOptionalValue("unige.internal-token", String.class)
                .orElse("");
    }

    @BeforeEach
    void setup() {
        fixtures.truncateAll();
    }

    @AfterEach
    void cleanup() {
        fixtures.truncateAll();
    }

    @Test
    void returnsAcceptedFollowedIds() {
        var follower  = fixtures.persistUser("auth0|ifid-follower",  "ifid-follower@example.com",  true);
        var followed1 = fixtures.persistUser("auth0|ifid-followed1", "ifid-followed1@example.com", true);
        var followed2 = fixtures.persistUser("auth0|ifid-followed2", "ifid-followed2@example.com", false);
        fixtures.persistFollow(follower.id, followed1.id, ch.unige.events.shared.domain.enums.FollowStatus.ACCEPTED);
        fixtures.persistFollow(follower.id, followed2.id, ch.unige.events.shared.domain.enums.FollowStatus.ACCEPTED);

        given()
            .header("X-Internal-Token", token())
            .queryParam("followerId", follower.id.toString())
            .when().get("/users/_internal-followed-ids")
            .then()
            .statusCode(200)
            .body("$", hasSize(2))
            .body("$", hasItem(followed1.id.toString()))
            .body("$", hasItem(followed2.id.toString()));
    }

    @Test
    void excludesPendingRows() {
        var follower = fixtures.persistUser("auth0|ifid-pend-follower", "ifid-pend-follower@example.com", true);
        var pending  = fixtures.persistUser("auth0|ifid-pend-target",   "ifid-pend-target@example.com",   false);
        var accepted = fixtures.persistUser("auth0|ifid-pend-ok",       "ifid-pend-ok@example.com",       true);
        fixtures.persistFollow(follower.id, pending.id,  ch.unige.events.shared.domain.enums.FollowStatus.PENDING);
        fixtures.persistFollow(follower.id, accepted.id, ch.unige.events.shared.domain.enums.FollowStatus.ACCEPTED);

        given()
            .header("X-Internal-Token", token())
            .queryParam("followerId", follower.id.toString())
            .when().get("/users/_internal-followed-ids")
            .then()
            .statusCode(200)
            .body("$", hasSize(1))
            .body("$", hasItem(accepted.id.toString()))
            .body("$", not(hasItem(pending.id.toString())));
    }

    @Test
    void missingFollowerId_returnsEmptyList() {
        given()
            .header("X-Internal-Token", token())
            .when().get("/users/_internal-followed-ids")
            .then()
            .statusCode(200)
            .body("$", empty());
    }

    @Test
    void unknownFollowerId_returnsEmptyList() {
        given()
            .header("X-Internal-Token", token())
            .queryParam("followerId", UUID.randomUUID().toString())
            .when().get("/users/_internal-followed-ids")
            .then()
            .statusCode(200)
            .body("$", empty());
    }

    @Test
    void wrongInternalToken_returns404() {
        if (token().isEmpty()) {
            return;
        }
        given()
            .header("X-Internal-Token", "bad-token")
            .queryParam("followerId", UUID.randomUUID().toString())
            .when().get("/users/_internal-followed-ids")
            .then()
            .statusCode(404);
    }
}
