# UNIGE Events API

[![Quality Gate Status](https://sonarcloud.io/api/project_badges/measure?project=unige-pinfo6-2026_unige-events-api&metric=alert_status&token=2b5a50d0d86698b04d4d626b4887a57750f003f8)](https://sonarcloud.io/summary/new_code?id=unige-pinfo6-2026_unige-events-api)

[![Coverage](https://sonarcloud.io/api/project_badges/measure?project=unige-pinfo6-2026_unige-events-api&metric=coverage&token=2b5a50d0d86698b04d4d626b4887a57750f003f8)](https://sonarcloud.io/summary/new_code?id=unige-pinfo6-2026_unige-events-api)

[![Bugs](https://sonarcloud.io/api/project_badges/measure?project=unige-pinfo6-2026_unige-events-api&metric=bugs&token=2b5a50d0d86698b04d4d626b4887a57750f003f8)](https://sonarcloud.io/summary/new_code?id=unige-pinfo6-2026_unige-events-api)
[![Code Smells](https://sonarcloud.io/api/project_badges/measure?project=unige-pinfo6-2026_unige-events-api&metric=code_smells&token=2b5a50d0d86698b04d4d626b4887a57750f003f8)](https://sonarcloud.io/summary/new_code?id=unige-pinfo6-2026_unige-events-api)
[![Duplicated Lines (%)](https://sonarcloud.io/api/project_badges/measure?project=unige-pinfo6-2026_unige-events-api&metric=duplicated_lines_density&token=2b5a50d0d86698b04d4d626b4887a57750f003f8)](https://sonarcloud.io/summary/new_code?id=unige-pinfo6-2026_unige-events-api)
[![Lines of Code](https://sonarcloud.io/api/project_badges/measure?project=unige-pinfo6-2026_unige-events-api&metric=ncloc&token=2b5a50d0d86698b04d4d626b4887a57750f003f8)](https://sonarcloud.io/summary/new_code?id=unige-pinfo6-2026_unige-events-api)

[![Reliability Rating](https://sonarcloud.io/api/project_badges/measure?project=unige-pinfo6-2026_unige-events-api&metric=reliability_rating&token=2b5a50d0d86698b04d4d626b4887a57750f003f8)](https://sonarcloud.io/summary/new_code?id=unige-pinfo6-2026_unige-events-api)
[![Security Rating](https://sonarcloud.io/api/project_badges/measure?project=unige-pinfo6-2026_unige-events-api&metric=security_rating&token=2b5a50d0d86698b04d4d626b4887a57750f003f8)](https://sonarcloud.io/summary/new_code?id=unige-pinfo6-2026_unige-events-api)
[![Maintainability Rating](https://sonarcloud.io/api/project_badges/measure?project=unige-pinfo6-2026_unige-events-api&metric=sqale_rating&token=2b5a50d0d86698b04d4d626b4887a57750f003f8)](https://sonarcloud.io/summary/new_code?id=unige-pinfo6-2026_unige-events-api)
[![Vulnerabilities](https://sonarcloud.io/api/project_badges/measure?project=unige-pinfo6-2026_unige-events-api&metric=vulnerabilities&token=2b5a50d0d86698b04d4d626b4887a57750f003f8)](https://sonarcloud.io/summary/new_code?id=unige-pinfo6-2026_unige-events-api)

[![Technical Debt](https://sonarcloud.io/api/project_badges/measure?project=unige-pinfo6-2026_unige-events-api&metric=sqale_index&token=2b5a50d0d86698b04d4d626b4887a57750f003f8)](https://sonarcloud.io/summary/new_code?id=unige-pinfo6-2026_unige-events-api)

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

### Run the project

http://localhost:8080/api
```bash
./mvnw quarkus:dev
```