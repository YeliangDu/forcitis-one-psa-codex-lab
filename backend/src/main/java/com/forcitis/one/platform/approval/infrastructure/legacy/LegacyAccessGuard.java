package com.forcitis.one.platform.approval.infrastructure.legacy;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeSet;
import org.springframework.jdbc.core.simple.JdbcClient;

/** I-1 proof: asks information_schema (SQL Server and PostgreSQL) that only the inbox view is SELECT-able; grants via database roles are invisible, so role membership is forbidden for this account. */
class LegacyAccessGuard {

    private final JdbcClient jdbc;
    private final String viewName;

    LegacyAccessGuard(JdbcClient jdbc, String viewName) {
        this.jdbc = jdbc;
        this.viewName = viewName;
    }

    void verify() {
        List<Map<String, Object>> granted = jdbc.sql("""
                SELECT DISTINCT table_schema, table_name FROM information_schema.table_privileges
                 WHERE privilege_type = 'SELECT'
                   AND lower(table_schema) NOT IN ('pg_catalog', 'information_schema', 'sys')
                """).query().listOfRows();
        String expectedName = viewName.contains(".") ? viewName.substring(viewName.indexOf('.') + 1) : viewName;
        TreeSet<String> unexpected = new TreeSet<>();
        for (Map<String, Object> row : granted) {
            String name = String.valueOf(row.get("table_name"));
            if (!name.toLowerCase(Locale.ROOT).equals(expectedName.toLowerCase(Locale.ROOT))) {
                unexpected.add(row.get("table_schema") + "." + name);
            }
        }
        if (!unexpected.isEmpty()) {
            throw new IllegalStateException("The legacy read-only account may read more than the inbox view " + viewName
                    + " (I-1, column-level grant): " + unexpected + ". Revoke these grants before starting.");
        }
    }
}
