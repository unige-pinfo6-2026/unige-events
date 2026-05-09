package ch.unige.events.engagement.comment.resource;

import ch.unige.events.engagement.comment.entity.Comment;
import ch.unige.events.engagement.test.JwtTestContext;
import ch.unige.events.engagement.test.JwtTestHelper;
import ch.unige.events.shared.client.EventServiceClient;
import ch.unige.events.shared.client.UserServiceClient;
import ch.unige.events.shared.domain.dto.UserPublicResponse;

import io.quarkus.panache.mock.PanacheMock;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

@QuarkusTest
@TestSecurity(user = "auth0|test-comment-direct")
class CommentDirectResourceTest {

    @InjectMock
    @RestClient
    EventServiceClient eventClient;

    @InjectMock
    @RestClient
    UserServiceClient userClient;

    private final UUID userId = UUID.randomUUID();

    @BeforeEach
    void stageJwt() {
        JwtTestContext.set(JwtTestHelper.jwtFor(userId));
        lenient().when(userClient.getById(any(UUID.class))).thenAnswer(inv -> {
            UUID id = inv.getArgument(0);
            return new UserPublicResponse(id, "User-" + id, null, null, null,
                    null, null, null, 0L, 0L, null);
        });
        lenient().when(eventClient.getOrganizerUuids(any(Long.class)))
                .thenReturn(List.of());
    }

    @AfterEach
    void clearJwt() {
        JwtTestContext.clear();
    }

    @Test
    void delete_byAuthor_returns204() {
        Comment c = new Comment();
        c.id = 3001L;
        c.eventId = 30L;
        c.authorId = userId;
        c.content = "x";
        c.createdAt = LocalDateTime.now();
        Comment spy = spy(c);
        doNothing().when(spy).delete();

        PanacheMock.mock(Comment.class);
        when(Comment.findByIdOptional(3001L)).thenReturn(Optional.of(spy));

        given()
            .when().delete("/comments/3001")
            .then().statusCode(204);
    }

    @Test
    void delete_unknownComment_returns404() {
        PanacheMock.mock(Comment.class);
        when(Comment.findByIdOptional(3002L)).thenReturn(Optional.empty());

        given()
            .when().delete("/comments/3002")
            .then().statusCode(404);
    }

    @Test
    void delete_notAuthor_returns403() {
        Comment c = new Comment();
        c.id = 3003L;
        c.eventId = 31L;
        c.authorId = UUID.randomUUID();
        c.content = "x";
        c.createdAt = LocalDateTime.now();
        Comment spy = spy(c);
        doNothing().when(spy).delete();

        PanacheMock.mock(Comment.class);
        when(Comment.findByIdOptional(3003L)).thenReturn(Optional.of(spy));
        when(eventClient.getOrganizerUuids(31L)).thenReturn(List.of());

        given()
            .when().delete("/comments/3003")
            .then().statusCode(403);
    }
}
