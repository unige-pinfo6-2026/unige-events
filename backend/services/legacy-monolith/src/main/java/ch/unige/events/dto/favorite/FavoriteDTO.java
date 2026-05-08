package ch.unige.events.dto.favorite;

import ch.unige.events.entity.Favorite;

import java.time.LocalDateTime;
import java.util.UUID;

public record FavoriteDTO(
        Long id,
        UUID userId,
        Long eventId,
        LocalDateTime createdAt
) {
    public static FavoriteDTO from(Favorite favorite) {
        return new FavoriteDTO(
                favorite.id,
                favorite.userId,
                favorite.eventId,
                favorite.createdAt
        );
    }
}
