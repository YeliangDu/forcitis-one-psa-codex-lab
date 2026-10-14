package com.forcitis.one.platform.integration.infrastructure.seeyon;

import java.util.List;
import java.util.Set;
import org.springframework.jdbc.core.simple.JdbcClient;

/** The account may only hold SELECT on the dedicated views: no write, execute, DDL or database-wide read. */
final class ReadOnlyGuard {

    static final Set<String> FORBIDDEN = Set.of("INSERT", "UPDATE", "DELETE", "EXECUTE", "ALTER", "CONTROL", "REFERENCES",
            "TAKE OWNERSHIP", "CREATE TABLE", "CREATE VIEW", "CREATE PROCEDURE", "CREATE FUNCTION", "CREATE SCHEMA",
            "SELECT", "VIEW DEFINITION", "ALTER ANY SCHEMA", "ALTER ANY USER", "ALTER ANY ROLE");

    private ReadOnlyGuard() {
    }

    static List<String> excessiveDatabasePermissions(JdbcClient jdbc) {
        return jdbc.sql("SELECT permission_name FROM fn_my_permissions(NULL, 'DATABASE')")
                .query(String.class).list().stream()
                .map(String::trim)
                .filter(p -> FORBIDDEN.contains(p.toUpperCase(java.util.Locale.ROOT)))
                .distinct().sorted().toList();
    }
}
