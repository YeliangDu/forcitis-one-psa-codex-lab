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
 * the operations command and the account signs in. The finance head is bound to a legal entity (a 暂名, see CountersignNames); the
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

    @Test
    @DisplayName("#894 用户答复 Q3（2026-10-30）、ADR-0073 第 3 条第 6 项（行 74）：投产后升级到的业务管理员持会签权限，不必绑定法人：可批准任一法人的请求，决定人记为其账号")
    void theEscalatedPersonMayDecideAnyRequest() throws Exception {
        Awaiting waiting = crossEntityTransfer(DAY);
        Staff escalated = account(ESCALATED_ROLE);

        approve(escalated, waiting.command(), KIND_CHANGE, e1);
        approve(escalated, waiting.command(), KIND_CHANGE, e2);

        assertThat(commandStatus(waiting.command())).isEqualTo(PENDING);
        assertThat(request(waiting.command(), KIND_CHANGE, e1).get("decided_by")).isEqualTo(escalated.userId());
        assertThat(request(waiting.command(), KIND_CHANGE, e2).get("decided_by")).isEqualTo(escalated.userId());
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
    @DisplayName("#894 note #878 第 6 节、第 10 条第 6 项、ADR-0073 第 3 条第 6 项（行 71）（多角色）：既是人事专员又是 E1 财务负责人的人，不能会签自己起草的命令（应用拒绝）；E1 的另一位负责人和 E2 的负责人批准后命令照常转待生效")
    void aSpecialistWhoIsAlsoAHeadCannotDecideTheirOwnCommand() throws Exception {
        Staff both = specialist(FINANCE_HEAD_ROLE);
        bind(both, e1);
        UUID employee = inheriting(unitE1);
        actIn(both);
        UUID command = transfer(employee, DAY, unitE2);
        Awaiting waiting = new Awaiting(employee, command, DAY);

        assertRefusedAndUntouched(both, waiting, e1, false);
        approve(headE1, command, KIND_CHANGE, e1);
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
