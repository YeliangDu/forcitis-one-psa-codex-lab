package com.forcitis.one.acceptance.organization.workforce;

import static com.forcitis.one.acceptance.organization.workforce.PersonnelCommandNames.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import com.forcitis.one.TestDatabase;
import com.forcitis.one.TestPostgres;
import com.forcitis.one.platform.identity.api.Permission;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * #871 IMP-1030-PAR-B2 (ADR-0068 item 1.6 line 59; user decision 2026-10-30 「人事专员」; playbook acceptance step 5): one permission,
 * {@code PersonnelCommandNames.PERMISSION}, enters and withdraws personnel commands. Holders are accounts carrying that permission; the
 * others are real sessions of the seeded roles. Data scope (which employees) is out of scope (#729, #730).
 */
class PersonnelCommandPermissionIT extends PersonnelCommandSupport {

    static final TestDatabase postgres = TestPostgres.database(PersonnelCommandPermissionIT.class, TestPostgres.MIGRATED).withUrlParam("currentSchema", "platform_approval");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        postgres.register(registry);
    }

    private void assertEnterRefused(Throwable failure, UUID employee, long commandsBefore, int auditedBefore) throws Exception {
        assertThat(failure).as("the entry is refused").isNotNull();
        assertThat(isPermissionRefusal(failure)).as("as a refusal of the caller's rights, not of the content: " + failure).isTrue();
        assertThat(commandCount(employee)).as("no command row").isEqualTo(commandsBefore);
        assertThat(auditCount(AUDIT_ENTERED)).as("and no entry audit").isEqualTo(auditedBefore);
    }

    @Test
    @DisplayName("#871 ADR-0068 第 1 条第 6 项（行 59）、用户决定「人事专员」：持有人事命令权限的账号可以录入并撤回；撤回审计的操作人是该账号")
    void theHolderCanEnterAndWithdraw() throws Exception {
        UUID employee = employed(PROBATION);

        UUID command = enter(employee, REGULARIZE, TODAY.plusDays(7));
        commands().withdraw(command, "synthetic: holder withdraws");

        assertThat(command(command).get("status")).isEqualTo(WITHDRAWN);
        assertThat(auditEntriesBy(hr.userId(), AUDIT_WITHDRAWN)).isNotEmpty();
    }

    @ParameterizedTest(name = "role {0}")
    @ValueSource(strings = {"super_admin", "org_data_admin", "business_admin", "security_auditor", "process_administrator", "employee"})
    @DisplayName("#871 ADR-0068 第 1 条第 6 项（行 59）、验收手册第 5 步：不持有该权限的账号（含其他管理员角色、超级管理员、只有默认员工角色的人）录入被拒，不留命令行、不写审计")
    void everyOtherSeededRoleIsRefusedWhenEntering(String role) throws Exception {
        UUID employee = employed(PROBATION);
        Staff other = role.equals("employee") ? staff() : staff(role);
        long commands = commandCount(employee);
        int audited = auditCount(AUDIT_ENTERED);
        actInSession(other.session());

        Throwable failure = catchThrowable(() -> enter(employee, REGULARIZE, TODAY.plusDays(7)));

        assertEnterRefused(failure, employee, commands, audited);
    }

    @ParameterizedTest(name = "role {0}")
    @ValueSource(strings = {"super_admin", "org_data_admin", "business_admin", "security_auditor", "process_administrator", "employee"})
    @DisplayName("#871 ADR-0068 第 1 条第 6 项（行 59）：撤回同样须持有该权限：其他角色撤回被拒，命令保持待生效，不写撤回审计")
    void everyOtherSeededRoleIsRefusedWhenWithdrawing(String role) throws Exception {
        UUID command = enter(employed(PROBATION), REGULARIZE, TODAY.plusDays(7));
        Staff other = role.equals("employee") ? staff() : staff(role);
        int audited = auditCount(AUDIT_WITHDRAWN);
        actInSession(other.session());

        Throwable failure = catchThrowable(() -> commands().withdraw(command, "synthetic: not allowed"));

        assertThat(failure).isNotNull();
        assertThat(isPermissionRefusal(failure)).as(String.valueOf(failure)).isTrue();
        assertThat(command(command).get("status")).isEqualTo(PENDING);
        assertThat(command(command).get("withdrawn_by")).isNull();
        assertThat(auditCount(AUDIT_WITHDRAWN)).isEqualTo(audited);
    }

    @Test
    @DisplayName("#871 验收手册第 5 步（无角色）：没有任何权限的已登录账号、以及未登录的调用，录入和撤回都被拒")
    void noRoleAndNoSessionAreRefused() throws Exception {
        UUID employee = employed(PROBATION);
        UUID command = enter(employee, REGULARIZE, TODAY.plusDays(7));
        long commands = commandCount(employee);
        int audited = auditCount(AUDIT_ENTERED);
        Staff nobody = staff();

        actWith(nobody);
        assertEnterRefused(catchThrowable(() -> enter(employee, REGULARIZE, TODAY.plusDays(8))), employee, commands, audited);
        assertThat(isPermissionRefusal(catchThrowable(() -> commands().withdraw(command, "synthetic: no rights")))).isTrue();

        SecurityContextHolder.clearContext();
        assertEnterRefused(catchThrowable(() -> enter(employee, REGULARIZE, TODAY.plusDays(8))), employee, commands, audited);
        assertThat(isPermissionRefusal(catchThrowable(() -> commands().withdraw(command, "synthetic: no session")))).isTrue();
        assertThat(command(command).get("status")).isEqualTo(PENDING);
    }

    @Test
    @DisplayName("#871 验收手册第 5 步（多角色）：同时持有人事命令权限和另一个角色的账号照常可录入、撤回；同时持有两个都没有该权限的真实角色（组织数据管理员加安全审计员）仍被拒")
    void aUserWithTwoRoles() throws Exception {
        UUID employee = employed(PROBATION);
        Staff both = staff();
        actWith(both, PERMISSION, Permission.Codes.ORGANIZATION_WORKFORCE_ORG_UNIT_READ, Permission.Codes.ORGANIZATION_WORKFORCE_LEGAL_ENTITY_UPDATE,
                Permission.Codes.ORGANIZATION_WORKFORCE_SEPARATION_HOLD_APPROVE);

        UUID command = enter(employee, REGULARIZE, TODAY.plusDays(7));
        commands().withdraw(command, "synthetic: holder with another role");

        assertThat(command(command).get("withdrawn_by")).isEqualTo(both.userId());
        Staff neither = staff("org_data_admin", "security_auditor");
        long commands = commandCount(employee);
        int audited = auditCount(AUDIT_ENTERED);
        actInSession(neither.session());
        assertEnterRefused(catchThrowable(() -> enter(employee, REGULARIZE, TODAY.plusDays(9))), employee, commands, audited);
    }

    @Test
    @DisplayName("#871 越权（验收手册第 5 步）：持有放行待放行离职权限（安全审计员）的账号不能撤回待放行命令，命令与暂停记录都保持待放行")
    void thePermissionToReleaseAHoldIsNotThePermissionToWithdraw() throws Exception {
        Held held = held();
        actInSession(staff("security_auditor").session());

        Throwable failure = catchThrowable(() -> commands().withdraw(held.command(), "synthetic: auditor tries"));

        assertThat(isPermissionRefusal(failure)).as(String.valueOf(failure)).isTrue();
        assertThat(command(held.command()).get("status")).isEqualTo(HELD);
        assertThat(rows("select status from " + SEPARATION_HOLDS + " where separation_hold_id = ?", held.hold()).get(0).get("status")).isEqualTo(HELD);
    }

    @Test
    @DisplayName("#871 越权：权限判断先于业务校验，无权限的人对不存在的命令和对存在的命令得到同一种拒绝，不能借此探测命令是否存在；无权限的人用不存在的员工录入也只得到权限拒绝")
    void theRightsAreCheckedBeforeTheContentSoNothingLeaks() throws Exception {
        UUID existing = enter(employed(PROBATION), REGULARIZE, TODAY.plusDays(7));
        actInSession(staff("org_data_admin").session());

        Throwable forExisting = catchThrowable(() -> commands().withdraw(existing, "synthetic: probing"));
        Throwable forAbsent = catchThrowable(() -> commands().withdraw(UUID.randomUUID(), "synthetic: probing"));
        Throwable forAbsentEmployee = catchThrowable(() -> enter(newEmployeeId(), REGULARIZE, TODAY));

        assertThat(isPermissionRefusal(forExisting)).isTrue();
        assertThat(isPermissionRefusal(forAbsent)).as(String.valueOf(forAbsent)).isTrue();
        assertThat(forAbsent.getClass()).isEqualTo(forExisting.getClass());
        assertThat(isPermissionRefusal(forAbsentEmployee)).as(String.valueOf(forAbsentEmployee)).isTrue();
    }
}
