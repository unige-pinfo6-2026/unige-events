package ch.unige.events.resource;

import ch.unige.events.entity.Event;
import ch.unige.events.service.FavoriteServiceMock;
import ch.unige.events.service.ShareServiceMock;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import io.restassured.http.ContentType;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

@QuarkusTest
class FavoriteResourceTest {

    @Inject
    FavoriteServiceMock favoriteServiceMock;

    @Inject
    ShareServiceMock shareServiceMock;

    @BeforeEach
    void setUp() {
        favoriteServiceMock.reset();
        shareServiceMock.reset();
    }

    // =========================================================
    // POST /events/{id}/favorite
    // =========================================================

    @Test
    @TestSecurity(user = "auth0|alice")
    void addFavorite_existingEvent_returns200() {
        Event event = favoriteServiceMock.seedEvent("Conférence Java");

        given()
                .when().post("/events/{id}/favorite", event.id)
                .then()
                .statusCode(200);
    }

    @Test
    @TestSecurity(user = "auth0|alice")
    void addFavorite_alreadyFavorited_returns200Idempotent() {
        Event event = favoriteServiceMock.seedEvent("Conférence Java");
        // Premier ajout
        given().when().post("/events/{id}/favorite", event.id).then().statusCode(200);
        // Deuxième ajout — doit aussi retourner 200 sans erreur
        given().when().post("/events/{id}/favorite", event.id).then().statusCode(200);
    }

    @Test
    @TestSecurity(user = "auth0|alice")
    void addFavorite_unknownEvent_returns404() {
        FavoriteServiceMock.forceNotFoundOnAdd = true;

        given()
                .when().post("/events/{id}/favorite", 999L)
                .then()
                .statusCode(404)
                .body("error", is("not_found"));
    }

    @Test
    void addFavorite_unauthenticated_returns401() {
        given()
                .when().post("/events/{id}/favorite", 1L)
                .then()
                .statusCode(401);
    }

    // =========================================================
    // DELETE /events/{id}/favorite
    // =========================================================

    @Test
    @TestSecurity(user = "auth0|alice")
    void removeFavorite_existingFavorite_returns204() {
        Event event = favoriteServiceMock.seedEvent("Conférence Java");
        favoriteServiceMock.seedFavorite(event.id);

        given()
                .when().delete("/events/{id}/favorite", event.id)
                .then()
                .statusCode(204);
    }

    @Test
    @TestSecurity(user = "auth0|alice")
    void removeFavorite_notFavorited_returns404() {
        FavoriteServiceMock.forceNotFoundOnRemove = true;

        given()
                .when().delete("/events/{id}/favorite", 999L)
                .then()
                .statusCode(404)
                .body("error", is("not_found"));
    }

    @Test
    void removeFavorite_unauthenticated_returns401() {
        given()
                .when().delete("/events/{id}/favorite", 1L)
                .then()
                .statusCode(401);
    }

    // =========================================================
    // GET /events/{id}/share
    // =========================================================

    @Test
    @TestSecurity(user = "auth0|alice")
    void getShareInfo_existingEvent_returnsShareUrlAndCode() {
        Event event = shareServiceMock.seedEvent("Conférence Java");

        given()
                .when().get("/events/{id}/share", event.id)
                .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .body("shareUrl", containsString("/events/" + event.id))
                .body("shortCode", notNullValue())
                .body("shortCode", not(emptyString()));
    }

    @Test
    @TestSecurity(user = "auth0|alice")
    void getShareInfo_calledTwice_returnsSameShortCode() {
        Event event = shareServiceMock.seedEvent("Conférence Java");

        // Premier appel — génère le code
        String code1 = given()
                .when().get("/events/{id}/share", event.id)
                .then().statusCode(200)
                .extract().path("shortCode");

        // Deuxième appel — doit retourner le même code (idempotent)
        String code2 = given()
                .when().get("/events/{id}/share", event.id)
                .then().statusCode(200)
                .extract().path("shortCode");

        org.junit.jupiter.api.Assertions.assertEquals(code1, code2);
    }

    @Test
    @TestSecurity(user = "auth0|alice")
    void getShareInfo_unknownEvent_returns404() {
        given()
                .when().get("/events/{id}/share", 999L)
                .then()
                .statusCode(404)
                .body("error", is("not_found"));
    }

    @Test
    void getShareInfo_unauthenticated_returns401() {
        given()
                .when().get("/events/{id}/share", 1L)
                .then()
                .statusCode(401);
    }

    // =========================================================
    // GET /s/{shortCode} (RedirectResource)
    // =========================================================

    @Test
    void redirect_validShortCode_returns302() {
        Event event = shareServiceMock.seedEvent("Conférence Java");
        // Seed le code via getShareInfo
        shareServiceMock.getShareInfo(event.id);

        given()
                .redirects().follow(false)   // Ne pas suivre la redirection — on teste le 302
                .when().get("/s/{code}", event.shareCode)
                .then()
                .statusCode(302)
                .header("Location", containsString("/events/" + event.id));
    }

    @Test
    void redirect_unknownShortCode_returns404() {
        given()
                .when().get("/s/{code}", "inexistant")
                .then()
                .statusCode(404)
                .body("error", is("not_found"));
    }
}
