package ch.unige.events.user.follow.kafka;

import ch.unige.events.shared.kafka.events.FollowLifecycleEvent;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.reactive.messaging.Channel;
import org.eclipse.microprofile.reactive.messaging.Emitter;

/**
 * Fans a {@link FollowLifecycleEvent} out to one of three Kafka topics
 * (users.followed, users.follow-requested, users.follow-accepted).
 * Pattern mirrors event-service.EventLifecyclePublisher (Décision F).
 *
 * <p>Failures are logged + swallowed — Kafka outage must not roll back
 * the user-facing follow transaction.
 */
@ApplicationScoped
public class FollowLifecyclePublisher {

    private final Emitter<FollowLifecycleEvent> followedEmitter;
    private final Emitter<FollowLifecycleEvent> requestedEmitter;
    private final Emitter<FollowLifecycleEvent> acceptedEmitter;

    @Inject
    public FollowLifecyclePublisher(
            @Channel("users-followed") Emitter<FollowLifecycleEvent> followedEmitter,
            @Channel("users-follow-requested") Emitter<FollowLifecycleEvent> requestedEmitter,
            @Channel("users-follow-accepted") Emitter<FollowLifecycleEvent> acceptedEmitter) {
        this.followedEmitter = followedEmitter;
        this.requestedEmitter = requestedEmitter;
        this.acceptedEmitter = acceptedEmitter;
    }

    public void send(FollowLifecycleEvent ev) {
        Emitter<FollowLifecycleEvent> emitter = switch (ev.type()) {
            case FOLLOWED -> followedEmitter;
            case REQUESTED -> requestedEmitter;
            case ACCEPTED -> acceptedEmitter;
        };
        try {
            emitter.send(ev);
        } catch (Exception e) {
            Log.warnf(e,
                    "Failed to publish %s for follow %s -> %s ; downstream consumers will not see this transition",
                    ev.type(), ev.followerId(), ev.followedId());
        }
    }
}
