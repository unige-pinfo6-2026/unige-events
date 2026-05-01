package ch.unige.events.resource;

import ch.unige.events.dto.coorganizer.CoOrganizerInvitationDTO;
import ch.unige.events.dto.event.EventDTO;
import ch.unige.events.entity.CoOrganizerStatus;
import ch.unige.events.entity.EventCategory;
import ch.unige.events.entity.EventStatus;
import ch.unige.events.service.AttendanceServiceMock;
import ch.unige.events.service.CalendarServiceMock;
import ch.unige.events.service.EventCoOrganizerServiceMock;
import ch.unige.events.service.EventServiceMock;
import ch.unige.events.service.FavoriteServiceMock;
import ch.unige.events.service.UserServiceMock;

import java.time.LocalDateTime;
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
import static org.hamcrest.Matchers.nullValue;

@QuarkusTest
class UserResourceTest {

    @Inject UserServiceMock userServiceMock;
    @Inject FavoriteServiceMock favoriteServiceMock;
    @Inject CalendarServiceMock calendarServiceMock;
    @Inject AttendanceServiceMock attendanceServiceMock;
    @Inject EventServiceMock eventServiceMock;
    @Inject EventCoOrganizerServiceMock coOrganizerServiceMock;

    @BeforeEach
    void setUp() {
        userServiceMock.reset();
        favoriteServiceMock.reset();
        attendanceServiceMock.reset();
        eventServiceMock.reset();
        coOrganizerServiceMock.reset();
    }

    // --- GET /users/{id} — ISSUE-93 (pentest 4.1 + 4.1b) ---

    @Test
    void getProfile_publicProfile_anon_returns200_strippedPayload() {
        var user = userServiceMock.seedUser("auth0|public-profile", "public@example.com");
        user.profilePublic = true;
        user.displayName = "Alice";
        user.faculty = "Sciences";
        user.studyLevel = "Master";
        user.bio = "Étudiant";
        user.interests = java.util.List.of("tech");
        user.avatarUrl = "https://cdn/avatar.png";
        user.bannerUrl = "https://cdn/banner.png";

        given()
            .when().get("/users/" + user.id)
            .then()
            .statusCode(200)
            .contentType(ContentType.JSON)
            .body("id", equalTo(user.id.toString()))
            .body("displayName", equalTo("Alice"))
            .body("avatarUrl", equalTo("https://cdn/avatar.png"))
            // Sensitive fields stripped for anonymous callers (hotfix pentest 4.1b).
            .body("faculty", nullValue())
            .body("studyLevel", nullValue())
            .body("bio", nullValue())
            .body("interests", nullValue())
            .body("bannerUrl", nullValue());
    }

    @Test
    void getProfile_privateProfile_anon_returns404_sameEnvelopeAsUnknown() {
        // Anti-oracle: the 404 response for a hidden private profile must be strictly
        // identical to the 404 response for an unknown UUID. Extract the unknown-id
        // response at runtime and compare the FULL raw body, not just `error`/`message` —
        // if ApiErrorResponse ever gains a field (path, timestamp, requestId...), a
        // field-wise assertion would let the two responses diverge silently while still
        // passing. Full-body equality closes that latent coupling.
        //
        // This also avoids hard-coding the JAX-RS default message (implementation detail
        // that could shift with a RESTEasy / Quarkus upgrade — see ISSUE-92 commit 5273d13).
        var user = userServiceMock.seedUser("auth0|private-profile", "private@example.com");
        user.profilePublic = false;

        String unknownBody = given()
            .when().get("/users/" + UUID.randomUUID())
            .then()
            .statusCode(404)
            .extract().body().asString();

        given()
            .when().get("/users/" + user.id)
            .then()
            .statusCode(404)
            .body(equalTo(unknownBody));
    }

    @Test
    void getProfile_unknownUuid_anon_returns404() {
        given()
            .when().get("/users/" + UUID.randomUUID())
            .then()
            .statusCode(404)
            .body("error", equalTo("not_found"));
    }

    @Test
    @TestSecurity(user = "auth0|bob", attributes = {
        @SecurityAttribute(key = "email", value = "bob@example.com")
    })
    void getProfile_privateProfile_otherUser_returns404() {
        var user = userServiceMock.seedUser("auth0|alice-private", "alice@example.com");
        user.profilePublic = false;

        given()
            .when().get("/users/" + user.id)
            .then()
            .statusCode(404)
            .body("error", equalTo("not_found"));
    }

    @Test
    @TestSecurity(user = "auth0|alice-self", attributes = {
        @SecurityAttribute(key = "email", value = "alice-self@example.com")
    })
    void getProfile_privateProfile_owner_returns200_fullPayload() {
        var user = userServiceMock.seedUser("auth0|alice-self", "alice-self@example.com");
        user.profilePublic = false;
        user.faculty = "Sciences";
        user.bio = "Secret bio";

        given()
            .when().get("/users/" + user.id)
            .then()
            .statusCode(200)
            .body("id", equalTo(user.id.toString()))
            .body("faculty", equalTo("Sciences"))
            .body("bio", equalTo("Secret bio"));
    }

    @Test
    @TestSecurity(user = "auth0|bob", attributes = {
        @SecurityAttribute(key = "email", value = "bob@example.com")
    })
    void getProfile_publicProfile_authenticated_returns200_fullPayload() {
        // Non-regression: any authenticated caller receives the FULL payload on a
        // public profile — stripping applies only to anonymous callers.
        var user = userServiceMock.seedUser("auth0|alice-pub", "alice-pub@example.com");
        user.profilePublic = true;
        user.faculty = "Sciences";
        user.bio = "Public bio";

        given()
            .when().get("/users/" + user.id)
            .then()
            .statusCode(200)
            .body("id", equalTo(user.id.toString()))
            .body("faculty", equalTo("Sciences"))
            .body("bio", equalTo("Public bio"));
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

    // --- GET /users/me/events ---

    @Test
    @TestSecurity(user = "auth0|alice")
    void getMyEvents_empty_returnsEmptyArray() {
        given()
            .when().get("/users/me/events")
            .then()
            .statusCode(200)
            .contentType(ContentType.JSON)
            .body("$", hasSize(0));
    }

    @Test
    @TestSecurity(user = "auth0|alice")
    void getMyEvents_threeStatuses_returnsAllThreeWithoutFilter() {
        LocalDateTime base = LocalDateTime.now().minusHours(3);
        eventServiceMock.seedEventWithStatus("auth0|alice", "Draft", EventStatus.DRAFT, base);
        eventServiceMock.seedEventWithStatus("auth0|alice", "Published", EventStatus.PUBLISHED, base.plusMinutes(1));
        eventServiceMock.seedEventWithStatus("auth0|alice", "Cancelled", EventStatus.CANCELLED, base.plusMinutes(2));

        given()
            .when().get("/users/me/events")
            .then()
            .statusCode(200)
            .body("$", hasSize(3));
    }

    @Test
    @TestSecurity(user = "auth0|alice")
    void getMyEvents_filterDraft_returnsOnlyDraft() {
        LocalDateTime base = LocalDateTime.now().minusHours(1);
        eventServiceMock.seedEventWithStatus("auth0|alice", "Draft", EventStatus.DRAFT, base);
        eventServiceMock.seedEventWithStatus("auth0|alice", "Published", EventStatus.PUBLISHED, base.plusMinutes(1));
        eventServiceMock.seedEventWithStatus("auth0|alice", "Cancelled", EventStatus.CANCELLED, base.plusMinutes(2));

        given()
            .queryParam("status", "DRAFT")
            .when().get("/users/me/events")
            .then()
            .statusCode(200)
            .body("$", hasSize(1))
            .body("[0].status", equalTo("DRAFT"))
            .body("[0].title", equalTo("Draft"));
    }

    @Test
    @TestSecurity(user = "auth0|alice")
    void getMyEvents_ordersByCreatedAtDesc() {
        LocalDateTime base = LocalDateTime.now().minusDays(1);
        eventServiceMock.seedEventWithStatus("auth0|alice", "Oldest", EventStatus.DRAFT, base);
        eventServiceMock.seedEventWithStatus("auth0|alice", "Middle", EventStatus.DRAFT, base.plusMinutes(5));
        eventServiceMock.seedEventWithStatus("auth0|alice", "Newest", EventStatus.DRAFT, base.plusMinutes(10));

        given()
            .when().get("/users/me/events")
            .then()
            .statusCode(200)
            .body("$", hasSize(3))
            .body("[0].title", equalTo("Newest"))
            .body("[1].title", equalTo("Middle"))
            .body("[2].title", equalTo("Oldest"));
    }

    /**
     * Security-critical test: a different authenticated user MUST NOT see user Alice's events,
     * even DRAFT events. This is the whole point of SCRUM-133: deriving identity from the JWT
     * removes the ability to enumerate someone else's drafts via a query parameter.
     */
    @Test
    @TestSecurity(user = "auth0|bob")
    void getMyEvents_userBcannotSeeUserAEvents() {
        LocalDateTime base = LocalDateTime.now().minusHours(2);
        eventServiceMock.seedEventWithStatus("auth0|alice", "Alice draft", EventStatus.DRAFT, base);
        eventServiceMock.seedEventWithStatus("auth0|alice", "Alice cancelled", EventStatus.CANCELLED, base);

        given()
            .queryParam("status", "DRAFT")
            .when().get("/users/me/events")
            .then()
            .statusCode(200)
            .body("$", hasSize(0));
    }

    @Test
    void getMyEvents_unauthenticated_returns401() {
        given()
            .when().get("/users/me/events")
            .then()
            .statusCode(401);
    }

    @Test
    @TestSecurity(user = "auth0|alice")
    void getMyEvents_userNotFound_returns404() {
        EventServiceMock.forceMyEventsNotFound = true;
        try {
            given()
                .when().get("/users/me/events")
                .then()
                .statusCode(404);
        } finally {
            EventServiceMock.forceMyEventsNotFound = false;
        }
    }

    @Test
    @TestSecurity(user = "auth0|alice")
    void getMyEvents_sizeTooLarge_returns400() {
        given()
            .queryParam("size", 101)
            .when().get("/users/me/events")
            .then()
            .statusCode(400);
    }

    @Test
    @TestSecurity(user = "auth0|alice")
    void getMyEvents_sizeZero_returns400() {
        given()
            .queryParam("size", 0)
            .when().get("/users/me/events")
            .then()
            .statusCode(400);
    }

    @Test
    @TestSecurity(user = "auth0|alice")
    void getMyEvents_pageNegative_returns400() {
        given()
            .queryParam("page", -1)
            .when().get("/users/me/events")
            .then()
            .statusCode(400);
    }

    @Test
    @TestSecurity(user = "auth0|alice")
    void getMyEvents_invalidStatus_returnsError() {
        // Quarkus rejects unparseable query enum with 404 (framework behavior, not a 400).
        // We only assert that the request does not succeed (not 2xx).
        given()
            .queryParam("status", "NOT_A_STATUS")
            .when().get("/users/me/events")
            .then()
            .statusCode(org.hamcrest.Matchers.not(org.hamcrest.Matchers.equalTo(200)));
    }

    // =========================================================
    // SCRUM-136 — GET /users/me/co-organizer-invitations
    // =========================================================

    @Test
    @TestSecurity(user = "auth0|alice")
    void getMyCoOrganizerInvitations_default_returnsPending() {
        coOrganizerServiceMock.myInvitationsFixture.add(new CoOrganizerInvitationDTO(
                1L, sampleEventDTO("Event 1"), CoOrganizerStatus.PENDING, java.time.LocalDateTime.now()));

        given()
            .when().get("/users/me/co-organizer-invitations")
            .then()
            .statusCode(200)
            .body("$", hasSize(1))
            .body("[0].status", equalTo("PENDING"));
    }

    @Test
    @TestSecurity(user = "auth0|alice")
    void getMyCoOrganizerInvitations_filterAccepted_returnsAccepted() {
        coOrganizerServiceMock.myInvitationsFixture.add(new CoOrganizerInvitationDTO(
                1L, sampleEventDTO("Accepted event"), CoOrganizerStatus.ACCEPTED, java.time.LocalDateTime.now()));

        given()
            .queryParam("status", "ACCEPTED")
            .when().get("/users/me/co-organizer-invitations")
            .then()
            .statusCode(200)
            .body("$", hasSize(1))
            .body("[0].status", equalTo("ACCEPTED"));
    }

    @Test
    @TestSecurity(user = "auth0|alice")
    void getMyCoOrganizerInvitations_pagination() {
        for (int i = 0; i < 3; i++) {
            coOrganizerServiceMock.myInvitationsFixture.add(new CoOrganizerInvitationDTO(
                    (long) i, sampleEventDTO("Event " + i), CoOrganizerStatus.PENDING,
                    java.time.LocalDateTime.now()));
        }
        given()
            .queryParam("size", "2")
            .when().get("/users/me/co-organizer-invitations")
            .then()
            .statusCode(200);
        // Le mock renvoie tout le fixture (pagination déléguée à la couche DB) ;
        // on valide ici que la query passe les contraintes @Min/@Max sans 400.
    }

    @Test
    void getMyCoOrganizerInvitations_unauthenticated_returns401() {
        given()
            .when().get("/users/me/co-organizer-invitations")
            .then()
            .statusCode(401);
    }

    @Test
    @TestSecurity(user = "auth0|alice")
    void getMyCoOrganizerInvitations_userNotProvisioned_returns404() {
        EventCoOrganizerServiceMock.forceNotFoundOnGetMyInvitations = true;
        given()
            .when().get("/users/me/co-organizer-invitations")
            .then()
            .statusCode(404);
    }

    private EventDTO sampleEventDTO(String title) {
        return new EventDTO(
                42L, title, null, "Uni Mail",
                java.time.LocalDateTime.now(), java.time.LocalDateTime.now().plusHours(2),
                EventCategory.ACADEMIC, null, null,
                UUID.randomUUID(), EventStatus.PUBLISHED, null, false,
                false, 0L, null, 0L,
                null, null, null, java.util.List.of(),
                java.time.LocalDateTime.now(), java.time.LocalDateTime.now()
        );
    }
}
