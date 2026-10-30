package com.forcitis.one.acceptance.organization.workforce;

import static com.forcitis.one.acceptance.organization.workforce.CountersignNames.*;
import static com.forcitis.one.acceptance.organization.workforce.PersonnelCommandNames.*;
import static org.assertj.core.api.Assertions.assertThat;

import com.forcitis.one.TestDatabase;
import com.forcitis.one.TestPostgres;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * #894 IMP-1030-FC-C, design note of #878 section 10 item 6 and section 6 (ADR-0073 item 3.6 lines 71 and 74; ADR-0027 item 2.2; user
 * answers Q1 and Q3 of 2026-10-30; playbook acceptance step 5): who may decide a request. Holders are real sessions: roles are granted by
 * the operations command and the account signs in. The finance head of an entity is the finance_head_employee_id of its legal_entity_versions row valid today (one-design ruling, #893 DD amendment 2); the
 * escalated person (Q3) holds the permission without a binding.
 */
class CountersignAuthorityIT extends CountersignSupport {

    static final TestDatabase postgres = TestPostgres.database(CountersignAuthorityIT.class, TestPostgres.MIGRATED).withUrlParam("currentSchema", "platform_approval");

    private static final LocalDate DAY = TODAY.plusDays(10);

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        postgres.register(registry);
    }

    private void assertRefusedAndUntouched(Staff who, Awaiting waiting, UUID entity, boolean permissionRefusal) throws Exception {
        int approved = auditCount(AUDIT_APPROVED);

        Throwable failure = refusal(() -> approve(who, waiting.command(), KIND_CHANGE, entity));

        assertThat(failure).as("refused").isNotNull();
        if (permissionRefusal) {
            assertThat(isPermissionRefusal(failure)).as("as a refusal of the caller's rights: " + failure).isTrue();
        }
        assertThat(commandStatus(waiting.command())).isEqualTo(AWAITING);
        assertThat(openRequests(waiting.command())).as("both requests are still open").isEqualTo(2);
        assertThat(versionRowsWrittenBy(waiting.command())).isZero();
        assertThat(auditCount(AUDIT_APPROVED)).isEqualTo(approved);
    }

    @ParameterizedTest(name = "role {0}")
    @ValueSource(strings = {"personnel_specialist", "org_data_admin", "super_admin", "security_auditor", "process_administrator", "employee"})
    @DisplayName("#894 note #878 第 10 条第 6 项、第 6 节、验收手册第 5 步（越权）：不持有会签权限的账号（含起草的人事专员、其他管理员角色、超级管理员、默认员工）批准被拒，请求仍未决，命令仍待会签，不写审计")
    void everyRoleWithoutThePermissionIsRefused(String role) throws Exception {
        Awaiting waiting = crossEntityTransfer(DAY);
        Staff other = role.equals("employee") ? account() : account(role);

        assertRefusedAndUntouched(other, waiting, e1, true);
        assertRefusedAndUntouched(other, waiting, e2, true);
    }

    @Test
    @DisplayName("#894 note #878 第 10 条第 6 项、第 6 节（跨范围）：另一法人（E3）的财务负责人批准 E1 或 E2 的请求被拒；E1 的负责人不能批准 E2 的请求（反之亦然）；请求仍未决")
    void aHeadOfAnotherEntityCannotDecide() throws Exception {
        Awaiting waiting = crossEntityTransfer(DAY);

        assertRefusedAndUntouched(headE3, waiting, e1, true);
        assertRefusedAndUntouched(headE3, waiting, e2, true);
        assertRefusedAndUntouched(headE1, waiting, e2, true);
        assertRefusedAndUntouched(headE2, waiting, e1, true);
    }

    @Test
    @DisplayName("#894 note #878 第 10 条第 6 项、第 6 节（空范围）：持有法人财务负责人角色但没有绑定任何法人的账号，批准被拒；请求仍未决")
    void aHeadBoundToNoEntityCannotDecide() throws Exception {
        Awaiting waiting = crossEntityTransfer(DAY);
        Staff unbound = account(FINANCE_HEAD_ROLE);

        assertRefusedAndUntouched(unbound, waiting, e1, true);
        assertRefusedAndUntouched(unbound, waiting, e2, true);
    }

    @ParameterizedTest(name = "escalation level {0}")
    @ValueSource(ints = {0, 1})
    @DisplayName("#894 用户答复 Q3（2026-10-30）、ADR-0073 第 3 条第 6 项（行 74）、one-design 裁定：升级到的业务管理员只有在该行升级等级不低于 2 时才可决定；等级 0、1 时批准被拒，请求仍未决")
    void theEscalatedPersonIsRefusedBelowLevelTwo(int level) throws Exception {
        Awaiting waiting = crossEntityTransfer(DAY);
        escalateTo(waiting.command(), KIND_CHANGE, e1, level);
        Staff escalated = account(ESCALATED_ROLE);

        assertRefusedAndUntouched(escalated, waiting, e1, true);
    }

    @ParameterizedTest(name = "escalation level {0}")
    @ValueSource(ints = {2, 3})
    @DisplayName("#894 用户答复 Q3（2026-10-30）、one-design 裁定：该行升级等级不低于 2（含 3）时业务管理员可决定，不必是该法人的财务负责人，决定人记为其账号；另一行（等级 0）业务管理员仍不能决定")
    void theEscalatedPersonMayDecideFromLevelTwo(int level) throws Exception {
        Awaiting waiting = crossEntityTransfer(DAY);
        escalateTo(waiting.command(), KIND_CHANGE, e1, level);
        Staff escalated = account(ESCALATED_ROLE);

        approve(escalated, waiting.command(), KIND_CHANGE, e1);

        assertThat(request(waiting.command(), KIND_CHANGE, e1).get("decided_by")).isEqualTo(escalated.userId());
        assertThat(refusal(() -> approve(escalated, waiting.command(), KIND_CHANGE, e2))).as("the other row is still at level 0").isNotNull();
        assertThat(commandStatus(waiting.command())).isEqualTo(AWAITING);
    }

    @ParameterizedTest(name = "escalation level {0}")
    @ValueSource(ints = {0, 1, 2, 3})
    @DisplayName("#894 one-design 裁定、ADR-0073 第 3 条第 6 项（行 74）：该法人的财务负责人在升级等级 0 至 3 都可决定（等级 3 没有新的决定人）")
    void theRowsOwnHeadDecidesAtEveryLevel(int level) throws Exception {
        Awaiting waiting = crossEntityTransfer(DAY);
        escalateTo(waiting.command(), KIND_CHANGE, e1, level);

        approve(headE1, waiting.command(), KIND_CHANGE, e1);

        assertThat(request(waiting.command(), KIND_CHANGE, e1).get("decided_by")).isEqualTo(headE1.userId());
    }

    // ---- reject and WITHDRAWAL rows follow the same decider rule (playbook step 5) ----

    private Awaiting rowsOf(String kind) throws Exception {
        Awaiting waiting = crossEntityTransfer(DAY);
        if (kind.equals(KIND_WITHDRAWAL)) {
            approveBoth(waiting.command());
            actIn(hr);
            commands().withdraw(waiting.command(), "synthetic: plan changed");
        }
        return waiting;
    }

    private void assertDecisionRefused(Staff who, Awaiting waiting, String kind, UUID entity, boolean reject) throws Exception {
        Throwable failure = refusal(() -> {
            if (reject) {
                reject(who, waiting.command(), kind, entity, "synthetic: not allowed");
            } else {
                approve(who, waiting.command(), kind, entity);
            }
        });
        assertThat(failure).as((reject ? "reject " : "approve ") + kind + " of " + entity).isNotNull();
        assertThat(isPermissionRefusal(failure)).as(String.valueOf(failure)).isTrue();
        assertThat(request(waiting.command(), kind, entity).get("decision_kind")).as("the row is still open").isNull();
    }

    @ParameterizedTest(name = "{0} rows")
    @ValueSource(strings = {KIND_CHANGE, KIND_WITHDRAWAL})
    @DisplayName("#894 note #878 第 6 节、第 10 条第 6 项、验收手册第 5 步（越权、跨范围）：拒签以及撤回请求行的批准、拒签，与批准 CHANGE 行同一决定人规则：不持权限的账号、持权限但不是该行法人负责人的人（另一法人负责人）、等级低于 2 的业务管理员都被拒，行仍未决")
    void rejectionsAndWithdrawalRowsFollowTheSameRule(String kind) throws Exception {
        Awaiting waiting = rowsOf(kind);
        Staff nobody = account();
        Staff escalated = account(ESCALATED_ROLE);

        for (boolean reject : new boolean[] {true, false}) {
            assertDecisionRefused(nobody, waiting, kind, e1, reject);
            assertDecisionRefused(headE3, waiting, kind, e1, reject);
            assertDecisionRefused(headE2, waiting, kind, e1, reject);
            assertDecisionRefused(escalated, waiting, kind, e1, reject);
        }
        assertThat(commandStatus(waiting.command())).isIn(AWAITING, PENDING);
    }

    @Test
    @DisplayName("#894 note #878 第 6 节、第 10 条第 6 项（权限与负责人两个条件缺一不可）：是该法人当前版本的财务负责人但没有会签权限（角色被收回或从未授予）的人，批准与拒签都被拒，请求仍未决")
    void beingTheRecordedHeadWithoutThePermissionIsNotEnough() throws Exception {
        Awaiting waiting = crossEntityTransfer(DAY);
        Staff recordedOnly = account("personnel_specialist");
        bind(recordedOnly, e1);

        assertDecisionRefused(recordedOnly, waiting, KIND_CHANGE, e1, false);
        assertDecisionRefused(recordedOnly, waiting, KIND_CHANGE, e1, true);
    }

    @Test
    @DisplayName("#894 note #878 第 6 节、#893 数据设计修订 2（取今天有效的版本）、验收手册第 5 步（边界）：负责人换人后，原负责人（版本已关闭）批准与拒签都被拒、新负责人可决定")
    void aFormerHeadCannotDecideAndTheNewOneCan() throws Exception {
        Awaiting waiting = crossEntityTransfer(DAY);
        Staff successor = head(e1);

        assertDecisionRefused(headE1, waiting, KIND_CHANGE, e1, false);
        assertDecisionRefused(headE1, waiting, KIND_CHANGE, e1, true);
        approve(successor, waiting.command(), KIND_CHANGE, e1);

        assertThat(request(waiting.command(), KIND_CHANGE, e1).get("decided_by")).isEqualTo(successor.userId());
    }

    @Test
    @DisplayName("#894 #893 数据设计修订 2（取今天有效的版本）边界：明天才起任的新负责人今天不能决定，今天在任的负责人仍可决定")
    void aHeadWhoStartsTomorrowCannotDecideToday() throws Exception {
        Awaiting waiting = crossEntityTransfer(DAY);
        Staff future = account(FINANCE_HEAD_ROLE);
        bindFrom(future, e1, TODAY.plusDays(1));

        assertDecisionRefused(future, waiting, KIND_CHANGE, e1, false);
        assertDecisionRefused(future, waiting, KIND_CHANGE, e1, true);
        approve(headE1, waiting.command(), KIND_CHANGE, e1);

        assertThat(request(waiting.command(), KIND_CHANGE, e1).get("decided_by")).isEqualTo(headE1.userId());
    }

    @Test
    @DisplayName("#894 note #878 第 6 节（多角色）、用户答复 Q1：同时是两个法人的财务负责人的人，两行请求分别决定：批一行后命令仍待会签，另一行仍未决；两次决定各有一条审计、各有自己的审计记录 ID")
    void oneHeadOfBothEntitiesDecidesEachRowSeparately() throws Exception {
        Awaiting waiting = crossEntityTransfer(DAY);
        Staff both = head(e1, e2);
        int approved = auditCount(AUDIT_APPROVED);

        approve(both, waiting.command(), KIND_CHANGE, e1);

        assertThat(commandStatus(waiting.command())).isEqualTo(AWAITING);
        assertThat(decision(waiting.command(), KIND_CHANGE, e1)).isEqualTo(APPROVED);
        assertThat(request(waiting.command(), KIND_CHANGE, e2).get("decision_kind")).as("the second row is not decided by the first approval").isNull();
        approve(both, waiting.command(), KIND_CHANGE, e2);

        assertThat(commandStatus(waiting.command())).isEqualTo(PENDING);
        assertThat(auditCount(AUDIT_APPROVED)).isEqualTo(approved + 2);
        assertThat(request(waiting.command(), KIND_CHANGE, e1).get("decision_audit_entry_id")).isNotEqualTo(request(waiting.command(), KIND_CHANGE, e2).get("decision_audit_entry_id"));
    }

    @Test
    @DisplayName("#894 note #878 第 6 节、第 10 条第 6 项、ADR-0073 第 3 条第 6 项（行 71）（多角色）：既是人事专员又是 E1 财务负责人的人，不能会签自己起草的命令（应用拒绝）；该行升级到等级 2 后由业务管理员批准、E2 的负责人批准后命令照常转待生效")
    void aSpecialistWhoIsAlsoAHeadCannotDecideTheirOwnCommand() throws Exception {
        Staff both = specialist(FINANCE_HEAD_ROLE);
        bind(both, e1);
        UUID employee = inheriting(unitE1);
        actIn(both);
        UUID command = transfer(employee, DAY, unitE2);
        Awaiting waiting = new Awaiting(employee, command, DAY);

        assertRefusedAndUntouched(both, waiting, e1, false);
        escalateTo(command, KIND_CHANGE, e1, ESCALATED_FROM_LEVEL);
        approve(account(ESCALATED_ROLE), command, KIND_CHANGE, e1);
        approve(headE2, command, KIND_CHANGE, e2);

        assertThat(commandStatus(command)).isEqualTo(PENDING);
    }

    @Test
    @DisplayName("#894 note #878 第 10 条第 6 项、ADR-0073 第 3 条第 5 项（行 69）、ADR-0027 第 2 项第 2 条：会签人不能是其法人归属被改的员工本人：是 E1、E2 财务负责人的员工，其本人的法人变更他自己批准被拒")
    void anEmployeeCannotDecideTheirOwnChange() throws Exception {
        Staff employeeAndHead = head(e1, e2);
        actIn(hr);
        UUID command = enterChange(employeeAndHead.employeeId(), CHANGE_LEGAL_ENTITY, DAY, e2);
        assertThat(commandStatus(command)).isEqualTo(AWAITING);

        assertRefusedAndUntouched(employeeAndHead, new Awaiting(employeeAndHead.employeeId(), command, DAY), e1, false);
        assertRefusedAndUntouched(employeeAndHead, new Awaiting(employeeAndHead.employeeId(), command, DAY), e2, false);
    }

    @Test
    @DisplayName("#894 note #878 第 10 条第 6 项、第 5.2 节、#893 数据设计会签（具名约束 ck_attribution_countersigns_not_drafter）：决定人等于起草人，数据库拒绝，即使应用把请求人记成别人（撤回请求由另一名人事专员提出）；决定人等于请求人也被拒；其他人可以决定")
    void theDatabaseRefusesTheDrafterAndTheRequesterAsDecider() throws Exception {
        Awaiting waiting = crossEntityTransfer(DAY);
        approveBoth(waiting.command());
        Staff other = specialist();
        actIn(other);
        commands().withdraw(waiting.command(), "synthetic: requested by another specialist");
        UUID row = requestId(waiting.command(), KIND_WITHDRAWAL, e1);
        assertThat(request(waiting.command(), KIND_WITHDRAWAL, e1).get("requested_by")).isEqualTo(other.userId());
        assertThat(request(waiting.command(), KIND_WITHDRAWAL, e1).get("drafted_by")).isEqualTo(hr.userId());

        assertDecisionRefusedByCheck(row, hr.userId());
        assertDecisionRefusedByCheck(row, other.userId());
        decideInTheDatabase(row, headE1.userId());

        assertThat(request(waiting.command(), KIND_WITHDRAWAL, e1).get("decided_by")).isEqualTo(headE1.userId());
    }

    private void assertDecisionRefusedByCheck(UUID row, UUID decider) throws Exception {
        try {
            decideInTheDatabase(row, decider);
        } catch (SQLException refused) {
            assertThat(String.valueOf(refused.getMessage())).contains(NOT_DRAFTER_CHECK);
            return;
        }
        throw new AssertionError("the database accepted a decision by " + decider + ", who drafted or requested");
    }

    private void decideInTheDatabase(UUID row, UUID decider) throws SQLException {
        OffsetDateTime at = OffsetDateTime.ofInstant(NOW, ZoneOffset.UTC);
        try (Connection workforce = schemaConnection("organization_workforce")) {
            update(workforce, "update " + COUNTERSIGNS + " set decision_kind = 'APPROVED', decided_at = ?, decided_by = ?, decision_audit_entry_id = ? where attribution_countersign_id = ?",
                    at, decider, UUID.randomUUID(), row);
        }
    }

    @Test
    @DisplayName("#894 note #878 第 10 条第 6 项、ADR-0073 第 3 条第 6 项（行 71）：起草人是同一命令中请求的唯一依据：CHANGE 请求的起草人、请求人都是录入命令的人事专员（由数据库经命令外键保证），员工 ID 与命令一致")
    void theDrafterOfARequestIsTheCommandsOwnDrafter() throws Exception {
        Awaiting waiting = crossEntityTransfer(DAY);

        List<java.util.Map<String, Object>> requests = requests(waiting.command(), KIND_CHANGE);

        assertThat(requests).hasSize(2).allSatisfy(row -> {
            assertThat(row.get("drafted_by")).isEqualTo(command(waiting.command()).get("created_by"));
            assertThat(row.get("employee_id")).isEqualTo(command(waiting.command()).get("employee_id"));
        });
    }
}
