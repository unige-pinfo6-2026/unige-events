# docs/types.md — Types TypeScript

Source de vérité frontend : src/types/index.ts.
Source de vérité contrat API : docs/openapi/openapi.yaml.

## Types événements

### EventCategory

Valeurs supportées : ACADEMIC, SPORTS, CULTURAL, SOCIAL, CONFERENCE, OTHER.

### EventStatus

Valeurs supportées : DRAFT, PUBLISHED, CANCELLED.

### Event

Champs principaux :
- id : number
- title : string
- description : string optionnel
- location : string
- startDate : string ISO 8601
- endDate : string ISO 8601
- category : EventCategory
- bannerUrl : string optionnel
- creatorId : string
- status : EventStatus
- capacity : number optionnel
- createdAt : string
- updatedAt : string optionnel

### CreateEventRequest

Champs requis : title, location, startDate, endDate, category.
Champs optionnels : description, bannerUrl, capacity, status.
Le status initial peut être DRAFT ou PUBLISHED. Le frontend expose ces deux choix dans EventForm.

### UpdateEventRequest

Le backend documente un PUT à sémantique de remplacement complet.
Le frontend envoie donc systématiquement un payload complet en édition avec :
- title
- location
- startDate
- endDate
- category
et, si nécessaire :
- description
- bannerUrl
- capacity
- status

## Règles générales

- Les champs restent en camelCase exactement comme dans le backend.
- Les booléens backend ne prennent pas de préfixe is sauf si le backend le fait réellement.
- Les types d’entités vivent dans src/types/index.ts et ne doivent pas être redéfinis ailleurs.
- Le frontend ne doit pas utiliser any.
