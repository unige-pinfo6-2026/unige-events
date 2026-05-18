package ch.unige.events.event.attachment.resource;

import ch.unige.events.event.attachment.dto.AttachmentDTO;
import ch.unige.events.event.attachment.service.EventAttachmentService;
import ch.unige.events.shared.error.ApiErrorResponse;

import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import jakarta.ws.rs.ForbiddenException;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.jboss.resteasy.reactive.multipart.FileUpload;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

/**
 * SCRUM-148 — REST-layer tests for {@link EventAttachmentResource}
 * (Décisions V + U). Status-code surface only ; the deep cascade
 * permission rules are exercised in {@link EventAttachmentServiceTest}.
 */
@QuarkusTest
@org.junit.jupiter.api.TestMethodOrder(org.junit.jupiter.api.MethodOrderer.OrderAnnotation.class)
class EventAttachmentResourceTest {

    @InjectMock
    EventAttachmentService service;

    private static AttachmentDTO sampleDto(Long eventId) {
        return new AttachmentDTO(
                1L,
                "deck.pdf",
                "http://s3/bucket/event-attachments/abc.pdf",
                1024L,
                "application/pdf",
                UUID.randomUUID(),
                LocalDateTime.now()
        );
    }

    private static WebApplicationException apiError(int status, String error, String message) {
        return new WebApplicationException(
                Response.status(status)
                        .entity(new ApiErrorResponse(error, message))
                        .type(MediaType.APPLICATION_JSON_TYPE)
                        .build());
    }

    private static Path tinyPdf(Path tmp) throws IOException {
        Path file = tmp.resolve("payload.pdf");
        Files.write(file, "%PDF-1.4\nfake".getBytes());
        return file;
    }

    @Test
    @TestSecurity(user = "auth0|attach-happy")
    void postAttachment_happyPath_returns201(@TempDir Path tmp) throws IOException {
        when(service.upload(eq(42L), any(FileUpload.class), anyBoolean()))
                .thenReturn(sampleDto(42L));

        given()
            .multiPart("file", tinyPdf(tmp).toFile(), "application/pdf")
            .when().post("/events/42/attachments")
            .then().statusCode(201);
    }

    @Test
    @TestSecurity(user = "auth0|attach-missing")
    void postAttachment_missingFileForm_returns400() {
        // No multiPart("file", ...) — the @RestForm("file") param is null,
        // the resource short-circuits with BadRequestException.
        given()
            .multiPart("other", "ignored")
            .when().post("/events/43/attachments")
            .then().statusCode(400);
    }

    @Test
    void postAttachment_unauthenticated_returns401(@TempDir Path tmp) throws IOException {
        given()
            .multiPart("file", tinyPdf(tmp).toFile(), "application/pdf")
            .when().post("/events/44/attachments")
            .then().statusCode(401);
    }

    @Test
    @TestSecurity(user = "auth0|attach-403")
    void postAttachment_nonOrganizer_returns403(@TempDir Path tmp) throws IOException {
        when(service.upload(eq(45L), any(FileUpload.class), anyBoolean()))
                .thenThrow(new ForbiddenException());

        given()
            .multiPart("file", tinyPdf(tmp).toFile(), "application/pdf")
            .when().post("/events/45/attachments")
            .then().statusCode(403);
    }

    @Test
    @TestSecurity(user = "auth0|attach-404")
    void postAttachment_eventNotFound_returns404(@TempDir Path tmp) throws IOException {
        when(service.upload(eq(46L), any(FileUpload.class), anyBoolean()))
                .thenThrow(new NotFoundException());

        given()
            .multiPart("file", tinyPdf(tmp).toFile(), "application/pdf")
            .when().post("/events/46/attachments")
            .then().statusCode(404);
    }

    @Test
    @TestSecurity(user = "auth0|attach-422-size")
    void postAttachment_oversized_returns422(@TempDir Path tmp) throws IOException {
        when(service.upload(eq(47L), any(FileUpload.class), anyBoolean()))
                .thenThrow(apiError(422, "attachment_invalid_size",
                        "File size exceeds the 10 MiB limit."));

        given()
            .multiPart("file", tinyPdf(tmp).toFile(), "application/pdf")
            .when().post("/events/47/attachments")
            .then().statusCode(422);
    }

    @Test
    @TestSecurity(user = "auth0|attach-422-type")
    void postAttachment_invalidMime_returns422(@TempDir Path tmp) throws IOException {
        when(service.upload(eq(48L), any(FileUpload.class), anyBoolean()))
                .thenThrow(apiError(422, "attachment_invalid_type",
                        "File type 'image/png' is not allowed."));

        given()
            .multiPart("file", tinyPdf(tmp).toFile(), "image/png")
            .when().post("/events/48/attachments")
            .then().statusCode(422);
    }

    @Test
    @TestSecurity(user = "auth0|attach-422-cap")
    void postAttachment_capExceeded_returns422(@TempDir Path tmp) throws IOException {
        when(service.upload(eq(49L), any(FileUpload.class), anyBoolean()))
                .thenThrow(apiError(422, "attachment_limit_exceeded",
                        "An event can have at most 5 attachments."));

        given()
            .multiPart("file", tinyPdf(tmp).toFile(), "application/pdf")
            .when().post("/events/49/attachments")
            .then().statusCode(422);
    }

    @Test
    @TestSecurity(user = "auth0|attach-del-happy")
    void deleteAttachment_existing_returns204() {
        doNothing().when(service).delete(eq(50L), eq(101L), anyBoolean());
        given()
            .when().delete("/events/50/attachments/101")
            .then().statusCode(204);
    }

    @Test
    @TestSecurity(user = "auth0|attach-del-403")
    void deleteAttachment_nonAuth_returns403() {
        doThrow(new ForbiddenException()).when(service).delete(eq(51L), eq(102L), anyBoolean());
        given()
            .when().delete("/events/51/attachments/102")
            .then().statusCode(403);
    }

    @Test
    @TestSecurity(user = "auth0|attach-del-404")
    void deleteAttachment_pathMismatchOrMissing_returns404() {
        doThrow(new NotFoundException()).when(service).delete(eq(52L), eq(103L), anyBoolean());
        given()
            .when().delete("/events/52/attachments/103")
            .then().statusCode(404);
    }

    @Test
    void deleteAttachment_unauthenticated_returns401() {
        given()
            .when().delete("/events/53/attachments/104")
            .then().statusCode(401);
    }

    /**
     * Bursts {@code events.uploadAttachment} (max=10/60 s, Décision U) until
     * the rate-limit interceptor trips. Pinned last via {@link org.junit.jupiter.api.Order}
     * because the bucket is principal-scoped and shared across {@code @Test}
     * methods on the same {@code @QuarkusTest} instance — a leftover full
     * bucket would otherwise cross-contaminate any later happy-path call.
     */
    @org.junit.jupiter.api.Order(Integer.MAX_VALUE)
    @Test
    @TestSecurity(user = "auth0|attach-burst")
    void postAttachment_rateLimit_eventually_triggers429(@TempDir Path tmp) throws IOException {
        when(service.upload(anyLong(), any(FileUpload.class), anyBoolean()))
                .thenReturn(sampleDto(60L));

        boolean saw429 = false;
        Path payload = tinyPdf(tmp);
        for (int i = 0; i < 20; i++) {
            int code = given()
                .multiPart("file", payload.toFile(), "application/pdf")
                .when().post("/events/60/attachments")
                .then().extract().statusCode();
            if (code == 429) {
                saw429 = true;
                break;
            }
        }
        org.junit.jupiter.api.Assertions.assertTrue(saw429,
                "Expected a 429 after 10 successful POSTs (bucket max=10/60s)");
    }
}
