package ch.unige.events.resource;

import ch.unige.events.dto.event.CreateEventRequest;
import ch.unige.events.dto.event.UpdateEventRequest;
import ch.unige.events.entity.EventCategory;
import ch.unige.events.entity.EventStatus;
import ch.unige.events.entity.Faculty;
import ch.unige.events.service.EventServiceMock;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.SecurityAttribute;
import io.quarkus.test.security.TestSecurity;
import io.restassured.http.ContentType;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

@QuarkusTest
class EventResourceTest {

    @Inject
    EventServiceMock eventServiceMock;

    @BeforeEach
    void setUp() {
        eventServiceMock.reset();
    }

    // --- GET /events ---

    @Test
    void getAll_returnsOk() {
        given()
                .when().get("/events")
                .then()
                .statusCode(200)
                .body("", instanceOf(java.util.List.class));
    }

    @Test
    void getAll_withStatusFilter_returnsFiltered() {
        eventServiceMock.seedEvent("auth0|alice", "Event DRAFT");
        var published = eventServiceMock.seedEvent("auth0|alice", "Event PUBLISHED");
        published.status = EventStatus.PUBLISHED;

        given()
                .queryParam("status", "PUBLISHED")
                .when().get("/events")
                .then()
                .statusCode(200)
                .body("", hasSize(1))
                .body("[0].title", is("Event PUBLISHED"));
    }

    // --- GET /events?organizerId=... — hardening (SCRUM-133) ---

    @Test
    void getAll_organizerIdWithoutStatus_implicitlyFiltersPublished() {
        eventServiceMock.seedEventWithStatus("auth0|alice", "Draft", EventStatus.DRAFT, LocalDateTime.now());
        var published = eventServiceMock.seedEventWithStatus("auth0|alice", "Published", EventStatus.PUBLISHED, LocalDateTime.now());
        eventServiceMock.seedEventWithStatus("auth0|alice", "Cancelled", EventStatus.CANCELLED, LocalDateTime.now());
        var organizerId = published.creator.id;

        given()
                .queryParam("organizerId", organizerId.toString())
                .when().get("/events")
                .then()
                .statusCode(200)
                .body("", hasSize(1))
                .body("[0].title", is("Published"));
    }

    @Test
    void getAll_organizerIdWithStatusPublished_allowed() {
        var published = eventServiceMock.seedEventWithStatus("auth0|alice", "Published", EventStatus.PUBLISHED, LocalDateTime.now());
        var organizerId = published.creator.id;

        given()
                .queryParam("organizerId", organizerId.toString())
                .queryParam("status", "PUBLISHED")
                .when().get("/events")
                .then()
                .statusCode(200)
                .body("", hasSize(1))
                .body("[0].title", is("Published"));
    }

    @Test
    void getAll_organizerIdWithStatusDraft_returns400() {
        var draft = eventServiceMock.seedEventWithStatus("auth0|alice", "Draft", EventStatus.DRAFT, LocalDateTime.now());
        var organizerId = draft.creator.id;

        given()
                .queryParam("organizerId", organizerId.toString())
                .queryParam("status", "DRAFT")
                .when().get("/events")
                .then()
                .statusCode(400)
                .body("error", is("organizer_filter_requires_published"));
    }

    @Test
    void getAll_organizerIdWithStatusCancelled_returns400() {
        var cancelled = eventServiceMock.seedEventWithStatus("auth0|alice", "Cancelled", EventStatus.CANCELLED, LocalDateTime.now());
        var organizerId = cancelled.creator.id;

        given()
                .queryParam("organizerId", organizerId.toString())
                .queryParam("status", "CANCELLED")
                .when().get("/events")
                .then()
                .statusCode(400)
                .body("error", is("organizer_filter_requires_published"));
    }

    @Test
    void getAll_withoutOrganizerId_statusDraftStillAllowed() {
        eventServiceMock.seedEventWithStatus("auth0|alice", "Draft", EventStatus.DRAFT, LocalDateTime.now());

        given()
                .queryParam("status", "DRAFT")
                .when().get("/events")
                .then()
                .statusCode(200)
                .body("", hasSize(1))
                .body("[0].title", is("Draft"));
    }

    // --- POST /events ---

    @Test
    @TestSecurity(user = "auth0|alice", attributes = {
            @SecurityAttribute(key = "email", value = "alice@example.com")
    })
    void create_withValidRequest_returns201() {
        CreateEventRequest req = new CreateEventRequest();
        req.title = "Conférence Test";
        req.location = "Uni Mail";
        req.startDate = LocalDateTime.now().plusDays(1);
        req.endDate = LocalDateTime.now().plusDays(2);
        req.category = EventCategory.CONFERENCE;

        given()
                .contentType(ContentType.JSON)
                .body(req)
                .when().post("/events")
                .then()
                .statusCode(201)
                .body("title", is("Conférence Test"))
                .body("creatorId", notNullValue());
    }

    @Test
    void create_unauthenticated_returns401() {
        CreateEventRequest req = new CreateEventRequest();
        req.title = "Test";
        req.location = "Uni Mail";
        req.startDate = LocalDateTime.now().plusDays(1);
        req.endDate = LocalDateTime.now().plusDays(2);
        req.category = EventCategory.CONFERENCE;

        given()
                .contentType(ContentType.JSON)
                .body(req)
                .when().post("/events")
                .then()
                .statusCode(401);
    }

    @Test
    @TestSecurity(user = "auth0|alice")
    void create_withBlankTitle_returns400() {
        CreateEventRequest req = new CreateEventRequest();
        req.title = "";
        req.location = "Uni Mail";
        req.startDate = LocalDateTime.now().plusDays(1);
        req.endDate = LocalDateTime.now().plusDays(2);
        req.category = EventCategory.CONFERENCE;

        given()
                .contentType(ContentType.JSON)
                .body(req)
                .when().post("/events")
                .then()
                .statusCode(400);
    }

    @Test
    @TestSecurity(user = "auth0|alice")
    void create_withNullCategory_returns400() {
        CreateEventRequest req = new CreateEventRequest();
        req.title = "Test";
        req.location = "Uni Mail";
        req.startDate = LocalDateTime.now().plusDays(1);
        req.endDate = LocalDateTime.now().plusDays(2);
        req.category = null;

        given()
                .contentType(ContentType.JSON)
                .body(req)
                .when().post("/events")
                .then()
                .statusCode(400);
    }

    // --- GET /events/{id} ---

    @Test
    void getById_existingEvent_returns200() {
        var event = eventServiceMock.seedEvent("auth0|alice", "Mon Événement");

        given()
                .when().get("/events/" + event.id)
                .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .body("id", equalTo(event.id.intValue()))
                .body("title", is("Mon Événement"));
    }

    @Test
    void getById_unknownEvent_returns404() {
        given()
                .when().get("/events/9999")
                .then()
                .statusCode(404)
                .body("error", equalTo("not_found"));
    }

    // --- PUT /events/{id} ---

    @Test
    @TestSecurity(user = "auth0|alice")
    void update_asCreator_returns200() {
        var event = eventServiceMock.seedEvent("auth0|alice", "Ancien Titre");

        UpdateEventRequest req = new UpdateEventRequest();
        req.title = "Nouveau Titre";
        req.location = "Uni Bastions";
        req.startDate = LocalDateTime.now().plusDays(3);
        req.endDate = LocalDateTime.now().plusDays(4);
        req.category = EventCategory.CULTURAL;

        given()
                .contentType(ContentType.JSON)
                .body(req)
                .when().put("/events/" + event.id)
                .then()
                .statusCode(200)
                .body("title", is("Nouveau Titre"))
                .body("location", is("Uni Bastions"))
                .body("category", is("CULTURAL"));
    }

    @Test
    void update_unauthenticated_returns401() {
        var event = eventServiceMock.seedEvent("auth0|alice", "Test");

        UpdateEventRequest req = new UpdateEventRequest();
        req.title = "Update";
        req.location = "Uni Mail";
        req.startDate = LocalDateTime.now().plusDays(1);
        req.endDate = LocalDateTime.now().plusDays(2);
        req.category = EventCategory.ACADEMIC;

        given()
                .contentType(ContentType.JSON)
                .body(req)
                .when().put("/events/" + event.id)
                .then()
                .statusCode(401);
    }

    @Test
    @TestSecurity(user = "auth0|alice")
    void update_notCreator_returns403() {
        EventServiceMock.forceForbiddenOnUpdate = true;
        var event = eventServiceMock.seedEvent("auth0|bob", "Événement de Bob");

        UpdateEventRequest req = new UpdateEventRequest();
        req.title = "Tentative";
        req.location = "Uni Mail";
        req.startDate = LocalDateTime.now().plusDays(1);
        req.endDate = LocalDateTime.now().plusDays(2);
        req.category = EventCategory.ACADEMIC;

        given()
                .contentType(ContentType.JSON)
                .body(req)
                .when().put("/events/" + event.id)
                .then()
                .statusCode(403)
                .body("error", equalTo("forbidden"));
    }

    @Test
    @TestSecurity(user = "auth0|alice")
    void update_unknownEvent_returns404() {
        UpdateEventRequest req = new UpdateEventRequest();
        req.title = "Test";
        req.location = "Uni Mail";
        req.startDate = LocalDateTime.now().plusDays(1);
        req.endDate = LocalDateTime.now().plusDays(2);
        req.category = EventCategory.ACADEMIC;

        given()
                .contentType(ContentType.JSON)
                .body(req)
                .when().put("/events/9999")
                .then()
                .statusCode(404)
                .body("error", equalTo("not_found"));
    }

    @Test
    @TestSecurity(user = "auth0|alice")
    void update_invalidBody_returns400() {
        var event = eventServiceMock.seedEvent("auth0|alice", "Test");

        UpdateEventRequest req = new UpdateEventRequest();
        req.title = "";   // @NotBlank — doit échouer
        req.location = "Uni Mail";
        req.startDate = LocalDateTime.now().plusDays(1);
        req.endDate = LocalDateTime.now().plusDays(2);
        req.category = EventCategory.ACADEMIC;

        given()
                .contentType(ContentType.JSON)
                .body(req)
                .when().put("/events/" + event.id)
                .then()
                .statusCode(400)
                .body("error", equalTo("validation_error"));
    }

    // --- DELETE /events/{id} ---

    @Test
    @TestSecurity(user = "auth0|alice")
    void delete_cancelledEvent_asCreator_returns204() {
        var event = eventServiceMock.seedEvent("auth0|alice", "À supprimer");
        event.status = EventStatus.CANCELLED;

        given()
                .when().delete("/events/" + event.id)
                .then()
                .statusCode(204);
    }

    @Test
    @TestSecurity(user = "auth0|alice")
    void delete_nonCancelledEvent_returns409() {
        var event = eventServiceMock.seedEvent("auth0|alice", "Draft");

        given()
                .when().delete("/events/" + event.id)
                .then()
                .statusCode(409)
                .body("error", equalTo("conflict"));
    }

    // --- PATCH /events/{id}/cancel ---

    @Test
    @TestSecurity(user = "auth0|alice")
    void cancel_draftEvent_returns200() {
        var event = eventServiceMock.seedEvent("auth0|alice", "Draft");

        given()
                .when().patch("/events/" + event.id + "/cancel")
                .then()
                .statusCode(200)
                .body("status", equalTo("CANCELLED"));
    }

    @Test
    @TestSecurity(user = "auth0|alice")
    void cancel_publishedEvent_returns200() {
        var event = eventServiceMock.seedEvent("auth0|alice", "Published");
        event.status = EventStatus.PUBLISHED;

        given()
                .when().patch("/events/" + event.id + "/cancel")
                .then()
                .statusCode(200)
                .body("status", equalTo("CANCELLED"));
    }

    @Test
    @TestSecurity(user = "auth0|alice")
    void cancel_alreadyCancelled_returns409() {
        var event = eventServiceMock.seedEvent("auth0|alice", "Already cancelled");
        event.status = EventStatus.CANCELLED;

        given()
                .when().patch("/events/" + event.id + "/cancel")
                .then()
                .statusCode(409)
                .body("error", equalTo("conflict"));
    }

    @Test
    void cancel_unauthenticated_returns401() {
        var event = eventServiceMock.seedEvent("auth0|alice", "Draft");

        given()
                .when().patch("/events/" + event.id + "/cancel")
                .then()
                .statusCode(401);
    }

    @Test
    @TestSecurity(user = "auth0|bob")
    void cancel_notCreator_returns403() {
        var event = eventServiceMock.seedEvent("auth0|alice", "Alice's");

        given()
                .when().patch("/events/" + event.id + "/cancel")
                .then()
                .statusCode(403);
    }

    @Test
    @TestSecurity(user = "auth0|alice")
    void cancel_unknownEvent_returns404() {
        given()
                .when().patch("/events/9999/cancel")
                .then()
                .statusCode(404);
    }

    // --- PATCH /events/{id}/restore ---

    @Test
    @TestSecurity(user = "auth0|alice")
    void restore_cancelledEvent_returns200() {
        var event = eventServiceMock.seedEvent("auth0|alice", "Cancelled");
        event.status = EventStatus.CANCELLED;

        given()
                .when().patch("/events/" + event.id + "/restore")
                .then()
                .statusCode(200)
                .body("status", equalTo("DRAFT"));
    }

    @Test
    @TestSecurity(user = "auth0|alice")
    void restore_nonCancelled_returns409() {
        var event = eventServiceMock.seedEvent("auth0|alice", "Draft");

        given()
                .when().patch("/events/" + event.id + "/restore")
                .then()
                .statusCode(409)
                .body("error", equalTo("conflict"));
    }

    @Test
    void restore_unauthenticated_returns401() {
        var event = eventServiceMock.seedEvent("auth0|alice", "Draft");

        given()
                .when().patch("/events/" + event.id + "/restore")
                .then()
                .statusCode(401);
    }

    @Test
    @TestSecurity(user = "auth0|bob")
    void restore_notCreator_returns403() {
        var event = eventServiceMock.seedEvent("auth0|alice", "Alice's");
        event.status = EventStatus.CANCELLED;

        given()
                .when().patch("/events/" + event.id + "/restore")
                .then()
                .statusCode(403);
    }

    @Test
    @TestSecurity(user = "auth0|alice")
    void restore_unknownEvent_returns404() {
        given()
                .when().patch("/events/9999/restore")
                .then()
                .statusCode(404);
    }

    // --- PUT /events/{id} on a CANCELLED event ---

    @Test
    @TestSecurity(user = "auth0|alice")
    void update_cancelledEvent_returns409() {
        var event = eventServiceMock.seedEvent("auth0|alice", "Cancelled");
        event.status = EventStatus.CANCELLED;

        UpdateEventRequest req = new UpdateEventRequest();
        req.title = "New";
        req.location = "Uni Mail";
        req.startDate = LocalDateTime.now().plusDays(1);
        req.endDate = LocalDateTime.now().plusDays(2);
        req.category = EventCategory.ACADEMIC;

        given()
                .contentType(ContentType.JSON)
                .body(req)
                .when().put("/events/" + event.id)
                .then()
                .statusCode(409)
                .body("error", equalTo("conflict"));
    }

    @Test
    void delete_unauthenticated_returns401() {
        var event = eventServiceMock.seedEvent("auth0|alice", "Test");

        given()
                .when().delete("/events/" + event.id)
                .then()
                .statusCode(401);
    }

    @Test
    @TestSecurity(user = "auth0|alice")
    void delete_notCreator_returns403() {
        EventServiceMock.forceForbiddenOnDelete = true;
        var event = eventServiceMock.seedEvent("auth0|bob", "Événement de Bob");

        given()
                .when().delete("/events/" + event.id)
                .then()
                .statusCode(403)
                .body("error", equalTo("forbidden"));
    }

    @Test
    @TestSecurity(user = "auth0|alice")
    void delete_unknownEvent_returns404() {
        given()
                .when().delete("/events/9999")
                .then()
                .statusCode(404)
                .body("error", equalTo("not_found"));
    }

    // --- PATCH /events/{id}/publish ---

    @Test
    @TestSecurity(user = "auth0|admin", roles = {"ADMIN"})
    void publishAsAdminOnAnyEventReturns200() {
        var event = eventServiceMock.seedEvent("auth0|bob", "Bob's Draft Event");

        given()
                .when().patch("/events/" + event.id + "/publish")
                .then()
                .statusCode(200)
                .body("status", equalTo("PUBLISHED"));
    }

    @Test
    void publishUnauthenticatedReturns401() {
        var event = eventServiceMock.seedEvent("auth0|alice", "Draft Event");

        given()
                .when().patch("/events/" + event.id + "/publish")
                .then()
                .statusCode(401);
    }

    @Test
    @TestSecurity(user = "auth0|alice")
    void publishAsAuthenticatedOwnerWithoutRolesReturns200() {
        // Regression: the publish endpoint was previously locked behind
        // @RolesAllowed({"ORGANIZER","ADMIN"}), blocking regular users
        // from publishing their own drafts. It is now @Authenticated — the
        // owner-or-admin check lives in the service layer.
        var event = eventServiceMock.seedEvent("auth0|alice", "Draft Event");

        given()
                .when().patch("/events/" + event.id + "/publish")
                .then()
                .statusCode(200)
                .body("status", equalTo("PUBLISHED"));
    }

    @Test
    @TestSecurity(user = "auth0|alice", roles = {"ORGANIZER"})
    void publishAsOrganiserNotOwnerReturns403() {
        EventServiceMock.forceForbiddenOnUpdate = true;
        var event = eventServiceMock.seedEvent("auth0|bob", "Bob's Event");

        given()
                .when().patch("/events/" + event.id + "/publish")
                .then()
                .statusCode(403)
                .body("error", equalTo("forbidden"));
    }

    @Test
    @TestSecurity(user = "auth0|alice", roles = {"ORGANIZER"})
    void publishAlreadyPublishedReturns409() {
        var event = eventServiceMock.seedEvent("auth0|alice", "Published Event");
        EventServiceMock.forceConflictOnPublish = true;

        given()
                .when().patch("/events/" + event.id + "/publish")
                .then()
                .statusCode(409)
                .body("error", equalTo("conflict"));
    }

    // --- POST /events/{id}/image ---

    @Test
    @TestSecurity(user = "auth0|alice", roles = {"ORGANIZER"})
    void uploadImageWithValidJpegReturns200() {
        var event = eventServiceMock.seedEvent("auth0|alice", "Event avec bannière");

        given()
                .contentType("multipart/form-data")
                .multiPart("file", "banner.jpg", "fake-jpeg-bytes".getBytes(), "image/jpeg")
                .when().post("/events/" + event.id + "/image")
                .then()
                .statusCode(200)
                .body("bannerUrl", notNullValue());
    }

    @Test
    @TestSecurity(user = "auth0|alice", roles = {"ORGANIZER"})
    void uploadImageWithInvalidMimeReturns400() {
        var event = eventServiceMock.seedEvent("auth0|alice", "Event");
        EventServiceMock.forceBadMimeOnUpload = true;

        given()
                .contentType("multipart/form-data")
                .multiPart("file", "script.sh", "#!/bin/bash".getBytes(), "text/plain")
                .when().post("/events/" + event.id + "/image")
                .then()
                .statusCode(400);
    }

    @Test
    @TestSecurity(user = "auth0|alice")
    void uploadImage_asAuthenticatedCreator_returns200() {
        var event = eventServiceMock.seedEvent("auth0|alice", "Event avec bannière");

        given()
                .contentType("multipart/form-data")
                .multiPart("file", "banner-no-role.png", "fake-png-bytes".getBytes(), "image/png")
                .when().post("/events/" + event.id + "/image")
                .then()
                .statusCode(200)
                .body("bannerUrl", notNullValue());
    }

    @Test
    void uploadImage_unauthenticated_returns401() {
        var event = eventServiceMock.seedEvent("auth0|alice", "Event");

        given()
                .contentType("multipart/form-data")
                .multiPart("file", "banner.jpg", "fake-jpeg-bytes".getBytes(), "image/jpeg")
                .when().post("/events/" + event.id + "/image")
                .then()
                .statusCode(401);
    }

    @Test
    @TestSecurity(user = "auth0|alice")
    void uploadImage_asAuthenticatedNonCreator_returns403() {
        EventServiceMock.forceForbiddenOnUpdate = true;
        var event = eventServiceMock.seedEvent("auth0|bob", "Event de Bob");

        given()
                .contentType("multipart/form-data")
                .multiPart("file", "banner.jpg", "fake-jpeg-bytes".getBytes(), "image/jpeg")
                .when().post("/events/" + event.id + "/image")
                .then()
                .statusCode(403)
                .body("error", equalTo("forbidden"));
    }

    // --- GET /events?faculty= ---

    @Test
    void getAll_withFacultyFilter_returnsFiltered() {
        var e1 = eventServiceMock.seedEvent("auth0|alice", "Event SCIENCES");
        e1.faculty = Faculty.SCIENCES;
        var e2 = eventServiceMock.seedEvent("auth0|alice", "Event LETTRES");
        e2.faculty = Faculty.LETTERS;

        given()
                .queryParam("faculty", "SCIENCES")
                .when().get("/events")
                .then()
                .statusCode(200)
                .body("", hasSize(1))
                .body("[0].title", is("Event SCIENCES"))
                .body("[0].faculty", is("SCIENCES"));
    }

    @Test
    void getAll_withFacultyFilter_noMatch_returnsEmpty() {
        var e = eventServiceMock.seedEvent("auth0|alice", "Event SCIENCES");
        e.faculty = Faculty.SCIENCES;

        given()
                .queryParam("faculty", "LAW")
                .when().get("/events")
                .then()
                .statusCode(200)
                .body("", hasSize(0));
    }

    // --- POST /events avec faculty ---

    @Test
    @TestSecurity(user = "auth0|alice", attributes = {
            @SecurityAttribute(key = "email", value = "alice@example.com")
    })
    void create_withFaculty_returnsFacultyInResponse() {
        CreateEventRequest req = new CreateEventRequest();
        req.title = "Conférence Médecine";
        req.location = "CMU";
        req.startDate = LocalDateTime.now().plusDays(1);
        req.endDate = LocalDateTime.now().plusDays(2);
        req.category = EventCategory.CONFERENCE;
        req.faculty = Faculty.MEDICINE;

        given()
                .contentType(ContentType.JSON)
                .body(req)
                .when().post("/events")
                .then()
                .statusCode(201)
                .body("faculty", is("MEDICINE"));
    }

    @Test
    @TestSecurity(user = "auth0|alice", attributes = {
            @SecurityAttribute(key = "email", value = "alice@example.com")
    })
    void create_withoutFaculty_returnsNullFaculty() {
        CreateEventRequest req = new CreateEventRequest();
        req.title = "Event sans faculté";
        req.location = "Uni Mail";
        req.startDate = LocalDateTime.now().plusDays(1);
        req.endDate = LocalDateTime.now().plusDays(2);
        req.category = EventCategory.OTHER;

        given()
                .contentType(ContentType.JSON)
                .body(req)
                .when().post("/events")
                .then()
                .statusCode(201)
                .body("faculty", nullValue());
    }

    @Test
    void getAll_withNullFaculty_returnsAll() {
        var e1 = eventServiceMock.seedEvent("auth0|alice", "Event A");
        e1.faculty = Faculty.SCIENCES;
        eventServiceMock.seedEvent("auth0|alice", "Event B");

        given()
                .when().get("/events")
                .then()
                .statusCode(200)
                .body("", hasSize(2));
    }

    // --- GET /events?facultyNone= ---

    @Test
    void getAll_withFacultyNoneFilter_returnsOnlyUnaffiliated() {
        var e1 = eventServiceMock.seedEvent("auth0|alice", "Event SCIENCES");
        e1.faculty = Faculty.SCIENCES;
        eventServiceMock.seedEvent("auth0|alice", "Event sans faculté");

        given()
                .queryParam("facultyNone", "true")
                .when().get("/events")
                .then()
                .statusCode(200)
                .body("", hasSize(1))
                .body("[0].title", is("Event sans faculté"))
                .body("[0].faculty", nullValue());
    }

    @Test
    void getAll_withFacultyNoneAndFaculty_facultyNoneWins() {
        var e1 = eventServiceMock.seedEvent("auth0|alice", "Event SCIENCES");
        e1.faculty = Faculty.SCIENCES;
        eventServiceMock.seedEvent("auth0|alice", "Event sans faculté");

        given()
                .queryParam("faculty", "SCIENCES")
                .queryParam("facultyNone", "true")
                .when().get("/events")
                .then()
                .statusCode(200)
                .body("", hasSize(1))
                .body("[0].faculty", nullValue());
    }

    // =========================================================
    // SCRUM-126 — websiteUrl, contactEmail, registrationDeadline, tags
    // =========================================================

    @Test
    @TestSecurity(user = "auth0|alice")
    void create_withAllNewFields_persistsAndReturns() {
        CreateEventRequest req = new CreateEventRequest();
        req.title = "Avec URL et email";
        req.location = "Uni Mail";
        req.startDate = LocalDateTime.now().plusDays(1);
        req.endDate = LocalDateTime.now().plusDays(2);
        req.category = EventCategory.CONFERENCE;
        req.websiteUrl = "https://unige.ch/events/42";
        req.contactEmail = "orga@unige.ch";
        req.registrationDeadline = LocalDateTime.now().plusHours(12);
        req.tags = new ArrayList<>(Arrays.asList("cinema", "plein-air"));

        given()
                .contentType(ContentType.JSON)
                .body(req)
                .when().post("/events")
                .then()
                .statusCode(201)
                .body("websiteUrl", is("https://unige.ch/events/42"))
                .body("contactEmail", is("orga@unige.ch"))
                .body("registrationDeadline", notNullValue())
                .body("tags", hasSize(2))
                .body("tags", hasItems("cinema", "plein-air"));
    }

    @Test
    @TestSecurity(user = "auth0|alice")
    void create_withInvalidWebsiteUrl_returns400() {
        CreateEventRequest req = new CreateEventRequest();
        req.title = "Bad URL";
        req.location = "Uni Mail";
        req.startDate = LocalDateTime.now().plusDays(1);
        req.endDate = LocalDateTime.now().plusDays(2);
        req.category = EventCategory.CONFERENCE;
        req.websiteUrl = "not-a-url";

        given()
                .contentType(ContentType.JSON)
                .body(req)
                .when().post("/events")
                .then()
                .statusCode(400);
    }

    @Test
    @TestSecurity(user = "auth0|alice")
    void create_withInvalidContactEmail_returns400() {
        CreateEventRequest req = new CreateEventRequest();
        req.title = "Bad Email";
        req.location = "Uni Mail";
        req.startDate = LocalDateTime.now().plusDays(1);
        req.endDate = LocalDateTime.now().plusDays(2);
        req.category = EventCategory.CONFERENCE;
        req.contactEmail = "foo@";

        given()
                .contentType(ContentType.JSON)
                .body(req)
                .when().post("/events")
                .then()
                .statusCode(400);
    }

    @Test
    @TestSecurity(user = "auth0|alice")
    void create_withDuplicateTags_returnsNormalized() {
        CreateEventRequest req = new CreateEventRequest();
        req.title = "Normalized tags";
        req.location = "Uni Mail";
        req.startDate = LocalDateTime.now().plusDays(1);
        req.endDate = LocalDateTime.now().plusDays(2);
        req.category = EventCategory.CULTURAL;
        req.tags = new ArrayList<>(Arrays.asList("Foo", "FOO", "bar", " foo "));

        given()
                .contentType(ContentType.JSON)
                .body(req)
                .when().post("/events")
                .then()
                .statusCode(201)
                .body("tags", hasSize(2))
                .body("tags[0]", is("foo"))
                .body("tags[1]", is("bar"));
    }

    @Test
    @TestSecurity(user = "auth0|alice")
    void create_registrationDeadlineInPast_accepted() {
        CreateEventRequest req = new CreateEventRequest();
        req.title = "Deadline past";
        req.location = "Uni Mail";
        req.startDate = LocalDateTime.now().plusDays(1);
        req.endDate = LocalDateTime.now().plusDays(2);
        req.category = EventCategory.CONFERENCE;
        req.registrationDeadline = LocalDateTime.now().minusDays(1);

        given()
                .contentType(ContentType.JSON)
                .body(req)
                .when().post("/events")
                .then()
                .statusCode(201)
                .body("registrationDeadline", notNullValue());
    }

    // =========================================================
    // SCRUM-129 — availableSpots / waitlistedCount exposed in response
    // =========================================================

    @Test
    void getById_withoutCapacity_returnsNullAvailableSpots() {
        var event = eventServiceMock.seedEvent("auth0|alice", "Sans capacité");
        event.capacity = null;

        given()
                .when().get("/events/" + event.id)
                .then()
                .statusCode(200)
                .body("availableSpots", nullValue())
                .body("waitlistedCount", equalTo(0));
    }

    @Test
    void getById_withCapacity_returnsAvailableSpots() {
        var event = eventServiceMock.seedEvent("auth0|alice", "Avec capacité");
        event.capacity = 10;

        given()
                .when().get("/events/" + event.id)
                .then()
                .statusCode(200)
                .body("availableSpots", equalTo(10))
                .body("waitlistedCount", equalTo(0));
    }

    @Test
    void getAll_withFeaturedFilter_returnsOk() {
        given()
                .queryParam("featured", "true")
                .when().get("/events")
                .then()
                .statusCode(200)
                .body("", instanceOf(java.util.List.class));
    }

    // --- GET /events/featured ---

    @Test
    void getFeatured_returnsOk() {
        given()
                .when().get("/events/featured")
                .then()
                .statusCode(200)
                .body("", instanceOf(java.util.List.class));
    }

    @Test
    void getFeatured_withLimit_returnsOk() {
        given()
                .queryParam("limit", "3")
                .when().get("/events/featured")
                .then()
                .statusCode(200);
    }

    @Test
    void getFeatured_limitAbove12_returns400() {
        given()
                .queryParam("limit", "99")
                .when().get("/events/featured")
                .then()
                .statusCode(400);
    }

    @Test
    void getFeatured_limitBelow1_returns400() {
        given()
                .queryParam("limit", "0")
                .when().get("/events/featured")
                .then()
                .statusCode(400);
    }
}
