#!/bin/bash
# Runs once when a test container's data directory is first initialised (TestPostgres.container()). The database setup script of the real deployment creates
# btree_gist before the migrations run (one_db_roles_schemas.sql); platform_identity's baseline only checks that it exists. template1 makes every database
# created later on the shared instance (TestPostgres.database) carry it too.
set -e
for database in template1 "$POSTGRES_DB"; do
    psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$database" -c 'CREATE EXTENSION IF NOT EXISTS btree_gist WITH SCHEMA public'
done
