package ch.unige.events.service;

import ch.unige.events.dto.notification.NotificationDTO;
import ch.unige.events.entity.Notification;
import ch.unige.events.entity.NotificationType;
import io.quarkus.test.Mock;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.ForbiddenException;
import jakarta.ws.rs.NotFoundException;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@Mock
@ApplicationScoped
public class NotificationServiceMock extends NotificationService {

    private final Map<Long, Notification> store = new ConcurrentHashMap<>();
    private final AtomicLong idSeq = new AtomicLong(1);

    // Auth0Id of the "current user" in tests — set before each test.
    public volatile UUID currentUserId = UUID.randomUUID();
    public volatile boolean forceNotFound = false;

    public void reset() {
        store.clear();
        idSeq.set(1);
        currentUserId = UUID.randomUUID();
        forceNotFound = false;
    }

    public Notification seedNotification(NotificationType type, boolean read) {
        Notification n = new Notification();
        n.id = idSeq.getAndIncrement();
        n.userId = currentUserId;
        n.type = type;
        n.message = "Test notification";
        n.eventId = 1L;
        n.read = read;
        n.createdAt = LocalDateTime.now();
        store.put(n.id, n);
        return n;
    }

    @Override
    public List<NotificationDTO> getNotifications(String auth0Id, int page, int size) {
        return store.values().stream()
                .filter(n -> n.userId.equals(currentUserId))
                .sorted((a, b) -> {
                    int cmp = Boolean.compare(a.read, b.read); // unread first
                    if (cmp != 0) return cmp;
                    return b.createdAt.compareTo(a.createdAt);
                })
                .map(NotificationDTO::from)
                .toList();
    }

    @Override
    public NotificationDTO markRead(Long id, String auth0Id) {
        if (forceNotFound) throw new NotFoundException();
        Notification n = store.get(id);
        if (n == null) throw new NotFoundException();
        if (!n.userId.equals(currentUserId)) throw new ForbiddenException("Not your notification");
        n.read = true;
        return NotificationDTO.from(n);
    }

    @Override
    public void markAllRead(String auth0Id) {
        store.values().stream()
                .filter(n -> n.userId.equals(currentUserId))
                .forEach(n -> n.read = true);
    }

    @Override
    public void notifyAttendees(Long eventId, NotificationType type, String message) {
        // No-op in mock — NotificationResource does not call this directly.
    }
}
