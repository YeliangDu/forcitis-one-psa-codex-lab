package com.forcitis.one.acceptance.organization.workforce;

import static com.forcitis.one.acceptance.organization.workforce.OrgStructureCommandNames.*;
import static com.forcitis.one.acceptance.organization.workforce.PersonnelCommandNames.ACTOR_TYPE_USER;
import static com.forcitis.one.acceptance.organization.workforce.PersonnelCommandNames.EFFECTIVE;
import static com.forcitis.one.acceptance.organization.workforce.PersonnelCommandNames.PENDING;
import static org.assertj.core.api.Assertions.assertThat;

import com.forcitis.one.TestDatabase;
import com.forcitis.one.TestPostgres;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * #906 IMP-1030-ORG-F2a (ADR-0068 item 4 and its attachment, columns of org_structure_commands; ADR-0073 item 3.3 initial attribution;
 * W2 section 2): creating an org unit. The server makes the unit's id and inserts its snapshot row in the command's transaction; a
 * command dated today or earlier takes effect in the entry transaction (ADR-0068 item 1.8), a future one stays PENDING with its version
 * already written; the initial legal entity rides on the command and needs no countersign.
 */
class OrgUnitCreateIT extends OrgStructureCommandSupport {

    static final TestDatabase postgres = TestPostgres.database(OrgUnitCreateIT.class, TestPostgres.MIGRATED).withUrlParam("currentSchema", "platform_approval");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        postgres.register(registry);
    }

    @Test
    @DisplayName("#906 ADR-0068 第 4 条、附件「组织结构命令」列说明：新建无上级的根组织单元：命令 ID 为服务端的 UUIDv7、录入人为调用者；目标组织单元行同事务建好；写入首个版本（COMMAND 渠道、指向本命令、当天起开放），区域与交付单元标记取命令所带")
    void creatingARootUnit() throws Exception {
        UUID command = createUnit(TODAY, TARGET_CODE, "NEW-" + UUID.randomUUID().toString().substring(0, 8), TARGET_NAME, "Synthetic root unit", TARGET_SHORT_NAME, "Root", TARGET_REGION_KIND, REGIONAL,
                TARGET_IS_DELIVERY_UNIT, true);

        assertThat(command.version()).as("UUIDv7").isEqualTo(7);
        Map<String, Object> row = orgCommand(command);
        assertThat(row.get("command_kind")).isEqualTo(CREATE);
        assertThat(row.get("target_kind")).isEqualTo(ORG_UNIT);
        assertThat(row.get("status")).isEqualTo(EFFECTIVE);
        assertThat(row.get("applied_at")).isEqualTo(NOW);
        assertThat(row.get("created_by")).as("the recorder is the signed-in account").isEqualTo(orgAdmin.userId());
        assertThat(row.get("target_parent_org_unit_id")).isNull();
        UUID created = targetUnitOf(command);
        assertThat(rows("select name from " + ORG_UNITS + " where id = ?", stored(created))).as("the snapshot row of the new unit").hasSize(1);
        List<Map<String, Object>> versions = unitVersions(created);
        assertThat(versions).hasSize(1);
        Map<String, Object> first = versions.get(0);
        assertThat(first.get("name")).isEqualTo("Synthetic root unit");
        assertThat(first.get("code")).isEqualTo(row.get("target_code"));
        assertThat(first.get("short_name")).as("one-design ruling 2026-10-30 #906").isEqualTo("Root");
        assertThat(first.get("parent_org_unit_id")).isNull();
        assertThat(first.get("status")).isEqualTo(ACTIVE);
        assertThat(first.get("region_kind")).isEqualTo(REGIONAL);
        assertThat(first.get("is_delivery_unit")).isEqualTo(true);
        assertThat(first.get("effective_from_date")).isEqualTo(TODAY);
        assertThat(first.get("effective_to_date")).isNull();
        assertThat(first.get("change_channel_kind")).isEqualTo("COMMAND");
        assertThat(first.get("change_org_structure_command_id")).isEqualTo(command);
    }

    @Test
    @DisplayName("#906 ADR-0068 第 4 条、附件列说明「新建组织单元的上级可空（根）」：带上级新建时版本的上级是该单元；第二个命令得到不同的单元和不同的命令 ID")
    void creatingAUnitUnderAParent() throws Exception {
        UUID parent = seedUnit(null);

        UUID first = createUnit(TODAY, TARGET_PARENT_ORG_UNIT_ID, parent);
        UUID second = createUnit(TODAY, TARGET_PARENT_ORG_UNIT_ID, parent);

        assertThat(fromStored(unitOn(targetUnitOf(first), TODAY).get("parent_org_unit_id"))).isEqualTo(parent);
        assertThat(targetUnitOf(second)).isNotEqualTo(targetUnitOf(first));
        assertThat(second).isNotEqualTo(first);
        assertThat(orgCommands().enterComponents()).as("the caller cannot choose the new unit's id, the status or the recorder")
                .noneMatch(name -> name.equalsIgnoreCase("id") || name.equalsIgnoreCase(ORG_STRUCTURE_COMMAND_ID) || name.equalsIgnoreCase("createdBy")
                        || name.equalsIgnoreCase("status") || name.equalsIgnoreCase("appliedAt"));
    }

    @Test
    @DisplayName("#906 ADR-0073 第 3 条第 3 款（初始归属不会签）、第 2 条第 2 款：新建时可带初始法人，当场生效；单元法人归属期间自生效日起开放、指向本命令；不带则不写归属")
    void anInitialLegalEntityNeedsNoCountersign() throws Exception {
        UUID with = createUnit(TODAY, TARGET_LEGAL_ENTITY_ID, legalEntity);
        UUID without = createUnit(TODAY);

        assertThat(orgCommand(with).get("status")).as("no countersign for an initial attribution").isEqualTo(EFFECTIVE);
        List<Map<String, Object>> attributions = rows("select * from " + ORG_UNIT_LEGAL_ENTITIES + " where org_unit_id = ?", stored(targetUnitOf(with)));
        assertThat(attributions).hasSize(1);
        assertThat(attributions.get(0).get("legal_entity_id")).isEqualTo(legalEntity);
        assertThat(attributions.get(0).get("effective_from_date")).isEqualTo(TODAY);
        assertThat(attributions.get(0).get("effective_to_date")).isNull();
        assertThat(attributions.get(0).get("change_org_structure_command_id")).isEqualTo(with);
        assertThat(rows("select 1 from " + ORG_UNIT_LEGAL_ENTITIES + " where org_unit_id = ?", stored(targetUnitOf(without)))).isEmpty();
    }

    @Test
    @DisplayName("#906 ADR-0068 附件 ck_org_structure_commands_name、_code、_create_columns 与 ck_unit_attributes：名称必填且 1 至 500 字、不为空白，编码不为空白且至多 500 字，交付单元标记新建必填，上级与法人必须存在；被拒的新建不留命令行、不留组织单元")
    void aBadCreateLeavesNothingBehind() throws Exception {
        long commands = orgCommandCount();
        long units = unitCount();
        List<Object[]> bad = List.of(
                new Object[] {TARGET_NAME, " "},
                new Object[] {TARGET_NAME, "x".repeat(501)},
                new Object[] {TARGET_NAME, null},
                new Object[] {TARGET_CODE, " "},
                new Object[] {TARGET_CODE, "c".repeat(501)},
                new Object[] {TARGET_IS_DELIVERY_UNIT, null},
                new Object[] {TARGET_PARENT_ORG_UNIT_ID, UUID.randomUUID()},
                new Object[] {TARGET_LEGAL_ENTITY_ID, UUID.randomUUID()},
                new Object[] {TARGET_REGION_KIND, "ELSEWHERE"});
        for (Object[] one : bad) {
            assertRefused(() -> createUnit(TODAY, one[0], one[1]));
        }

        assertThat(orgCommandCount()).as("no command row").isEqualTo(commands);
        assertThat(unitCount()).as("no unit row").isEqualTo(units);
        assertThat(targetUnitOf(createUnit(TODAY, TARGET_NAME, "n".repeat(500), TARGET_CODE, "c".repeat(500)))).as("the boundary values 500 pass").isNotNull();
    }

    @Test
    @DisplayName("#906 ADR-0068 第 4 条、附件 ck_org_structure_commands_create_columns：新建不得带负责人、区域应收负责人、合并目标；也不得借新建指定已有单元（调用方不提供新单元的 ID）")
    void aCreateCarriesNoOtherContent() throws Exception {
        UUID head = newEmployeeId();
        employee(head, unit, null);
        long commands = orgCommandCount();

        assertRefused(() -> createUnit(TODAY, TARGET_HEAD_EMPLOYEE_ID, head));
        assertRefused(() -> createUnit(TODAY, TARGET_RECEIVABLES_MANAGER_EMPLOYEE_ID, head));
        assertRefused(() -> createUnit(TODAY, MERGE_INTO_ORG_UNIT_ID, unit));
        assertRefused(() -> createUnit(TODAY, TARGET_ORG_UNIT_ID, unit));

        assertThat(orgCommandCount()).isEqualTo(commands);
    }

    @Test
    @DisplayName("#906 ADR-0068 第 1 条第 8 项（行 66）：新建在今天或更早生效的当场生效并发布一次「组织单元已新建」；状态转换记入 state_transitions、写录入审计一条")
    void takingEffectIsAuditedPublishedAndRecorded() throws Exception {
        Set<Object> audited = auditIds(AUDIT_ENTERED);

        UUID command = createUnit(TODAY.minusDays(3));

        assertThat(orgCommand(command).get("status")).isEqualTo(EFFECTIVE);
        assertLawfulOrgLifecycle(transitions(AGGREGATE_ORG_COMMAND, command), EFFECTIVE);
        assertThat(transitions(AGGREGATE_ORG_COMMAND, command).getLast().get("actor_id")).isEqualTo(orgAdmin.userId());
        assertThat(transitions(AGGREGATE_ORG_COMMAND, command).getLast().get("actor_type")).isEqualTo(ACTOR_TYPE_USER);
        assertThat(eventsOf(EVENT_ORG_UNIT_CREATED, command)).isEqualTo(1);
        assertThat(orgEventsOf(command)).as("no other org event").isEqualTo(1);
        assertThat(auditIds(AUDIT_ENTERED)).hasSize(audited.size() + 1);
        assertThat(unitOn(targetUnitOf(command), TODAY.minusDays(3)).get("effective_from_date")).isEqualTo(TODAY.minusDays(3));
        assertThat(unitOn(targetUnitOf(command), TODAY.minusDays(4))).as("nothing before the unit exists").isNull();
    }

    @Test
    @DisplayName("#906 ADR-0068 第 1 条第 8 项（行 65 至 66）：生效日在将来的新建保持 PENDING、applied_at 为空，首个版本已写入并从生效日起；按今天读还没有这个单元，按生效日读有；录入不发事件")
    void aFutureCreateStaysPending() throws Exception {
        LocalDate day = TODAY.plusDays(12);

        UUID command = createUnit(day);

        assertThat(orgCommand(command).get("status")).isEqualTo(PENDING);
        assertThat(orgCommand(command).get("applied_at")).isNull();
        UUID created = targetUnitOf(command);
        assertThat(unitOn(created, TODAY)).isNull();
        assertThat(unitOn(created, day).get("change_org_structure_command_id")).isEqualTo(command);
        assertLawfulOrgLifecycle(transitions(AGGREGATE_ORG_COMMAND, command), PENDING);
        assertThat(orgEventsOf(command)).as("events only when it takes effect").isZero();
    }
}
