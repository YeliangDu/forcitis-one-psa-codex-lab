package com.forcitis.one.organization.workforce.application;

import java.util.List;
import org.springframework.jdbc.core.simple.JdbcClient;

/** The version tables refuse TRUNCATE; a test that starts from an empty schema lifts that for the one statement (the test database is its own). */
public final class VersionTables {
    private static final List<String> TRUNCATE_TRIGGERS = List.of(
            "org_unit_versions|tg_org_unit_versions_append_only_truncate",
            "assignment_versions|tg_assignment_versions_append_only_truncate",
            "legal_entity_versions|tg_legal_entity_versions_append_only_truncate",
            "legal_entity_attribution_periods|tg_legal_entity_attribution_periods_append_only_truncate");

    private VersionTables() {
    }

    public static void truncate(JdbcClient jdbc, String statement) {
        change(jdbc, "disable");
        try {
            jdbc.sql(statement).update();
        } finally {
            change(jdbc, "enable");
        }
    }

    private static void change(JdbcClient jdbc, String what) {
        for (String entry : TRUNCATE_TRIGGERS) {
            String[] parts = entry.split("\\|");
            jdbc.sql("alter table organization_workforce." + parts[0] + " " + what + " trigger " + parts[1]).update();
        }
    }
}
