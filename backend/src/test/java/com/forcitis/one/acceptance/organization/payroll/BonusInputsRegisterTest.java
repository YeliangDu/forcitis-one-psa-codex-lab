package com.forcitis.one.acceptance.organization.payroll;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * #1436 acceptance part (PY3 bonus input port), done-when 2: the registry line and the classification lines of {@code organization_payroll.bonus_inputs}
 * as signed in the PY3 data-design record of organization-payroll (#1428, user Q-12 a: pay is L3 {@code SENSITIVE}), and that no event of the payroll
 * catalogue carries a per-person bonus figure (spec 4.5 / P-R5). The files are read as text, so this class compiles before the slice exists.
 */
class BonusInputsRegisterTest {

    private static final String COLUMN = "organization_payroll.bonus_inputs.";

    private static String read(String relative) throws IOException {
        Path file = Path.of(relative);
        if (!Files.exists(file)) {
            file = Path.of("backend").resolve(relative);
        }
        return Files.readString(file);
    }

    /** The line "<column> | LEVEL | MARK | reason" for the column, or null. */
    private static String[] classification(String column) throws IOException {
        for (String line : read("architecture/data-classification.txt").split("\n")) {
            if (line.startsWith(COLUMN + column + " ")) {
                String[] parts = line.split("\\|");
                return new String[] {parts[1].trim(), parts[2].trim()};
            }
        }
        return null;
    }

    @Test
    @DisplayName("#1436 完成标准 2：bonus_amount 登记为 SENSITIVE / PI（工资数额，L3，Q-12 a）")
    void theAmountIsSensitivePersonalInformation() throws IOException {
        assertThat(classification("bonus_amount")).as("PY3 record C1 to C5: bonus_amount is registered").isNotNull().containsExactly("SENSITIVE", "PI");
    }

    @Test
    @DisplayName("#1436 完成标准 2：reason_key 登记为 SENSITIVE / PI（谁因何获得奖金）")
    void theReasonKeyIsSensitivePersonalInformation() throws IOException {
        assertThat(classification("reason_key")).as("PY3 record C1 to C5: reason_key is registered").isNotNull().containsExactly("SENSITIVE", "PI");
    }

    @Test
    @DisplayName("#1436 完成标准 2：币种、来源、月份、状态登记为 INTERNAL / NONE；录入人、作废人、作废时间同为 INTERNAL / NONE")
    void theQualifyingAndAuditColumnsAreInternal() throws IOException {
        for (String column : List.of("bonus_currency", "source_kind", "payroll_month_start_date", "status", "entered_by_employee_id", "voided_by_user_id", "voided_at")) {
            assertThat(classification(column)).as("PY3 record C1 to C5: " + column + " is registered").isNotNull().containsExactly("INTERNAL", "NONE");
        }
    }

    @Test
    @DisplayName("#1436 完成标准 2：代理键与跨上下文引用 id 不登记（默认 INTERNAL）")
    void theSurrogateAndReferenceIdsAreNotRegistered() throws IOException {
        for (String column : List.of("id", "employment_relationship_id", "paying_legal_entity_id", "approval_instance_id")) {
            assertThat(classification(column)).as("PY3 record: " + column + " stays unregistered").isNull();
        }
    }

    @Test
    @DisplayName("#1436 完成标准 2：数据访问登记表有 bonus_inputs：聚合根 BonusDetermination，有条件更新，写入方 organization.payroll，业务键三列")
    void theRegistryLineIsAsSigned() throws IOException {
        String registry = read("architecture/data-access-registry.yaml");
        int at = registry.indexOf("\n  organization_payroll.bonus_inputs:");
        assertThat(at).as("PY3 record: the registry has a line for organization_payroll.bonus_inputs").isGreaterThanOrEqualTo(0);
        Matcher next = Pattern.compile("\\n  \\S").matcher(registry);  // the next top-level entry (two-space indent), so a neighbour's lines never count
        String entry = registry.substring(at, next.find(at + 3) ? next.start() : registry.length());

        assertThat(entry).as("PY3 record: role").contains("role: AGGREGATE_ROOT");
        assertThat(entry).as("PY3 record: aggregate").contains("aggregate: BonusDetermination");
        assertThat(entry).as("PY3 record: business key").contains("business_key: [employment_relationship_id, payroll_month_start_date, reason_key]");
        assertThat(entry).as("PY3 record: only payroll writes").contains("writers: [organization.payroll]");
        assertThat(entry).as("PY3 record: the void is the one update").contains("mutability: CONDITIONAL_UPDATE");
        assertThat(entry).as("PY3 record: a new table").contains("source: NEW");
        assertThat(entry).as("PY3 record: the condition names the one void and its trigger").contains("condition:").contains("tg_bonus_inputs_change");
    }

    @Test
    @DisplayName("#1436 完成标准 2、P-R5：薪酬事件目录里没有任何事件携带奖金金额或原因")
    void noPayrollEventCarriesTheBonusFigureOrReason() throws IOException {
        Path file = Path.of("../docs/architecture/events/payroll.yaml");
        org.junit.jupiter.api.Assumptions.assumeTrue(Files.exists(file), "no payroll event catalogue yet: nothing carries a figure");
        String catalogue = Files.readString(file);
        Matcher fields = Pattern.compile("^ {10}(\\w+):\\s*$", Pattern.MULTILINE).matcher(catalogue);
        while (fields.find()) {
            String field = fields.group(1).toLowerCase();
            assertThat(field).as("P-R5 and PY3 record E1: no event payload field names a bonus amount or reason").isNotIn("bonusamount", "reasonkey", "bonus_amount", "reason_key");
        }
    }
}
