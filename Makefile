SERVICES = event user engagement moderation notification

.PHONY: install-backend install-frontend install \
        frontend backend dev $(SERVICES) \
        test-backend test-frontend test \
        build-backend build-frontend

MODE = development

# ─── Install ────────────────────────────────────────────────────────────────
# Installe les shared libs dans le dépôt local Maven (~/.m2).
# À lancer une fois avant make <service> ou make backend.
install-backend:
	cd backend && ./mvnw -f shared/pom.xml install -DskipTests -B -q

install-frontend:
	cd frontend && npm install

install: install-frontend install-backend

# ─── Dev — services individuels ─────────────────────────────────────────────
# make event | make user | make engagement | make moderation | make notification
$(SERVICES):
	cd backend && ./mvnw quarkus:dev -pl services/$@-service -am

# ─── Dev — tous les services ─────────────────────────────────────────────────
backend:
	$(MAKE) -j$(words $(SERVICES)) $(SERVICES)

frontend:
	cd frontend && npm run dev -- --mode $(MODE)

dev:
	$(MAKE) -j2 frontend backend

# ─── Tests ──────────────────────────────────────────────────────────────────
test-backend:
	cd backend && ./mvnw install -B

test-frontend: install-frontend
	cd frontend && npm run lint && npm run test:coverage && npm run build

test: test-backend test-frontend

# ─── Build ──────────────────────────────────────────────────────────────────
build-backend:
	cd backend && ./mvnw clean package -B

build-frontend:
	cd frontend && npm run build -- --mode $(MODE)

.DEFAULT_GOAL := install
