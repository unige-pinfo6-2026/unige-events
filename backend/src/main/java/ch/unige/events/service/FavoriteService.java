package ch.unige.events.service;

import ch.unige.events.dto.event.EventDTO;
import ch.unige.events.entity.Event;
import ch.unige.events.entity.Favorite;
import ch.unige.events.entity.User;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.NotFoundException;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
public class FavoriteService {

    @Transactional
    public void addFavorite(String auth0Id, Long eventId) {
        // Vérifier que l'événement existe
        Event.<Event>findByIdOptional(eventId)
                .orElseThrow(() -> new NotFoundException("Event not found"));

        UUID userId = resolveUserId(auth0Id);

        // Idempotent : ne rien faire si le favori existe déjà
        boolean alreadyExists = Favorite.findByUserAndEvent(userId, eventId).isPresent();
        if (alreadyExists) {
            return;
        }

        Favorite favorite = new Favorite();
        favorite.userId = userId;
        favorite.eventId = eventId;
        favorite.persist();
    }

    @Transactional
    public void removeFavorite(String auth0Id, Long eventId) {
        UUID userId = resolveUserId(auth0Id);

        Favorite favorite = Favorite.findByUserAndEvent(userId, eventId)
                .orElseThrow(() -> new NotFoundException("Favorite not found"));

        favorite.delete();
    }

    @Transactional
    public List<EventDTO> getFavorites(String auth0Id, int page, int size) {
        UUID userId = resolveUserId(auth0Id);

        return Favorite.findByUser(userId, page, size).stream()
                .map(f -> Event.<Event>findByIdOptional(f.eventId))
                .flatMap(Optional::stream)
                .map(EventDTO::from)
                .toList();
    }

    private UUID resolveUserId(String auth0Id) {
        return User.<User>find("auth0Id", auth0Id)
                .firstResultOptional()
                .orElseThrow(() -> new NotFoundException("User profile not found"))
                .id;
    }
}
