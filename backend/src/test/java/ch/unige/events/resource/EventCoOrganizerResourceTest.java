package ch.unige.events.resource;

import ch.unige.events.dto.coorganizer.CoOrganizerDTO;
import ch.unige.events.entity.CoOrganizerStatus;
import ch.unige.events.service.EventCoOrganizerServiceMock;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import io.restassured.http.ContentType;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;

@QuarkusTest
class EventCoOrganizerResourceTest {

    @Inject
    EventCoOrganizerServiceMock serviceMock;

    @BeforeEach
    void setUp() {
        serviceMock.reset();
    }

    // =========================================================
    // POST /events/{id}/co-organizers
    // =========================================================

    @Test
    @TestSecurity(user = "auth0|alice")
    void invite_byCreator_returns201() {
        UUID targetUserId = UUID.randomUUID();
        given()
                .contentType(ContentType.JSON)
                .body("{\"userId\":\"" + targetUserId + "\"}")
                .when().post("/events/{id}/co-organizers", 1L)
                .then()
                .statusCode(201)
                .body("status", equalTo("PENDING"))
                .body("userId", equalTo(targetUserId.toString()));
    }

    @Test
    @TestSecurity(user = "auth0|bob")
    void invite_byNonCreator_returns403() {
        EventCoOrganizerServiceMock.forceForbiddenOnInvite = true;
        given()
                .contentType(ContentType.JSON)
                .body("{\"userId\":\"" + UUID.randomUUID() + "\"}")
                .when().post("/events/{id}/co-organizers", 1L)
                .then()
                .statusCode(403);
    }

    @Test
    @TestSecurity(user = "auth0|alice")
    void invite_eventNotFound_returns404() {
        EventCoOrganizerServiceMock.forceNotFoundOnInvite = true;
        given()
                .contentType(ContentType.JSON)
                .body("{\"userId\":\"" + UUID.randomUUID() + "\"}")
                .when().post("/events/{id}/co-organizers", 999L)
                .then()
                .statusCode(404);
    }

    @Test
    @TestSecurity(user = "auth0|alice")
    void invite_self_returns400() {
        EventCoOrganizerServiceMock.forceCannotInviteSelf = true;
        given()
                .contentType(ContentType.JSON)
                .body("{\"userId\":\"" + UUID.randomUUID() + "\"}")
                .when().post("/events/{id}/co-organizers", 1L)
                .then()
                .statusCode(400)
                .body("error", equalTo("cannot_invite_self"));
    }

    @Test
    @TestSecurity(user = "auth0|alice")
    void invite_alreadyInvited_returns409() {
        EventCoOrganizerServiceMock.forceConflictOnInvite = true;
        given()
                .contentType(ContentType.JSON)
                .body("{\"userId\":\"" + UUID.randomUUID() + "\"}")
                .when().post("/events/{id}/co-organizers", 1L)
                .then()
                .statusCode(409)
                .body("error", equalTo("already_invited"));
    }

    @Test
    @TestSecurity(user = "auth0|alice")
    void invite_targetUserNotFound_returns404() {
        EventCoOrganizerServiceMock.forceNotFoundOnInvite = true;
        given()
                .contentType(ContentType.JSON)
                .body("{\"userId\":\"" + UUID.randomUUID() + "\"}")
                .when().post("/events/{id}/co-organizers", 1L)
                .then()
                .statusCode(404);
    }

    @Test
    void invite_unauthenticated_returns401() {
        given()
                .contentType(ContentType.JSON)
                .body("{\"userId\":\"" + UUID.randomUUID() + "\"}")
                .when().post("/events/{id}/co-organizers", 1L)
                .then()
                .statusCode(401);
    }

    @Test
    @TestSecurity(user = "auth0|alice")
    void invite_missingUserId_returns400() {
        given()
                .contentType(ContentType.JSON)
                .body("{}")
                .when().post("/events/{id}/co-organizers", 1L)
                .then()
                .statusCode(400);
    }

    @Test
    @TestSecurity(user = "auth0|admin", roles = {"ADMIN"})
    void invite_byAdmin_returns201() {
        UUID targetUserId = UUID.randomUUID();
        given()
                .contentType(ContentType.JSON)
                .body("{\"userId\":\"" + targetUserId + "\"}")
                .when().post("/events/{id}/co-organizers", 1L)
                .then()
                .statusCode(201)
                .body("status", equalTo("PENDING"));
    }

    // =========================================================
    // PATCH /events/{id}/co-organizers/me/accept
    // =========================================================

    @Test
    @TestSecurity(user = "auth0|bob")
    void accept_pendingInvitation_returns200() {
        given()
                .when().patch("/events/{id}/co-organizers/me/accept", 1L)
                .then()
                .statusCode(200)
                .body("status", equalTo("ACCEPTED"));
    }

    @Test
    @TestSecurity(user = "auth0|carol")
    void accept_noInvitation_returns404() {
        EventCoOrganizerServiceMock.forceNotFoundOnAccept = true;
        given()
                .when().patch("/events/{id}/co-organizers/me/accept", 1L)
                .then()
                .statusCode(404);
    }

    @Test
    void accept_unauthenticated_returns401() {
        given()
                .when().patch("/events/{id}/co-organizers/me/accept", 1L)
                .then()
                .statusCode(401);
    }

    // =========================================================
    // PATCH /events/{id}/co-organizers/me/decline
    // =========================================================

    @Test
    @TestSecurity(user = "auth0|bob")
    void decline_pendingInvitation_returns204() {
        given()
                .when().patch("/events/{id}/co-organizers/me/decline", 1L)
                .then()
                .statusCode(204);
    }

    @Test
    @TestSecurity(user = "auth0|bob")
    void decline_noInvitation_returns404() {
        EventCoOrganizerServiceMock.forceNotFoundOnDecline = true;
        given()
                .when().patch("/events/{id}/co-organizers/me/decline", 1L)
                .then()
                .statusCode(404);
    }

    // =========================================================
    // DELETE /events/{id}/co-organizers/{userId}
    // =========================================================

    @Test
    @TestSecurity(user = "auth0|alice")
    void remove_byCreator_returns204() {
        given()
                .when().delete("/events/{id}/co-organizers/{userId}", 1L, UUID.randomUUID())
                .then()
                .statusCode(204);
    }

    @Test
    @TestSecurity(user = "auth0|bob")
    void remove_byNonCreator_returns403() {
        EventCoOrganizerServiceMock.forceForbiddenOnRemove = true;
        given()
                .when().delete("/events/{id}/co-organizers/{userId}", 1L, UUID.randomUUID())
                .then()
                .statusCode(403);
    }

    @Test
    @TestSecurity(user = "auth0|admin", roles = {"ADMIN"})
    void remove_byAdmin_returns204() {
        given()
                .when().delete("/events/{id}/co-organizers/{userId}", 1L, UUID.randomUUID())
                .then()
                .statusCode(204);
    }

    @Test
    @TestSecurity(user = "auth0|alice")
    void remove_idempotentWhenNoInvitation_returns204() {
        given()
                .when().delete("/events/{id}/co-organizers/{userId}", 1L, UUID.randomUUID())
                .then()
                .statusCode(204);
    }

    // =========================================================
    // GET /events/{id}/co-organizers
    // =========================================================

    @Test
    @TestSecurity(user = "auth0|alice")
    void list_emptyEvent_returnsEmptyArray() {
        given()
                .when().get("/events/{id}/co-organizers", 1L)
                .then()
                .statusCode(200)
                .body("$", hasSize(0));
    }

    @Test
    @TestSecurity(user = "auth0|alice")
    void list_withInvitations_returnsArray() {
        serviceMock.coOrganizersFixture.add(new CoOrganizerDTO(
                1L, UUID.randomUUID(), "Alice", null,
                CoOrganizerStatus.PENDING, LocalDateTime.now()));
        serviceMock.coOrganizersFixture.add(new CoOrganizerDTO(
                2L, UUID.randomUUID(), "Bob", null,
                CoOrganizerStatus.ACCEPTED, LocalDateTime.now()));

        given()
                .when().get("/events/{id}/co-organizers", 1L)
                .then()
                .statusCode(200)
                .body("$", hasSize(2));
    }

    @Test
    void list_unauthenticated_returns401() {
        given()
                .when().get("/events/{id}/co-organizers", 1L)
                .then()
                .statusCode(401);
    }

    @Test
    @TestSecurity(user = "auth0|alice")
    void list_eventNotFound_returns404() {
        EventCoOrganizerServiceMock.forceNotFoundOnList = true;
        given()
                .when().get("/events/{id}/co-organizers", 999L)
                .then()
                .statusCode(404);
    }
}
