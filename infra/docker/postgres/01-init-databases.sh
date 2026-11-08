#!/bin/bash
set -e

# ==============================================================================
# Enterprise AI Platform: Multi-Database & Role Provisioning Script
# Executed automatically by the official postgres entrypoint on initial init.
# Service isolation invariant: Each domain service owns its logical database.
# ==============================================================================

echo ">>> Initializing Enterprise AI Platform service databases and roles..."

# Create non-superuser application role for Row-Level Security enforcement
psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" <<-EOSQL
    CREATE ROLE app_user WITH LOGIN PASSWORD 'app_user_password';
    GRANT ALL PRIVILEGES ON SCHEMA public TO app_user;
EOSQL

# List of microservice databases to provision
DATABASES=(
    "identity_db"
    "ticket_db"
    "knowledge_db"
    "workflow_db"
    "notification_db"
    "usage_db"
    "ai_db"
    "keycloak_db"
)

for db in "${DATABASES[@]}"; do
    echo ">>> Creating database: $db"
    psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" <<-EOSQL
        SELECT 'CREATE DATABASE $db'
        WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = '$db')\gexec
        GRANT ALL PRIVILEGES ON DATABASE $db TO $POSTGRES_USER;
        GRANT ALL PRIVILEGES ON DATABASE $db TO app_user;
EOSQL
done

echo ">>> All service databases and roles successfully provisioned."

