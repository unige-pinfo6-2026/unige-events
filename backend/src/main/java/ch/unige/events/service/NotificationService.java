package ch.unige.events.service;

import ch.unige.events.dto.notification.NotificationDTO;
import ch.unige.events.entity.Attendance;
import ch.unige.events.entity.AttendanceStatus;
import ch.unige.events.entity.Notification;
import ch.unige.events.entity.NotificationType;
import ch.unige.events.entity.User;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.ForbiddenException;
import jakarta.ws.rs.NotFoundException;

import java.util.List;

@ApplicationScoped
public class NotificationService {

    @Transactional
    public List<NotificationDTO> getNotifications(String auth0Id, int page, int size) {
        User user = User.findByAuth0Id(auth0Id)
                .orElseThrow(() -> new NotFoundException("User profile not found"));
        return Notification.findByUser(user.id, page, size)
                .stream()
                .map(NotificationDTO::from)
                .toList();
    }

    @Transactional
    public NotificationDTO markRead(Long id, String auth0Id) {
        User user = User.findByAuth0Id(auth0Id)
                .orElseThrow(() -> new NotFoundException("User profile not found"));
        Notification n = Notification.<Notification>findByIdOptional(id)
                .orElseThrow(NotFoundException::new);
        if (!n.userId.equals(user.id)) {
            throw new ForbiddenException("Not your notification");
        }
        n.read = true;
        return NotificationDTO.from(n);
    }

    @Transactional
    public void markAllRead(String auth0Id) {
        User user = User.findByAuth0Id(auth0Id)
                .orElseThrow(() -> new NotFoundException("User profile not found"));
        Notification.update("read = true WHERE userId = ?1 AND read = false", user.id);
    }

    @Transactional
    public void notifyAttendees(Long eventId, NotificationType type, String message) {
        List<Attendance> attendees = Attendance.list(
                "eventId = ?1 AND status = ?2", eventId, AttendanceStatus.ATTENDING);
        for (Attendance att : attendees) {
            Notification n = new Notification();
            n.userId = att.userId;
            n.type = type;
            n.message = message;
            n.eventId = eventId;
            n.read = false;
            n.persist();
        }
    }
}
