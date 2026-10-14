package com.forcitis.one.organization.workforce.infrastructure.external;

import java.util.List;
import java.util.Set;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * Verifies that an external read-only account really is read-only, at startup (SQL Server). Database-level
 * permissions that allow writing, executing, changing structure - or reading every table - are not acceptable:
 * the account may only hold SELECT on the dedicated views (granted per object or per schema, not per database).
 */
final class ReadOnlyGuard {

    /** Permissions that must not be held at database scope. */
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
