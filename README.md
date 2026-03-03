# UNIGE Events API

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
- Maven

### Setup environment variables
- Copy `.env.example` to `.env` and fill the variables.

### Setup database

```bash
docker compose up -d db
```

### Run the project

http://localhost:8080/api
```bash
./mvnw quarkus:dev
```