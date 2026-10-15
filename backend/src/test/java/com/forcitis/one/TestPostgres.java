package com.forcitis.one;

import org.testcontainers.postgresql.PostgreSQLContainer;

/** The one way tests start PostgreSQL: same sort order (built-in C.UTF-8) as the dev database, so an ORDER BY on text answers the same everywhere (DDL-11). */
public final class TestPostgres {
    static final String INITDB_ARGS = "--locale-provider=builtin --locale=C.UTF-8 --builtin-locale=C.UTF-8";

    private TestPostgres() {
    }

    public static PostgreSQLContainer container() {
        return new PostgreSQLContainer(TestImages.startingWith("postgres:")).withEnv("POSTGRES_INITDB_ARGS", INITDB_ARGS);
    }
}
