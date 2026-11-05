#!/bin/bash
# Runs once, when the data volume is first initialised: the extension the database setup of the real deployment creates before the migrations run.
set -e
psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" -c 'CREATE EXTENSION IF NOT EXISTS btree_gist WITH SCHEMA public'
