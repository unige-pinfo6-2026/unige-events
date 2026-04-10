package ch.unige.events.service;

import ch.unige.events.entity.Attendance;
import ch.unige.events.entity.AttendanceStatus;
import ch.unige.events.entity.Event;
import ch.unige.events.entity.Favorite;
import ch.unige.events.entity.EventCategory;
import ch.unige.events.entity.EventStatus;
import ch.unige.events.entity.User;
import jakarta.persistence.EntityManager;

import java.time.LocalDateTime;
import java.util.UUID;

final class ServiceCoverageTestHelper {

    private ServiceCoverageTestHelper() {}

    static User persistUser(EntityManager em, String auth0Id, String email) {
        User user = new User();
        user.auth0Id = auth0Id;
        user.email = email;
        user.profilePublic = false;
        user.createdAt = LocalDateTime.now();
        em.persist(user);
        em.flush();
        return user;
    }

    static Event persistEvent(EntityManager em, String title, User creator,
                               EventStatus status, Integer capacity) {
        Event event = new Event();
        event.title = title;
        event.location = "Uni Mail";
        event.startDate = LocalDateTime.now().plusDays(1);
        event.endDate = LocalDateTime.now().plusDays(2);
        event.category = EventCategory.ACADEMIC;
        event.status = status;
        event.creator = creator;
        event.capacity = capacity;
        em.persist(event);
        em.flush();
        return event;
    }

    static Attendance persistAttendance(EntityManager em, UUID userId,
                                         Long eventId, AttendanceStatus status) {
        Attendance a = new Attendance();
        a.userId = userId;
        a.eventId = eventId;
        a.status = status;
        em.persist(a);
        em.flush();
        return a;
    }

    static Favorite persistFavorite(EntityManager em, UUID userId, Long eventId) {
        Favorite f = new Favorite();
        f.userId = userId;
        f.eventId = eventId;
        em.persist(f);
        em.flush();
        return f;
    }
}
