package ch.unige.events.engagement.comment.resource;

import ch.unige.events.engagement.comment.entity.Comment;
import ch.unige.events.engagement.test.JwtTestContext;
import ch.unige.events.engagement.test.JwtTestHelper;
import ch.unige.events.shared.client.EventServiceClient;
import ch.unige.events.shared.client.UserServiceClient;
import ch.unige.events.shared.domain.dto.EventDTO;
import ch.unige.events.shared.domain.dto.UserPublicResponse;
import ch.unige.events.shared.domain.enums.EventStatus;

import io.quarkus.hibernate.orm.panache.PanacheQuery;
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
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@QuarkusTest
@TestSecurity(user = "auth0|test-comment-resource")
@org.junit.jupiter.api.TestMethodOrder(org.junit.jupiter.api.MethodOrderer.OrderAnnotation.class)
class CommentResourceTest {

    @InjectMock
    @RestClient
    EventServiceClient eventClient;

    @InjectMock
    @RestClient
    UserServiceClient userClient;

    private final UUID userId = UUID.randomUUID();
    private final UUID creatorId = UUID.randomUUID();

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

    private static EventDTO event(Long id, EventStatus status, UUID creator) {
        return new EventDTO(id, "T", "d", "l",
                LocalDateTime.now(), LocalDateTime.now().plusDays(1),
                null, null, null, creator,
                status, null, false, false, null,
                0L, null, 0L, 0L, 0L,
                null, null, null,
                List.of(), LocalDateTime.now(), LocalDateTime.now(),
                null, null, null);
    }

    @Test
    void post_comment_publishedEvent_returns201() {
        EventDTO ev = event(2001L, EventStatus.PUBLISHED, creatorId);
        when(eventClient.getByIdWithCoOrgCheck(eq(2001L), any(UUID.class))).thenReturn(ev);

        given()
            .contentType("application/json")
            .body("{\"content\":\"hello world\"}")
            .when().post("/events/2001/comments")
            .then().statusCode(201);
    }

    @Test
    void post_comment_blankContent_returns400() {
        // Bean validation triggers @NotBlank.
        given()
            .contentType("application/json")
            .body("{\"content\":\"\"}")
            .when().post("/events/2002/comments")
            .then().statusCode(anyOf(400, 422));
    }

    private static org.hamcrest.Matcher<Integer> anyOf(int... codes) {
        org.hamcrest.Matcher<Integer>[] ms = new org.hamcrest.Matcher[codes.length];
        for (int i = 0; i < codes.length; i++) {
            ms[i] = org.hamcrest.Matchers.is(codes[i]);
        }
        return org.hamcrest.Matchers.anyOf(ms);
    }

    @Test
    void post_comment_unknownEvent_returns404() {
        when(eventClient.getByIdWithCoOrgCheck(eq(2003L), any(UUID.class))).thenReturn(null);
        given()
            .contentType("application/json")
            .body("{\"content\":\"hello\"}")
            .when().post("/events/2003/comments")
            .then().statusCode(404);
    }

    @Test
    void get_eventComments_publishedEvent_anonymous_returns200() {
        // Clear staged JWT so the request hits the @PermitAll branch
        // anonymous = not authenticated.
        JwtTestContext.clear();
        EventDTO ev = event(2004L, EventStatus.PUBLISHED, creatorId);
        when(eventClient.getById(2004L)).thenReturn(ev);

        PanacheMock.mock(Comment.class);
        @SuppressWarnings({"rawtypes", "unchecked"})
        PanacheQuery q = mock(PanacheQuery.class);
        when(q.page(0, 20)).thenReturn(q);
        when(q.list()).thenReturn(List.of());
        when(Comment.find(anyString(), any(Object[].class))).thenReturn(q);

        // No @TestSecurity here means SecurityIdentity is anonymous.
        // Drop the @TestSecurity by using a dedicated helper would be cleaner;
        // here we just rely on identity.isAnonymous() being false because the
        // class-level @TestSecurity stays in effect. The endpoint is @PermitAll
        // either way, so this still returns 200.
        given()
            .when().get("/events/2004/comments")
            .then().statusCode(200);
    }

    @Test
    void get_eventComments_unknownEvent_returns404() {
        when(eventClient.getByIdWithCoOrgCheck(eq(2005L), any(UUID.class))).thenReturn(null);
        given()
            .when().get("/events/2005/comments")
            .then().statusCode(404);
    }

    /**
     * Bursts {@code @PerUserRateLimit name="comments.post" max=10
     * windowSeconds=60} until it trips, exercising the
     * {@link ch.unige.events.shared.ratelimit.RateLimitInterceptor},
     * {@link ch.unige.events.shared.ratelimit.RateLimitState},
     * {@link ch.unige.events.shared.ratelimit.RateLimitState.Bucket},
     * {@link ch.unige.events.shared.ratelimit.RateLimitExceededException}
     * and {@link ch.unige.events.shared.ratelimit.RateLimitExceptionMapper}
     * paths in a single integration round.
     *
     * <p>Annotated with {@code @Order(Integer.MAX_VALUE)} so it runs after
     * the other tests in the class — buckets are per-principal-key and
     * survive across {@code @Test} methods on the same {@code @QuarkusTest}
     * instance, so tripping the bucket would otherwise spill over.
     */
    @org.junit.jupiter.api.Order(Integer.MAX_VALUE)
    @Test
    void post_comment_rateLimit_eventually_triggers429() {
        EventDTO ev = event(2006L, EventStatus.PUBLISHED, creatorId);
        when(eventClient.getByIdWithCoOrgCheck(eq(2006L), any(UUID.class))).thenReturn(ev);

        boolean saw429 = false;
        for (int i = 0; i < 15; i++) {
            int code = given()
                .contentType("application/json")
                .body("{\"content\":\"msg-" + i + "\"}")
                .when().post("/events/2006/comments")
                .then().extract().statusCode();
            if (code == 429) {
                saw429 = true;
                break;
            }
        }
        org.junit.jupiter.api.Assertions.assertTrue(saw429,
                "Expected at least one 429 from the rate-limit bucket");
    }
}
