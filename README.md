# UNIGE Events

Centralized event platform for the UNIGE community : discover, create and manage university events.

![Bannière UNIGE](./docs/assets/unige_banner.png)

## Group 6
- [Elie Bussod](https://github.com/nexiumito)
- [Viona Cufo](https://github.com/vionacufo)
- [Daniel Dosh](https://github.com/DanyDosh)
- [Agon Kolgeci](https://github.com/agonkolgeci)
- [Antoine Maendly](https://github.com/antoinemdly)

---

### Prerequisites
- Java 21+
- Node.js 18+
- Maven

### Setup environment variables
- Copy `.env.example` to `.env` and fill the variables. In `backend/` and `frontend/` too.

### Setup database

```bash
docker compose up -d db
```

### Run the project

**Backend**: http://localhost:8080
```bash
cd backend && ./mvnw quarkus:dev
```

**Frontend**: http://localhost:5173
```bash
cd frontend && npm install && npm run dev
```