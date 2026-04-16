package ch.unige.events.resource;

import ch.unige.events.service.AttendanceServiceMock;
import ch.unige.events.service.CalendarServiceMock;
import ch.unige.events.service.FavoriteServiceMock;
import ch.unige.events.service.UserServiceMock;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.SecurityAttribute;
import io.quarkus.test.security.TestSecurity;
import io.restassured.http.ContentType;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.notNullValue;

@QuarkusTest
class UserResourceTest {

    @Inject UserServiceMock userServiceMock;
    @Inject FavoriteServiceMock favoriteServiceMock;
    @Inject CalendarServiceMock calendarServiceMock;
    @Inject AttendanceServiceMock attendanceServiceMock;

    @BeforeEach
    void setUp() {
        userServiceMock.reset();
        favoriteServiceMock.reset();
        attendanceServiceMock.reset();
    }

    @Test
    void getProfilePublicSuccess() {
        var user = userServiceMock.seedUser("auth0|public-profile", "public@example.com");
        user.profilePublic = true;

        given()
            .when().get("/users/" + user.id)
            .then()
            .statusCode(200)
            .contentType(ContentType.JSON)
            .body("id", equalTo(user.id.toString()));
    }

    @Test
    void getProfilePrivateForbidden() {
        var user = userServiceMock.seedUser("auth0|private-profile", "private@example.com");

        given()
            .when().get("/users/" + user.id)
            .then()
            .statusCode(403)
            .body("error", equalTo("forbidden"));
    }

    @Test
    void getProfileNotFound() {
        given()
            .when().get("/users/" + UUID.randomUUID())
            .then()
            .statusCode(404)
            .body("error", equalTo("not_found"));
    }

    @Test
    @TestSecurity(user = "auth0|alice", attributes = {
        @SecurityAttribute(key = "email", value = "alice@example.com"),
        @SecurityAttribute(key = "name", value = "Alice"),
        @SecurityAttribute(key = "picture", value = "https://cdn.example.com/alice.png")
    })
    void getMeAuthenticatedCreatesProfile() {
        given()
            .when().get("/users/me")
            .then()
            .statusCode(200)
            .contentType(ContentType.JSON)
            .body("id", notNullValue())
            .body("auth0Id", equalTo("auth0|alice"))
            .body("email", equalTo("alice@example.com"))
            .body("displayName", equalTo("Alice"))
            .body("avatarUrl", equalTo("https://cdn.example.com/alice.png"))
            .body("profilePublic", equalTo(false))
            .body("createdAt", notNullValue());
    }

    @Test
    @TestSecurity(user = "auth0|alice")
    void getMeAuthenticatedReturnsExistingProfile() {
        String firstId = userServiceMock.seedUser("auth0|alice", "alice@example.com").id.toString();

        given()
            .when().get("/users/me")
            .then()
            .statusCode(200)
            .body("id", equalTo(firstId));
    }

    @Test
    @TestSecurity(user = "auth0|bob")
    void getMeMissingEmailClaimReturnsUnauthorized() {
        given()
            .when().get("/users/me")
            .then()
            .statusCode(401)
            .contentType(ContentType.JSON)
            .body("error", equalTo("unauthorized"));
    }

    @Test
    void getMeUnauthenticated() {
        given()
            .when().get("/users/me")
            .then()
            .statusCode(401);
    }

    @Test
    @TestSecurity(user = "auth0|alice")
    void updateMePartialUpdateSuccess() {
        userServiceMock.seedUser("auth0|alice", "alice@example.com");

        given()
            .contentType(ContentType.JSON)
            .body("""
                {
                  "displayName": "Alice",
                  "bio": "Student at UNIGE",
                  "profilePublic": true
                }
                """)
            .when().put("/users/me")
            .then()
            .statusCode(200)
            .contentType(ContentType.JSON)
            .body("id", notNullValue())
            .body("auth0Id", equalTo("auth0|alice"))
            .body("email", equalTo("alice@example.com"))
            .body("displayName", equalTo("Alice"))
            .body("bio", equalTo("Student at UNIGE"))
            .body("profilePublic", equalTo(true));
    }

    @Test
    @TestSecurity(user = "auth0|alice")
    void updateMeNotFound() {
        given()
            .contentType(ContentType.JSON)
            .body("{}")
            .when().put("/users/me")
            .then()
            .statusCode(404)
            .body("error", equalTo("not_found"));
    }

    @Test
    @TestSecurity(user = "auth0|alice")
    void updateMeInvalidBodyUnknownField() {
        userServiceMock.seedUser("auth0|alice", "alice@example.com");

        given()
            .contentType(ContentType.JSON)
            .body("""
                {
                  "email": "new@example.com"
                }
                """)
            .when().put("/users/me")
            .then()
            .statusCode(400);
    }

    @Test
    @TestSecurity(user = "auth0|alice")
    void updateMeInvalidBodyValidation() {
        userServiceMock.seedUser("auth0|alice", "alice@example.com");

        given()
            .contentType(ContentType.JSON)
            .body("""
                {
                  "avatarUrl": "ftp://invalid.example.com/avatar.png"
                }
                """)
            .when().put("/users/me")
            .then()
            .statusCode(400)
            .body("error", equalTo("validation_error"));
    }

    @Test
    @TestSecurity(user = "auth0|alice")
    void updateMeForbidden() {
        userServiceMock.seedUser("auth0|alice", "alice@example.com");
        userServiceMock.forceForbiddenOnUpdate = true;

        given()
            .contentType(ContentType.JSON)
            .body("{}")
            .when().put("/users/me")
            .then()
            .statusCode(403)
            .body("error", equalTo("forbidden"));
    }

    @Test
    @TestSecurity(user = "auth0|alice")
    void updateMeConflict() {
        userServiceMock.seedUser("auth0|alice", "alice@example.com");
        userServiceMock.forceConflictOnUpdate = true;

        given()
            .contentType(ContentType.JSON)
            .body("{}")
            .when().put("/users/me")
            .then()
            .statusCode(409)
            .body("error", equalTo("conflict"));
    }

    @Test
    void updateMeUnauthenticated() {
        given()
            .contentType(ContentType.JSON)
            .body("{}")
            .when().put("/users/me")
            .then()
            .statusCode(401);
    }

    // --- POST /users/me/image ---

    @Test
    @TestSecurity(user = "auth0|alice")
    void uploadImageSuccess() {
        userServiceMock.seedUser("auth0|alice", "alice@example.com");

        given()
            .contentType("multipart/form-data")
            .multiPart("file", "photo.jpg", "fake-jpeg-bytes".getBytes(), "image/jpeg")
            .when().post("/users/me/image")
            .then()
            .statusCode(200)
            .contentType(ContentType.JSON)
            .body("avatarUrl", notNullValue());
    }

    @Test
    @TestSecurity(user = "auth0|alice")
    void uploadImageInvalidMimeReturns400() {
        userServiceMock.seedUser("auth0|alice", "alice@example.com");
        UserServiceMock.forceBadMimeOnUpload = true;

        given()
            .contentType("multipart/form-data")
            .multiPart("file", "script.sh", "#!/bin/bash".getBytes(), "text/plain")
            .when().post("/users/me/image")
            .then()
            .statusCode(400);
    }

    @Test
    void uploadImageUnauthenticatedReturns401() {
        given()
            .contentType("multipart/form-data")
            .multiPart("file", "photo.jpg", "fake-jpeg-bytes".getBytes(), "image/jpeg")
            .when().post("/users/me/image")
            .then()
            .statusCode(401);
    }

    @Test
    @TestSecurity(user = "auth0|alice")
    void uploadImageUserNotFoundReturns404() {
        // No seedUser — alice doesn't exist in the mock store

        given()
            .contentType("multipart/form-data")
            .multiPart("file", "photo.jpg", "fake-jpeg-bytes".getBytes(), "image/jpeg")
            .when().post("/users/me/image")
            .then()
            .statusCode(404)
            .body("error", equalTo("not_found"));
    }

    // --- POST /users/me/banner ---

    @Test
    @TestSecurity(user = "auth0|alice")
    void uploadBannerSuccess() {
        userServiceMock.seedUser("auth0|alice", "alice@example.com");

        given()
            .contentType("multipart/form-data")
            .multiPart("file", "banner.jpg", "fake-jpeg-bytes".getBytes(), "image/jpeg")
            .when().post("/users/me/banner")
            .then()
            .statusCode(200)
            .contentType(ContentType.JSON)
            .body("bannerUrl", notNullValue());
    }

    @Test
    @TestSecurity(user = "auth0|alice")
    void uploadBannerInvalidMimeReturns400() {
        userServiceMock.seedUser("auth0|alice", "alice@example.com");
        UserServiceMock.forceBadMimeOnBannerUpload = true;

        given()
            .contentType("multipart/form-data")
            .multiPart("file", "script.sh", "#!/bin/bash".getBytes(), "text/plain")
            .when().post("/users/me/banner")
            .then()
            .statusCode(400);
    }

    @Test
    void uploadBannerUnauthenticatedReturns401() {
        given()
            .contentType("multipart/form-data")
            .multiPart("file", "banner.jpg", "fake-jpeg-bytes".getBytes(), "image/jpeg")
            .when().post("/users/me/banner")
            .then()
            .statusCode(401);
    }

    @Test
    @TestSecurity(user = "auth0|alice")
    void uploadBannerUserNotFoundReturns404() {
        given()
            .contentType("multipart/form-data")
            .multiPart("file", "banner.jpg", "fake-jpeg-bytes".getBytes(), "image/jpeg")
            .when().post("/users/me/banner")
            .then()
            .statusCode(404)
            .body("error", equalTo("not_found"));
    }

    // --- DELETE /users/me/banner ---

    @Test
    @TestSecurity(user = "auth0|alice")
    void deleteBannerSuccess() {
        var user = userServiceMock.seedUser("auth0|alice", "alice@example.com");
        user.bannerUrl = "/api/uploads/test-banner.jpg";

        given()
            .when().delete("/users/me/banner")
            .then()
            .statusCode(200)
            .contentType(ContentType.JSON)
            .body("bannerUrl", equalTo(null));
    }

    @Test
    void deleteBannerUnauthenticatedReturns401() {
        given()
            .when().delete("/users/me/banner")
            .then()
            .statusCode(401);
    }

    @Test
    @TestSecurity(user = "auth0|alice")
    void deleteBannerUserNotFoundReturns404() {
        given()
            .when().delete("/users/me/banner")
            .then()
            .statusCode(404)
            .body("error", equalTo("not_found"));
    }

    // --- GET /users/me/favorites ---

    @Test
    @TestSecurity(user = "auth0|alice")
    void getMyFavorites_authenticated_returnsEmptyList() {
        given()
            .when().get("/users/me/favorites")
            .then()
            .statusCode(200)
            .contentType(ContentType.JSON)
            .body("$", hasSize(0));
    }

    @Test
    void getMyFavorites_unauthenticated_returns401() {
        given()
            .when().get("/users/me/favorites")
            .then()
            .statusCode(401);
    }

    // --- GET /users/me/calendar-token ---

    @Test
    @TestSecurity(user = "auth0|alice")
    void getCalendarToken_authenticated_returns200() {
        given()
            .when().get("/users/me/calendar-token")
            .then()
            .statusCode(200)
            .contentType(ContentType.JSON)
            .body("calendarToken", notNullValue())
            .body("webcalUrl", notNullValue())
            .body("httpsUrl", notNullValue());
    }

    @Test
    void getCalendarToken_unauthenticated_returns401() {
        given()
            .when().get("/users/me/calendar-token")
            .then()
            .statusCode(401);
    }

    @Test
    @TestSecurity(user = "auth0|alice")
    void regenerateCalendarToken_authenticated_returns200() {
        given()
            .when().post("/users/me/calendar-token/regenerate")
            .then()
            .statusCode(200)
            .contentType(ContentType.JSON)
            .body("calendarToken", notNullValue());
    }

    // --- GET /users/me/attendances ---

    @Test
    @TestSecurity(user = "auth0|alice")
    void getMyAttendances_authenticated_returns200() {
        given()
            .when().get("/users/me/attendances")
            .then()
            .statusCode(200)
            .contentType(ContentType.JSON)
            .body("$", hasSize(0));
    }

    @Test
    void getMyAttendances_unauthenticated_returns401() {
        given()
            .when().get("/users/me/attendances")
            .then()
            .statusCode(401);
    }
}
