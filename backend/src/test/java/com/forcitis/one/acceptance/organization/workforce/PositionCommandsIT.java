package com.forcitis.one.acceptance.organization.workforce;

import static com.forcitis.one.acceptance.organization.workforce.OrgStructureCommandNames.*;
import static com.forcitis.one.acceptance.organization.workforce.PersonnelCommandNames.EFFECTIVE;
import static com.forcitis.one.acceptance.organization.workforce.PersonnelCommandNames.PENDING;
import static com.forcitis.one.acceptance.organization.workforce.PersonnelCommandNames.REGULAR;
import static org.assertj.core.api.Assertions.assertThat;

import com.forcitis.one.TestDatabase;
import com.forcitis.one.TestPostgres;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * #906 IMP-1030-ORG-F2a (ADR-0068 item 4: position create and disable, no duties or headcount; attachment: position code company-wide
 * unique and nullable, DISABLE like DISSOLVE refused while people hold the position; user decision 2026-10-21 quoted in the #904 DD: codes are
 * company-unique and never reused; W2 section 3.3 for position_versions). Positions have no delete (ADR-0068 item 15.4).
 */
class PositionCommandsIT extends OrgStructureCommandSupport {

    static final TestDatabase postgres = TestPostgres.database(PositionCommandsIT.class, TestPostgres.MIGRATED).withUrlParam("currentSchema", "platform_approval");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        postgres.register(registry);
    }

    private static String code() {
        return "POS-" + UUID.randomUUID().toString().substring(0, 8);
    }

    @Test
    @DisplayName("#906 ADR-0068 第 4 条、附件列说明与 #904 DD position_versions：新建岗位：目标岗位行同事务建好，首个版本 ACTIVE、带编码与名称、COMMAND 渠道指向本命令；命令 ID 为 UUIDv7、录入人为调用者；发一次「岗位已新建」")
    void creatingAPosition() throws Exception {
        String code = code();

        UUID command = createPosition(TODAY, code, "Synthetic engineer");

        assertThat(command.version()).isEqualTo(7);
        Map<String, Object> row = orgCommand(command);
        assertThat(row.get("target_kind")).isEqualTo(POSITION);
        assertThat(row.get("command_kind")).isEqualTo(CREATE);
        assertThat(row.get("status")).isEqualTo(EFFECTIVE);
        assertThat(row.get("created_by")).isEqualTo(orgAdmin.userId());
        assertThat(row.get("target_org_unit_id")).isNull();
        UUID position = targetPositionOf(command);
        assertThat(rows("select name from " + POSITIONS + " where id = ?", stored(position))).as("the snapshot row of the new position").hasSize(1);
        Map<String, Object> version = positionOn(position, TODAY);
        assertThat(version.get("code")).isEqualTo(code);
        assertThat(version.get("name")).isEqualTo("Synthetic engineer");
        assertThat(version.get("status")).isEqualTo(ACTIVE);
        assertThat(version.get("effective_from_date")).isEqualTo(TODAY);
        assertThat(version.get("effective_to_date")).isNull();
        assertThat(version.get("change_channel_kind")).isEqualTo("COMMAND");
        assertThat(version.get("change_org_structure_command_id")).isEqualTo(command);
        assertThat(positionOn(position, TODAY.minusDays(1))).isNull();
        assertLawfulOrgLifecycle(transitions(AGGREGATE_ORG_COMMAND, command), EFFECTIVE);
        assertThat(eventsOf(EVENT_POSITION_CREATED, command)).isEqualTo(1);
        assertThat(orgEventsOf(command)).isEqualTo(1);
    }

    @Test
    @DisplayName("#906 ADR-0068 附件列说明「岗位编码全公司唯一、可空」、用户 2026-10-21「编码全公司唯一、不复用」：同一编码不能新建两次；已停用岗位的编码也不能再用；与同步来的岗位快照行的编码相同也被拒；被拒不留命令行")
    void aCodeIsCompanyUniqueAndNeverReused() throws Exception {
        String code = code();
        UUID first = createPosition(TODAY.minusDays(1), code, "First");
        long commands = orgCommandCount();

        assertRefused(() -> createPosition(TODAY, code, "Second with the same code"));
        UUID firstPosition = targetPositionOf(first);
        positionCommand(DISABLE, firstPosition, TODAY);
        assertThat(positionOn(firstPosition, TODAY).get("status")).isEqualTo(DISABLED);
        assertRefused(() -> createPosition(TODAY, code, "Reusing a disabled position's code"));
        UUID synced = newPosition();
        String syncedCode = (String) rows("select code from " + POSITIONS + " where id = ?", stored(synced)).get(0).get("code");
        assertRefused(() -> createPosition(TODAY, syncedCode, "Same code as a synchronized position"));

        assertThat(orgCommandCount()).as("only the first create and the disable were recorded").isEqualTo(commands + 1);
    }

    @Test
    @DisplayName("#906 ADR-0068 附件 ck_org_structure_commands_code、_name 与 #904 DD position_versions 的 code、name 检查：编码可空（两个无编码岗位并存）；编码空白或超过 500 字被拒，500 字可；名称必填、不空白、至多 500 字；岗位新建不带简称、上级、区域标记")
    void codeAndNameBoundaries() throws Exception {
        long positions = positionCount();

        assertThat(createPosition(TODAY, null, "Without code")).isNotNull();
        assertThat(createPosition(TODAY, null, "Also without code")).isNotNull();
        assertThat(createPosition(TODAY, "c".repeat(500), "n".repeat(500))).as("500 characters pass").isNotNull();
        long after = positionCount();
        assertRefused(() -> createPosition(TODAY, " ", "Blank code"));
        assertRefused(() -> createPosition(TODAY, "d".repeat(501), "Long code"));
        assertRefused(() -> createPosition(TODAY, code(), " "));
        assertRefused(() -> createPosition(TODAY, code(), "n".repeat(501)));
        assertRefused(() -> createPosition(TODAY, code(), null));
        assertRefused(() -> orgCommands().enter(fields(TARGET_KIND, POSITION, COMMAND_KIND, CREATE, EFFECTIVE_DATE, TODAY, TARGET_CODE, code(), TARGET_NAME, "With a short name",
                TARGET_SHORT_NAME, "short")));
        assertRefused(() -> orgCommands().enter(fields(TARGET_KIND, POSITION, COMMAND_KIND, CREATE, EFFECTIVE_DATE, TODAY, TARGET_CODE, code(), TARGET_NAME, "With a parent",
                TARGET_PARENT_ORG_UNIT_ID, unit)));
        assertRefused(() -> orgCommands().enter(fields(TARGET_KIND, POSITION, COMMAND_KIND, CREATE, EFFECTIVE_DATE, TODAY, TARGET_CODE, code(), TARGET_NAME, "With a region",
                TARGET_REGION_KIND, REGIONAL)));

        assertThat(positionCount()).as("no position row left by a refusal").isEqualTo(after);
        assertThat(after).isEqualTo(positions + 3);
    }

    @Test
    @DisplayName("#906 ADR-0068 第 4 条、第 15 条第 4 项（岗位状态为 ACTIVE、DISABLED，不设删除）：没有在任员工的岗位可以停用：当天生效，版本 DISABLED（前一版本在当天关闭）、编码名称沿用，发一次「岗位已停用」；岗位只有新建与停用两种命令，用组织单元的种类被拒")
    void disablingAFreePosition() throws Exception {
        String code = code();
        UUID position = targetPositionOf(createPosition(TODAY.minusDays(2), code, "Free position"));

        UUID command = positionCommand(DISABLE, position, TODAY);

        assertThat(orgCommand(command).get("status")).isEqualTo(EFFECTIVE);
        assertContiguous(currentPositionVersions(position));
        Map<String, Object> disabled = positionOn(position, TODAY);
        assertThat(disabled.get("status")).isEqualTo(DISABLED);
        assertThat(disabled.get("code")).isEqualTo(code);
        assertThat(disabled.get("name")).isEqualTo("Free position");
        assertThat(disabled.get("change_org_structure_command_id")).isEqualTo(command);
        assertThat(positionOn(position, TODAY.minusDays(1)).get("status")).isEqualTo(ACTIVE);
        assertThat(eventsOf(EVENT_POSITION_DISABLED, command)).isEqualTo(1);
        assertThat(orgEventsOf(command)).isEqualTo(1);
        UUID another = targetPositionOf(createPosition(TODAY, code(), "Another"));
        assertRefused(() -> positionCommand(DISSOLVE, another, TODAY.plusDays(1)));
        assertRefused(() -> positionCommand(REPARENT, another, TODAY.plusDays(1)));
        assertRefused(() -> positionCommand(DISABLE, UUID.randomUUID(), TODAY.plusDays(1)));
    }

    @Test
    @DisplayName("#906 ADR-0068 附件列说明「停用岗位同理」（目标仍有在职员工的，应用层拒绝）：有在任员工的岗位，停用（今天或将来、该员工那天仍在任）被拒，不留命令行、岗位仍 ACTIVE；员工离职生效后可以停用")
    void aPositionWithAHolderCannotBeDisabled() throws Exception {
        UUID position = targetPositionOf(createPosition(TODAY.minusDays(40), code(), "Held position"));
        UUID holder = employedAs(seedUnit(null), REGULAR, position);
        assertThat(positionHeldBy(holder, TODAY)).isEqualTo(position);
        long commands = orgCommandCount();

        assertRefused(() -> positionCommand(DISABLE, position, TODAY));
        assertRefused(() -> positionCommand(DISABLE, position, TODAY.plusDays(5)));

        assertThat(orgCommandCount()).isEqualTo(commands);
        assertThat(positionOn(position, TODAY).get("status")).isEqualTo(ACTIVE);
        actInSpecialist();
        separate(holder, TODAY.minusDays(1));
        actIn(orgAdmin);
        assertThat(positionCommand(DISABLE, position, TODAY)).as("nobody holds it any more").isNotNull();
        assertThat(positionOn(position, TODAY).get("status")).isEqualTo(DISABLED);
    }

    @Test
    @DisplayName("#906 ADR-0068 附件 uk_org_structure_commands_open_position_per_day：同一岗位同一生效日只能有一条待生效命令；将来生效的停用保持 PENDING、版本已写入、今天仍 ACTIVE、不发事件；换一天不挡")
    void oneOpenCommandPerPositionAndDay() throws Exception {
        UUID position = targetPositionOf(createPosition(TODAY.minusDays(1), code(), "Position"));
        LocalDate day = TODAY.plusDays(7);

        UUID first = positionCommand(DISABLE, position, day);

        assertThat(orgCommand(first).get("status")).isEqualTo(PENDING);
        assertThat(positionOn(position, TODAY).get("status")).isEqualTo(ACTIVE);
        assertThat(positionOn(position, day).get("status")).isEqualTo(DISABLED);
        assertThat(orgEventsOf(first)).isZero();
        assertRefused(() -> positionCommand(DISABLE, position, day));
        assertThat(positionCommand(DISABLE, position, day.plusDays(1))).as("another day").isNotNull();
        List<Map<String, Object>> open = rows("select 1 from " + ORG_COMMANDS + " where target_position_id = ? and status = 'PENDING'", stored(position));
        assertThat(open).hasSize(2);
    }
}
