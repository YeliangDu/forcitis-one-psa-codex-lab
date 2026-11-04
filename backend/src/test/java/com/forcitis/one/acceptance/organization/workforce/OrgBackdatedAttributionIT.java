package com.forcitis.one.acceptance.organization.workforce;

import static com.forcitis.one.acceptance.organization.workforce.OrgCountersignNames.*;
import static com.forcitis.one.acceptance.organization.workforce.OrgStructureCommandNames.ORG_UNIT_LEGAL_ENTITIES;
import static com.forcitis.one.acceptance.organization.workforce.PersonnelCommandNames.EFFECTIVE;
import static com.forcitis.one.acceptance.organization.workforce.PersonnelCommandNames.WITHDRAWN;
import static org.assertj.core.api.Assertions.assertThat;

import com.forcitis.one.TestDatabase;
import com.forcitis.one.TestPostgres;
import java.sql.SQLException;
import java.util.ArrayList;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * #1147 (ADR-0073 section 3 item 7, W2 design section 2, org-structure-commands.md section 5): a back-dated ASSIGN_LEGAL_ENTITY is handled by
 * void-and-append through the same countersign path. The rows covered by the new period are marked superseded (never deleted, content never
 * changed), the new row is appended from the effective date, a row that is cut in two is re-appended for its part before the date with its
 * original source and corrected_from_version_id, and the originals stay readable by their command id. The closed finance period is deferred by
 * the card and not tested. What period the new row covers (up to the next row, or open-ended) is not decided yet, so nothing here asserts the
 * rows after the effective date; the cases that depend on it are left out until it is ruled.
 */
class OrgBackdatedAttributionIT extends OrgCountersignSupport {

    static final TestDatabase postgres = TestPostgres.database(OrgBackdatedAttributionIT.class, TestPostgres.MIGRATED).withUrlParam("currentSchema", "platform_approval");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        postgres.register(registry);
    }

    private static final String CONTENT = "org_unit_legal_entity_id, org_unit_id, effective_from_date, legal_entity_id, change_channel_kind, change_org_structure_command_id, version_audit_entry_id";

    /** Every row of the unit, superseded or not, with the columns that never change once written. */
    private List<Map<String, Object>> allRows(UUID unit) throws SQLException {
        return rows("select " + CONTENT + ", effective_to_date, superseded_at, superseding_org_structure_command_id, corrected_from_version_id from " + ORG_UNIT_LEGAL_ENTITIES
                + " where org_unit_id = ? order by effective_from_date, created_at, org_unit_legal_entity_id", stored(unit));
    }

    private Map<String, Object> content(Map<String, Object> row) {
        Map<String, Object> kept = new LinkedHashMap<>();
        for (String column : CONTENT.split(", ")) {
            kept.put(column, row.get(column));
        }
        return kept;
    }

    private Map<String, Object> rowById(UUID unit, Object id) throws SQLException {
        return allRows(unit).stream().filter(row -> id.equals(row.get("org_unit_legal_entity_id"))).findFirst().orElseThrow(() -> new AssertionError("the row " + id + " is gone"));
    }

    /** A seeded unit nobody is attached to, whose attribution is the one row written by an ordinary assignment of today (a command id to read it by). */
    private UUID unitWithACommandRow(UUID entity) throws Exception {
        UUID unit = seedUnit(topE1);
        UUID first = assignLegalEntity(unit, TODAY, entity);
        assertThat(statusOf(first)).as("nobody is affected, so the assignment takes effect on entry").isEqualTo(EFFECTIVE);
        return unit;
    }

    private UUID commandOf(Map<String, Object> row) {
        return (UUID) row.get("change_org_structure_command_id");
    }

    /** A unit attributed to E1 from 30 days ago, then moved to E2 from today by a countersigned command: the history has a row after any earlier day. */
    private UUID unitMovedToE2Today() throws Exception {
        UUID unit = attributedUnit(topE1, e1);
        inheritingIn(unit);
        UUID c1 = assignLegalEntity(unit, TODAY, e2);
        approve(headE1, c1, KIND_CHANGE, e1);
        approve(headE2, c1, KIND_CHANGE, e2);
        assertThat(statusOf(c1)).isEqualTo(EFFECTIVE);
        return unit;
    }

    private Staff headOf(UUID entity) {
        return Map.of(e1, headE1, e2, headE2, e3, headE3).get(entity);
    }

    // ---- into the middle of a closed row, with a later row after it ----

    @Test
    @DisplayName("#1147 完成标准第 1 条（落在已关闭的行中间）：单元自今天起归 E2（命令 C1 的一行），30 天后改归 E3（该行于是关闭为 [今天, +30)），今天已是 40 天后，补登 10 天后生效的归 E1（C2）：命令 EFFECTIVE；原 E2 行未删、内容未改、被 C2 作废；当前事实里有一行 E2 自今天起止于补登日，它是重新追加的，来源（渠道、命令、审计）照抄原行、corrected_from 指向原行；另有一行 E1 自补登日起、来源为 C2；按 C1 仍取得原事实")
    void aBackDatedAssignmentIntoTheMiddleOfAClosedRow() throws Exception {
        UUID unit = unitWithACommandRow(e2);
        Map<String, Object> original = unitAttributions(unit).getFirst();
        UUID firstCommand = commandOf(original);
        Map<String, Object> before = content(original);
        setToday(TODAY.plusDays(30));
        assertThat(statusOf(assignLegalEntity(unit, TODAY.plusDays(30), e3))).as("nobody is affected, so it takes effect on entry").isEqualTo(EFFECTIVE);
        assertThat(rowById(unit, original.get("org_unit_legal_entity_id")).get("effective_to_date")).as("the first row is closed by the later one").isEqualTo(TODAY.plusDays(30));
        LocalDate back = TODAY.plusDays(10);
        setToday(TODAY.plusDays(40));

        UUID backDated = assignLegalEntity(unit, back, e1);

        assertThat(statusOf(backDated)).isEqualTo(EFFECTIVE);
        Map<String, Object> voided = rowById(unit, original.get("org_unit_legal_entity_id"));
        assertThat(content(voided)).as("a superseded row keeps its content").isEqualTo(before);
        assertThat(voided.get("superseded_at")).isNotNull();
        assertThat(voided.get("superseding_org_structure_command_id")).isEqualTo(backDated);
        List<Map<String, Object>> current = unitAttributions(unit);
        Map<String, Object> head = current.stream().filter(row -> e2.equals(row.get("legal_entity_id"))).findFirst().orElseThrow();
        Map<String, Object> fresh = current.stream().filter(row -> backDated.equals(commandOf(row))).findFirst().orElseThrow();
        assertThat(head.get("effective_from_date")).isEqualTo(TODAY);
        assertThat(head.get("effective_to_date")).isEqualTo(back);
        assertThat(head.get("change_channel_kind")).isEqualTo(voided.get("change_channel_kind"));
        assertThat(commandOf(head)).isEqualTo(firstCommand);
        assertThat(head.get("version_audit_entry_id")).isEqualTo(voided.get("version_audit_entry_id"));
        assertThat(head.get("corrected_from_version_id")).isEqualTo(voided.get("org_unit_legal_entity_id"));
        assertThat(fresh.get("effective_from_date")).isEqualTo(back);
        assertThat(fresh.get("legal_entity_id")).isEqualTo(e1);
        assertThat(allRows(unit).stream().filter(row -> firstCommand.equals(commandOf(row)) && row.get("corrected_from_version_id") == null).map(this::content).toList())
                .as("read by the original command, the original fact is as it was").containsExactly(before);
    }

    // ---- the countersign ----

    @Test
    @DisplayName("#1147 完成标准第 2 条（批准时复核仍然有效）：单元自 30 天前归 E1、今天起经会签归 E2，补登 10 天前归 E3 等会签，除最后一位外都批准后单元里多了一位继承法人的员工，最后一次批准时重算的依据变了：该请求 REJECTED 并带理由，命令转已撤回，不作废任何行、不写新行，历史行原样")
    void theApprovalStillReChecksTheBasis() throws Exception {
        UUID unit = unitMovedToE2Today();
        List<Map<String, Object>> before = allRows(unit);
        UUID command = assignLegalEntity(unit, TODAY.minusDays(10), e3);
        assertThat(statusOf(command)).isEqualTo(AWAITING);
        List<UUID> asked = new ArrayList<>(requestedEntities(command, KIND_CHANGE));
        assertThat(asked).contains(e1, e3);
        for (UUID entity : asked.subList(0, asked.size() - 1)) {
            approve(headOf(entity), command, KIND_CHANGE, entity);
        }
        inheritingIn(unit);
        UUID last = asked.getLast();

        approve(headOf(last), command, KIND_CHANGE, last);

        assertThat(statusOf(command)).isEqualTo(WITHDRAWN);
        assertThat(decision(command, KIND_CHANGE, last)).isEqualTo(REJECTED);
        assertThat(request(command, KIND_CHANGE, last).get("decision_reason")).isNotNull();
        assertThat(unitAttributionRowsWrittenBy(command)).isZero();
        assertThat(allRows(unit)).isEqualTo(before);
    }

    @Test
    @DisplayName("#1147 完成标准第 1 条（会签期间不动原行）：补登（早于单元已有的后续行）等会签时，单元的历史行一行不动（无作废、无新行）；一位负责人拒签后命令已撤回，历史行仍原样")
    void nothingIsVoidedWhileTheCommandAwaitsOrWhenItIsRejected() throws Exception {
        UUID unit = unitMovedToE2Today();
        List<Map<String, Object>> before = allRows(unit);
        UUID command = assignLegalEntity(unit, TODAY.minusDays(10), e3);

        assertThat(statusOf(command)).isEqualTo(AWAITING);
        assertThat(allRows(unit)).isEqualTo(before);
        reject(headE3, command, KIND_CHANGE, e3, "synthetic: wrong entity");

        assertThat(statusOf(command)).isEqualTo(WITHDRAWN);
        assertThat(allRows(unit)).isEqualTo(before);
    }
}
