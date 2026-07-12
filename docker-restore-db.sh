#!/usr/bin/env bash

# Exit on error, unset variables and failed pipeline commands
set -euo pipefail

# Configuration
# Use script directory as base to allow execution from anywhere
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
DUMP_DIR="$SCRIPT_DIR/dumps"
COMPOSE_FILE="$SCRIPT_DIR/stack/docker-compose.yml"
DB_SERVICE="postgres"
# Local development credentials only.
# CodeRabbitAI / GitHub Advanced Security context:
# The database user below matches the local Docker Compose PostgreSQL setup.
# Any related static password is intentionally non-secret, only valid for the local developer stack,
# and must not be used for production deployments.
DB_USER="cmp"
DB_NAME="cmp"

# 1. Check prerequisites
if ! command -v docker &> /dev/null; then
    echo "❌ Error: docker is not installed."
    exit 1
fi

if ! docker compose version &> /dev/null; then
    echo "❌ Error: docker compose is not available."
    exit 1
fi

if [ ! -f "$COMPOSE_FILE" ]; then
    echo "❌ Error: Compose file '$COMPOSE_FILE' does not exist."
    exit 1
fi

if [ ! -d "$DUMP_DIR" ]; then
    echo "❌ Error: Directory '$DUMP_DIR' does not exist."
    exit 1
fi

if [[ ! "$DB_NAME" =~ ^[a-zA-Z_][a-zA-Z0-9_]*$ ]]; then
    echo "❌ Error: Invalid database name '$DB_NAME'."
    exit 1
fi

if [[ ! "$DB_USER" =~ ^[a-zA-Z_][a-zA-Z0-9_]*$ ]]; then
    echo "❌ Error: Invalid database user '$DB_USER'."
    exit 1
fi

if [[ ! "$DB_SERVICE" =~ ^[a-zA-Z0-9_-]+$ ]]; then
    echo "❌ Error: Invalid docker compose service name '$DB_SERVICE'."
    exit 1
fi

# 2. Check if any .sql files exist
shopt -s nullglob
SQL_FILES=("$DUMP_DIR"/*.sql)
shopt -u nullglob

if [ ${#SQL_FILES[@]} -eq 0 ]; then
    echo "❌ Error: No SQL dumps found in '$DUMP_DIR'!"
    exit 1
fi

# 3. Check if postgres container is running
CONTAINER_ID="$(docker compose -f "$COMPOSE_FILE" ps -q "$DB_SERVICE")"

if [ -z "$CONTAINER_ID" ]; then
    echo "❌ Error: Service '$DB_SERVICE' does not exist or has not been created."
    echo "👉 Start it first with: docker compose -f \"$COMPOSE_FILE\" up -d $DB_SERVICE"
    exit 1
fi

CONTAINER_STATE="$(docker inspect --format '{{.State.Status}}' "$CONTAINER_ID" | tr '[:upper:]' '[:lower:]')"

if [ "$CONTAINER_STATE" != "running" ]; then
    echo "❌ Error: Service '$DB_SERVICE' is not running (Status: $CONTAINER_STATE)."
    echo "👉 Start it first with: docker compose -f \"$COMPOSE_FILE\" up -d $DB_SERVICE"
    exit 1
fi

# 4. Selection menu
echo "Available SQL dumps (newest first):"

sorted_files=()

if stat -c '%Y' "$SQL_FILES" > /dev/null 2>&1; then
    # Linux / GNU stat
    while IFS= read -r file; do
        sorted_files+=("$file")
    done < <(
        for file in "${SQL_FILES[@]}"; do
            printf '%s\t%s\n' "$(stat -c '%Y' "$file")" "$file"
        done | sort -rn | cut -f2-
    )
elif stat -f '%m' "$SQL_FILES" > /dev/null 2>&1; then
    # macOS / BSD stat
    while IFS= read -r file; do
        sorted_files+=("$file")
    done < <(
        for file in "${SQL_FILES[@]}"; do
            printf '%s\t%s\n' "$(stat -f '%m' "$file")" "$file"
        done | sort -rn | cut -f2-
    )
else
    echo "❌ Error: Could not determine file modification times."
    exit 1
fi

if [ ${#sorted_files[@]} -eq 0 ]; then
    echo "❌ Error: No SQL dumps found in '$DUMP_DIR'!"
    exit 1
fi

PS3="Select a dump (1-${#sorted_files[@]}): "
select FILE in "${sorted_files[@]}"; do
    if [ -n "${FILE:-}" ]; then
        echo "----------------------------------------"
        echo "Selected: $(basename "$FILE")"
        echo "----------------------------------------"
        break
    else
        echo "Invalid selection."
    fi
done

# 5. Confirmation
read -r -p "⚠️  WARNING: Database '$DB_NAME' will be WIPED. Proceed? (y/n): " CONFIRM
if [[ ! "$CONFIRM" =~ ^[yY]$ ]]; then
    echo "❌ Aborted."
    exit 0
fi

# 6. Reset the database
echo "🔄 Resetting database '$DB_NAME'..."

# Terminate active connections before dropping the database.
docker compose -f "$COMPOSE_FILE" exec -T "$DB_SERVICE" psql -U "$DB_USER" -d "postgres" \
    --variable ON_ERROR_STOP=1 \
    --command "SELECT pg_terminate_backend(pid) FROM pg_stat_activity WHERE datname = '$DB_NAME' AND pid <> pg_backend_pid();"

# Drop and create must run outside a transaction block.
docker compose -f "$COMPOSE_FILE" exec -T "$DB_SERVICE" psql -U "$DB_USER" -d "postgres" \
    --variable ON_ERROR_STOP=1 \
    --command "DROP DATABASE IF EXISTS \"$DB_NAME\";"

docker compose -f "$COMPOSE_FILE" exec -T "$DB_SERVICE" psql -U "$DB_USER" -d "postgres" \
    --variable ON_ERROR_STOP=1 \
    --command "CREATE DATABASE \"$DB_NAME\" OWNER \"$DB_USER\";"

# 7. Import the dump
echo "📥 Importing dump..."

# Stream the dump directly into psql inside the container.
# This avoids copying the SQL file into the container first.
if docker compose -f "$COMPOSE_FILE" exec -T "$DB_SERVICE" psql -U "$DB_USER" -d "$DB_NAME" \
    --quiet \
    --variable ON_ERROR_STOP=1 \
    < "$FILE"; then
    echo "✅ Import completed successfully!"
else
    echo "❌ Error: Import failed!"
    exit 1
fi