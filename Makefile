.PHONY: frontend backend install-frontend install-backend install test-frontend test-backend test

# Installations
install-backend:
	cd backend && ./mvnw dependency:resolve

install-frontend:
	cd frontend && npm install

install: install-frontend install-backend

# Dev
backend:
	cd backend && ./mvnw quarkus:dev

frontend:
	cd frontend && npm run dev

dev:
	$(MAKE) -j2 frontend backend

# Tests
test-backend:
	cd backend && ./mvnw verify -B

test-frontend:
	cd frontend && npm run test:coverage

test: test-backend test-frontend

.DEFAULT_GOAL := install