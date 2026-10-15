package com.forcitis.one.organization.workforce.application;

import java.util.List;
import org.springframework.jdbc.core.simple.JdbcClient;

/** The version tables and the attribution corrections refuse TRUNCATE (trigger type bit 32); a test that starts from an empty schema lifts that for the one statement (the test database is its own). */
public final class VersionTables {
    private VersionTables() {
    }

    public static void truncate(JdbcClient jdbc, String statement) {
        List<String> triggers = jdbc.sql("""
                select r.relname || '|' || g.tgname from pg_trigger g join pg_class r on r.oid = g.tgrelid join pg_namespace n on n.oid = r.relnamespace
                 where n.nspname = 'organization_workforce' and not g.tgisinternal and (g.tgtype & 32) <> 0
                """).query(String.class).list();
        change(jdbc, triggers, "disable");
        try {
            jdbc.sql(statement).update();
        } finally {
            change(jdbc, triggers, "enable");
        }
    }

    private static void change(JdbcClient jdbc, List<String> triggers, String what) {
        for (String entry : triggers) {
            String[] parts = entry.split("\\|");
            jdbc.sql("alter table organization_workforce." + parts[0] + " " + what + " trigger " + parts[1]).update();
        }
    }
}
