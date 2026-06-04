package ch.unige.events.engagement.comment.resource;

import ch.unige.events.engagement.comment.entity.Comment;
import ch.unige.events.shared.client.EventServiceClient;
import ch.unige.events.shared.domain.dto.EventDTO;
import ch.unige.events.shared.domain.enums.EventStatus;

import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import org.eclipse.microprofile.config.ConfigProvider;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import java.time.Month;

/**
 * SCRUM-144 — sentinel pour l'endpoint interne /comments/{id}/_internal-visibility.
 *
 * <p>Vérifie le 404 anti-oracle uniforme entre :
 * <ul>
 *   <li>token absent / faux → InternalTokenFilter renvoie 404</li>
 *   <li>commentId inexistant → service renvoie 404</li>
 *   <li>event invisible (cascade ISSUE-92) → service renvoie 404</li>
 * </ul>
 */
@SuppressWarnings("java:S1612")
@QuarkusTest
class CommentVisibilityInternalResourceTest {

    @InjectMock @RestClient
    EventServiceClient eventClient;

    private static String token() {
        return ConfigProvider.getConfig()
                .getOptionalValue("unige.internal-token", String.class)
                .orElse("");
    }

    private static EventDTO publishedEvent(long eventId, UUID creator) {
        return new EventDTO(eventId, "Concert", null, "loc",
                java.time.LocalDateTime.of(2025, Month.JANUARY, 1, 12, 0), java.time.LocalDateTime.of(2999, Month.JANUARY, 1, 0, 0).plusHours(2),
                null, null, null, creator, EventStatus.PUBLISHED, null, false, false, null,
                0L, null, 0L, 0L, 0L,
                null, null, null, List.of(), null, null, null, null, null);
    }

    @BeforeEach
    @AfterEach
    void cleanRows() {
        QuarkusTransaction.requiringNew().run(() -> Comment.delete("eventId in ?1",
                List.of(800_001L, 800_002L, 800_003L)));
    }

    private Long persistComment(Long eventId, UUID authorId) {
        return QuarkusTransaction.requiringNew().call(() -> {
            Comment c = new Comment();
            c.eventId = eventId;
            c.authorId = authorId;
            c.content = "x";
            c.persist();
            return c.id;
        });
    }

    @Test
    void getVisibility_validToken_existingCommentVisibleEvent_returnsProjection() {
        UUID author = UUID.randomUUID();
        UUID caller = UUID.randomUUID();
        Long eventId = 800_001L;
        Long commentId = persistComment(eventId, author);
        when(eventClient.getByIdWithCoOrgCheck(eq(eventId), eq(caller)))
                .thenReturn(publishedEvent(eventId, author));

        given()
            .header("X-Internal-Token", token())
            .queryParam("callerId", caller.toString())
            .when().get("/comments/" + commentId + "/_internal-visibility")
            .then()
            .statusCode(200)
            .body("commentId", equalTo(commentId.intValue()))
            .body("eventId", equalTo(eventId.intValue()))
            .body("authorId", equalTo(author.toString()))
            .body("callerHasAccess", is(true));
    }

    @Test
    void getVisibility_wrongToken_returns404AntiOracle() {
        UUID caller = UUID.randomUUID();
        Long commentId = persistComment(800_002L, UUID.randomUUID());

        given()
            .header("X-Internal-Token", "wrong-token-xxx")
            .queryParam("callerId", caller.toString())
            .when().get("/comments/" + commentId + "/_internal-visibility")
            .then().statusCode(404);
    }

    @Test
    void getVisibility_missingToken_returns404() {
        UUID caller = UUID.randomUUID();
        given()
            .queryParam("callerId", caller.toString())
            .when().get("/comments/1/_internal-visibility")
            .then().statusCode(404);
    }

    @Test
    void getVisibility_unknownCommentId_returns404() {
        UUID caller = UUID.randomUUID();
        given()
            .header("X-Internal-Token", token())
            .queryParam("callerId", caller.toString())
            .when().get("/comments/999999999/_internal-visibility")
            .then().statusCode(404);
    }

    @Test
    void getVisibility_eventInvisible_returns404AntiOracle() {
        UUID author = UUID.randomUUID();
        UUID caller = UUID.randomUUID();
        Long eventId = 800_003L;
        Long commentId = persistComment(eventId, author);
        // Event invisible — getByIdWithCoOrgCheck returns null (cascade ISSUE-92).
        when(eventClient.getByIdWithCoOrgCheck(eq(eventId), any(UUID.class))).thenReturn(null);

        given()
            .header("X-Internal-Token", token())
            .queryParam("callerId", caller.toString())
            .when().get("/comments/" + commentId + "/_internal-visibility")
            .then().statusCode(404);
    }
}
