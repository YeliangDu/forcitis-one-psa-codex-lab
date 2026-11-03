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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * #959 (W2 section 3.1 boundaries 1 to 4 and section 7 item 1; user answer Q1 of 2026-10-30; one-design DD on #959): the 人事专员 changes
 * the current profile of an employee — display name, gender, email, WeCom user id — with immediate effect; a future date is refused; every
 * change is audited; the employee code never changes; no version row is written. The WeCom user id is unique across everyone, separated
 * people too. The change carries the whole new profile (replace, not patch).
 */
class EmployeeProfileChangeIT extends EmployeeHireSupport {

    static final TestDatabase postgres = TestPostgres.database(EmployeeProfileChangeIT.class, TestPostgres.MIGRATED).withUrlParam("currentSchema", "platform_approval");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        postgres.register(registry);
    }

    @Test
    @DisplayName("#959 W2 第 3.1 节第③④条：今天的档案修改即时生效：姓名、性别、邮箱、企业微信账号都按新值落到员工根；写一条审计；工号不变；不写任何版本行")
    void aChangeTakesEffectImmediatelyAndIsAudited() throws Exception {
        UUID person = employed(REGULAR);
        String code = (String) root(person).get("employee_code");
        String oldName = (String) root(person).get("display_name");
        Counts before = counts();
        int audited = auditCount(AUDIT_PROFILE_CHANGED);
        String wecom = syntheticWecom("rebind");

        change(person, "Synthetic renamed", "MALE", "renamed@example.invalid", wecom, TODAY);

        Map<String, Object> root = root(person);
        assertThat(root.get("display_name")).isEqualTo("Synthetic renamed");
        assertThat(root.get("gender")).isEqualTo("MALE");
        assertThat(root.get("email")).isEqualTo("renamed@example.invalid");
        assertThat(root.get("wecom_user_id")).isEqualTo(wecom);
        assertThat(root.get("employee_code")).as("never changes").isEqualTo(code);
        assertThat(auditCount(AUDIT_PROFILE_CHANGED)).isEqualTo(audited + 1);
        assertThat(counts()).as("current values only: no version, no command").isEqualTo(before);
        List<Map<String, Object>> entries = auditEntriesBy(hr.userId(), AUDIT_PROFILE_CHANGED).stream()
                .filter(entry -> text(person).equals(entry.get("subjectId"))).toList();
        assertThat(entries).as("one entry about this employee, the caller as actor").hasSize(1);
        Map<String, Object> entry = entries.get(0);
        assertThat((String) entry.get("beforeText")).as("W2 section 3.1 item 4: the L2 old value").contains(oldName);
        assertThat((String) entry.get("afterText")).as("the L2 new value").contains("Synthetic renamed");
        assertThat(entry.values()).as("the employee code (L3) is never written to the audit").noneMatch(value -> value != null && value.toString().contains(code));
    }

    @Test
    @DisplayName("#959 W2 第 3.1 节第③条：带未来日期的档案修改拒收，员工根不变、不写审计")
    void aFutureDatedChangeIsRefused() throws Exception {
        UUID person = employed(REGULAR);
        Map<String, Object> rootBefore = root(person);

        String name = (String) rootBefore.get("display_name");
        String wecom = (String) rootBefore.get("wecom_user_id");
        String gender = (String) rootBefore.get("gender");
        String email = (String) rootBefore.get("email");
        change(person, name, gender, email, wecom, TODAY);
        Map<String, Object> same = root(person);
        assertThat(same.get("display_name")).as("the same profile dated today is accepted").isEqualTo(name);
        int auditedToday = auditCount(AUDIT_PROFILE_CHANGED);

        Throwable failure = catchThrowable(() -> change(person, "Synthetic too soon", gender, email, wecom, TODAY.plusDays(1)));

        assertThat(failure).as("only the date differs from an accepted change").isInstanceOf(DomainException.class);
        assertThat(root(person).get("display_name")).isEqualTo(name);
        assertThat(auditCount(AUDIT_PROFILE_CHANGED)).isEqualTo(auditedToday);
    }

    @Test
    @DisplayName("#959 用户 Q1：入职时没有企业微信账号的人，事后由人事专员补填；再改一次即换绑；邮箱可改为空")
    void theWecomUserIdIsFilledInAfterHireAndRebound() throws Exception {
        UUID person = employed(REGULAR);
        change(person, "Synthetic person", null, "someone@example.invalid", null, TODAY);
        assertThat(root(person).get("wecom_user_id")).isNull();

        String first = syntheticWecom("first");
        change(person, "Synthetic person", null, null, first, TODAY);
        assertThat(root(person).get("wecom_user_id")).isEqualTo(first);
        assertThat(root(person).get("email")).as("cleared").isNull();
        String second = syntheticWecom("second");
        change(person, "Synthetic person", null, null, second, TODAY);
        assertThat(root(person).get("wecom_user_id")).isEqualTo(second);
    }

    @Test
    @DisplayName("#959 用户 Q1、W2 第 3.1 节 N-05：改成别人（在职或已离职）占用的企业微信账号拒收，员工根不变")
    void aWecomUserIdHeldByAnyoneElseIsRefused() throws Exception {
        UUID person = employed(REGULAR);
        String employedHolds = syntheticWecom("employed");
        rootWith("SYN-" + UUID.randomUUID(), employedHolds, EMPLOYED);
        String separatedHolds = syntheticWecom("separated");
        rootWith("SYN-" + UUID.randomUUID(), separatedHolds, SEPARATED);
        Map<String, Object> rootBefore = root(person);

        for (String taken : List.of(employedHolds, separatedHolds)) {
            Throwable failure = catchThrowable(() -> change(person, "Synthetic person", null, null, taken, TODAY));
            assertThat(failure).as(taken).isInstanceOf(DomainException.class);
            assertThat(root(person)).as(taken).isEqualTo(rootBefore);
        }
    }

    @Test
    @DisplayName("#959 W2 第 3.1 节第①条：工号发号后不可改——档案修改没有工号参数")
    void theChangeHasNoEmployeeCode() {
        assertThat(commands().changeComponents()).noneMatch(name -> name.toLowerCase(java.util.Locale.ROOT).contains("code"));
    }

    @ParameterizedTest(name = "role {0}")
    @ValueSource(strings = {"super_admin", "org_data_admin", "business_admin", "security_auditor", "process_administrator", "employee"})
    @DisplayName("#959 卡（权限：人事专员）、验收手册第 5 步（越权）：不持有人事专员角色的每个种子角色（含超级管理员、组织数据管理员、业务管理员）改档案被拒，员工根不变、不写审计")
    void everyOtherSeededRoleIsRefused(String role) throws Exception {
        UUID person = employed(REGULAR);
        Staff other = role.equals("employee") ? staff() : staff(role);
        assertChangeRefused(person, other);
    }

    @Test
    @DisplayName("#959 验收手册第 5 步（无人登录、多角色）：无人登录被拒；同时持有两个非人事专员角色仍被拒；人事专员兼另一角色照常可改，审计的操作人是其账号")
    void nobodyAndTwoRoles() throws Exception {
        UUID person = employed(REGULAR);
        assertChangeRefused(person, null);
        assertChangeRefused(person, staff("org_data_admin", "business_admin"));

        Staff both = staff(PERSONNEL_SPECIALIST_ROLE, "org_data_admin");
        actIn(both);
        change(person, "Synthetic by two roles", null, null, null, TODAY);

        assertThat(root(person).get("display_name")).isEqualTo("Synthetic by two roles");
        assertThat(auditEntriesBy(both.userId(), AUDIT_PROFILE_CHANGED)).as("audited with the caller as actor").isNotEmpty();
    }

    @Test
    @DisplayName("#959 用户 Q1：改档案时保留本人已持有的企业微信账号不算重复，照常生效")
    void keepingOnesOwnWecomUserIdIsNotADuplicate() throws Exception {
        UUID person = employed(REGULAR);
        String own = (String) root(person).get("wecom_user_id");

        change(person, "Synthetic keeps wecom", null, null, own, TODAY);

        assertThat(root(person).get("display_name")).isEqualTo("Synthetic keeps wecom");
        assertThat(root(person).get("wecom_user_id")).isEqualTo(own);
    }

    /** The change by this caller (null: nobody signed in) is refused for the caller's rights; the root and the audit stay as they were. */
    private void assertChangeRefused(UUID person, Staff caller) throws Exception {
        Map<String, Object> rootBefore = root(person);
        int audited = auditCount(AUDIT_PROFILE_CHANGED);
        if (caller == null) {
            actAsNobody();
        } else {
            actIn(caller);
        }

        Throwable failure = catchThrowable(() -> change(person, "Synthetic intruder", null, null, null, TODAY));

        assertThat(isPermissionRefusal(failure)).as(String.valueOf(failure)).isTrue();
        assertThat(root(person)).isEqualTo(rootBefore);
        assertThat(auditCount(AUDIT_PROFILE_CHANGED)).isEqualTo(audited);
    }
}
