package com.forcitis.one.acceptance.organization.workforce;

import static com.forcitis.one.acceptance.organization.workforce.OrgStructureCommandNames.*;
import static com.forcitis.one.acceptance.organization.workforce.PersonnelCommandNames.PENDING;
import static com.forcitis.one.acceptance.organization.workforce.PersonnelCommandNames.PERSONNEL_SPECIALIST_ROLE;
import static com.forcitis.one.acceptance.organization.workforce.PersonnelCommandNames.PROBATION;
import static com.forcitis.one.acceptance.organization.workforce.PersonnelCommandNames.REGULAR;
import static com.forcitis.one.acceptance.organization.workforce.PersonnelCommandNames.REGULARIZE;
import static com.forcitis.one.acceptance.organization.workforce.PersonnelCommandNames.WITHDRAWN;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import com.forcitis.one.TestDatabase;
import com.forcitis.one.TestPostgres;
import com.jayway.jsonpath.JsonPath;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * #906 IMP-1030-ORG-F2a (ADR-0073 item 2.4 line 45 and ADR-0027 item 2.1: the 组织数据管理员 enters org-structure commands; playbook acceptance
 * step 5): one permission, {@code OrgStructureCommandNames.PERMISSION}, held by the seeded role org_data_admin, enters and withdraws org-structure
 * commands and rehearses a merge (assumption: the rehearsal lists employees, so it needs the same permission). Callers are real sessions: the role
 * is granted by the operations command and the account signs in. The permission of the personnel specialist and this one are separate: neither
 * role can do the other's commands.
 */
class OrgStructureCommandPermissionIT extends OrgStructureCommandSupport {

    static final TestDatabase postgres = TestPostgres.database(OrgStructureCommandPermissionIT.class, TestPostgres.MIGRATED).withUrlParam("currentSchema", "platform_approval");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        postgres.register(registry);
    }

    private List<String> permissionsOf(Staff who) throws Exception {
        String body = mvc.perform(get("/api/v1/platform/sessions:current").with(session(who.session()))).andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.permissionCodes");
    }

    private void assertNothingWritten(long commands, long units, long positions, int audited) throws Exception {
        assertThat(orgCommandCount()).as("no command row").isEqualTo(commands);
        assertThat(unitCount()).as("no unit row").isEqualTo(units);
        assertThat(positionCount()).as("no position row").isEqualTo(positions);
        assertThat(auditIds(AUDIT_ENTERED)).as("no entry audit").hasSize(audited);
    }

    @Test
    @DisplayName("#906 ADR-0073 第 2 条第 4 项（行 45）、ADR-0027 第 2 项第 1 条：组织数据管理员角色（org_data_admin）的真实登录会话持有该权限码，人事专员不持有")
    void onlyTheOrgDataAdminRoleCarriesThePermission() throws Exception {
        assertThat(permissionsOf(orgAdmin)).contains(PERMISSION);
        assertThat(hr).as("the seeded role personnel_specialist (#871)").isNotNull();
        assertThat(permissionsOf(hr)).doesNotContain(PERMISSION);
    }

    @Test
    @DisplayName("#906 ADR-0073 第 2 条第 4 项：持有人可以录入、预演合并、撤回；录入审计与撤回审计的操作人是该账号")
    void theHolderCanEnterPreviewAndWithdraw() throws Exception {
        UUID target = seedUnit(null);
        UUID command = unitCommand(SET_ATTRIBUTES, target, TODAY.plusDays(3), TARGET_NAME, "Holder plans");

        assertThat(orgCommands().previewMerge(target, seedUnit(null), TODAY)).isEmpty();
        orgCommands().withdraw(command, "synthetic: holder withdraws");

        assertThat(orgCommand(command).get("status")).isEqualTo(WITHDRAWN);
        assertThat(orgCommand(command).get("withdrawn_by")).isEqualTo(orgAdmin.userId());
        assertThat(auditEntriesBy(orgAdmin.userId(), AUDIT_ENTERED)).isNotEmpty();
        assertThat(auditEntriesBy(orgAdmin.userId(), AUDIT_WITHDRAWN)).isNotEmpty();
    }

    @ParameterizedTest(name = "role {0}")
    @ValueSource(strings = {PERSONNEL_SPECIALIST_ROLE, "super_admin", "business_admin", "security_auditor", "process_administrator", "employee"})
    @DisplayName("#906 one-design ruling 2026-10-30 #906（super_admin 无业务权限，ADR-0028 第 5 项）、ADR-0073 第 2 条第 4 项、验收手册第 5 步：不持有该权限的账号（人事专员、其他管理员角色、只有默认员工角色的人）录入组织单元与岗位命令被拒，不留命令行、单元、岗位，不写审计")
    void everyOtherSeededRoleIsRefusedWhenEntering(String role) throws Exception {
        UUID target = seedUnit(null);
        Staff other = role.equals("employee") ? staff() : staff(role);
        long commands = orgCommandCount();
        long units = unitCount();
        long positions = positionCount();
        int audited = auditIds(AUDIT_ENTERED).size();
        actIn(other);

        for (Throwable failure : List.of(catchThrowable(() -> createUnit(TODAY)), catchThrowable(() -> unitCommand(SET_ATTRIBUTES, target, TODAY, TARGET_NAME, "x")),
                catchThrowable(() -> createPosition(TODAY, "NOPE-" + UUID.randomUUID().toString().substring(0, 8), "x")))) {
            assertThat(failure).as("the entry is refused").isNotNull();
            assertThat(isPermissionRefusal(failure)).as("as a refusal of the caller's rights, not of the content: " + failure).isTrue();
        }

        assertNothingWritten(commands, units, positions, audited);
        assertThat(unitOn(target, TODAY).get("name")).isEqualTo(seededName(target));
    }

    @ParameterizedTest(name = "role {0}")
    @ValueSource(strings = {PERSONNEL_SPECIALIST_ROLE, "super_admin", "business_admin", "security_auditor", "process_administrator", "employee"})
    @DisplayName("#906 ADR-0073 第 2 条第 4 项：撤回与预演合并同样须持有该权限：其他角色撤回被拒（命令仍待生效、无撤回人、不写撤回审计），预演被拒")
    void everyOtherSeededRoleIsRefusedWhenWithdrawingOrPreviewing(String role) throws Exception {
        UUID target = seedUnit(null);
        UUID command = unitCommand(SET_ATTRIBUTES, target, TODAY.plusDays(3), TARGET_NAME, "Holder plans");
        Staff other = role.equals("employee") ? staff() : staff(role);
        int audited = auditIds(AUDIT_WITHDRAWN).size();
        actIn(other);

        Throwable withdrawal = catchThrowable(() -> orgCommands().withdraw(command, "synthetic: not allowed"));
        Throwable preview = catchThrowable(() -> orgCommands().previewMerge(target, seedUnitQuietly(), TODAY));

        assertThat(isPermissionRefusal(withdrawal)).as(String.valueOf(withdrawal)).isTrue();
        assertThat(isPermissionRefusal(preview)).as(String.valueOf(preview)).isTrue();
        assertThat(orgCommand(command).get("status")).isEqualTo(PENDING);
        assertThat(orgCommand(command).get("withdrawn_by")).isNull();
        assertThat(auditIds(AUDIT_WITHDRAWN)).hasSize(audited);
    }

    private UUID seedUnitQuietly() {
        try {
            return seedUnit(null);
        } catch (Exception failure) {
            throw new AssertionError(failure);
        }
    }

    @Test
    @DisplayName("#906 验收手册第 5 步（无角色、未登录）：没有任何角色的已登录账号，以及未登录的调用，录入和撤回都被拒")
    void noRoleAndNoSessionAreRefused() throws Exception {
        UUID command = unitCommand(SET_ATTRIBUTES, seedUnit(null), TODAY.plusDays(3), TARGET_NAME, "Holder plans");
        long commands = orgCommandCount();
        long units = unitCount();
        long positions = positionCount();
        int audited = auditIds(AUDIT_ENTERED).size();

        actIn(staff());
        assertThat(isPermissionRefusal(catchThrowable(() -> createUnit(TODAY)))).isTrue();
        assertThat(isPermissionRefusal(catchThrowable(() -> orgCommands().withdraw(command, "synthetic: no rights")))).isTrue();
        actAsNobody();
        assertThat(isPermissionRefusal(catchThrowable(() -> createUnit(TODAY)))).isTrue();
        assertThat(isPermissionRefusal(catchThrowable(() -> orgCommands().withdraw(command, "synthetic: no session")))).isTrue();

        assertNothingWritten(commands, units, positions, audited);
        assertThat(orgCommand(command).get("status")).isEqualTo(PENDING);
    }

    @Test
    @DisplayName("#906 验收手册第 5 步（多角色）：同时持有组织数据管理员和人事专员两个角色的账号两边的命令都能录；同时持有两个都没有该权限的真实角色（人事专员加安全审计员）仍不能录组织结构命令")
    void aUserWithTwoRoles() throws Exception {
        UUID employee = employed(PROBATION);
        Staff both = staff(ORG_DATA_ADMIN_ROLE, PERSONNEL_SPECIALIST_ROLE);
        actIn(both);

        assertThat(createUnit(TODAY)).isNotNull();
        assertThat(enter(employee, REGULARIZE, TODAY.plusDays(7))).isNotNull();

        Staff neither = staff(PERSONNEL_SPECIALIST_ROLE, "security_auditor");
        long commands = orgCommandCount();
        actIn(neither);
        assertThat(isPermissionRefusal(catchThrowable(() -> createUnit(TODAY)))).isTrue();
        assertThat(orgCommandCount()).isEqualTo(commands);
    }

    @Test
    @DisplayName("#906 越权（验收手册第 5 步）：组织数据管理员不能录人事命令（转正未生成命令行、版本不变）；人事专员不能录组织结构命令；两个权限互不包含")
    void thePermissionsOfTheTwoLinesAreSeparate() throws Exception {
        UUID employee = employedIn(seedUnit(null), PROBATION, true);
        long personnelBefore = commandCount(employee);
        actIn(orgAdmin);

        Throwable personnel = catchThrowable(() -> enter(employee, REGULARIZE, TODAY.plusDays(7)));

        assertThat(isPermissionRefusal(personnel)).as(String.valueOf(personnel)).isTrue();
        assertThat(commandCount(employee)).isEqualTo(personnelBefore);
        actInSpecialist();
        long commands = orgCommandCount();
        Throwable org = catchThrowable(() -> createUnit(TODAY));
        assertThat(isPermissionRefusal(org)).as(String.valueOf(org)).isTrue();
        assertThat(orgCommandCount()).isEqualTo(commands);
        assertThat(enter(employee, REGULARIZE, TODAY.plusDays(7))).as("and the specialist still does their own work").isNotNull();
    }

    @Test
    @DisplayName("#906 越权：权限判断先于业务校验：无权限的人对不存在的命令和对存在的命令撤回得到同一种拒绝，用不存在的单元录入也只得到权限拒绝，不能借此探测")
    void theRightsAreCheckedBeforeTheContentSoNothingLeaks() throws Exception {
        UUID existing = unitCommand(SET_ATTRIBUTES, seedUnit(null), TODAY.plusDays(3), TARGET_NAME, "Holder plans");
        actInSpecialist();

        Throwable forExisting = catchThrowable(() -> orgCommands().withdraw(existing, "synthetic: probing"));
        Throwable forAbsent = catchThrowable(() -> orgCommands().withdraw(UUID.randomUUID(), "synthetic: probing"));
        Throwable forAbsentUnit = catchThrowable(() -> unitCommand(DISSOLVE, UUID.randomUUID(), TODAY));

        assertThat(isPermissionRefusal(forExisting)).isTrue();
        assertThat(isPermissionRefusal(forAbsent)).as(String.valueOf(forAbsent)).isTrue();
        assertThat(forAbsent.getClass()).isEqualTo(forExisting.getClass());
        assertThat(isPermissionRefusal(forAbsentUnit)).as(String.valueOf(forAbsentUnit)).isTrue();
    }
}
