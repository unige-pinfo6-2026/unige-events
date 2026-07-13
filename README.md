# 📅 [UNIGE Events](https://pinfo6.p-info.net/)

> [!NOTE]
> **No live preview available here?** Watch a quick demo of the platform in action instead: **[▶️ YouTube Demo](https://youtu.be/Q7SBkecQm-0)**

Centralized event platform for the UNIGE community : discover, create and manage university events.

[![CI/CD Pipeline](https://github.com/unige-pinfo6-2026/unige-events/actions/workflows/ci-cd.yml/badge.svg)](https://github.com/unige-pinfo6-2026/unige-events/actions/workflows/ci-cd.yml)
[![Production Deployment](https://img.shields.io/github/deployments/unige-pinfo6-2026/unige-events/production?label=Production&logo=github)](https://pinfo6.p-info.net/)
[![Preview Deployments](https://img.shields.io/github/deployments/unige-pinfo6-2026/unige-events/preview?label=Preview&logo=github)](https://github.com/unige-pinfo6-2026/unige-events/pulls)
[![Ask DeepWiki](https://deepwiki.com/badge.svg)](https://deepwiki.com/unige-pinfo6-2026/unige-events)

![Bannière UNIGE](./docs/assets/unige_banner.png)

---

## 📊 Quality & Code Metrics

To ensure high-quality standards, the project is analyzed by **SonarCloud**. The table below summarizes the key metrics for both our Frontend and Backend applications:

| Metric | Backend | Frontend |
| :--- | :---: | :---: |
| **Quality Gate** | [![Quality Gate Status](https://sonarcloud.io/api/project_badges/measure?project=unige-pinfo6-2026_unige-events-backend&metric=alert_status&token=6e0a9f64ae6b4e1930eb93f39ad99b45d53437c7)](https://sonarcloud.io/summary/new_code?id=unige-pinfo6-2026_unige-events-backend) | [![Quality Gate Status](https://sonarcloud.io/api/project_badges/measure?project=unige-pinfo6-2026_unige-events-frontend&metric=alert_status&token=f20bd39fb9ab102440acacca97ddb3926adb8552)](https://sonarcloud.io/summary/new_code?id=unige-pinfo6-2026_unige-events-frontend) |
| **Coverage** | [![Coverage](https://sonarcloud.io/api/project_badges/measure?project=unige-pinfo6-2026_unige-events-backend&metric=coverage&token=6e0a9f64ae6b4e1930eb93f39ad99b45d53437c7)](https://sonarcloud.io/summary/new_code?id=unige-pinfo6-2026_unige-events-backend) | [![Coverage](https://sonarcloud.io/api/project_badges/measure?project=unige-pinfo6-2026_unige-events-frontend&metric=coverage&token=f20bd39fb9ab102440acacca97ddb3926adb8552)](https://sonarcloud.io/summary/new_code?id=unige-pinfo6-2026_unige-events-frontend) |
| **Bugs** | [![Bugs](https://sonarcloud.io/api/project_badges/measure?project=unige-pinfo6-2026_unige-events-backend&metric=bugs&token=6e0a9f64ae6b4e1930eb93f39ad99b45d53437c7)](https://sonarcloud.io/summary/new_code?id=unige-pinfo6-2026_unige-events-backend) | [![Bugs](https://sonarcloud.io/api/project_badges/measure?project=unige-pinfo6-2026_unige-events-frontend&metric=bugs&token=f20bd39fb9ab102440acacca97ddb3926adb8552)](https://sonarcloud.io/summary/new_code?id=unige-pinfo6-2026_unige-events-frontend) |
| **Code Smells** | [![Code Smells](https://sonarcloud.io/api/project_badges/measure?project=unige-pinfo6-2026_unige-events-backend&metric=code_smells&token=6e0a9f64ae6b4e1930eb93f39ad99b45d53437c7)](https://sonarcloud.io/summary/new_code?id=unige-pinfo6-2026_unige-events-backend) | [![Code Smells](https://sonarcloud.io/api/project_badges/measure?project=unige-pinfo6-2026_unige-events-frontend&metric=code_smells&token=f20bd39fb9ab102440acacca97ddb3926adb8552)](https://sonarcloud.io/summary/new_code?id=unige-pinfo6-2026_unige-events-frontend) |
| **Vulnerabilities** | [![Vulnerabilities](https://sonarcloud.io/api/project_badges/measure?project=unige-pinfo6-2026_unige-events-backend&metric=vulnerabilities&token=6e0a9f64ae6b4e1930eb93f39ad99b45d53437c7)](https://sonarcloud.io/summary/new_code?id=unige-pinfo6-2026_unige-events-backend) | [![Vulnerabilities](https://sonarcloud.io/api/project_badges/measure?project=unige-pinfo6-2026_unige-events-frontend&metric=vulnerabilities&token=f20bd39fb9ab102440acacca97ddb3926adb8552)](https://sonarcloud.io/summary/new_code?id=unige-pinfo6-2026_unige-events-frontend) |
| **Technical Debt** | [![Technical Debt](https://sonarcloud.io/api/project_badges/measure?project=unige-pinfo6-2026_unige-events-backend&metric=sqale_index&token=6e0a9f64ae6b4e1930eb93f39ad99b45d53437c7)](https://sonarcloud.io/summary/new_code?id=unige-pinfo6-2026_unige-events-backend) | [![Technical Debt](https://sonarcloud.io/api/project_badges/measure?project=unige-pinfo6-2026_unige-events-frontend&metric=sqale_index&token=f20bd39fb9ab102440acacca97ddb3926adb8552)](https://sonarcloud.io/summary/new_code?id=unige-pinfo6-2026_unige-events-frontend) |

---

## 🏛️ Architecture

This project is structured as a monorepo containing both the user-facing web application and the distributed backend services.

```
unige-events/
├── frontend/        # React 19 · TypeScript · Vite · Nginx
├── backend/         # Java 21 · Quarkus 3 · PostgreSQL 16 · Kong
├── helm/            # Kubernetes Helm Charts for deployment
└── openapi/         # OpenAPI contracts (Shared Source of Truth)
```

### 💻 Frontend

![React](https://img.shields.io/badge/React-19.2.0-61DAFB?style=for-the-badge&logo=react&logoColor=black) ![Vite](https://img.shields.io/badge/Vite-8.0.1-646CFF?style=for-the-badge&logo=vite&logoColor=white) ![NodeJS](https://img.shields.io/badge/Node.js-24-339933?style=for-the-badge&logo=nodedotjs&logoColor=white) ![NPM](https://img.shields.io/badge/NPM-11.11.0-CB3837?style=for-the-badge&logo=npm&logoColor=white)

* **Stack**: React 19 · TypeScript · Vite
* **Scope**: Responsive event discovery web portal, moderation workspace, and interactive user dashboard.
* For setup & guidelines, see [`frontend/AGENTS.md`](frontend/AGENTS.md).

### ⚙️ Backend

![Java](https://img.shields.io/badge/Java-21-ED8B00?style=for-the-badge&logo=openjdk&logoColor=white) ![Quarkus](https://img.shields.io/badge/Quarkus-3.24.4-4695EB?style=for-the-badge&logo=quarkus&logoColor=white)

* **Stack**: Quarkus 3 · Java 21 · Hibernate Panache · PostgreSQL 16
* **Architecture**: DB-per-service microservices routing through **Kong API Gateway**.
* **Active Services**:
  * `event-service` - Event lifecycle and categorization.
  * `user-service` - User profiles, authentication, and registrations.
  * `engagement-service` - User interactions, reviews, registrations.
  * `moderation-service` - Content approval workflows.
  * `notification-service` - Automated email notifications.
* For microservice conventions, see [`backend/AGENTS.md`](backend/AGENTS.md).

---

## 🚀 Getting Started

> [!IMPORTANT]
> **Mandatory Environment**: The use of **Dev Containers** is mandatory for all developers working on this project. This ensures a consistent workspace with all dependencies and tools (Java 21, Node 24, Docker, databases, Kong) pre-configured.

### 🐳 Setting Up Your Dev Container

1. Ensure you have **Docker** and **VS Code** installed.
2. Install the **Dev Containers** extension in VS Code.
3. Open the project root folder in VS Code.
4. When prompted in the bottom right, click **"Reopen in Container"** (or open the Command Palette `Cmd+Shift+P`/`Ctrl+Shift+P` and select `Dev Containers: Reopen in Container`).
5. Wait for the container to build.

> [!NOTE]
> Dependencies for both frontend and backend are automatically installed on container creation (runs `make install` under the hood).

---

### 🛠️ Development & Command Reference

Once your container is running, you can manage the entire application using the provided root [Makefile](file:///Users/agon/Development/UNIGE/PINFO/unige-events/Makefile):

| Command | Action | URL / Port |
| :--- | :--- | :--- |
| `make dev` | Starts **both** the Frontend and Backend concurrently | - |
| `make frontend` | Starts **only** the React frontend server | [http://localhost:5173/](http://localhost:5173/) |
| `make backend` | Starts **all 5** Quarkus backend microservices in parallel | [http://localhost:8080/api](http://localhost:8080/api) |
| `make test` | Runs all linters and test suites (Frontend + Backend) | - |

#### Running Specific Backend Services
If you want to run a single backend microservice at a time (e.g., to save RAM or focus on one service), use:
```bash
make backend-<service-name>
```
*Available services: `event`, `user`, `engagement`, `moderation`, `notification`.*

*Example: `make backend-event`*

---

## 👥 Group 6 Team

* [Agon Kolgeci](https://github.com/agonkolgeci)
* [Antoine Maendly](https://github.com/antoinemdly)
* [Daniel Dosh](https://github.com/DanyDosh)
* [Elie Bussod](https://github.com/nexiumito)
* [Viona Cufo](https://github.com/vionacufo)

## 📄 License & Policies

* **License**: This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.
* **Contributing**: As this is an academic university project, we do not accept external contributions. Please see the [CONTRIBUTING](CONTRIBUTING.md) guidelines for more information.
* **Code of Conduct**: We are committed to a welcoming and respectful environment. Please see our [Code of Conduct](CODE_OF_CONDUCT.md).