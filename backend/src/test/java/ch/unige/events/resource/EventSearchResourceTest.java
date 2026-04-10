package ch.unige.events.resource;

import ch.unige.events.entity.EventCategory;
import ch.unige.events.entity.Faculty;
import ch.unige.events.service.EventSearchServiceMock;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

@QuarkusTest
class EventSearchResourceTest {

    @Inject
    EventSearchServiceMock eventSearchServiceMock;

    @BeforeEach
    void setUp() {
        eventSearchServiceMock.reset();
    }

    // --- GET /events/search (sans filtre) ---

    @Test
    void search_noParams_returns200WithAll() {
        eventSearchServiceMock.seedEvent("Conférence Java", "Talk sur Quarkus", EventCategory.CONFERENCE, null);
        eventSearchServiceMock.seedEvent("Match de foot", "Tournoi inter-facs", EventCategory.SPORTS, null);

        given()
                .when().get("/events/search")
                .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .body("", hasSize(2));
    }

    // --- Filtre ?q= (ILIKE title) ---

    @Test
    void search_withQ_matchesTitle() {
        eventSearchServiceMock.seedEvent("Conférence Java", "Talk générique", EventCategory.CONFERENCE, null);
        eventSearchServiceMock.seedEvent("Match de foot", "Tournoi inter-facs", EventCategory.SPORTS, null);

        given()
                .queryParam("q", "java")
                .when().get("/events/search")
                .then()
                .statusCode(200)
                .body("", hasSize(1))
                .body("[0].title", is("Conférence Java"));
    }

    @Test
    void search_withQ_matchesDescription() {
        // Vérifie que l'ILIKE cherche aussi dans description
        eventSearchServiceMock.seedEvent("Conférence Tech", "Talk sur Quarkus et Java", EventCategory.CONFERENCE, null);
        eventSearchServiceMock.seedEvent("Match de foot", "Tournoi inter-facs", EventCategory.SPORTS, null);

        given()
                .queryParam("q", "quarkus")
                .when().get("/events/search")
                .then()
                .statusCode(200)
                .body("", hasSize(1))
                .body("[0].title", is("Conférence Tech"));
    }

    @Test
    void search_withQ_isCaseInsensitive() {
        eventSearchServiceMock.seedEvent("Conférence JAVA", null, EventCategory.CONFERENCE, null);

        given()
                .queryParam("q", "java")   // minuscule → doit trouver "JAVA"
                .when().get("/events/search")
                .then()
                .statusCode(200)
                .body("", hasSize(1));
    }

    // --- Filtre ?category= ---

    @Test
    void search_withCategory_returnsFiltered() {
        eventSearchServiceMock.seedEvent("Conférence Java", null, EventCategory.CONFERENCE, null);
        eventSearchServiceMock.seedEvent("Match de foot", null, EventCategory.SPORTS, null);

        given()
                .queryParam("category", "SPORTS")
                .when().get("/events/search")
                .then()
                .statusCode(200)
                .body("", hasSize(1))
                .body("[0].category", is("SPORTS"));
    }

    // --- Filtre ?dateFrom= ---

    @Test
    void search_withDateFrom_excludesPastEvents() {
        LocalDateTime past = LocalDateTime.now().minusDays(5);
        LocalDateTime future = LocalDateTime.now().plusDays(5);
        eventSearchServiceMock.seedEvent("Événement passé", null, EventCategory.ACADEMIC, past);
        eventSearchServiceMock.seedEvent("Événement futur", null, EventCategory.ACADEMIC, future);

        given()
                .queryParam("dateFrom", LocalDate.now().toString())
                .when().get("/events/search")
                .then()
                .statusCode(200)
                .body("", hasSize(1))
                .body("[0].title", is("Événement futur"));
    }

    // --- Filtre ?dateTo= ---

    @Test
    void search_withDateTo_excludesFutureEvents() {
        LocalDateTime past = LocalDateTime.now().minusDays(5);
        LocalDateTime future = LocalDateTime.now().plusDays(5);
        eventSearchServiceMock.seedEvent("Événement passé", null, EventCategory.ACADEMIC, past);
        eventSearchServiceMock.seedEvent("Événement futur", null, EventCategory.ACADEMIC, future);

        given()
                .queryParam("dateTo", LocalDate.now().toString())
                .when().get("/events/search")
                .then()
                .statusCode(200)
                .body("", hasSize(1))
                .body("[0].title", is("Événement passé"));
    }

    // --- Combinaison de filtres ---

    @Test
    void search_withAllFilters_returnsOnlyMatch() {
        LocalDateTime target = LocalDateTime.now().plusDays(3);
        eventSearchServiceMock.seedEvent("Conférence Java", "Talk Quarkus", EventCategory.CONFERENCE, target);
        eventSearchServiceMock.seedEvent("Conférence Python", "Talk Django", EventCategory.CONFERENCE,
                LocalDateTime.now().plusDays(10));  // hors plage dateTo
        eventSearchServiceMock.seedEvent("Match de foot", "Tournoi", EventCategory.SPORTS, target);  // mauvaise catégorie

        given()
                .queryParam("q", "java")
                .queryParam("category", "CONFERENCE")
                .queryParam("dateFrom", LocalDate.now().toString())
                .queryParam("dateTo", LocalDate.now().plusDays(5).toString())
                .when().get("/events/search")
                .then()
                .statusCode(200)
                .body("", hasSize(1))
                .body("[0].title", is("Conférence Java"));
    }

    // --- Aucun résultat ---

    @Test
    void search_noResults_returns200EmptyList() {
        // 200 avec tableau vide — jamais 404 même si 0 résultats
        eventSearchServiceMock.seedEvent("Conférence Java", null, EventCategory.CONFERENCE, null);

        given()
                .queryParam("q", "xyzresultatimpossible")
                .when().get("/events/search")
                .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .body("", hasSize(0));
    }

    // --- q blanc → ignoré ---

    @Test
    void search_blankQ_treatedAsNoFilter() {
        eventSearchServiceMock.seedEvent("Conférence Java", null, EventCategory.CONFERENCE, null);
        eventSearchServiceMock.seedEvent("Match de foot", null, EventCategory.SPORTS, null);

        given()
                .queryParam("q", "   ")   // blancs → doit retourner tout
                .when().get("/events/search")
                .then()
                .statusCode(200)
                .body("", hasSize(2));
    }

    // --- Pagination ---

    @Test
    void search_withPageSize_returnsPaginatedResults() {
        for (int i = 1; i <= 5; i++) {
            eventSearchServiceMock.seedEvent("Événement " + i, null, EventCategory.ACADEMIC, null);
        }

        given()
                .queryParam("page", 0)
                .queryParam("size", 2)
                .when().get("/events/search")
                .then()
                .statusCode(200)
                .body("", hasSize(2));
    }

    // --- Filtre ?faculty= ---

    @Test
    void search_withFaculty_returnsFiltered() {
        var e1 = eventSearchServiceMock.seedEvent("Labo Chimie", null, EventCategory.ACADEMIC, null);
        e1.faculty = Faculty.SCIENCES;
        var e2 = eventSearchServiceMock.seedEvent("Cours de Droit", null, EventCategory.ACADEMIC, null);
        e2.faculty = Faculty.DROIT;

        given()
                .queryParam("faculty", "SCIENCES")
                .when().get("/events/search")
                .then()
                .statusCode(200)
                .body("", hasSize(1))
                .body("[0].title", is("Labo Chimie"))
                .body("[0].faculty", is("SCIENCES"));
    }

    @Test
    void search_withFacultyNoMatch_returnsEmpty() {
        var e = eventSearchServiceMock.seedEvent("Labo Chimie", null, EventCategory.ACADEMIC, null);
        e.faculty = Faculty.SCIENCES;

        given()
                .queryParam("faculty", "DROIT")
                .when().get("/events/search")
                .then()
                .statusCode(200)
                .body("", hasSize(0));
    }

    @Test
    void search_withFacultyAndQ_combined() {
        var e1 = eventSearchServiceMock.seedEvent("Conférence Java", null, EventCategory.CONFERENCE, null);
        e1.faculty = Faculty.SCIENCES;
        var e2 = eventSearchServiceMock.seedEvent("Conférence Java", null, EventCategory.CONFERENCE, null);
        e2.faculty = Faculty.LETTRES;

        given()
                .queryParam("q", "java")
                .queryParam("faculty", "SCIENCES")
                .when().get("/events/search")
                .then()
                .statusCode(200)
                .body("", hasSize(1))
                .body("[0].faculty", is("SCIENCES"));
    }

    @Test
    void search_withNullFaculty_returnsAll() {
        var e1 = eventSearchServiceMock.seedEvent("Event A", null, EventCategory.ACADEMIC, null);
        e1.faculty = Faculty.SCIENCES;
        eventSearchServiceMock.seedEvent("Event B", null, EventCategory.ACADEMIC, null);

        given()
                .when().get("/events/search")
                .then()
                .statusCode(200)
                .body("", hasSize(2));
    }
}
