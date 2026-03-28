.PHONY: frontend backend

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

.DEFAULT_GOAL := install