package com.forcitis.one.acceptance.platform.identity;

import static org.assertj.core.api.Assertions.assertThat;

import com.forcitis.one.organization.workforce.api.EmploymentStatus;
import com.forcitis.one.platform.audit.api.AuditEntry;
import com.forcitis.one.platform.audit.api.AuditSubject;
import com.forcitis.one.platform.audit.api.AuditSubjectKind;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** The witness may be left out only in the single operator mode, only when it is not given, and each such omission is marked in the audit trail. */
class SingleOperatorWitnessOmissionIT extends SingleOperatorIdentitySupport {

    static final String OMITTED = "relaxation=WITNESS_OMITTED";

    private AuditEntry onlyEntry(String action) {
        assertThat(entries(action)).as(action).hasSize(1);
        return entries(action).getFirst();
    }

    private void assertOmissionMarked(String normalAction, int appliedBefore) {
        List<AuditEntry> marks = applied();
        assertThat(marks).as("one mark more after %s", normalAction).hasSize(appliedBefore + 1);
        AuditEntry mark = marks.getLast();
        AuditEntry normal = entries(normalAction).getLast();
        assertThat(mark.detail()).isEqualTo(OMITTED);
        assertThat(mark.subject()).as("same subject as the normal entry").isEqualTo(normal.subject());
        assertThat(normal.detail()).contains("operatorId=" + OPERATOR_ID + " witnessId=none").doesNotContain("witness=").doesNotContain("operator=");
    }

    // RULE-437
    @Test
    @DisplayName("SINGLE-OP P2-01 模式开启：grant-role 省略 --witness 成功，授予生效；正常审计照写，detail 为 operatorId=<UUID> witnessId=none role=<码>")
    void p201_grantRoleWithoutWitnessSucceeds() {
        mode(true);
        knownEmployee(33, EmploymentStatus.EMPLOYED);

        Result result = run(alone("grant-role", employee(33), "--role-code=business_admin"));

        assertThat(result.exitCode()).as(result.err()).isZero();
        assertThat(result.out()).isEqualTo("role granted\n");
        assertThat(holds(33, "business_admin")).isTrue();
        assertThat(onlyEntry(ROLE_GRANTED).detail()).isEqualTo("operatorId=" + OPERATOR_ID + " witnessId=none role=business_admin");
        assertThat(onlyEntry(ROLE_GRANTED).subject()).isEqualTo(AuditSubject.of(AuditSubjectKind.USER_ACCOUNT, accountOf(33)));
    }

    // RULE-437
    @Test
    @DisplayName("SINGLE-OP P2-02 同一事务中另写标注审计 platform.identity.single-operator-mode.applied：subjectKey 与正常条目相同，detail 恰为放宽项代码 relaxation=WITNESS_OMITTED；标注与正常条目都经 record（加入调用方事务），不经 recordIndependently")
    void p202_markIsWrittenInTheSameTransaction() {
        mode(true);
        knownEmployee(33, EmploymentStatus.EMPLOYED);

        run(alone("grant-role", employee(33), "--role-code=business_admin"));

        assertOmissionMarked(ROLE_GRANTED, 0);
        assertThat(recordedIndependently()).extracting(AuditEntry::action).doesNotContain(APPLIED, ROLE_GRANTED);
    }

    @Test
    @DisplayName("SINGLE-OP P2-03 register-emergency-admin 模式开启省略见证人成功并写一条标注；再次登记同一登录名（无改动）不写标注")
    void p203_registerWithoutWitness() {
        mode(true);
        createAccount(7, "super_admin");
        knownEmployee(7, EmploymentStatus.EMPLOYED);

        Result registered = run(alone("register-emergency-admin", employee(7), "--login-name=breakglass"));

        assertThat(registered.exitCode()).as(registered.err()).isZero();
        assertOmissionMarked(ADMIN_REGISTERED, 0);
        assertThat(onlyEntry(ADMIN_REGISTERED).subject().kind()).isEqualTo(AuditSubjectKind.EMERGENCY_ADMIN);

        Result again = run(alone("register-emergency-admin", employee(7), "--login-name=breakglass"));

        assertThat(again.exitCode()).isZero();
        assertThat(again.out()).contains("already registered");
        assertThat(applied()).as("already registered: nothing changed, nothing marked").hasSize(1);
    }

    // RULE-437
    @Test
    @DisplayName("SINGLE-OP P2-03 reset-emergency-admin-credentials 模式开启省略见证人成功并写一条标注")
    void p203_resetWithoutWitness() {
        mode(true);
        createAccount(7, "super_admin");
        knownEmployee(7, EmploymentStatus.EMPLOYED);
        assertThat(run(attested("register-emergency-admin", employee(7), "--login-name=breakglass")).exitCode()).isZero();
        assertThat(applied()).as("a witness was given, nothing was omitted").isEmpty();

        Result reset = run(alone("reset-emergency-admin-credentials", employee(7)));

        assertThat(reset.exitCode()).as(reset.err()).isZero();
        assertOmissionMarked(ADMIN_RESET, 0);
    }

    @Test
    @DisplayName("SINGLE-OP P2-03 disable-/enable-emergency-admin 模式开启省略见证人成功并各写一条标注；已是目标状态（无改动）不写标注")
    void p203_disableAndEnableWithoutWitness() {
        mode(true);
        createAccount(7, "super_admin");
        knownEmployee(7, EmploymentStatus.EMPLOYED);
        run(attested("register-emergency-admin", employee(7), "--login-name=breakglass"));

        Result disabled = run(alone("disable-emergency-admin", employee(7)));
        assertThat(disabled.exitCode()).as(disabled.err()).isZero();
        assertThat(disabled.out()).isEqualTo("disabled\n");
        assertOmissionMarked(ADMIN_DISABLED, 0);

        Result disabledAgain = run(alone("disable-emergency-admin", employee(7)));
        assertThat(disabledAgain.out()).contains("already disabled");
        assertThat(applied()).as("already disabled").hasSize(1);

        Result enabled = run(alone("enable-emergency-admin", employee(7)));
        assertThat(enabled.exitCode()).as(enabled.err()).isZero();
        assertThat(enabled.out()).isEqualTo("enabled\n");
        assertOmissionMarked(ADMIN_ENABLED, 1);

        Result enabledAgain = run(alone("enable-emergency-admin", employee(7)));
        assertThat(enabledAgain.out()).contains("already enabled");
        assertThat(applied()).as("already enabled").hasSize(2);
    }

    // RULE-438
    @Test
    @DisplayName("SINGLE-OP P2-03 grant-role 已持有该角色（无改动）不写标注、不写正常审计")
    void p203_grantAlreadyHeldIsNotMarked() {
        mode(true);
        knownEmployee(33, EmploymentStatus.EMPLOYED);
        run(alone("grant-role", employee(33), "--role-code=business_admin"));

        Result again = run(alone("grant-role", employee(33), "--role-code=business_admin"));

        assertThat(again.exitCode()).isZero();
        assertThat(again.out()).contains("already held");
        assertThat(applied()).hasSize(1);
        assertThat(entries(ROLE_GRANTED)).hasSize(1);
    }

    // RULE-437
    @Test
    @DisplayName("SINGLE-OP P2-03 revoke-role 模式开启省略见证人成功并写一条标注")
    void p203_revokeWithoutWitness() {
        mode(true);
        knownEmployee(33, EmploymentStatus.EMPLOYED);
        run(attested("grant-role", employee(33), "--role-code=business_admin"));
        assertThat(applied()).isEmpty();

        Result revoked = run(alone("revoke-role", employee(33), "--role-code=business_admin"));

        assertThat(revoked.exitCode()).as(revoked.err()).isZero();
        assertThat(holds(33, "business_admin")).isFalse();
        assertOmissionMarked(ROLE_REVOKED, 0);
    }

    // RULE-439
    @Test
    @DisplayName("SINGLE-OP P2-04 模式开启：--operator 缺失仍被拒（退出码 1，PLATFORM_IDENTITY_OPERATION_WITNESS_REQUIRED），库内无变化、无标注")
    void p204_operatorIsStillRequired() {
        mode(true);
        knownEmployee(33, EmploymentStatus.EMPLOYED);
        List<Long> before = identityRows();

        for (String[] arguments : List.of(new String[] {"--operation=grant-role", employee(33), "--role-code=business_admin"},
                new String[] {"--operation=grant-role", employee(33), "--role-code=business_admin", "--witness=" + WITNESS_ID},
                new String[] {"--operation=grant-role", employee(33), "--role-code=business_admin", "--operator=" + OPERATOR_ID.toUpperCase(Locale.ROOT)},
                new String[] {"--operation=grant-role", employee(33), "--role-code=business_admin", "--operator=alice"})) {
            Result result = run(arguments);

            assertThat(result.exitCode()).as(String.join(" ", arguments)).isEqualTo(REFUSED);
            assertThat(result.err()).contains(WITNESS_REQUIRED);
        }
        assertNothingChanged(before);
        assertThat(entries(ROLE_GRANTED)).isEmpty();
    }

    // RULE-439
    @Test
    @DisplayName("RULING-COORD-20261017-01 单人模式开启、操作人在组织快照中不存在、不带 --witness：退出码 1（PLATFORM_IDENTITY_OPERATION_WITNESS_REQUIRED），不写授予、不写 relaxation 标注")
    void rulingCoord20261017_01_unknownOperatorWithoutWitnessIsRefused() {
        mode(true);
        knownEmployee(33, EmploymentStatus.EMPLOYED);
        List<Long> before = identityRows();

        Result result = run("--operation=grant-role", employee(33), "--role-code=business_admin", "--operator=01900000-0000-7000-8000-00000000a0ff");

        assertThat(result.exitCode()).as(result.err()).isEqualTo(REFUSED);
        assertThat(result.err()).contains(WITNESS_REQUIRED);
        assertThat(holds(33, "business_admin")).isFalse();
        assertThat(entries(ROLE_GRANTED)).isEmpty();
        assertNothingChanged(before);
    }

    // RULE-440
    @Test
    @DisplayName("SINGLE-OP P2-05 模式开启：操作人与见证人同一员工 ID 仍被拒，无变化、无标注")
    void p205_sameEmployeeIsStillRefused() {
        mode(true);
        knownEmployee(33, EmploymentStatus.EMPLOYED);
        List<Long> before = identityRows();

        Result result = run("--operation=grant-role", employee(33), "--role-code=business_admin", "--operator=" + OPERATOR_ID, "--witness=" + OPERATOR_ID);

        assertThat(result.exitCode()).isEqualTo(REFUSED);
        assertThat(result.err()).contains(WITNESS_REQUIRED);
        assertNothingChanged(before);
    }

    // RULE-440
    @ParameterizedTest(name = "[{index}] --witness={0}")
    @ValueSource(strings = {"01900000-0000-7000-8000-00000000A002", "alice", "01900000-0000-7000-8000", " 01900000-0000-7000-8000-00000000a002",
            "01900000-0000-7000-8000-00000000a002 ", "00000000-0000-0000-0000-000000000000", "7"})
    @DisplayName("SINGLE-OP P2-06 模式开启：给了 --witness 但不是小写规范 UUID（大写、姓名、残缺、带空白、全零、数字）仍被拒，不被当作省略")
    void p206_aWitnessThatIsGivenButInvalidIsRefused(String witness) {
        mode(true);
        knownEmployee(33, EmploymentStatus.EMPLOYED);
        List<Long> before = identityRows();

        Result result = run("--operation=grant-role", employee(33), "--role-code=business_admin", "--operator=" + OPERATOR_ID, "--witness=" + witness);

        assertThat(result.exitCode()).isEqualTo(REFUSED);
        assertThat(result.err()).contains(WITNESS_REQUIRED);
        assertNothingChanged(before);
        assertThat(entries(ROLE_GRANTED)).isEmpty();
    }

    @Test
    @DisplayName("SINGLE-OP P2-07 模式开启：--witness 出现两次、--witness= 空值、--witness 无值，一律被拒，不被当作省略；对每个运维操作都一样")
    void p207_repeatedEmptyOrValuelessWitnessIsRefused() {
        mode(true);
        createAccount(7, "super_admin");
        knownEmployee(7, EmploymentStatus.EMPLOYED);
        knownEmployee(33, EmploymentStatus.EMPLOYED);
        List<Long> before = identityRows();
        List<List<String>> witnessForms = List.of(List.of("--witness=" + WITNESS_ID, "--witness=01900000-0000-7000-8000-00000000a003"), List.of("--witness="),
                List.of("--witness"), List.of("--witness=" + WITNESS_ID, "--witness="));
        List<List<String>> operations = List.of(List.of("--operation=grant-role", employee(33), "--role-code=business_admin"),
                List.of("--operation=revoke-role", employee(33), "--role-code=business_admin"),
                List.of("--operation=register-emergency-admin", employee(7), "--login-name=breakglass"),
                List.of("--operation=reset-emergency-admin-credentials", employee(7)), List.of("--operation=disable-emergency-admin", employee(7)),
                List.of("--operation=enable-emergency-admin", employee(7)));

        for (List<String> operation : operations) {
            for (List<String> witness : witnessForms) {
                String[] arguments = join(operation.toArray(String[]::new), join(new String[] {"--operator=" + OPERATOR_ID}, witness.toArray(String[]::new)));

                Result result = run(arguments);

                assertThat(result.exitCode()).as(String.join(" ", arguments)).isNotZero();
            }
        }
        assertNothingChanged(before);
        assertThat(recorded()).as("nothing was audited").isEmpty();
    }

    // RULE-441
    @Test
    @DisplayName("SINGLE-OP P2-08 模式开启且给了合法的不同见证人：行为与模式关闭时逐字节相同（输出与 detail 为 operatorId=… witnessId=<UUID> role=…），不写标注")
    void p208_aGivenWitnessBehavesAsWithTheModeOff() {
        knownEmployee(33, EmploymentStatus.EMPLOYED);
        knownEmployee(34, EmploymentStatus.EMPLOYED);
        Result off = run(attested("grant-role", employee(33), "--role-code=business_admin"));
        mode(true);

        Result on = run(attested("grant-role", employee(34), "--role-code=business_admin"));

        assertThat(on).isEqualTo(off);
        assertThat(entries(ROLE_GRANTED)).extracting(AuditEntry::detail)
                .containsExactly("operatorId=" + OPERATOR_ID + " witnessId=" + WITNESS_ID + " role=business_admin",
                        "operatorId=" + OPERATOR_ID + " witnessId=" + WITNESS_ID + " role=business_admin");
        assertThat(applied()).isEmpty();
    }

    // RULE-441
    @Test
    @DisplayName("SINGLE-OP P2-09 模式关闭：省略 --witness 仍被拒（退出码 1），库内无变化、无标注；同名仍被拒")
    void p209_withTheModeOffTheWitnessIsRequired() {
        knownEmployee(33, EmploymentStatus.EMPLOYED);
        List<Long> before = identityRows();

        Result omitted = run(alone("grant-role", employee(33), "--role-code=business_admin"));
        Result same = run("--operation=grant-role", employee(33), "--role-code=business_admin", "--operator=" + OPERATOR_ID, "--witness=" + OPERATOR_ID);

        assertThat(omitted.exitCode()).isEqualTo(REFUSED);
        assertThat(omitted.err()).contains(WITNESS_REQUIRED);
        assertThat(same.exitCode()).isEqualTo(REFUSED);
        assertThat(same.err()).contains(WITNESS_REQUIRED);
        assertNothingChanged(before);
        assertThat(entries(ROLE_GRANTED)).isEmpty();
    }

    // RULE-441
    @Test
    @DisplayName("SINGLE-OP P2-09 模式关闭：每个运维操作省略见证人都被拒，库内无变化、无标注")
    void p209_everyOperationNeedsTheWitnessWithTheModeOff() {
        createAccount(7, "super_admin");
        knownEmployee(7, EmploymentStatus.EMPLOYED);
        knownEmployee(33, EmploymentStatus.EMPLOYED);
        List<Long> before = identityRows();

        for (String[] arguments : List.of(alone("grant-role", employee(33), "--role-code=business_admin"), alone("revoke-role", employee(33), "--role-code=business_admin"),
                alone("register-emergency-admin", employee(7), "--login-name=breakglass"), alone("reset-emergency-admin-credentials", employee(7)),
                alone("disable-emergency-admin", employee(7)), alone("enable-emergency-admin", employee(7)))) {
            Result result = run(arguments);

            assertThat(result.exitCode()).as(String.join(" ", arguments)).isEqualTo(REFUSED);
            assertThat(result.err()).contains(WITNESS_REQUIRED);
        }
        assertNothingChanged(before);
    }

    @Test
    @DisplayName("SINGLE-OP P2-21 运维用法文本含 revoke-role，且仍含其余五个操作名；缺 --operation 退出码 2")
    void p221_usageNamesRevokeRole() {
        Result result = run("--employee-id=" + UUID.randomUUID());

        assertThat(result.exitCode()).isEqualTo(WRONG_USAGE);
        assertThat(result.err()).contains("revoke-role", "grant-role", "register-emergency-admin", "reset-emergency-admin-credentials", "disable-emergency-admin",
                "enable-emergency-admin");
    }

    @ParameterizedTest(name = "[{index}] {0}")
    @ValueSource(strings = {"--Witness=01900000-0000-7000-8000-00000000a002", "--witnes=01900000-0000-7000-8000-00000000a002",
            "-witness=01900000-0000-7000-8000-00000000a002", "witness=01900000-0000-7000-8000-00000000a002", "--wtiness=01900000-0000-7000-8000-00000000a002",
            "--witness-id=01900000-0000-7000-8000-00000000a002", "stray"})
    @DisplayName("SINGLE-OP P2-07 命令行出现未知选项或非选项参数（选项名写错、少横线、多余词）一律按用法错误拒绝：退出码 2，无变化、无审计、无标注，不被当作省略见证人")
    void p207_aMisspeltOptionIsWrongUsageNotAnOmission(String stray) {
        knownEmployee(33, EmploymentStatus.EMPLOYED);
        List<Long> before = identityRows();

        for (boolean modeOn : new boolean[] {true, false}) {
            mode(modeOn);
            Result withoutWitness = run(join(alone("grant-role", employee(33), "--role-code=business_admin"), stray));
            Result withWitness = run(join(attested("grant-role", employee(33), "--role-code=business_admin"), stray));

            assertThat(withoutWitness.exitCode()).as("mode on=%s, no witness, %s: %s", modeOn, stray, withoutWitness.err()).isIn(WRONG_USAGE, REFUSED);
            assertThat(withWitness.exitCode()).as("mode on=%s, witness given, %s: %s", modeOn, stray, withWitness.err()).isEqualTo(WRONG_USAGE);
        }
        mode(true);
        Result omitted = run(join(alone("grant-role", employee(33), "--role-code=business_admin"), stray));
        assertThat(omitted.exitCode()).as("mode on, witness left out, %s", stray).isEqualTo(WRONG_USAGE);
        assertNothingChanged(before);
        assertThat(recorded()).as("nothing audited").isEmpty();
    }
}
