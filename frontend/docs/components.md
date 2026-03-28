# docs/components.md — Pages, composants et services

## Pages

| Route | Composant | État |
|---|---|---|
| /home | HomePage | fait |
| /events/new | CreateEventPage | fait |
| /events/:id | EventDetailPage | fait |
| /events/:id/edit | EditEventPage | fait |

### HomePage

- Affiche l’utilisateur connecté via Avatar et useAuth().
- Intègre les liens rapides vers /events/new et /profile/me dans la carte de bienvenue.
- Liste les événements publiés via useEvents() et EventCard.
- Gère les états loading, error et empty.
- Propose un bouton Charger plus quand hasMore est vrai.

### EventDetailPage

- Charge l’événement via useEvent(id).
- Affiche la bannière, la catégorie, le titre, les dates, le lieu, la capacité et la description.
- Charge l’organisateur via getUserById(event.creatorId).
- Affiche Modifier et Supprimer uniquement pour l’organisateur.
- Ouvre une confirmation avant deleteEvent(id) puis redirige vers /home.
- Utilise une UI localisée en français.

### CreateEventPage

- Réutilise EventForm via useEventForm en mode create.
- Crée un événement via createEvent().
- Permet un statut initial DRAFT ou PUBLISHED.
- Bloque la soumission si la date de début est dans le passé.
- Upload une bannière optionnelle puis redirige vers la page détail.
- Affiche un toast de succès ou d’erreur.

### EditEventPage

- Recharge l’événement via getById(id).
- Réutilise EventForm via useEventForm en mode edit.
- Envoie un payload complet compatible avec le PUT backend via updateEvent(id, data), en conservant aussi le bannerUrl existant tant qu'une nouvelle image n'est pas envoyée.
- Réutilise les validations de formulaire, dont la date de début future.
- Remplace la bannière via uploadEventImage(id, file).
- Affiche un toast puis redirige vers /events/:id.

## Composants réutilisables

### EventCard

- Carte cliquable d’un événement pour la HomePage.
- Affiche les informations synthétiques utiles sans dupliquer la logique de détail.

### EventForm

- Formulaire partagé entre création et édition.
- Centralise les champs titre, description, lieu, dates, catégorie, capacité, statut et bannière.
- Garde le placeholder et l’aperçu de bannière contenus proprement dans la carte, y compris sur mobile et avec des noms de fichiers longs.
- Reçoit ses valeurs, erreurs et callbacks depuis useEventForm.

### Avatar

- Affiche soit une image soit des initiales à partir de displayName.
- Réutilisé dans la navigation, la HomePage, les profils et la page détail événement.

## Hooks

### useEvents

- Charge les événements publiés par pages de 12.
- Retourne events, loading, error, hasMore et loadMore.

### useEvent

- Charge un événement unique à partir de son identifiant.
- Retourne event, loading et error.

### useEventForm

- Centralise l’état du formulaire, la validation, l’aperçu local de bannière et la soumission.
- Valide les champs requis, l’ordre des dates, la capacité positive et la date de début dans le futur.
- En création, envoie le statut initial choisi au backend.
- En édition, envoie un payload complet pour rester cohérent avec le PUT documenté, y compris le bannerUrl déjà présent.
- Traduit les erreurs backend techniques en messages français plus utiles, tout en réutilisant les détails de validation quand ils sont disponibles.
- Après upload de bannière, réutilise l’événement retourné par l’API.

## Services

### eventApi.ts

- getAll(params) : liste paginée d’événements.
- getById(id) : détail d’un événement.
- createEvent(data) : création d’événement.
- updateEvent(id, data) : mise à jour d’événement.
- uploadEventImage(id, file) : upload de bannière et retour de l’événement mis à jour.
- deleteEvent(id) : annulation soft-delete d’un événement.
