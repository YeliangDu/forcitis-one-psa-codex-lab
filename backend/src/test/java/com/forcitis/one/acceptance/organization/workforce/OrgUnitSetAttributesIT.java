package com.forcitis.one.acceptance.organization.workforce;

import static com.forcitis.one.acceptance.organization.workforce.OrgStructureCommandNames.*;
import static com.forcitis.one.acceptance.organization.workforce.PersonnelCommandNames.EFFECTIVE;
import static org.assertj.core.api.Assertions.assertThat;

import com.forcitis.one.TestDatabase;
import com.forcitis.one.TestPostgres;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * #906 IMP-1030-ORG-F2a (ADR-0073 item 2: SET_ATTRIBUTES; W2 section 2 for the void-and-append of a back-dated effective date). One
 * command expresses one whole adjustment of name, short name, region kind and delivery flag, at least one of them; registering it
 * writes the org_unit_versions row, and a past effective date supersedes and re-appends instead of rewriting. The closed-period check
 * of item 2.2 has no module to ask yet and is not covered. The short name is a column of org_unit_versions (ADR-0068 item 15.4; one-design ruling 2026-10-30 #906, DD amendment of #904).
 */
class OrgUnitSetAttributesIT extends OrgStructureCommandSupport {

    static final TestDatabase postgres = TestPostgres.database(OrgUnitSetAttributesIT.class, TestPostgres.MIGRATED).withUrlParam("currentSchema", "platform_approval");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        postgres.register(registry);
    }

    @Test
    @DisplayName("#906 one-design ruling 2026-10-30 #906（简称进版本行）、ADR-0073 第 2 条第 1 项：一条调整属性命令一次改名称、简称、区域、交付单元标记，只有一条命令行；新版本带新值、其余（上级）沿用，前一版本在生效日关闭；发一次「组织单元属性已调整」")
    void oneCommandChangesSeveralAttributes() throws Exception {
        UUID parent = seedUnit(null);
        UUID target = seedUnit(parent);

        UUID command = unitCommand(SET_ATTRIBUTES, target, TODAY, TARGET_NAME, "Renamed unit", TARGET_SHORT_NAME, "Short", TARGET_REGION_KIND, REGIONAL,
                TARGET_IS_DELIVERY_UNIT, true);

        assertThat(rows("select 1 from " + ORG_COMMANDS + " where target_org_unit_id = ?", stored(target))).as("one command for one adjustment").hasSize(1);
        Map<String, Object> row = orgCommand(command);
        assertThat(row.get("command_kind")).isEqualTo(SET_ATTRIBUTES);
        assertThat(row.get("status")).isEqualTo(EFFECTIVE);
        assertThat(row.get("target_short_name")).isEqualTo("Short");
        List<Map<String, Object>> current = currentUnitVersions(target);
        assertContiguous(current);
        Map<String, Object> now = unitOn(target, TODAY);
        assertThat(now.get("name")).isEqualTo("Renamed unit");
        assertThat(now.get("region_kind")).isEqualTo(REGIONAL);
        assertThat(now.get("is_delivery_unit")).isEqualTo(true);
        assertThat(now.get("short_name")).as("one-design ruling 2026-10-30 #906: the short name is on the version row").isEqualTo("Short");
        assertThat(fromStored(now.get("parent_org_unit_id"))).as("untouched attributes are carried").isEqualTo(parent);
        assertThat(now.get("status")).isEqualTo(ACTIVE);
        assertThat(now.get("change_org_structure_command_id")).isEqualTo(command);
        Map<String, Object> before = unitOn(target, TODAY.minusDays(1));
        assertThat(before.get("name")).isEqualTo(seededName(target));
        assertThat(before.get("region_kind")).isEqualTo(HEADQUARTERS);
        assertThat(before.get("effective_to_date")).isEqualTo(TODAY);
        assertThat(before.get("closed_at")).as("closed when the new row starts").isNotNull();
        assertThat(eventsOf(EVENT_ATTRIBUTES_SET, command)).isEqualTo(1);
        assertThat(orgEventsOf(command)).isEqualTo(1);
    }

    @Test
    @DisplayName("#906 ADR-0073 第 2 条第 1 项：只改一项也可以（至少一项即可）；只改名称时区域、交付单元标记沿用")
    void oneAttributeIsEnough() throws Exception {
        UUID target = seedUnit(null);

        unitCommand(SET_ATTRIBUTES, target, TODAY, TARGET_NAME, "Only the name");

        assertThat(unitOn(target, TODAY).get("name")).isEqualTo("Only the name");
        assertThat(unitOn(target, TODAY).get("region_kind")).isEqualTo(HEADQUARTERS);
        assertThat(unitOn(target, TODAY).get("is_delivery_unit")).isEqualTo(false);
    }

    @Test
    @DisplayName("#906 ADR-0073 第 2 条第 1 项、第 4 项（#905 DD ck_set_attributes_columns）：一项也不改被拒；名称/简称为空白或超长被拒（简称 50 字、名称 500 字可）；带上级、负责人、法人等其余内容被拒；调整属性只用于组织单元，用在岗位上被拒")
    void thereIsAtLeastOneAttributeAndNothingElse() throws Exception {
        UUID target = seedUnit(null);
        UUID someone = newEmployeeId();
        employee(someone, unit, null);
        long commands = orgCommandCount();

        assertRefused(() -> unitCommand(SET_ATTRIBUTES, target, TODAY));
        assertRefused(() -> unitCommand(SET_ATTRIBUTES, target, TODAY, TARGET_NAME, " "));
        assertRefused(() -> unitCommand(SET_ATTRIBUTES, target, TODAY, TARGET_NAME, "n".repeat(501)));
        assertRefused(() -> unitCommand(SET_ATTRIBUTES, target, TODAY, TARGET_SHORT_NAME, "s".repeat(51)));
        assertRefused(() -> unitCommand(SET_ATTRIBUTES, target, TODAY, TARGET_REGION_KIND, "ELSEWHERE"));
        assertRefused(() -> unitCommand(SET_ATTRIBUTES, target, TODAY, TARGET_NAME, "With parent", TARGET_PARENT_ORG_UNIT_ID, unit));
        assertRefused(() -> unitCommand(SET_ATTRIBUTES, target, TODAY, TARGET_NAME, "With head", TARGET_HEAD_EMPLOYEE_ID, someone));
        assertRefused(() -> unitCommand(SET_ATTRIBUTES, target, TODAY, TARGET_NAME, "With entity", TARGET_LEGAL_ENTITY_ID, legalEntity));
        assertRefused(() -> unitCommand(SET_ATTRIBUTES, target, TODAY, TARGET_NAME, "With code", TARGET_CODE, "NEWCODE"));
        UUID position = newPosition();
        assertRefused(() -> positionCommand(SET_ATTRIBUTES, position, TODAY, TARGET_NAME, "A position"));

        assertThat(orgCommandCount()).isEqualTo(commands);
        assertThat(unitOn(target, TODAY).get("name")).isEqualTo(seededName(target));
        assertThat(unitOn(target, TODAY.plusDays(1)).get("name")).isEqualTo(seededName(target));
        unitCommand(SET_ATTRIBUTES, target, TODAY, TARGET_NAME, "n".repeat(500), TARGET_SHORT_NAME, "s".repeat(50));
        assertThat(unitOn(target, TODAY).get("name")).as("500 and 50 characters pass").isEqualTo("n".repeat(500));
    }

    @Test
    @DisplayName("#906 one-design ruling 2026-10-30 #906、ADR-0073 第 2 条第 2 项、W2 第 2 节第 2、3 步：补登过去的生效日：不改写已有行，被拆开的区间作废并按原内容与原来源重新追加（迁入行仍是迁入渠道），受影响的行指向本命令作废，当前事实首尾相接；中间的后续命令原样保留")
    void aBackDatedAdjustmentSupersedesAndAppends() throws Exception {
        UUID target = seedUnit(null);
        UUID later = unitCommand(SET_ATTRIBUTES, target, TODAY.minusDays(10), TARGET_NAME, "Name B");
        long rowsBefore = unitVersions(target).size();

        UUID backDated = unitCommand(SET_ATTRIBUTES, target, TODAY.minusDays(20), TARGET_REGION_KIND, REGIONAL);

        assertThat(orgCommand(backDated).get("status")).as("a past date takes effect at entry").isEqualTo(EFFECTIVE);
        List<Map<String, Object>> current = currentUnitVersions(target);
        assertContiguous(current);
        assertThat(current.get(0).get("effective_from_date")).as("the timeline still starts where the migration put it").isEqualTo(SINCE);
        List<Map<String, Object>> all = unitVersions(target);
        assertThat(all.size()).as("rows are added, none rewritten or deleted").isGreaterThan((int) rowsBefore);
        List<Map<String, Object>> superseded = all.stream().filter(row -> row.get("superseded_at") != null).toList();
        assertThat(superseded).isNotEmpty().allSatisfy(row -> assertThat(row.get("superseding_org_structure_command_id")).isEqualTo(backDated));
        Map<String, Object> beforeAll = unitOn(target, TODAY.minusDays(25));
        assertThat(beforeAll.get("region_kind")).as("before the back-dated day nothing changed").isEqualTo(HEADQUARTERS);
        assertThat(beforeAll.get("name")).isEqualTo(seededName(target));
        assertThat(beforeAll.get("change_channel_kind")).as("one-design ruling 2026-10-30 #906, W2 section 2 step 3: unchanged content keeps its original source").isEqualTo("MIGRATION");
        assertThat(beforeAll.get("change_org_structure_command_id")).isNull();
        Map<String, Object> between = unitOn(target, TODAY.minusDays(15));
        assertThat(between.get("region_kind")).isEqualTo(REGIONAL);
        assertThat(between.get("name")).isEqualTo(seededName(target));
        assertThat(between.get("change_org_structure_command_id")).as("changed content takes the back-dating command").isEqualTo(backDated);
        assertThat(between.get("change_channel_kind")).isEqualTo("COMMAND");
        assertThat(superseded.stream().map(row -> row.get("org_unit_version_id"))).as("and points at the row it replaced").contains(between.get("corrected_from_version_id"));
        Map<String, Object> afterLater = unitOn(target, TODAY);
        assertThat(afterLater.get("name")).as("the later command's adjustment is kept").isEqualTo("Name B");
        assertThat(afterLater.get("region_kind")).as("and the back-dated attribute reaches today").isEqualTo(REGIONAL);
        assertThat(afterLater.get("change_org_structure_command_id")).as("the mixed row now carries the back-dated attribute").isEqualTo(backDated);
        assertThat(superseded.stream().map(row -> row.get("org_unit_version_id"))).contains(afterLater.get("corrected_from_version_id"));
        assertThat(current).anyMatch(row -> TODAY.minusDays(10).equals(row.get("effective_from_date")));
        assertThat(orgCommand(later).get("status")).as("the later command is not touched").isEqualTo(EFFECTIVE);
        assertThat(all).as("the first command's rows are still on record").anyMatch(row -> later.equals(row.get("change_org_structure_command_id")));
    }

    @Test
    @DisplayName("#906 ADR-0073 第 2 条第 3 项：审计逐字段记改动前后值：名称、区域各记旧值与新值；没改的字段不记")
    void auditRecordsEachChangedFieldBeforeAndAfter() throws Exception {
        UUID target = seedUnit(null);
        Set<Object> audited = auditIds(AUDIT_ENTERED);

        unitCommand(SET_ATTRIBUTES, target, TODAY, TARGET_NAME, "Audit new name", TARGET_REGION_KIND, REGIONAL);

        String text = auditTextAdded(AUDIT_ENTERED, audited);
        assertThat(text).contains(seededName(target), "Audit new name", HEADQUARTERS, REGIONAL);
        assertThat(auditEntriesBy(orgAdmin.userId(), AUDIT_ENTERED)).isNotEmpty();
        UUID other = seedUnit(null);
        Set<Object> second = auditIds(AUDIT_ENTERED);

        unitCommand(SET_ATTRIBUTES, other, TODAY, TARGET_NAME, "Only the name changes");

        assertThat(auditTextAdded(AUDIT_ENTERED, second)).contains(seededName(other), "Only the name changes").doesNotContain(HEADQUARTERS).doesNotContain(REGIONAL);
    }
}
