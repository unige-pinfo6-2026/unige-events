package ch.unige.events.engagement.comment.resource;

import ch.unige.events.engagement.comment.entity.Comment;

import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import org.eclipse.microprofile.config.ConfigProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * QA bug batch (bug ③) — sentinels for the two moderation-facing internal
 * comment endpoints :
 * <ul>
 *   <li>{@code DELETE /comments/{id}/_internal-moderation} — hard-delete a
 *       reported comment; 204 on success, 404 when unknown / bad token.</li>
 *   <li>{@code GET /comments/_internal-by-ids?ids=…} — batch content projection
 *       for the admin reports listing; gated by X-Internal-Token.</li>
 * </ul>
 */
@SuppressWarnings("java:S1612")
@QuarkusTest
class CommentModerationInternalResourceTest {

    private static final long EVENT_A = 810_001L;
    private static final long EVENT_B = 810_002L;

    private static String token() {
        return ConfigProvider.getConfig()
                .getOptionalValue("unige.internal-token", String.class)
                .orElse("");
    }

    @BeforeEach
    @AfterEach
    void cleanRows() {
        QuarkusTransaction.requiringNew().run(() -> Comment.delete("eventId in ?1",
                List.of(EVENT_A, EVENT_B)));
    }

    private Long persistComment(Long eventId, String content) {
        return QuarkusTransaction.requiringNew().call(() -> {
            Comment c = new Comment();
            c.eventId = eventId;
            c.authorId = UUID.randomUUID();
            c.content = content;
            c.persist();
            return c.id;
        });
    }

    private boolean exists(Long id) {
        return QuarkusTransaction.requiringNew().call(() -> Comment.findByIdOptional(id).isPresent());
    }

    // ── DELETE /_internal-moderation ──────────────────────────────────────

    @Test
    void delete_validToken_existingComment_removesItAndReturns204() {
        Long id = persistComment(EVENT_A, "abusive");

        given()
            .header("X-Internal-Token", token())
            .when().delete("/comments/" + id + "/_internal-moderation")
            .then().statusCode(204);

        assertTrue(!exists(id), "comment must be hard-deleted");
    }

    @Test
    void delete_unknownComment_returns404() {
        given()
            .header("X-Internal-Token", token())
            .when().delete("/comments/999999999/_internal-moderation")
            .then().statusCode(404);
    }

    @Test
    void delete_wrongToken_returns404AntiOracle() {
        Long id = persistComment(EVENT_A, "abusive");
        given()
            .header("X-Internal-Token", "wrong-token-xxx")
            .when().delete("/comments/" + id + "/_internal-moderation")
            .then().statusCode(404);
        assertTrue(exists(id), "comment must survive a rejected (bad-token) request");
    }

    @Test
    void delete_missingToken_returns404() {
        given()
            .when().delete("/comments/1/_internal-moderation")
            .then().statusCode(404);
    }

    // ── GET /_internal-by-ids ─────────────────────────────────────────────

    @Test
    void byIds_validToken_returnsContentProjections() {
        Long a = persistComment(EVENT_A, "body-A");
        Long b = persistComment(EVENT_B, "body-B");

        given()
            .header("X-Internal-Token", token())
            .queryParam("ids", a, b)
            .when().get("/comments/_internal-by-ids")
            .then()
            .statusCode(200)
            .body("content", containsInAnyOrder("body-A", "body-B"))
            .body("id", hasSize(2));
    }

    @Test
    void byIds_unknownIds_omittedSilently() {
        Long a = persistComment(EVENT_A, "body-A");

        given()
            .header("X-Internal-Token", token())
            .queryParam("ids", a, 999999999L)
            .when().get("/comments/_internal-by-ids")
            .then()
            .statusCode(200)
            .body("id", hasSize(1))
            .body("[0].content", equalTo("body-A"));
    }

    @Test
    void byIds_wrongToken_returns404() {
        given()
            .header("X-Internal-Token", "wrong-token-xxx")
            .queryParam("ids", 1L)
            .when().get("/comments/_internal-by-ids")
            .then().statusCode(404);
    }

    @Test
    void byIds_noIdsParam_returnsEmptyList() {
        // The empty-input guard (ids null/empty → List.of()) is the contract:
        // EngagementServiceClient only calls this when there are comment reports,
        // but an empty batch must short-circuit to an empty list, not query.
        given()
            .header("X-Internal-Token", token())
            .when().get("/comments/_internal-by-ids")
            .then()
            .statusCode(200)
            .body("$.size()", equalTo(0));
    }
}
