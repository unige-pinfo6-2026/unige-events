SERVICES = event user engagement moderation notification

.PHONY: install-backend install-frontend install \
        frontend backend dev $(addprefix backend-,$(SERVICES)) \
        test-backend test-frontend test \
        build-backend build-frontend $(addprefix build-backend-,$(SERVICES))

MODE = development

# ─── Install ────────────────────────────────────────────────────────────────
install-backend:
	cd backend && ./mvnw install -DskipTests

install-frontend:
	cd frontend && npm install

install: install-frontend install-backend

# ─── Dev — services individuels ─────────────────────────────────────────────
# make backend-event | make backend-user | make backend-engagement | ...
$(addprefix backend-,$(SERVICES)):
	cd backend && ./mvnw quarkus:dev -pl services/$(patsubst backend-%,%,$@)-service -am

# ─── Dev — tous les services ─────────────────────────────────────────────────
backend:
	$(MAKE) -j$(words $(SERVICES)) $(addprefix backend-,$(SERVICES))

frontend:
	cd frontend && npm run dev -- --mode $(MODE)

dev:
	$(MAKE) -j2 frontend backend

# ─── Tests ──────────────────────────────────────────────────────────────────
test-backend:
	cd backend && ./mvnw verify

test-frontend: install-frontend
	cd frontend && npm run lint && npm run test:coverage && npm run build

test: test-backend test-frontend

# ─── Build ──────────────────────────────────────────────────────────────────
# make build-backend-event | make build-backend-user | ...
$(addprefix build-backend-,$(SERVICES)):
	cd backend && ./mvnw clean package -pl services/$(patsubst build-backend-%,%,$@)-service -am

build-backend:
	cd backend && ./mvnw clean package

build-frontend:
	cd frontend && npm run build -- --mode $(MODE)

.DEFAULT_GOAL := install
