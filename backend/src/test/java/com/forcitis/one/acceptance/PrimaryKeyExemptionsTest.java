package com.forcitis.one.acceptance;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * R-1b P5 RULING-P5-D12 (ADR-0033 section 1 item 8 and guard 1): after R-1b no table of ours has a key that is not a single uuid
 * column, so the baseline of such tables is gone; the only tables left out of the rule are the five whose primary key a framework owns,
 * named one per line, each with its reason, in primary-key-exemptions.txt. ADR-0048 (RULING-DR-PR1-1) adds one line: the partitioned
 * platform_audit.audit_entries, whose key must contain the partition column.
 */
class PrimaryKeyExemptionsTest {

    static final Path ARCHITECTURE = Path.of("architecture");
    static final String PARTITIONED_AUDIT_TABLE = "platform_audit.audit_entries";

    @Test
    @DisplayName("R-1b P5 RULING-P5-D12：primary-key-type-baseline.txt 已删除（自有表无基线行）")
    void theBaselineIsGone() {
        assertThat(Files.exists(ARCHITECTURE.resolve("primary-key-type-baseline.txt"))).isFalse();
    }

    @Test
    @DisplayName("R-1b P5 RULING-P5-D12 / ADR-0033 第 1 项第 8 条：primary-key-exemptions.txt 恰为 5 张框架表加 platform_audit.audit_entries 一行（该行注明 ADR-0048），每行写理由")
    void onlyTheFiveFrameworkTablesAreExempt() throws IOException {
        Path file = ARCHITECTURE.resolve("primary-key-exemptions.txt");
        assertThat(file).exists();
        Set<String> tables = new HashSet<>();
        String auditLine = null;
        for (String line : Files.readAllLines(file)) {
            String trimmed = line.strip();
            if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                continue;
            }
            int hash = trimmed.indexOf('#');
            assertThat(hash).as("a reason after # on: " + trimmed).isPositive();
            assertThat(trimmed.substring(hash + 1).strip()).as("reason of " + trimmed).isNotEmpty();
            String table = trimmed.substring(0, hash).strip();
            tables.add(table);
            if (table.equals(PARTITIONED_AUDIT_TABLE)) {
                auditLine = trimmed;
            }
        }

        Set<String> expected = new HashSet<>(P5Names.FRAMEWORK_TABLES);
        expected.add(PARTITIONED_AUDIT_TABLE);
        assertThat(tables).containsExactlyInAnyOrderElementsOf(expected);
        assertThat(auditLine).as("the audit_entries line names its ADR").contains("ADR-0048");
    }
}
