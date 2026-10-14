package com.forcitis.one.platform.approval.infrastructure.legacy;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeSet;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * Start-up proof of I-1 and of the column-level grant (REIMAGINED_ARCHITECTURE §3.6, SECURITY_INVARIANTS I-1): the read-only
 * account may SELECT exactly the inbox view and nothing else, so it can read no token table (nor any other table). Asks the
 * database itself (information_schema works on SQL Server and PostgreSQL), without naming any table. A grant on anything else
 * stops the application. Grants given through database roles are invisible to this query: the DBA instructions in
 * LEGACY_INBOX_VIEW.sql forbid role membership for this account.
 */
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
