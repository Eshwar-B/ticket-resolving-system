# ==============================================================================
# Enterprise AI Platform — Developer Workflow Makefile
# ==============================================================================

.PHONY: help up-core up-ai up-obs up-tools down status logs test-infra migrate

help:
	@echo "Available commands:"
	@echo "  make up-core     Start Core infrastructure (Postgres, Redis, Kafka KRaft)"
	@echo "  make up-obs      Start Observability stack (OTel Collector)"
	@echo "  make down        Stop all platform containers"
	@echo "  make status      Inspect status and resource consumption"
	@echo "  make logs        View container logs"
	@echo "  make test-infra  Verify connectivity of Postgres, Redis, and Kafka"
	@echo "  make migrate     Execute Flyway database migrations"

up-core:
	docker-compose --profile core up -d

up-obs:
	docker-compose --profile obs up -d

up-tools:
	docker-compose --profile tools up -d

down:
	docker-compose --profile full down

status:
	docker-compose --profile core ps
	@echo "--- Docker Resource Stats ---"
	docker stats --no-stream

logs:
	docker-compose --profile core logs -f

test-infra:
	pwsh -File ./scripts/test-connectivity.ps1

migrate:
	docker-compose --profile tools run --rm flyway migrate

