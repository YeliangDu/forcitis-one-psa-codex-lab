package com.forcitis.one.acceptance.platform.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.verify;

import com.forcitis.one.fixture.doubles.TestAccounts;
import com.forcitis.one.kernel.UserId;
import com.forcitis.one.organization.workforce.api.EmploymentStatus;
import com.forcitis.one.platform.audit.api.AuditEntry;
import com.forcitis.one.platform.audit.api.AuditSubject;
import com.forcitis.one.platform.audit.api.AuditSubjectKind;
import com.forcitis.one.platform.identity.api.RoleId;
import com.forcitis.one.platform.identity.application.AccessCache;
import com.forcitis.one.platform.identity.events.AccountRolesReplaced;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

/** The operations command that takes a role back: the counterpart of grant-role, with the same witness rule. */
class SingleOperatorRevokeRoleIT extends SingleOperatorIdentitySupport {

    @MockitoSpyBean
    AccessCache cacheSpy;

    private UUID roleId(String code) {
        return jdbc.sql("select role_id from platform_identity.roles where code = :code").param("code", code).query(UUID.class).single();
    }

    private long sessionsOf(long id) {
        return jdbc.sql("select count(*) from platform_identity.sessions where principal_name = (select cast(user_id as text) from platform_identity.user_accounts where employee_id = :employee)")
                .param("employee", TestAccounts.employee(id).value()).query(Long.class).single();
    }

    // RULE-432
    @Test
    @DisplayName("SINGLE-OP P2-17 revoke-role 收回已持有的角色：账号不再持有，该人全部会话被吊销（他人的不动），权限缓存被清，发布账号角色变更事件，写审计 operations.account.role.revoked（detail 同 grant-role 的格式）")
    void p217_revokingAHeldRole() throws Exception {
        knownEmployee(1001, EmploymentStatus.EMPLOYED);
        assertThat(run(attested("grant-role", employee(1001), "--role-code=business_admin")).exitCode()).isZero();
        loginWithWecom(1001);
        loginWithWecom(1002);
        assertThat(sessionsOf(1001)).isPositive();
        long othersSessions = sessionsOf(1002);
        assertThat(holds(1001, "business_admin")).isTrue();
        clearInvocations(cacheSpy);

        Result result = run(attested("revoke-role", employee(1001), "--role-code=business_admin"));

        assertThat(result.exitCode()).as(result.err()).isZero();
        assertThat(result.out()).isEqualTo("role revoked\n");
        assertThat(holds(1001, "business_admin")).isFalse();
        assertThat(holds(1001, "employee")).as("the other roles stay").isTrue();
        assertThat(sessionsOf(1001)).as("all sessions of the person ended").isZero();
        assertThat(sessionsOf(1002)).as("another person's sessions are not touched").isEqualTo(othersSessions);
        verify(cacheSpy).evict(new UserId(accountOf(1001)));
        assertThat(eventLog.of(AccountRolesReplaced.class)).filteredOn(event -> event.userId().value().equals(accountOf(1001))).last()
                .satisfies(event -> assertThat(event.roleIds()).doesNotContain(new RoleId(roleId("business_admin"))));
        assertThat(entries(ROLE_REVOKED)).singleElement().satisfies(entry -> {
            assertThat(entry.detail()).isEqualTo("operatorId=" + OPERATOR_ID + " witnessId=" + WITNESS_ID + " role=business_admin");
            assertThat(entry.subject()).isEqualTo(AuditSubject.of(AuditSubjectKind.USER_ACCOUNT, accountOf(1001)));
        });
        assertThat(applied()).as("a witness was given").isEmpty();
    }

    // RULE-433
    @Test
    @DisplayName("SINGLE-OP P2-18 revoke-role 对未持有的角色：退出码 0，输出「无变化」（nothing changed），无审计、无标注、无事件")
    void p218_revokingARoleThatIsNotHeldChangesNothing() {
        mode(true);
        knownEmployee(33, EmploymentStatus.EMPLOYED);
        assertThat(run(attested("grant-role", employee(33), "--role-code=org_data_admin")).exitCode()).isZero();
        int auditedBefore = recorded().size();
        int eventsBefore = eventLog.of(AccountRolesReplaced.class).size();

        Result result = run(alone("revoke-role", employee(33), "--role-code=business_admin"));

        assertThat(result.exitCode()).as(result.err()).isZero();
        assertThat(result.out()).contains("nothing changed");
        assertThat(entries(ROLE_REVOKED)).isEmpty();
        assertThat(applied()).isEmpty();
        assertThat(recorded()).hasSize(auditedBefore);
        assertThat(eventLog.of(AccountRolesReplaced.class)).hasSize(eventsBefore);
        assertThat(holds(33, "org_data_admin")).isTrue();
    }

    // RULE-433
    @Test
    @DisplayName("SINGLE-OP P2-18 revoke-role 对不存在的角色码：拒绝（退出码 1，PLATFORM_IDENTITY_ROLE_NOT_FOUND），无变化")
    void p218_unknownRoleCodeIsRefused() {
        knownEmployee(33, EmploymentStatus.EMPLOYED);
        run(attested("grant-role", employee(33), "--role-code=business_admin"));
        List<Long> before = identityRows();

        Result result = run(attested("revoke-role", employee(33), "--role-code=no_such_role"));

        assertThat(result.exitCode()).isEqualTo(REFUSED);
        assertThat(result.err()).contains("PLATFORM_IDENTITY_ROLE_NOT_FOUND");
        assertThat(identityRows()).isEqualTo(before);
        assertThat(entries(ROLE_REVOKED)).isEmpty();
    }

    // RULE-433
    @Test
    @DisplayName("SINGLE-OP P2-18 revoke-role 对没有账号的员工（在职或不存在）：拒绝，不开户；缺 --role-code 或 --employee-id 为用法错误（退出码 2），无变化")
    void p218_noAccountIsRefusedAndNoneIsOpened() {
        knownEmployee(40, EmploymentStatus.EMPLOYED);
        List<Long> before = identityRows();

        Result employedWithoutAccount = run(attested("revoke-role", employee(40), "--role-code=business_admin"));
        Result unknownEmployee = run(attested("revoke-role", employee(404), "--role-code=business_admin"));
        Result noRoleCode = run(attested("revoke-role", employee(40)));
        Result noEmployee = run(attested("revoke-role", "--role-code=business_admin"));

        assertThat(employedWithoutAccount.exitCode()).as(employedWithoutAccount.err()).isEqualTo(REFUSED);
        assertThat(unknownEmployee.exitCode()).as(unknownEmployee.err()).isEqualTo(REFUSED);
        assertThat(noRoleCode.exitCode()).as(noRoleCode.out() + noRoleCode.err()).isNotZero();
        assertThat(noEmployee.exitCode()).isEqualTo(WRONG_USAGE);
        assertThat(identityRows()).as("no account opened").isEqualTo(before);
        assertThat(count("user_accounts")).isZero();
        assertThat(entries(ROLE_REVOKED)).isEmpty();
    }

    // RULE-422
    @Test
    @DisplayName("SINGLE-OP P2-19 revoke-role 在模式关闭时要求见证人：省略被拒（退出码 1，WITNESS_REQUIRED），角色仍被持有，无审计、无标注")
    void p219_witnessIsRequiredWithTheModeOff() {
        knownEmployee(33, EmploymentStatus.EMPLOYED);
        run(attested("grant-role", employee(33), "--role-code=business_admin"));

        Result result = run(alone("revoke-role", employee(33), "--role-code=business_admin"));

        assertThat(result.exitCode()).isEqualTo(REFUSED);
        assertThat(result.err()).contains(WITNESS_REQUIRED);
        assertThat(holds(33, "business_admin")).isTrue();
        assertThat(entries(ROLE_REVOKED)).isEmpty();
        assertThat(applied()).isEmpty();
    }

    // RULE-426
    @Test
    @DisplayName("SINGLE-OP P2-19 revoke-role 在模式开启时可省略见证人并写标注：detail 为 operatorId=… witnessId=none role=…，标注 relaxation=WITNESS_OMITTED，主体相同")
    void p219_witnessMayBeLeftOutWithTheModeOn() {
        mode(true);
        knownEmployee(33, EmploymentStatus.EMPLOYED);
        run(alone("grant-role", employee(33), "--role-code=business_admin"));

        Result result = run(alone("revoke-role", employee(33), "--role-code=business_admin"));

        assertThat(result.exitCode()).as(result.err()).isZero();
        assertThat(holds(33, "business_admin")).isFalse();
        AuditEntry revoked = entries(ROLE_REVOKED).getFirst();
        assertThat(revoked.detail()).isEqualTo("operatorId=" + OPERATOR_ID + " witnessId=none role=business_admin");
        assertThat(applied()).extracting(AuditEntry::subject).contains(revoked.subject());
        assertThat(applied()).extracting(AuditEntry::detail).contains("relaxation=WITNESS_OMITTED");
        assertThat(applied()).hasSize(2);
    }

    // RULE-434
    @Test
    @DisplayName("SINGLE-OP P2-19 收回敏感角色同样可由运维命令完成（ADR-0030 第 3 项第 4 行）：security_auditor 与 super_admin 在两种模式下都能收回；应用内不建申请")
    void p219_sensitiveRolesCanBeRevokedByTheCommand() {
        knownEmployee(33, EmploymentStatus.EMPLOYED);
        knownEmployee(34, EmploymentStatus.EMPLOYED);
        for (long person : new long[] {33, 34}) {
            assertThat(run(attested("grant-role", employee(person), "--role-code=security_auditor")).exitCode()).isZero();
            assertThat(run(attested("grant-role", employee(person), "--role-code=super_admin")).exitCode()).isZero();
        }

        Result withWitness = run(attested("revoke-role", employee(33), "--role-code=security_auditor"));
        mode(true);
        Result withoutWitness = run(alone("revoke-role", employee(34), "--role-code=super_admin"));

        assertThat(withWitness.exitCode()).as(withWitness.err()).isZero();
        assertThat(withoutWitness.exitCode()).as(withoutWitness.err()).isZero();
        assertThat(holds(33, "security_auditor")).isFalse();
        assertThat(holds(33, "super_admin")).isTrue();
        assertThat(holds(34, "super_admin")).isFalse();
        assertThat(holds(34, "security_auditor")).isTrue();
        assertThat(jdbc.sql("select count(*) from platform_identity.authorization_requests").query(Long.class).single()).as("no authorization request was made").isZero();
    }
}
