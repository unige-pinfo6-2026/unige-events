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
    void delete_asCreator_returns204() {
        var event = eventServiceMock.seedEvent("auth0|alice", "À annuler");

        given()
                .when().delete("/events/" + event.id)
                .then()
                .statusCode(204);
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
    @TestSecurity(user = "auth0|alice", roles = {"ORGANIZER"})
    void publishAsOrganiserOwnerReturns200() {
        var event = eventServiceMock.seedEvent("auth0|alice", "Draft Event");

        given()
                .when().patch("/events/" + event.id + "/publish")
                .then()
                .statusCode(200)
                .body("status", equalTo("PUBLISHED"));
    }

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
    @TestSecurity(user = "auth0|alice", roles = {"STUDENT"})
    void publishAsStudentReturns403() {
        var event = eventServiceMock.seedEvent("auth0|alice", "Draft Event");

        given()
                .when().patch("/events/" + event.id + "/publish")
                .then()
                .statusCode(403);
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
        e2.faculty = Faculty.LETTRES;

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
                .queryParam("faculty", "DROIT")
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
        req.faculty = Faculty.MEDECINE;

        given()
                .contentType(ContentType.JSON)
                .body(req)
                .when().post("/events")
                .then()
                .statusCode(201)
                .body("faculty", is("MEDECINE"));
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
}
