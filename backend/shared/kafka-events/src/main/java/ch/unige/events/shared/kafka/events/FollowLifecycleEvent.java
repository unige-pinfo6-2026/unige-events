package ch.unige.events.shared.kafka.events;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

/**
 * Wire shape of the {@code users.{followed,follow-requested,follow-accepted}}
 * Kafka topics. Single record discriminated by {@link Type} so consumers
 * can fan out cheaply.
 *
 * <ul>
 * <li>{@code FOLLOWED} — auto-accept path (target = profilePublic).</li>
 * <li>{@code REQUESTED} — pending-approval path (target = private).</li>
 * <li>{@code ACCEPTED} — target accepted a pending request.</li>
 * </ul>
 */
public record FollowLifecycleEvent(
        Type type,
        UUID followerId,
        UUID followedId,
        Instant occurredAt
) {

    public enum Type {
        FOLLOWED,
        REQUESTED,
        ACCEPTED
    }

    public static FollowLifecycleEvent followed(UUID followerId, UUID followedId) {
        return new FollowLifecycleEvent(Type.FOLLOWED, followerId, followedId, Instant.now(Clock.systemUTC()));
    }

    public static FollowLifecycleEvent followRequested(UUID followerId, UUID followedId) {
        return new FollowLifecycleEvent(Type.REQUESTED, followerId, followedId, Instant.now(Clock.systemUTC()));
    }

    public static FollowLifecycleEvent followAccepted(UUID followerId, UUID followedId) {
        return new FollowLifecycleEvent(Type.ACCEPTED, followerId, followedId, Instant.now(Clock.systemUTC()));
    }
}
