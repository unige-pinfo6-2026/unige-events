package ch.unige.events.resource;

import ch.unige.events.exception.mapper.GenericExceptionMapper;
import ch.unige.events.service.EventServiceMock;
import ch.unige.events.service.UserServiceMock;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import jakarta.inject.Inject;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Pentest 2026-04-17 finding 4.22 — clients sending the wrong multipart field
 * name used to hit a 500 with RESTEasy's plain-text "Error id …" page (the
 * downstream NPE on a null FileUpload). Each affected endpoint must now
 * surface a structured {@code 400 validation_error}.
 *
 * <p>Also exercises {@link GenericExceptionMapper}: the catch-all path that
 * converts any otherwise-unhandled exception into the standard JSON envelope
 * so RESTEasy's default text page never reaches a client.
 */
@QuarkusTest
class MultipartValidationTest {

    @Inject
    UserServiceMock userServiceMock;

    @Inject
    EventServiceMock eventServiceMock;

    @BeforeEach
    void reset() {
        userServiceMock.reset();
        eventServiceMock.reset();
    }

    // -------------------------------------------------------------------------
    // HTTP integration — wrong multipart field name on every annotated endpoint.
    // -------------------------------------------------------------------------

    @Test
    @TestSecurity(user = "auth0|alice")
    void postUsersMeImage_wrongFieldName_returns400StructuredBody() {
        userServiceMock.seedUser("auth0|alice", "alice@example.com");

        given()
                .contentType("multipart/form-data")
                .multiPart("wrong", "photo.jpg", "fake-jpeg".getBytes(), "image/jpeg")
                .when().post("/users/me/image")
                .then()
                .statusCode(400)
                .body("error", equalTo("validation_error"))
                .body("message", equalTo("Request validation failed"))
                .body("details[0].message", containsString("file"));
    }

    @Test
    @TestSecurity(user = "auth0|alice")
    void postUsersMeBanner_wrongFieldName_returns400StructuredBody() {
        userServiceMock.seedUser("auth0|alice", "alice@example.com");

        given()
                .contentType("multipart/form-data")
                .multiPart("wrong", "banner.jpg", "fake-jpeg".getBytes(), "image/jpeg")
                .when().post("/users/me/banner")
                .then()
                .statusCode(400)
                .body("error", equalTo("validation_error"))
                .body("details[0].message", containsString("file"));
    }

    @Test
    @TestSecurity(user = "auth0|alice")
    void postEventsIdImage_wrongFieldName_returns400StructuredBody() {
        var event = eventServiceMock.seedEvent("auth0|alice", "Banner target");

        given()
                .contentType("multipart/form-data")
                .multiPart("wrong", "banner.jpg", "fake-jpeg".getBytes(), "image/jpeg")
                .when().post("/events/{id}/image", event.id)
                .then()
                .statusCode(400)
                .body("error", equalTo("validation_error"))
                .body("details[0].message", containsString("file"));
    }

    // -------------------------------------------------------------------------
    // GenericExceptionMapper unit tests.
    // -------------------------------------------------------------------------

    @Test
    void genericMapper_passesThroughWebApplicationExceptionResponse() {
        // For WAEs, the framework / dedicated mapper has already built the
        // response on the exception object — defer to it, don't double-wrap.
        GenericExceptionMapper mapper = new GenericExceptionMapper();

        Response builtIn = Response.status(418)
                .entity("teapot")
                .build();
        WebApplicationException waeWithResponse = new WebApplicationException(builtIn);

        Response result = mapper.toResponse(waeWithResponse);
        assertEquals(418, result.getStatus(),
                "WebApplicationException must keep its built-in response");
        assertEquals("teapot", result.getEntity());
    }

    @Test
    void genericMapper_translatesUnhandledExceptionToStructured500() {
        GenericExceptionMapper mapper = new GenericExceptionMapper();

        Response result = mapper.toResponse(new RuntimeException("boom"));

        assertEquals(500, result.getStatus());
        Object entity = result.getEntity();
        assertEquals(
                ch.unige.events.dto.ApiErrorResponse.class,
                entity.getClass(),
                "Unhandled exceptions must be wrapped in the standard JSON envelope so RESTEasy's plain-text Error id page never reaches a client");
        ch.unige.events.dto.ApiErrorResponse body =
                (ch.unige.events.dto.ApiErrorResponse) entity;
        assertEquals("internal_error", body.error());
        // The message must NOT echo the original exception detail — that's a
        // common information leak (stack frames, file paths, internal ids).
        org.junit.jupiter.api.Assertions.assertFalse(body.message().contains("boom"),
                "500 body must not echo the original exception's message");
    }

    @Test
    void genericMapper_doesNotShadowSpecificMappers() {
        // Sanity check: a NotFoundException still goes through its dedicated
        // mapper (404), not the generic 500 path. We don't invoke the runtime
        // resolver here — instead we assert the generic mapper, when handed a
        // WebApplicationException, defers to its built-in response.
        GenericExceptionMapper mapper = new GenericExceptionMapper();

        Response result = mapper.toResponse(new NotFoundException("nope"));
        // NotFoundException's default constructor builds a 404 response.
        assertEquals(404, result.getStatus());
    }

    @Test
    void genericMapper_passesThroughBadRequestExceptionWithoutWrapping() {
        // BadRequestException is a WebApplicationException, so the generic
        // mapper must NOT wrap it — that would override the dedicated
        // BadRequestExceptionMapper which produces the structured JSON used by
        // the resource-level null check (the actual fix for finding 4.22).
        GenericExceptionMapper mapper = new GenericExceptionMapper();

        Response result = mapper.toResponse(
                new BadRequestException("Missing required form field: file"));
        assertEquals(400, result.getStatus());
    }
}
