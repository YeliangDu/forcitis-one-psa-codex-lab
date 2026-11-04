package com.forcitis.one.acceptance.organization.workforce;

import static com.forcitis.one.acceptance.organization.workforce.OrgCountersignNames.*;
import static com.forcitis.one.acceptance.organization.workforce.OrgStructureCommandNames.ORG_UNIT_LEGAL_ENTITIES;
import static com.forcitis.one.acceptance.organization.workforce.PersonnelCommandNames.EFFECTIVE;
import static com.forcitis.one.acceptance.organization.workforce.PersonnelCommandNames.PENDING;
import static com.forcitis.one.acceptance.organization.workforce.PersonnelCommandNames.WITHDRAWN;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

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
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * #1147 (ADR-0073 section 3 item 7, W2 design section 2, org-structure-commands.md section 5): a back-dated ASSIGN_LEGAL_ENTITY is handled by
 * void-and-append through the same countersign path. The rows covered by the new period are marked superseded (never deleted, content never
 * changed), the new row is appended from the effective date, a row that is cut in two is re-appended for its part before the date with its
 * original source and corrected_from_version_id, and the originals stay readable by their command id. The closed finance period is deferred by
 * the card and not tested. The new row of a back-dated assignment from D ends where the row that contains D ended (its effective_to_date, open when it was open);
 * when D falls in a gap it ends at the start of the next row, open when there is none. Later rows stay as they are, only rows covered or split by that
 * period are voided (one-design rulings, 2026-11-04, relayed by lead; W2 section 2, ADR-0073 section 3 item 7: the basis is computed on the effective date only).
 * Withdrawing a countersigned PENDING command voids the rows it wrote and re-appends the rows it voided as new rows with their original source and
 * corrected_from_version_id; a voided row is never un-voided (one-design ruling on #1147, comment 6630901727).
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

    // ---- before the first row and with a later row ----

    @Test
    @DisplayName("#1147 完成标准第 1 条（早于第一行，裁定 a）：单元自今天起归 E2（命令 C1 的一行），补登比今天早 10 天的归 E3：命令 EFFECTIVE；新行止于下一行的起点（[补登日, 今天) E3，来源为本命令）；E2 那一行没被覆盖，不作废、原样仍现行")
    void aBackDatedAssignmentBeforeTheFirstRowStopsAtTheNextRow() throws Exception {
        UUID unit = unitWithACommandRow(e2);
        Map<String, Object> original = unitAttributions(unit).getFirst();
        Map<String, Object> before = content(original);
        LocalDate back = TODAY.minusDays(10);
        setToday(TODAY.plusDays(20));

        UUID earlier = assignLegalEntity(unit, back, e3);

        assertThat(statusOf(earlier)).isEqualTo(EFFECTIVE);
        Map<String, Object> kept = rowById(unit, original.get("org_unit_legal_entity_id"));
        assertThat(kept.get("superseded_at")).as("a row that starts after the period is not covered by it").isNull();
        assertThat(content(kept)).isEqualTo(before);
        List<Map<String, Object>> current = unitAttributions(unit);
        assertThat(current).hasSize(2);
        assertThat(current.get(0).get("effective_from_date")).isEqualTo(back);
        assertThat(current.get(0).get("effective_to_date")).isEqualTo(TODAY);
        assertThat(current.get(0).get("legal_entity_id")).isEqualTo(e3);
        assertThat(commandOf(current.get(0))).isEqualTo(earlier);
        assertThat(current.get(1).get("org_unit_legal_entity_id")).isEqualTo(original.get("org_unit_legal_entity_id"));
        assertThat(current.get(1).get("effective_to_date")).isNull();
    }

    @Test
    @DisplayName("#1147 完成标准第 2 条（依据按生效日算，裁定 a）：单元自 30 天前归 E1，今天起经会签改归 E2（C1）；补登 10 天前归 E3（C2）：依据里每个员工是 E1→E3，只要 E1、E3 两位负责人会签，E2 不在其中；两位批准后 EFFECTIVE；E1 那行被作废、前半段 [30 天前, 补登日) 重新追加，新行 [补登日, 今天) E3，C1 的 E2 行原样不动、仍现行且无限期")
    void theBasisIsComputedOnTheEffectiveDate() throws Exception {
        UUID unit = attributedUnit(topE1, e1);
        UUID first = inheritingIn(unit);
        UUID second = inheritingIn(unit);
        UUID c1 = assignLegalEntity(unit, TODAY, e2);
        approve(headE1, c1, KIND_CHANGE, e1);
        approve(headE2, c1, KIND_CHANGE, e2);
        assertThat(statusOf(c1)).isEqualTo(EFFECTIVE);
        Map<String, Object> c1Row = unitAttributions(unit).stream().filter(row -> c1.equals(commandOf(row))).findFirst().orElseThrow();
        Map<String, Object> c1Before = content(c1Row);
        Map<String, Object> e1Row = unitAttributions(unit).stream().filter(row -> e1.equals(row.get("legal_entity_id"))).findFirst().orElseThrow();
        Map<String, Object> e1Before = content(e1Row);
        LocalDate back = TODAY.minusDays(10);

        UUID c2 = assignLegalEntity(unit, back, e3);

        assertThat(statusOf(c2)).isEqualTo(AWAITING);
        assertThat(basis(c2)).containsOnlyKeys(first, second).allSatisfy((employee, entities) -> assertThat(entities).containsExactly(e1, e3));
        assertThat(requestedEntities(c2, KIND_CHANGE)).containsExactlyInAnyOrder(e1, e3);
        assertThat(unitAttributionRowsWrittenBy(c2)).as("nothing is written while it awaits").isZero();
        approve(headE1, c2, KIND_CHANGE, e1);
        assertThat(statusOf(c2)).isEqualTo(AWAITING);
        approve(headE3, c2, KIND_CHANGE, e3);

        assertThat(statusOf(c2)).isEqualTo(EFFECTIVE);
        Map<String, Object> voided = rowById(unit, e1Row.get("org_unit_legal_entity_id"));
        assertThat(content(voided)).isEqualTo(e1Before);
        assertThat(voided.get("superseding_org_structure_command_id")).isEqualTo(c2);
        Map<String, Object> untouched = rowById(unit, c1Row.get("org_unit_legal_entity_id"));
        assertThat(content(untouched)).isEqualTo(c1Before);
        assertThat(untouched.get("superseded_at")).as("a later row is another command's fact").isNull();
        List<Map<String, Object>> current = unitAttributions(unit);
        assertThat(current).hasSize(3);
        assertThat(current.get(0).get("effective_from_date")).isEqualTo(TODAY.minusDays(30));
        assertThat(current.get(0).get("effective_to_date")).isEqualTo(back);
        assertThat(current.get(0).get("legal_entity_id")).isEqualTo(e1);
        assertThat(current.get(0).get("corrected_from_version_id")).isEqualTo(voided.get("org_unit_legal_entity_id"));
        assertThat(current.get(1).get("effective_from_date")).isEqualTo(back);
        assertThat(current.get(1).get("effective_to_date")).isEqualTo(TODAY);
        assertThat(current.get(1).get("legal_entity_id")).isEqualTo(e3);
        assertThat(commandOf(current.get(1))).isEqualTo(c2);
        assertThat(current.get(2).get("org_unit_legal_entity_id")).isEqualTo(c1Row.get("org_unit_legal_entity_id"));
        assertThat(current.get(2).get("effective_to_date")).isNull();
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

    // ---- a row closed by :end, and the withdrawal of a command whose rows voided another (one-design rulings on #1147, 2026-11-04) ----

    /** What a reader of the current facts sees of a row; ids are left out because a re-appended row is a new row. */
    private Map<String, Object> shape(Map<String, Object> row) {
        Map<String, Object> kept = new LinkedHashMap<>();
        for (String column : List.of("effective_from_date", "effective_to_date", "legal_entity_id", "change_channel_kind", "change_org_structure_command_id", "version_audit_entry_id")) {
            kept.put(column, row.get(column));
        }
        return kept;
    }

    private List<Map<String, Object>> shapesOf(UUID unit) throws SQLException {
        return unitAttributions(unit).stream().map(this::shape).toList();
    }

    /** Ends the unit's attribution through the :end endpoint, as the attribution maintainer does. */
    private void endAttribution(UUID unit, LocalDate to) throws Exception {
        String url = "/api/v1/organization/org-unit-legal-entities/" + text(unit);
        MockHttpServletResponse read = mvc.perform(get(url).with(session(orgAdmin.session()))).andReturn().getResponse();
        assertThat(read.getStatus()).as("reading the attribution before ending it").isEqualTo(200);
        MockHttpServletResponse ended = mvc.perform(post(url + ":end").header("If-Match", read.getHeader("ETag")).with(csrf()).with(session(orgAdmin.session()))
                .contentType(MediaType.APPLICATION_JSON).content("{\"effectiveTo\":\"" + to + "\"}")).andReturn().getResponse();
        assertThat(ended.getStatus()).as("ending the attribution: " + ended.getContentAsString()).isEqualTo(200);
    }

    private void countersignAll(UUID command, String kind) throws Exception {
        for (UUID entity : requestedEntities(command, kind)) {
            approve(headOf(entity), command, kind, entity);
        }
    }

    @Test
    @DisplayName("#1147 完成标准第 1 条（一个设计裁定 a，落在 :end 关闭的行中间）：单元自 30 天前归 E1，30 天后经 :end 止于 T，今天已是 40 天后，补登 10 天后（D）归 E3：命令 EFFECTIVE 且无 ORG_LEGAL_ENTITY_CONFLICT；原行 [F, T) 被作废、内容未改；当前事实恰两行：[F, D) E1 重新追加（来源照抄原行、corrected_from 指向原行）与 [D, T) E3（来源为本命令）；T 及以后没有归属，空档原样保留")
    void aBackDatedAssignmentIntoARowClosedByEndStopsWhereThatRowStopped() throws Exception {
        UUID unit = attributedUnit(topE1, e1);
        LocalDate closedAt = TODAY.plusDays(30);
        endAttribution(unit, closedAt);
        Map<String, Object> original = unitAttributions(unit).getFirst();
        Map<String, Object> before = content(original);
        assertThat(original.get("effective_to_date")).as("the row is closed by :end and has no successor").isEqualTo(closedAt);
        LocalDate back = TODAY.plusDays(10);
        setToday(TODAY.plusDays(40));

        UUID command = assignLegalEntity(unit, back, e3);

        assertThat(statusOf(command)).as("nobody is affected, so it takes effect on entry").isEqualTo(EFFECTIVE);
        Map<String, Object> voided = rowById(unit, original.get("org_unit_legal_entity_id"));
        assertThat(content(voided)).as("a superseded row keeps its content").isEqualTo(before);
        assertThat(voided.get("effective_to_date")).isEqualTo(closedAt);
        assertThat(voided.get("superseded_at")).isNotNull();
        assertThat(voided.get("superseding_org_structure_command_id")).isEqualTo(command);
        List<Map<String, Object>> current = unitAttributions(unit);
        assertThat(current).hasSize(2);
        Map<String, Object> front = current.get(0);
        assertThat(front.get("effective_from_date")).isEqualTo(TODAY.minusDays(30));
        assertThat(front.get("effective_to_date")).isEqualTo(back);
        assertThat(front.get("legal_entity_id")).isEqualTo(e1);
        assertThat(front.get("change_channel_kind")).isEqualTo(voided.get("change_channel_kind"));
        assertThat(commandOf(front)).isEqualTo(commandOf(voided));
        assertThat(front.get("version_audit_entry_id")).isEqualTo(voided.get("version_audit_entry_id"));
        assertThat(front.get("corrected_from_version_id")).isEqualTo(voided.get("org_unit_legal_entity_id"));
        Map<String, Object> fresh = current.get(1);
        assertThat(fresh.get("effective_from_date")).isEqualTo(back);
        assertThat(fresh.get("effective_to_date")).as("the new row ends where the row that contained D ended; :end was a command").isEqualTo(closedAt);
        assertThat(fresh.get("legal_entity_id")).isEqualTo(e3);
        assertThat(commandOf(fresh)).isEqualTo(command);
    }

    /** C1 (E1 to E2 from D1) countersigned and PENDING; the unit's rows are then the old E1 row closed at D1 and the E2 row from D1. */
    private UUID pendingMoveTo(UUID unit, LocalDate day, UUID entity) throws Exception {
        UUID command = assignLegalEntity(unit, day, entity);
        assertThat(statusOf(command)).isEqualTo(AWAITING);
        countersignAll(command, KIND_CHANGE);
        assertThat(statusOf(command)).isEqualTo(PENDING);
        return command;
    }

    private void withdrawWithEveryHead(UUID command) throws Exception {
        orgCommands().withdraw(command, "synthetic: plan changed");
        assertThat(statusOf(command)).as("withdrawing a countersigned command needs every head").isEqualTo(PENDING);
        countersignAll(command, KIND_WITHDRAWAL);
        assertThat(statusOf(command)).isEqualTo(WITHDRAWN);
    }

    @Test
    @DisplayName("#1147 完成标准第 1 条（一个设计裁定 b，撤回待生效命令）：C1 于 D1、C2 于 D2（D2 在 D1 之后）都已会签待生效，C2 关闭了 C1 的开放行；撤回 C2（WITHDRAWAL 全体负责人批准）后：C2 转已撤回、C1 仍待生效；当前事实与 C2 会签前完全相同（区间、法人、来源命令、审计）；C2 自己写的行全部作废，被它关闭的 C1 行以新行原样重现且那一行此时已作废（corrected_from 指向被作废的行）；没有员工法人变更事件")
    void withdrawingALaterPendingCommandBringsTheEarlierRowsBack() throws Exception {
        UUID unit = attributedUnit(topE1, e1);
        UUID person = inheritingIn(unit);
        LocalDate d1 = TODAY.plusDays(10);
        LocalDate d2 = TODAY.plusDays(20);
        UUID c1 = pendingMoveTo(unit, d1, e2);
        List<Map<String, Object>> beforeC2 = shapesOf(unit);
        assertThat(beforeC2).hasSize(2);
        UUID c1Row = (UUID) unitAttributions(unit).stream().filter(row -> c1.equals(commandOf(row))).findFirst().orElseThrow().get("org_unit_legal_entity_id");
        Map<String, Object> c1Content = content(rowById(unit, c1Row));
        UUID c2 = assignLegalEntity(unit, d2, e3);
        assertThat(statusOf(c2)).isEqualTo(AWAITING);
        assertThat(requestedEntities(c2, KIND_CHANGE)).as("the basis on D2 moves the person from E2 to E3").containsExactlyInAnyOrder(e2, e3);
        countersignAll(c2, KIND_CHANGE);
        assertThat(statusOf(c2)).isEqualTo(PENDING);
        assertThat(shapesOf(unit)).as("C2 closed C1's open row and added its own").hasSize(3).isNotEqualTo(beforeC2);
        List<Map<String, Object>> everythingBefore = allRows(unit);

        withdrawWithEveryHead(c2);

        assertThat(statusOf(c1)).as("the earlier command is untouched").isEqualTo(PENDING);
        assertThat(shapesOf(unit)).as("readers see the facts they had before C2").isEqualTo(beforeC2);
        List<Map<String, Object>> history = allRows(unit);
        assertThat(history).as("history only grows: the rows of C2 are voided, the rows it voided come back as new rows").hasSizeGreaterThan(everythingBefore.size());
        assertThat(history.stream().filter(row -> c2.equals(commandOf(row))).toList()).isNotEmpty().allSatisfy(row -> assertThat(row.get("superseded_at")).isNotNull());
        Map<String, Object> back = unitAttributions(unit).stream().filter(row -> c1.equals(commandOf(row))).findFirst().orElseThrow();
        assertThat(back.get("effective_to_date")).as("C1's row is open-ended again").isNull();
        assertThat(back.get("org_unit_legal_entity_id")).as("a voided row is never un-voided: it comes back as a new row").isNotEqualTo(c1Row);
        assertThat(rowById(unit, c1Row).get("superseded_at")).as("the original row is voided by the withdrawal, never un-voided").isNotNull();
        assertThat(back.get("corrected_from_version_id")).as("it re-creates the row C2 voided").isEqualTo(c1Row);
        assertThat(content(back).get("effective_from_date")).isEqualTo(c1Content.get("effective_from_date"));
        assertThat(legalEntityChangesOf(person)).as("neither command took effect").isZero();
    }

    @Test
    @DisplayName("#1147 完成标准第 1 条（一个设计裁定 b，落在更早的已生效命令的行内）：单元自 30 天前归 E1、今天起经会签归 E2（C1 已生效，开放行），C2 于 15 天后改归 E3 已会签待生效，它关闭了 C1 的开放行；撤回 C2 后当前事实与会签前相同（来源命令与审计照旧、C1 的行重新无限期），C2 的行全部作废，C1 的行以新行原样重现且 corrected_from 指向被作废的原行；撤回的命令没有新增员工法人变更事件")
    void withdrawingACommandInsideAnEarlierRowReopensIt() throws Exception {
        UUID unit = attributedUnit(topE1, e1);
        UUID person = inheritingIn(unit);
        UUID c1 = assignLegalEntity(unit, TODAY, e2);
        approve(headE1, c1, KIND_CHANGE, e1);
        approve(headE2, c1, KIND_CHANGE, e2);
        assertThat(statusOf(c1)).isEqualTo(EFFECTIVE);
        int changesBefore = legalEntityChangesOf(person);
        List<Map<String, Object>> beforeC2 = shapesOf(unit);
        assertThat(beforeC2).as("E1 closed at today, then E2 open-ended from C1").hasSize(2);
        UUID c2 = pendingMoveTo(unit, TODAY.plusDays(15), e3);
        assertThat(shapesOf(unit)).hasSize(3).isNotEqualTo(beforeC2);

        withdrawWithEveryHead(c2);

        assertThat(shapesOf(unit)).as("readers see the facts they had before C2").isEqualTo(beforeC2);
        assertThat(allRows(unit).stream().filter(row -> c2.equals(commandOf(row))).toList()).isNotEmpty().allSatisfy(row -> assertThat(row.get("superseded_at")).isNotNull());
        Map<String, Object> back = unitAttributions(unit).getLast();
        assertThat(back.get("effective_to_date")).as("the open row of the effective command is open-ended again").isNull();
        assertThat(back.get("corrected_from_version_id")).isNotNull();
        assertThat(rowById(unit, back.get("corrected_from_version_id")).get("superseded_at")).as("it re-creates a voided row").isNotNull();
        assertThat(legalEntityChangesOf(person)).as("the withdrawn command never took effect").isEqualTo(changesBefore);
    }
}
