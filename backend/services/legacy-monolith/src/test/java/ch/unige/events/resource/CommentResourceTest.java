package ch.unige.events.resource;

import ch.unige.events.config.RateLimitState;
import ch.unige.events.dto.comment.CommentDTO;
import ch.unige.events.service.CommentServiceMock;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import io.restassured.http.ContentType;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.notNullValue;

@QuarkusTest
class CommentResourceTest {

    @Inject
    CommentServiceMock commentServiceMock;

    @Inject
    RateLimitState rateLimitState;

    @BeforeEach
    void setUp() {
        commentServiceMock.reset();
        // Each test starts with a fresh rate-limit budget so the comments.post
        // bucket (max=10) doesn't bleed across tests under the same auth0 user.
        rateLimitState.clearBuckets();
    }

    // -------------------------------------------------------------------------
    // POST /events/{eventId}/comments
    // -------------------------------------------------------------------------

    @Test
    @TestSecurity(user = "auth0|alice")
    void post_validBody_returns201() {
        given()
                .contentType(ContentType.JSON)
                .body("{\"content\":\"Hello world\"}")
                .when().post("/events/{eventId}/comments", 1L)
                .then()
                .statusCode(201)
                .body("content", equalTo("Hello world"))
                .body("authorIsOrganizer", equalTo(false))
                .body("likeCount", equalTo(0))
                .body("likedByMe", equalTo(false))
                .body("parentCommentId", equalTo(null))
                .body("replies", hasSize(0))
                .body("createdAt", notNullValue());
    }

    @Test
    @TestSecurity(user = "auth0|alice")
    void post_withParentCommentId_reflectsParent() {
        given()
                .contentType(ContentType.JSON)
                .body("{\"content\":\"A reply\",\"parentCommentId\":42}")
                .when().post("/events/{eventId}/comments", 1L)
                .then()
                .statusCode(201)
                .body("parentCommentId", equalTo(42));
    }

    @Test
    @TestSecurity(user = "auth0|alice")
    void post_emptyContent_returns400() {
        given()
                .contentType(ContentType.JSON)
                .body("{\"content\":\"\"}")
                .when().post("/events/{eventId}/comments", 1L)
                .then()
                .statusCode(400);
    }

    @Test
    @TestSecurity(user = "auth0|alice")
    void post_blankContent_returns400() {
        given()
                .contentType(ContentType.JSON)
                .body("{\"content\":\"   \"}")
                .when().post("/events/{eventId}/comments", 1L)
                .then()
                .statusCode(400);
    }

    @Test
    @TestSecurity(user = "auth0|alice")
    void post_contentTooLong_returns400() {
        String tooLong = "a".repeat(2001);
        given()
                .contentType(ContentType.JSON)
                .body("{\"content\":\"" + tooLong + "\"}")
                .when().post("/events/{eventId}/comments", 1L)
                .then()
                .statusCode(400);
    }

    @Test
    @TestSecurity(user = "auth0|alice")
    void post_eventDraftByNonCreator_returns404_antiOracle() {
        CommentServiceMock.forceNotFoundOnEvent = true;
        given()
                .contentType(ContentType.JSON)
                .body("{\"content\":\"Hello\"}")
                .when().post("/events/{eventId}/comments", 999L)
                .then()
                .statusCode(404);
    }

    @Test
    @TestSecurity(user = "auth0|alice")
    void post_eventCancelled_returns400_cannotCommentCancelled() {
        CommentServiceMock.forceCannotCommentCancelled = true;
        given()
                .contentType(ContentType.JSON)
                .body("{\"content\":\"Hello\"}")
                .when().post("/events/{eventId}/comments", 1L)
                .then()
                .statusCode(400)
                .body("error", equalTo("cannot_comment_cancelled_event"));
    }

    @Test
    @TestSecurity(user = "auth0|alice")
    void post_eventExpired_returns400_cannotCommentExpired() {
        CommentServiceMock.forceCannotCommentExpired = true;
        given()
                .contentType(ContentType.JSON)
                .body("{\"content\":\"Hello\"}")
                .when().post("/events/{eventId}/comments", 1L)
                .then()
                .statusCode(400)
                .body("error", equalTo("cannot_comment_expired_event"));
    }

    @Test
    @TestSecurity(user = "auth0|alice")
    void post_eventDraftByCreator_returns400_cannotCommentDraft() {
        CommentServiceMock.forceCannotCommentDraft = true;
        given()
                .contentType(ContentType.JSON)
                .body("{\"content\":\"Hello\"}")
                .when().post("/events/{eventId}/comments", 1L)
                .then()
                .statusCode(400)
                .body("error", equalTo("cannot_comment_draft_event"));
    }

    @Test
    @TestSecurity(user = "auth0|alice")
    void post_replyToReply_returns422_repliesTooDeep() {
        CommentServiceMock.forceRepliesTooDeep = true;
        given()
                .contentType(ContentType.JSON)
                .body("{\"content\":\"Too deep\",\"parentCommentId\":1}")
                .when().post("/events/{eventId}/comments", 1L)
                .then()
                .statusCode(422)
                .body("error", equalTo("replies_too_deep"));
    }

    @Test
    @TestSecurity(user = "auth0|alice")
    void post_parentInOtherEvent_returns422_parentNotInEvent() {
        CommentServiceMock.forceParentNotInEvent = true;
        given()
                .contentType(ContentType.JSON)
                .body("{\"content\":\"Cross-event\",\"parentCommentId\":1}")
                .when().post("/events/{eventId}/comments", 1L)
                .then()
                .statusCode(422)
                .body("error", equalTo("parent_comment_not_in_event"));
    }

    @Test
    @TestSecurity(user = "auth0|alice")
    void post_unknownParent_returns404_parentNotFound() {
        CommentServiceMock.forceParentNotFound = true;
        given()
                .contentType(ContentType.JSON)
                .body("{\"content\":\"Orphan\",\"parentCommentId\":99999}")
                .when().post("/events/{eventId}/comments", 1L)
                .then()
                .statusCode(404)
                .body("error", equalTo("parent_comment_not_found"));
    }

    @Test
    void post_unauthenticated_returns401() {
        given()
                .contentType(ContentType.JSON)
                .body("{\"content\":\"Hello\"}")
                .when().post("/events/{eventId}/comments", 1L)
                .then()
                .statusCode(401);
    }

    @Test
    @TestSecurity(user = "auth0|alice")
    void post_missingBody_returns400() {
        given()
                .contentType(ContentType.JSON)
                .when().post("/events/{eventId}/comments", 1L)
                .then()
                .statusCode(400);
    }

    // -------------------------------------------------------------------------
    // GET /events/{eventId}/comments
    // -------------------------------------------------------------------------

    @Test
    @TestSecurity(user = "auth0|alice")
    void get_authenticatedReturnsList() {
        commentServiceMock.seedTopLevel(new CommentDTO(
                1L, "Hello", UUID.randomUUID(), "Author", null,
                false, 0, false, LocalDateTime.now(), null, List.of()));

        given()
                .when().get("/events/{eventId}/comments", 1L)
                .then()
                .statusCode(200)
                .body("$", hasSize(1))
                .body("[0].content", equalTo("Hello"));
    }

    @Test
    void get_anonymousReturnsList_permitAll() {
        commentServiceMock.seedTopLevel(new CommentDTO(
                1L, "Public", UUID.randomUUID(), "Author", null,
                false, 0, false, LocalDateTime.now(), null, List.of()));

        given()
                .when().get("/events/{eventId}/comments", 1L)
                .then()
                .statusCode(200)
                .body("$", hasSize(1));
    }

    @Test
    void get_eventInvisibleReturnsNotFound_antiOracle() {
        CommentServiceMock.forceNotFoundOnEvent = true;

        given()
                .when().get("/events/{eventId}/comments", 999L)
                .then()
                .statusCode(404);
    }

    @Test
    void get_emptyList_returnsEmptyArray() {
        given()
                .when().get("/events/{eventId}/comments", 1L)
                .then()
                .statusCode(200)
                .body("$", hasSize(0));
    }

    @Test
    void get_sizeOver100_returns400() {
        given()
                .when().get("/events/{eventId}/comments?size=101", 1L)
                .then()
                .statusCode(400);
    }

    @Test
    void get_negativePage_returns400() {
        given()
                .when().get("/events/{eventId}/comments?page=-1", 1L)
                .then()
                .statusCode(400);
    }
}
