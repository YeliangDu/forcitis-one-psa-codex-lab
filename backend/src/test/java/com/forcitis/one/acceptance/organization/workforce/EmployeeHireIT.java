package com.forcitis.one.acceptance.organization.workforce;

import static com.forcitis.one.acceptance.organization.workforce.PersonnelCommandNames.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import com.forcitis.one.TestDatabase;
import com.forcitis.one.TestPostgres;
import com.forcitis.one.kernel.DomainException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * #959 (W2 section 3.1 and section 7 items 1, 2; ADR-0068 item 1.5; user answers of 2026-10-30, Q1 and Q2; one-design DD on #959): a HIRE
 * of a new person creates the employee root with the profile it carries and the first versions, in one transaction. The employee code is
 * never supplied by the caller: the system numbers it through platform.numbering under the confirmed template of the employee code type.
 * A collision of the issued code or a WeCom user id already held by anyone (separated people too) refuses the whole hire and leaves nothing.
 */
class EmployeeHireIT extends EmployeeHireSupport {

    static final TestDatabase postgres = TestPostgres.database(EmployeeHireIT.class, TestPostgres.MIGRATED).withUrlParam("currentSchema", "platform_approval");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        postgres.register(registry);
    }

    @BeforeEach
    void theEmployeeCodeTemplateIsConfirmed() throws Exception {
        Staff businessAdmin = staff(BUSINESS_ADMIN);
        confirmSeededNumberingRule(EMPLOYEE_DOCUMENT_TYPE, businessAdmin.session());
        actIn(hr);
    }

    @Test
    @DisplayName("#959 W2 第 3.1 节、ADR-0068 第 1 条第 5 项：新人入职一次建出员工根（姓名、性别、邮箱、企业微信账号按命令所带）、员工版本与任职版本；命令生效；工号由系统按确认后的模板发出")
    void aHireOfANewPersonCreatesTheEmployeeAndItsVersions() throws Exception {
        String wecom = syntheticWecom("hire");

        UUID command = hire("Synthetic new hire", "FEMALE", "new.hire@example.invalid", wecom);

        assertThat(command(command).get("status")).isEqualTo(EFFECTIVE);
        UUID employee = employeeOf(command);
        Map<String, Object> root = root(employee);
        assertThat(root.get("display_name")).isEqualTo("Synthetic new hire");
        assertThat(root.get("gender")).isEqualTo("FEMALE");
        assertThat(root.get("email")).isEqualTo("new.hire@example.invalid");
        assertThat(root.get("wecom_user_id")).isEqualTo(wecom);
        assertThat((String) root.get("employee_code")).as("numbered by the system").matches(EMPLOYEE_CODE_PATTERN);
        List<Map<String, Object>> versions = currentVersions(EMPLOYEE_VERSIONS, employee);
        assertThat(versions).hasSize(1);
        assertThat(versions.get(0).get("employment_status")).isEqualTo(EMPLOYED);
        assertThat(versions.get(0).get("employment_phase_kind")).isEqualTo(PROBATION);
        assertThat(versions.get(0).get("is_fee_earner")).isEqualTo(true);
        assertThat(versions.get(0).get("change_channel_kind")).isEqualTo("COMMAND");
        assertThat(versions.get(0).get("change_command_id")).isEqualTo(command);
        assertThat(versions.get(0).get("effective_from_date")).isEqualTo(TODAY);
        List<Map<String, Object>> assignments = currentVersions(ASSIGNMENT_VERSIONS, employee);
        assertThat(assignments).hasSize(1);
        assertThat(fromStored(assignments.get(0).get("org_unit_id"))).isEqualTo(unit);
        assertThat(assignments.get(0).get("change_command_id")).isEqualTo(command);
    }

    @Test
    @DisplayName("#959 用户 Q2：工号不由调用方提供——入职入口没有工号参数；两次入职得到两个不同的系统工号")
    void theCallerNeverSuppliesTheEmployeeCode() throws Exception {
        assertThat(commands().enterComponents()).as("EnterPersonnelCommand").doesNotContain(EMPLOYEE_CODE);

        String first = (String) root(employeeOf(hire("Synthetic first", null, null, null))).get("employee_code");
        String second = (String) root(employeeOf(hire("Synthetic second", null, null, null))).get("employee_code");

        assertThat(first).matches(EMPLOYEE_CODE_PATTERN);
        assertThat(second).matches(EMPLOYEE_CODE_PATTERN).isNotEqualTo(first);
    }

    @Test
    @DisplayName("#959 one-design 数据设计（撞号）：发出的工号已被某名员工（如迁入者）占用时整个入职拒收、不留任何行；计数随事务回滚，障碍移除后下一次入职拿到同一个号（不跳号）")
    void aCollidingCodeRefusesTheHireAndLeavesNoGap() throws Exception {
        String issued = (String) root(employeeOf(hire("Synthetic before", null, null, null))).get("employee_code");
        String next = "EMP" + String.format("%06d", Integer.parseInt(issued.substring(3)) + 1);
        UUID obstacle = rootWith(next, null, EMPLOYED);
        Counts before = counts();

        Throwable failure = catchThrowable(() -> hire("Synthetic collided", null, null, null));

        assertThat(failure).as("a business refusal").isInstanceOf(DomainException.class);
        assertThat(counts()).as("nothing written").isEqualTo(before);
        deleteRoot(obstacle);
        assertThat(root(employeeOf(hire("Synthetic after", null, null, null))).get("employee_code")).as("the counter rolled back with the refusal")
                .isEqualTo(next);
    }

    @Test
    @DisplayName("#959 用户 Q1、W2 第 3.1 节 N-05：企业微信账号全员唯一（含已离职者）：入职带已被占用的账号拒收，不留任何行")
    void aWecomUserIdHeldByAnyoneRefusesTheHire() throws Exception {
        String employedHolds = syntheticWecom("employed");
        rootWith("SYN-" + UUID.randomUUID(), employedHolds, EMPLOYED);
        String separatedHolds = syntheticWecom("separated");
        rootWith("SYN-" + UUID.randomUUID(), separatedHolds, SEPARATED);
        Counts before = counts();

        for (String taken : List.of(employedHolds, separatedHolds)) {
            Throwable failure = catchThrowable(() -> hire("Synthetic duplicate", null, null, taken));
            assertThat(failure).as(taken).isInstanceOf(DomainException.class);
            assertThat(counts()).as("nothing written for " + taken).isEqualTo(before);
        }
    }

    @Test
    @DisplayName("#959 卡（权限：人事专员）：只有默认员工角色的账号为新人入职被拒，不留任何行")
    void onlyThePersonnelSpecialistHires() throws Exception {
        actIn(staff());
        Counts before = counts();

        Throwable failure = catchThrowable(() -> hire("Synthetic unauthorised", null, null, null));

        assertThat(isPermissionRefusal(failure)).as(String.valueOf(failure)).isTrue();
        assertThat(counts()).isEqualTo(before);
    }
}
