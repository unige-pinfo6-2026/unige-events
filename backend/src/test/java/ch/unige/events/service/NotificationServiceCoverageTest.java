package ch.unige.events.service;

import ch.unige.events.dto.notification.NotificationDTO;
import ch.unige.events.entity.Attendance;
import ch.unige.events.entity.AttendanceStatus;
import ch.unige.events.entity.Event;
import ch.unige.events.entity.EventCategory;
import ch.unige.events.entity.EventStatus;
import ch.unige.events.entity.Notification;
import ch.unige.events.entity.NotificationType;
import ch.unige.events.entity.User;
import io.quarkus.test.TestTransaction;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.ws.rs.ForbiddenException;
import jakarta.ws.rs.NotFoundException;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
@TestProfile(ShareServiceCoverageProfile.class)
class NotificationServiceCoverageTest {

    @Inject
    NotificationService notificationService;

    @Inject
    EntityManager em;

    // -------------------------------------------------------------------------
    // notifyAttendees — creates one Notification per ATTENDING attendee
    // -------------------------------------------------------------------------

    @Test
    @TestTransaction
    void notifyAttendees_createsOneNotificationPerAttendee() {
        User creator = persistUser("auth0|notif-creator", "notif-creator@example.com");
        Event event = persistEvent("Notif Event", creator);

        User a1 = persistUser("auth0|notif-a1", "notif-a1@example.com");
        User a2 = persistUser("auth0|notif-a2", "notif-a2@example.com");
        User waitlisted = persistUser("auth0|notif-w1", "notif-w1@example.com");

        persistAttendance(a1.id, event.id, AttendanceStatus.ATTENDING);
        persistAttendance(a2.id, event.id, AttendanceStatus.ATTENDING);
        persistAttendance(waitlisted.id, event.id, AttendanceStatus.WAITLISTED);
        em.flush();

        notificationService.notifyAttendees(event.id, NotificationType.EVENT_UPDATED, "Updated");
        em.flush();

        List<Notification> notifs = Notification.list("eventId = ?1", event.id);
        assertEquals(2, notifs.size(), "Only ATTENDING attendees should receive a notification");
        assertTrue(notifs.stream().allMatch(n -> n.type == NotificationType.EVENT_UPDATED));
        assertTrue(notifs.stream().allMatch(n -> "Updated".equals(n.message)));
        assertTrue(notifs.stream().noneMatch(n -> n.read));
    }

    @Test
    @TestTransaction
    void notifyAttendees_noAttendees_createsNoNotifications() {
        User creator = persistUser("auth0|notif-empty", "notif-empty@example.com");
        Event event = persistEvent("Empty Event", creator);
        em.flush();

        notificationService.notifyAttendees(event.id, NotificationType.EVENT_CANCELLED, "Cancelled");
        em.flush();

        long count = Notification.count("eventId = ?1", event.id);
        assertEquals(0, count);
    }

    // -------------------------------------------------------------------------
    // getNotifications — unread first
    // -------------------------------------------------------------------------

    @Test
    @TestTransaction
    void getNotifications_returnsUnreadFirst() {
        User user = persistUser("auth0|notif-get", "notif-get@example.com");
        persistNotification(user.id, NotificationType.EVENT_UPDATED, true);
        persistNotification(user.id, NotificationType.EVENT_CANCELLED, false);
        em.flush();

        List<NotificationDTO> dtos = notificationService.getNotifications(user.auth0Id, 0, 20);

        assertEquals(2, dtos.size());
        assertFalse(dtos.get(0).read(), "Unread notification should appear first");
        assertTrue(dtos.get(1).read());
    }

    @Test
    @TestTransaction
    void getNotifications_unknownUser_throwsNotFound() {
        assertThrows(NotFoundException.class,
                () -> notificationService.getNotifications("auth0|ghost", 0, 20));
    }

    // -------------------------------------------------------------------------
    // markRead — happy path and guards
    // -------------------------------------------------------------------------

    @Test
    @TestTransaction
    void markRead_setsReadToTrue() {
        User user = persistUser("auth0|notif-mr", "notif-mr@example.com");
        Notification n = persistNotification(user.id, NotificationType.NEW_ATTENDEE, false);
        em.flush();

        NotificationDTO dto = notificationService.markRead(n.id, user.auth0Id);

        assertTrue(dto.read());
        assertTrue(n.read);
    }

    @Test
    @TestTransaction
    void markRead_unknownUser_throwsNotFound() {
        assertThrows(NotFoundException.class,
                () -> notificationService.markRead(1L, "auth0|ghost"));
    }

    @Test
    @TestTransaction
    void markRead_unknownNotification_throwsNotFound() {
        persistUser("auth0|notif-mrn", "notif-mrn@example.com");
        em.flush();

        assertThrows(NotFoundException.class,
                () -> notificationService.markRead(999999L, "auth0|notif-mrn"));
    }

    @Test
    @TestTransaction
    void markRead_otherUsersNotification_throwsForbidden() {
        User owner = persistUser("auth0|notif-own", "notif-own@example.com");
        persistUser("auth0|notif-thief", "notif-thief@example.com");
        Notification n = persistNotification(owner.id, NotificationType.EVENT_REMINDER, false);
        em.flush();

        assertThrows(ForbiddenException.class,
                () -> notificationService.markRead(n.id, "auth0|notif-thief"));
    }

    // -------------------------------------------------------------------------
    // markAllRead
    // -------------------------------------------------------------------------

    @Test
    @TestTransaction
    void markAllRead_marksOnlyCurrentUsersNotifications() {
        User alice = persistUser("auth0|notif-alice", "notif-alice@example.com");
        User bob = persistUser("auth0|notif-bob", "notif-bob@example.com");

        Notification na = persistNotification(alice.id, NotificationType.EVENT_UPDATED, false);
        Notification nb = persistNotification(bob.id, NotificationType.EVENT_UPDATED, false);
        em.flush();

        notificationService.markAllRead(alice.auth0Id);
        em.flush();
        em.refresh(na);
        em.refresh(nb);

        assertTrue(na.read, "Alice's notification should be read");
        assertFalse(nb.read, "Bob's notification must not be touched");
    }

    @Test
    @TestTransaction
    void markAllRead_unknownUser_throwsNotFound() {
        assertThrows(NotFoundException.class,
                () -> notificationService.markAllRead("auth0|ghost"));
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private User persistUser(String auth0Id, String email) {
        User u = new User();
        u.auth0Id = auth0Id;
        u.email = email;
        u.profilePublic = false;
        u.createdAt = LocalDateTime.now();
        em.persist(u);
        em.flush();
        return u;
    }

    private Event persistEvent(String title, User creator) {
        Event e = new Event();
        e.title = title;
        e.location = "Uni Mail";
        e.startDate = LocalDateTime.now().plusDays(1);
        e.endDate = LocalDateTime.now().plusDays(2);
        e.category = EventCategory.ACADEMIC;
        e.status = EventStatus.PUBLISHED;
        e.creator = creator;
        em.persist(e);
        em.flush();
        return e;
    }

    private void persistAttendance(UUID userId, Long eventId, AttendanceStatus status) {
        Attendance a = new Attendance();
        a.userId = userId;
        a.eventId = eventId;
        a.status = status;
        em.persist(a);
        em.flush();
    }

    private Notification persistNotification(UUID userId, NotificationType type, boolean read) {
        Notification n = new Notification();
        n.userId = userId;
        n.type = type;
        n.message = "Test message";
        n.read = read;
        em.persist(n);
        em.flush();
        return n;
    }
}
