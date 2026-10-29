package com.forcitis.one.platform.audit.infrastructure.persistence;

import com.forcitis.one.platform.audit.api.AuditSubjectKind;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/**
 * Keeps the code constants and the registry in step (ADR-0069 decision 14), before the web server listens and before any job runs: every
 * constant is a registered row with the same has_object whose effective date has come; a RETIRED kind may keep its constant (old entries stay
 * readable), but a write naming it is refused here, because the foreign key does not look at the status.
 */
@Component
class AuditSubjectKindCheck implements SmartInitializingSingleton {

    private static final Logger LOG = LoggerFactory.getLogger(AuditSubjectKindCheck.class);

    record Row(String status, boolean hasObject, LocalDate effectiveFrom) {
    }

    private final JdbcClient jdbc;
    private final Clock clock;
    private volatile Set<String> retired = Set.of();

    AuditSubjectKindCheck(JdbcClient jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    @Override
    public void afterSingletonsInstantiated() {
        Map<String, Row> rows = new HashMap<>();
        jdbc.sql("""
                select code, status, has_object, effective_from_date from platform_audit.audit_subject_kinds
                """)
                .query((rs, n) -> rows.put(rs.getString("code"), new Row(rs.getString("status"), rs.getBoolean("has_object"),
                        rs.getObject("effective_from_date", LocalDate.class)))).list();
        List<String> problems = problems(rows, List.of(AuditSubjectKind.values()), LocalDate.ofInstant(clock.instant(), clock.getZone()));
        if (!problems.isEmpty()) {
            throw new IllegalStateException("Audit subject kinds disagree with platform_audit.audit_subject_kinds:\n  " + String.join("\n  ", problems));
        }
        Set<String> found = new HashSet<>();
        rows.forEach((code, row) -> {
            if ("RETIRED".equals(row.status())) {
                found.add(code);
            }
        });
        retired = Set.copyOf(found);
    }

    /** Refuses a new entry that names a retired kind: nothing may write it once it is retired, whatever constant is left. */
    void refuseWhenRetired(AuditSubjectKind kind) {
        refuse(retired, kind);
    }

    static void refuse(Set<String> retired, AuditSubjectKind kind) {
        if (retired.contains(kind.name())) {
            LOG.error("ALERT platform.audit an entry names the retired subject kind {}: refused, remove its writer", kind);
            throw new IllegalStateException("audit subject kind " + kind + " is retired: no new entry may name it");
        }
    }

    static List<String> problems(Map<String, Row> registry, List<AuditSubjectKind> constants, LocalDate today) {
        List<String> problems = new ArrayList<>();
        for (AuditSubjectKind kind : constants) {
            Row row = registry.get(kind.name());
            if (row == null) {
                problems.add(kind + " is a code constant but not registered");
            } else if (row.hasObject() != kind.hasObject()) {
                problems.add(kind + " has_object differs: registry " + row.hasObject() + ", constant " + kind.hasObject());
            } else if ("ACTIVE".equals(row.status()) && row.effectiveFrom().isAfter(today)) {
                problems.add(kind + " is registered to take effect on " + row.effectiveFrom() + ", not yet in force");
            }
        }
        return problems;
    }
}
