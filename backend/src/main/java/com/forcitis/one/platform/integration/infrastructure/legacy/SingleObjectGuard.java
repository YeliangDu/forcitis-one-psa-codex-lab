package com.forcitis.one.platform.integration.infrastructure.legacy;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeSet;
import org.springframework.jdbc.core.simple.JdbcClient;

/** I-1: the read-only account may SELECT exactly the short-name view; information_schema works on SQL Server and PostgreSQL. */
final class SingleObjectGuard {

    private final JdbcClient jdbc;
    private final String viewName;

    SingleObjectGuard(JdbcClient jdbc, String viewName) {
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
            throw new IllegalStateException("The old-system read-only account may read more than the short-name view " + viewName
                    + " (I-1, column-level grant): " + unexpected + ". Revoke these grants before starting.");
        }
    }
}
