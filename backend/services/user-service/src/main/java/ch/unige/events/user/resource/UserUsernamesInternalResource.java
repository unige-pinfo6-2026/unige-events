package ch.unige.events.user.resource;

import ch.unige.events.shared.domain.dto.IdProjection;
import ch.unige.events.shared.jaxrs.Internal;
import ch.unige.events.user.entity.User;

import jakarta.annotation.security.PermitAll;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;

import java.util.Arrays;
import java.util.List;

/**
 * Internal endpoint resolving a batch of {@code username} handles to their
 * {@link IdProjection (id, username)} pairs. Primary consumer (SCRUM-145) :
 * notification-service's {@code CommentMentionConsumer} translates the
 * {@code @<handle>} mentions parsed from a comment into the {@code
 * notifications.user_id} UUIDs it must target — one network hop per
 * comment instead of N.
 *
 * <p>Anti-DoS cap (Décision L) : the CSV is silently truncated to 50
 * entries. A 500-char comment can hold at most ~30 mentions of 16 chars
 * each, so 50 is comfortable. Inputs are normalised (trim + lowercase +
 * deduplicated) before the {@code IN} clause inside
 * {@link User#findByUsernames(java.util.Collection)} — invalid handles
 * (wrong charset, out-of-range length) silently match nothing rather
 * than 400, mirroring how the consumer treats unresolved mentions as
 * "silent skip".
 *
 * <p>Bypasses the ISSUE-93 anti-oracle by design: this is a trusted
 * sibling-service call (gated by {@link Internal} via
 * {@link ch.unige.events.shared.jaxrs.InternalTokenFilter}), not the
 * browser-facing {@code GET /users/by-username/{u}}. The slim {@link
 * IdProjection} payload (id + username only) means {@code bio},
 * {@code interests}, {@code avatarUrl}, etc. never leave user-service.
 *
 * <p>Cf. {@code backend/docs/internal-endpoints.md}.
 */
@Path("/users")
@Produces(MediaType.APPLICATION_JSON)
public class UserUsernamesInternalResource {

    private static final int MAX_USERNAMES_PER_CALL = 50;

    @GET
    @Path("/_internal-by-usernames")
    @PermitAll
    @Internal
    public List<IdProjection> getByUsernames(@QueryParam("usernames") String csv) {
        if (csv == null || csv.isBlank()) {
            return List.of();
        }
        List<String> requested = Arrays.stream(csv.split(","))
                .map(String::trim)
                .filter(s -> !s.isBlank())
                .map(String::toLowerCase)
                .distinct()
                .limit(MAX_USERNAMES_PER_CALL)
                .toList();
        if (requested.isEmpty()) {
            return List.of();
        }
        return User.findByUsernames(requested).stream()
                .map(u -> new IdProjection(u.id, u.username))
                .toList();
    }
}
