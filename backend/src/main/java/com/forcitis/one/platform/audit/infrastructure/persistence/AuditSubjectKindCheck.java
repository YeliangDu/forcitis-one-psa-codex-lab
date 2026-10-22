package com.forcitis.one.platform.audit.infrastructure.persistence;

import com.forcitis.one.platform.audit.api.AuditSubjectKind;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/**
 * Fails the start when the code constants and the registry disagree (ADR-0069 decision 14): the foreign key does not look at the status,
 * so "a retired kind has no writer" is kept here — every constant must be an ACTIVE row with the same has_object, and a RETIRED row must have no constant.
 */
@Component
class AuditSubjectKindCheck implements ApplicationListener<ApplicationReadyEvent> {

    record Row(String status, boolean hasObject) {
    }

    private final JdbcClient jdbc;

    AuditSubjectKindCheck(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void onApplicationEvent(ApplicationReadyEvent event) {
        Map<String, Row> rows = new java.util.HashMap<>();
        jdbc.sql("select code, status, has_object from platform_audit.audit_subject_kinds")
                .query((rs, n) -> rows.put(rs.getString("code"), new Row(rs.getString("status"), rs.getBoolean("has_object")))).list();
        List<String> problems = problems(rows, List.of(AuditSubjectKind.values()));
        if (!problems.isEmpty()) {
            throw new IllegalStateException("Audit subject kinds disagree with platform_audit.audit_subject_kinds:\n  " + String.join("\n  ", problems));
        }
    }

    static List<String> problems(Map<String, Row> registry, List<AuditSubjectKind> constants) {
        List<String> problems = new ArrayList<>();
        Set<String> named = new TreeSet<>();
        for (AuditSubjectKind kind : constants) {
            named.add(kind.name());
            Row row = registry.get(kind.name());
            if (row == null) {
                problems.add(kind + " is a code constant but not registered");
            } else if (!"ACTIVE".equals(row.status())) {
                problems.add(kind + " is " + row.status() + " in the registry but still a code constant: remove its writers and the constant");
            } else if (row.hasObject() != kind.hasObject()) {
                problems.add(kind + " has_object differs: registry " + row.hasObject() + ", constant " + kind.hasObject());
            }
        }
        return problems;
    }
}
