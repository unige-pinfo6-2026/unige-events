package ch.unige.events.resource;

import ch.unige.events.service.CommentServiceMock;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;

@QuarkusTest
class CommentDirectResourceTest {

    @Inject
    CommentServiceMock commentServiceMock;

    @BeforeEach
    void setUp() {
        commentServiceMock.reset();
    }

    @Test
    @TestSecurity(user = "auth0|alice")
    void delete_byAuthor_returns204() {
        given()
                .when().delete("/comments/{commentId}", 42L)
                .then()
                .statusCode(204);
    }

    @Test
    @TestSecurity(user = "auth0|alice")
    void delete_byThirdParty_returns403_forbidden() {
        CommentServiceMock.forceForbiddenOnDelete = true;

        given()
                .when().delete("/comments/{commentId}", 42L)
                .then()
                .statusCode(403)
                .body("error", equalTo("forbidden"));
    }

    @Test
    @TestSecurity(user = "auth0|alice")
    void delete_unknownComment_returns404_commentNotFound() {
        CommentServiceMock.forceCommentNotFound = true;

        given()
                .when().delete("/comments/{commentId}", 99999L)
                .then()
                .statusCode(404)
                .body("error", equalTo("comment_not_found"));
    }

    @Test
    void delete_unauthenticated_returns401() {
        given()
                .when().delete("/comments/{commentId}", 42L)
                .then()
                .statusCode(401);
    }
}
