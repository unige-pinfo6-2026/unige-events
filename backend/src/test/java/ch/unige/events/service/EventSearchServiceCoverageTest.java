package ch.unige.events.service;

import ch.unige.events.dto.event.EventDTO;
import ch.unige.events.entity.Event;
import ch.unige.events.entity.EventCategory;
import ch.unige.events.entity.EventStatus;
import ch.unige.events.entity.Faculty;
import ch.unige.events.entity.User;
import io.quarkus.test.TestTransaction;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
@TestProfile(ShareServiceCoverageProfile.class)
class EventSearchServiceCoverageTest {

    @Inject
    EventSearchService eventSearchService;

    @Inject
    EntityManager entityManager;

    // --- Aucun filtre (branche conditions.isEmpty()) ---

    @Test
    @TestTransaction
    void search_noFilters_returnsAll() {
        deleteAll();
        User user = persistUser("auth0|s1", "s1@example.com");
        persistEvent("Conférence Java", "Talk Quarkus", EventCategory.CONFERENCE, LocalDateTime.now().plusDays(1), user);
        persistEvent("Match de foot", "Tournoi", EventCategory.SPORTS, LocalDateTime.now().plusDays(2), user);

        List<EventDTO> result = eventSearchService.search(null, null, null, null, null, null, 0, 20);

        assertEquals(2, result.size());
    }

    // --- Filtre q : branche conditions non vide + LOWER() title ---

    @Test
    @TestTransaction
    void search_withQ_matchesTitle() {
        deleteAll();
        User user = persistUser("auth0|s2", "s2@example.com");
        persistEvent("Conférence Java", "Talk générique", EventCategory.CONFERENCE, LocalDateTime.now().plusDays(1), user);
        persistEvent("Match de foot", "Tournoi", EventCategory.SPORTS, LocalDateTime.now().plusDays(2), user);

        List<EventDTO> result = eventSearchService.search("java", null, null, null, null, null, 0, 20);

        assertEquals(1, result.size());
        assertEquals("Conférence Java", result.get(0).title());
    }

    @Test
    @TestTransaction
    void search_withQ_matchesDescription() {
        deleteAll();
        User user = persistUser("auth0|s3", "s3@example.com");
        persistEvent("Conférence Tech", "Talk sur Quarkus et Java", EventCategory.CONFERENCE, LocalDateTime.now().plusDays(1), user);
        persistEvent("Match de foot", "Tournoi inter-facs", EventCategory.SPORTS, LocalDateTime.now().plusDays(2), user);

        List<EventDTO> result = eventSearchService.search("quarkus", null, null, null, null, null, 0, 20);

        assertEquals(1, result.size());
        assertEquals("Conférence Tech", result.get(0).title());
    }

    @Test
    @TestTransaction
    void search_withQ_isCaseInsensitive() {
        deleteAll();
        User user = persistUser("auth0|s4", "s4@example.com");
        persistEvent("Conférence JAVA", null, EventCategory.CONFERENCE, LocalDateTime.now().plusDays(1), user);

        List<EventDTO> result = eventSearchService.search("java", null, null, null, null, null, 0, 20);

        assertEquals(1, result.size());
    }

    // --- q blank → branche ignorée (comme null) ---

    @Test
    @TestTransaction
    void search_blankQ_treatedAsNoFilter() {
        deleteAll();
        User user = persistUser("auth0|s5", "s5@example.com");
        persistEvent("Event A", null, EventCategory.ACADEMIC, LocalDateTime.now().plusDays(1), user);
        persistEvent("Event B", null, EventCategory.ACADEMIC, LocalDateTime.now().plusDays(2), user);

        List<EventDTO> result = eventSearchService.search("   ", null, null, null, null, null, 0, 20);

        assertEquals(2, result.size());
    }

    // --- Filtre category ---

    @Test
    @TestTransaction
    void search_withCategory_returnsFiltered() {
        deleteAll();
        User user = persistUser("auth0|s6", "s6@example.com");
        persistEvent("Conférence Java", null, EventCategory.CONFERENCE, LocalDateTime.now().plusDays(1), user);
        persistEvent("Match de foot", null, EventCategory.SPORTS, LocalDateTime.now().plusDays(2), user);

        List<EventDTO> result = eventSearchService.search(null, EventCategory.SPORTS, null, null, null, null, 0, 20);

        assertEquals(1, result.size());
        assertEquals(EventCategory.SPORTS, result.get(0).category());
    }

    // --- Filtre dateFrom ---

    @Test
    @TestTransaction
    void search_withDateFrom_excludesPastEvents() {
        deleteAll();
        User user = persistUser("auth0|s7", "s7@example.com");
        persistEvent("Passé", null, EventCategory.ACADEMIC, LocalDateTime.now().minusDays(5), user);
        persistEvent("Futur", null, EventCategory.ACADEMIC, LocalDateTime.now().plusDays(5), user);

        List<EventDTO> result = eventSearchService.search(null, null, null, null, LocalDate.now(), null, 0, 20);

        assertEquals(1, result.size());
        assertEquals("Futur", result.get(0).title());
    }

    // --- Filtre dateTo ---

    @Test
    @TestTransaction
    void search_withDateTo_excludesFutureEvents() {
        deleteAll();
        User user = persistUser("auth0|s8", "s8@example.com");
        persistEvent("Passé", null, EventCategory.ACADEMIC, LocalDateTime.now().minusDays(5), user);
        persistEvent("Futur", null, EventCategory.ACADEMIC, LocalDateTime.now().plusDays(5), user);

        List<EventDTO> result = eventSearchService.search(null, null, null, null, null, LocalDate.now(), 0, 20);

        assertEquals(1, result.size());
        assertEquals("Passé", result.get(0).title());
    }

    // --- Combinaison de tous les filtres ---

    @Test
    @TestTransaction
    void search_allFilters_returnsOnlyMatch() {
        deleteAll();
        User user = persistUser("auth0|s9", "s9@example.com");
        LocalDateTime target = LocalDateTime.now().plusDays(3);
        persistEvent("Conférence Java", "Talk Quarkus", EventCategory.CONFERENCE, target, user);
        persistEvent("Conférence Python", "Talk Django", EventCategory.CONFERENCE, LocalDateTime.now().plusDays(10), user);
        persistEvent("Match de foot", "Tournoi", EventCategory.SPORTS, target, user);

        List<EventDTO> result = eventSearchService.search(
                "java",
                EventCategory.CONFERENCE,
                null,
                null,
                LocalDate.now(),
                LocalDate.now().plusDays(5),
                0, 20);

        assertEquals(1, result.size());
        assertEquals("Conférence Java", result.get(0).title());
    }

    // --- Filtre statut : les brouillons ne doivent jamais apparaître ---

    @Test
    @TestTransaction
    void search_draftEvent_isNotReturned() {
        deleteAll();
        User user = persistUser("auth0|draft1", "draft1@example.com");
        persistEvent("Événement publié", null, EventCategory.CONFERENCE, LocalDateTime.now().plusDays(1), user);
        Event draft = new Event();
        draft.title = "Brouillon secret";
        draft.location = "Uni Mail";
        draft.startDate = LocalDateTime.now().plusDays(2);
        draft.endDate = draft.startDate.plusHours(2);
        draft.category = EventCategory.ACADEMIC;
        draft.status = EventStatus.DRAFT;
        draft.creator = user;
        entityManager.persist(draft);
        entityManager.flush();

        List<EventDTO> result = eventSearchService.search(null, null, null, null, null, null, 0, 20);

        assertEquals(1, result.size());
        assertEquals("Événement publié", result.get(0).title());
    }

    // --- Aucun résultat → 200 + liste vide ---

    @Test
    @TestTransaction
    void search_noResults_returnsEmptyList() {
        deleteAll();
        User user = persistUser("auth0|s10", "s10@example.com");
        persistEvent("Conférence Java", null, EventCategory.CONFERENCE, LocalDateTime.now().plusDays(1), user);

        List<EventDTO> result = eventSearchService.search("xyzimpossible", null, null, null, null, null, 0, 20);

        assertTrue(result.isEmpty());
    }

    // --- Pagination ---

    @Test
    @TestTransaction
    void search_withPagination_returnsPage() {
        deleteAll();
        User user = persistUser("auth0|s11", "s11@example.com");
        for (int i = 1; i <= 5; i++) {
            persistEvent("Event " + i, null, EventCategory.ACADEMIC, LocalDateTime.now().plusDays(i), user);
        }

        List<EventDTO> page0 = eventSearchService.search(null, null, null, null, null, null, 0, 2);
        List<EventDTO> page1 = eventSearchService.search(null, null, null, null, null, null, 1, 2);
        List<EventDTO> page2 = eventSearchService.search(null, null, null, null, null, null, 2, 2);

        assertEquals(2, page0.size());
        assertEquals(2, page1.size());
        assertEquals(1, page2.size());
    }

    // --- Filtre faculty (SCRUM-77) ---

    @Test
    @TestTransaction
    void search_withFacultyFilter_returnsMatchingEvents() {
        deleteAll();
        User user = persistUser("auth0|sf1", "sf1@example.com");
        persistEvent("Labo Chimie", null, EventCategory.ACADEMIC, LocalDateTime.now().plusDays(1), user, Faculty.SCIENCES);
        persistEvent("Cours de Droit", null, EventCategory.ACADEMIC, LocalDateTime.now().plusDays(2), user, Faculty.LAW);

        List<EventDTO> result = eventSearchService.search(null, null, Faculty.SCIENCES, null, null, null, 0, 20);

        assertEquals(1, result.size());
        assertEquals(Faculty.SCIENCES, result.get(0).faculty());
    }

    @Test
    @TestTransaction
    void search_withFacultyAndCategory_combined() {
        deleteAll();
        User user = persistUser("auth0|sf2", "sf2@example.com");
        persistEvent("Conf Sciences", null, EventCategory.CONFERENCE, LocalDateTime.now().plusDays(1), user, Faculty.SCIENCES);
        persistEvent("Match Sciences", null, EventCategory.SPORTS, LocalDateTime.now().plusDays(2), user, Faculty.SCIENCES);
        persistEvent("Conf Droit", null, EventCategory.CONFERENCE, LocalDateTime.now().plusDays(3), user, Faculty.LAW);

        List<EventDTO> result = eventSearchService.search(null, EventCategory.CONFERENCE, Faculty.SCIENCES, null, null, null, 0, 20);

        assertEquals(1, result.size());
        assertEquals("Conf Sciences", result.get(0).title());
    }

    @Test
    @TestTransaction
    void search_withNullFaculty_returnsAll() {
        deleteAll();
        User user = persistUser("auth0|sf3", "sf3@example.com");
        persistEvent("Event A", null, EventCategory.ACADEMIC, LocalDateTime.now().plusDays(1), user, Faculty.SCIENCES);
        persistEvent("Event B", null, EventCategory.ACADEMIC, LocalDateTime.now().plusDays(2), user, null);

        List<EventDTO> result = eventSearchService.search(null, null, null, null, null, null, 0, 20);

        assertEquals(2, result.size());
    }

    @Test
    @TestTransaction
    void search_withFacultyNone_returnsNullFacultyEvents() {
        deleteAll();
        User user = persistUser("auth0|sfNone", "sfNone@example.com");
        persistEvent("Sciences Event", null, EventCategory.ACADEMIC, LocalDateTime.now().plusDays(1), user, Faculty.SCIENCES);
        persistEvent("No Faculty Event", null, EventCategory.ACADEMIC, LocalDateTime.now().plusDays(2), user, null);

        List<EventDTO> result = eventSearchService.search(null, null, null, true, null, null, 0, 20);

        assertEquals(1, result.size());
        assertNull(result.get(0).faculty());
        assertEquals("No Faculty Event", result.get(0).title());
    }

    @Test
    @TestTransaction
    void search_withFacultyNoneAndFaculty_facultyNoneWins() {
        deleteAll();
        User user = persistUser("auth0|sfNonePrio", "sfNonePrio@example.com");
        persistEvent("Sciences Event", null, EventCategory.ACADEMIC, LocalDateTime.now().plusDays(1), user, Faculty.SCIENCES);
        persistEvent("No Faculty Event", null, EventCategory.ACADEMIC, LocalDateTime.now().plusDays(2), user, null);

        List<EventDTO> result = eventSearchService.search(null, null, Faculty.SCIENCES, true, null, null, 0, 20);

        assertEquals(1, result.size());
        assertNull(result.get(0).faculty());
    }

    // --- helpers ---

    private void deleteAll() {
        entityManager.createNativeQuery("delete from events").executeUpdate();
        entityManager.createNativeQuery("delete from users").executeUpdate();
        entityManager.clear();
    }

    private User persistUser(String auth0Id, String email) {
        User user = new User();
        user.auth0Id = auth0Id;
        user.email = email;
        user.profilePublic = false;
        user.createdAt = LocalDateTime.now();
        entityManager.persist(user);
        entityManager.flush();
        return user;
    }

    private Event persistEvent(String title, String description, EventCategory category,
                                LocalDateTime startDate, User creator) {
        return persistEvent(title, description, category, startDate, creator, null);
    }

    private Event persistEvent(String title, String description, EventCategory category,
                                LocalDateTime startDate, User creator, Faculty faculty) {
        Event event = new Event();
        event.title = title;
        event.description = description;
        event.location = "Uni Mail";
        event.startDate = startDate;
        event.endDate = startDate.plusHours(2);
        event.category = category;
        event.status = EventStatus.PUBLISHED;
        event.creator = creator;
        event.faculty = faculty;
        entityManager.persist(event);
        entityManager.flush();
        return event;
    }
}
