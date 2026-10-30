package com.forcitis.one.acceptance.organization.workforce;

import static com.forcitis.one.acceptance.organization.workforce.PersonnelCommandNames.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import com.forcitis.one.TestDatabase;
import com.forcitis.one.TestPostgres;
import com.forcitis.one.kernel.DomainException;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
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
    }

    @Test
    @DisplayName("#959 W2 第 3.1 节第③条：带未来日期的档案修改拒收，员工根不变、不写审计")
    void aFutureDatedChangeIsRefused() throws Exception {
        UUID person = employed(REGULAR);
        Map<String, Object> rootBefore = root(person);
        int audited = auditCount(AUDIT_PROFILE_CHANGED);

        Throwable failure = catchThrowable(() -> change(person, "Synthetic too soon", "MALE", null, null, TODAY.plusDays(1)));

        assertThat(failure).isInstanceOf(DomainException.class);
        assertThat(root(person)).isEqualTo(rootBefore);
        assertThat(auditCount(AUDIT_PROFILE_CHANGED)).isEqualTo(audited);
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
        assertThat(commands().changeComponents()).doesNotContain(EMPLOYEE_CODE);
    }

    @Test
    @DisplayName("#959 卡（权限：人事专员）、验收手册第 5 步：只有默认员工角色的账号、无人登录时改档案被拒，员工根不变、不写审计")
    void onlyThePersonnelSpecialistChangesAProfile() throws Exception {
        UUID person = employed(REGULAR);
        Map<String, Object> rootBefore = root(person);
        int audited = auditCount(AUDIT_PROFILE_CHANGED);

        actIn(staff());
        Throwable byStaff = catchThrowable(() -> change(person, "Synthetic intruder", null, null, null, TODAY));
        actAsNobody();
        Throwable byNobody = catchThrowable(() -> change(person, "Synthetic intruder", null, null, null, TODAY));

        assertThat(isPermissionRefusal(byStaff)).as(String.valueOf(byStaff)).isTrue();
        assertThat(isPermissionRefusal(byNobody)).as(String.valueOf(byNobody)).isTrue();
        assertThat(root(person)).isEqualTo(rootBefore);
        assertThat(auditCount(AUDIT_PROFILE_CHANGED)).isEqualTo(audited);
    }

    private void change(UUID person, String displayName, String gender, String email, String wecomUserId, LocalDate effective) {
        Map<String, Object> fields = new HashMap<>();
        fields.put(EMPLOYEE_ID, person);
        fields.put(DISPLAY_NAME, displayName);
        fields.put(GENDER, gender);
        fields.put(EMAIL, email);
        fields.put(WECOM_USER_ID, wecomUserId);
        fields.put(EFFECTIVE_DATE, effective);
        fields.values().removeIf(java.util.Objects::isNull);
        commands().changeProfile(fields);
    }
}
