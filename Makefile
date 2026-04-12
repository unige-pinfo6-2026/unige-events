.PHONY: frontend backend install-frontend install-backend install test-frontend test-backend test

# Installations
install-backend:
	cd backend && ./mvnw clean dependency:resolve

install-frontend:
	cd frontend && npm install

install-playwright-deps:
	cd frontend && npx playwright install-deps chromium

install: install-frontend install-backend install-playwright-deps

# Dev
backend: install-backend
	cd backend && ./mvnw clean quarkus:dev

frontend: install-frontend
	cd frontend && npm run dev

dev:
	$(MAKE) -j2 frontend backend

# Tests
test-backend:
	cd backend && ./mvnw clean verify -B

test-frontend:
	cd frontend && npm run lint && npm run test:coverage && npm run build

test: test-backend test-frontend

.DEFAULT_GOAL := install