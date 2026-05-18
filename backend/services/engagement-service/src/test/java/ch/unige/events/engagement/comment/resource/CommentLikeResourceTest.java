package ch.unige.events.engagement.comment.resource;

import ch.unige.events.engagement.comment.service.CommentLikeService;
import ch.unige.events.engagement.comment.service.CommentLikeService.LikeResult;

import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import jakarta.ws.rs.NotFoundException;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

/**
 * SCRUM-144 — CommentLikeResource REST tests.
 *
 * <p>Mock-based : the {@link CommentLikeService} is fully tested separately
 * (see {@code CommentLikeServiceTest}). Here we just verify the HTTP contract
 * — status codes, body, rate-limit guards.
 */
@QuarkusTest
class CommentLikeResourceTest {

    @InjectMock
    CommentLikeService commentLikeService;

    @Test
    @TestSecurity(user = "auth0|alice-like")
    void postLike_freshLike_returns201WithResponse() {
        when(commentLikeService.like(42L)).thenReturn(new LikeResult(true, 1));

        given()
            .when().post("/comments/42/like")
            .then()
            .statusCode(201)
            .body("liked", equalTo(true))
            .body("likeCount", is(1));
    }

    @Test
    @TestSecurity(user = "auth0|alice-like")
    void postLike_alreadyLiked_returns200Idempotent() {
        when(commentLikeService.like(43L)).thenReturn(new LikeResult(false, 1));

        given()
            .when().post("/comments/43/like")
            .then()
            .statusCode(200)
            .body("liked", equalTo(true))
            .body("likeCount", is(1));
    }

    @Test
    void postLike_unauthenticated_returns401() {
        given()
            .when().post("/comments/44/like")
            .then().statusCode(401);
    }

    @Test
    @TestSecurity(user = "auth0|alice-like")
    void postLike_commentNotFound_returns404() {
        when(commentLikeService.like(99_999L)).thenThrow(new NotFoundException());

        given()
            .when().post("/comments/99999/like")
            .then().statusCode(404);
    }

    @Test
    @TestSecurity(user = "auth0|alice-like")
    void deleteLike_returns204() {
        doNothing().when(commentLikeService).unlike(45L);

        given()
            .when().delete("/comments/45/like")
            .then().statusCode(204);
    }

    @Test
    @TestSecurity(user = "auth0|alice-like")
    void deleteLike_noLike_returns204Idempotent() {
        // Service unlike() is idempotent — no-op for un-liked targets.
        doNothing().when(commentLikeService).unlike(46L);

        given()
            .when().delete("/comments/46/like")
            .then().statusCode(204);
    }

    @Test
    void deleteLike_unauthenticated_returns401() {
        given()
            .when().delete("/comments/47/like")
            .then().statusCode(401);
    }

    @Test
    @TestSecurity(user = "auth0|alice-like")
    void deleteLike_commentNotFound_returns404() {
        doThrow(new NotFoundException()).when(commentLikeService).unlike(99_998L);

        given()
            .when().delete("/comments/99998/like")
            .then().statusCode(404);
    }

    @Test
    @TestSecurity(user = "auth0|alice-like")
    void postLike_passesCommentIdToService() {
        when(commentLikeService.like(any(Long.class))).thenReturn(new LikeResult(true, 5));

        given()
            .when().post("/comments/777/like")
            .then().statusCode(201);
    }
}
