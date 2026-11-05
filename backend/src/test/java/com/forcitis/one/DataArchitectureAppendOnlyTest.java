package com.forcitis.one;

import static org.assertj.core.api.Assertions.assertThat;

import com.forcitis.one.DataArchitectureTest.Column;
import com.forcitis.one.DataArchitectureTest.Entry;
import com.forcitis.one.DataArchitectureTest.Table;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Scorecard D1 covers every APPEND_ONLY table whatever its role (#1329); the real schema is checked in DataArchitectureTest. */
class DataArchitectureAppendOnlyTest {

    @Test
    @DisplayName("injection: an APPEND_ONLY table of any role without triggers that refuse DELETE, UPDATE and TRUNCATE fails D1")
    void appendOnlyTableOfAnyRoleNeedsTheRefusingTriggers() {
        for (String role : List.of("IDEMPOTENCY", "LOG_APPEND_ONLY", "DIMENSION_VERSIONED", "BUSINESS_DOCUMENT")) {
            Table ledger = new Table("platform_audit.handled_things");
            ledger.comment = "handled things";
            ledger.columns.put("id", new Column("id", "bigint", true, "id"));
            Entry entry = new Entry(ledger.name, new LinkedHashMap<>(Map.of("role", role, "mutability", "APPEND_ONLY",
                    "retention", Map.of("policy", "PERMANENT"), "lineage", Map.of("source", "NEW"))), List.of());
            assertThat(DataArchitectureTest.violations(Map.of(ledger.name, ledger), Map.of(ledger.name, entry), Map.of(), Set.of()))
                    .as(role).anyMatch(x -> x.rule().equals("D1") && x.table().equals(ledger.name));
        }
    }
}
