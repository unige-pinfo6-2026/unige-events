package ch.unige.events.user.follow.service;

import ch.unige.events.shared.kafka.events.FollowLifecycleEvent;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Test-only CDI bean that records every {@link FollowLifecycleEvent} fired
 * by {@link FollowService}. Observes in the default {@code IN_PROGRESS}
 * phase so the event is captured synchronously at {@code fire()} time —
 * unlike the production {@code FollowLifecycleKafkaBridge} (AFTER_SUCCESS),
 * which never runs under {@code @TestTransaction} (the transaction rolls
 * back). Lets the unit tests assert WHICH lifecycle events a follow / accept
 * / reject path emits, without standing up Kafka.
 */
@ApplicationScoped
public class RecordingFollowLifecycleObserver {

    private final List<FollowLifecycleEvent> events = new CopyOnWriteArrayList<>();

    void onEvent(@Observes FollowLifecycleEvent ev) {
        events.add(ev);
    }

    public void reset() {
        events.clear();
    }

    public List<FollowLifecycleEvent> events() {
        return Collections.unmodifiableList(events);
    }
}
