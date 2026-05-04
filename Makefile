.PHONY: frontend backend install-frontend install-backend install test-frontend test-backend test

MODE=dev

# Installations
install-backend:
	cd backend && ./mvnw clean dependency:resolve

install-frontend:
	cd frontend && npm install

install: install-frontend install-backend

# Dev
backend:
	cd backend && ./mvnw clean quarkus:dev

frontend:
	cd frontend && npm run dev

dev:
	$(MAKE) -j2 frontend backend

# Tests
test-backend:
	cd backend && ./mvnw test -B

test-frontend: install-frontend
	cd frontend && npm run lint && npm run test:coverage && npm run build

test: test-backend test-frontend

# Build
build-backend:
	cd backend && ./mvnw clean package -B

build-frontend:
	cd frontend && npm run build -- --mode $(MODE)

.DEFAULT_GOAL := install