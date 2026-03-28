# docs/sprint-context.md — État d'avancement

Dernière mise à jour : 2026-03-27

## Sprint 1 — Authentification & profils

Terminé.

## Sprint 2 — Consultation & gestion des événements

Terminé.

Fonctionnalités livrées :
- HomePage avec liste paginée des événements publiés, états loading/error/empty et liens rapides.
- EventCard réutilisable pour la liste.
- EventDetailPage riche avec organisateur, actions Modifier/Supprimer réservées au créateur et modal de confirmation.
- CreateEventPage et EditEventPage basées sur EventForm.
- useEvents, useEvent et useEventForm.
- eventApi unifié pour liste, détail, création, édition, annulation et upload de bannière.
- Types Event, EventCategory, EventStatus, CreateEventRequest et UpdateEventRequest dans src/types/index.ts.

Points de cohérence importants conservés après merge :
- Le browsing d’événements déjà intégré sur main reste intact.
- Les flux create/edit/upload du formulaire sont conservés.
- Le statut initial peut être envoyé dès la création pour s’aligner sur le contrat backend.
- L’édition envoie un payload complet pour respecter la sémantique PUT documentée.

Suite prévue :
- Recherche et filtres avancés.
- Vue calendrier.
- Extraction de composants génériques de loading et d’erreur.
