package com.forcitis.one.acceptance.organization.workforce;

import static com.forcitis.one.acceptance.organization.workforce.CountersignNames.*;
import static com.forcitis.one.acceptance.organization.workforce.PersonnelCommandNames.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.forcitis.one.TestDatabase;
import com.forcitis.one.TestPostgres;
import com.forcitis.one.kernel.DomainException;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * #894 IMP-1030-FC-C, design note of #878 section 10 items 1, 9, 10, 12 and the user's answer Q4 of 2026-10-30 (ADR-0073 item 3.4 lines
 * 58 to 59, item 3.2, item 3.5, item 3.6 line 73): what enters {@code AWAITING_ATTRIBUTION_COUNTERSIGN}, with which required set of requests, and what is
 * refused at entry. The cost legal entity is the own attribution first, else the unit chain.
 */
class CountersignEntryIT extends CountersignSupport {

    static final TestDatabase postgres = TestPostgres.database(CountersignEntryIT.class, TestPostgres.MIGRATED).withUrlParam("currentSchema", "platform_approval");

    private static final LocalDate DAY = TODAY.plusDays(10);

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        postgres.register(registry);
    }

    @Test
    @DisplayName("#894 note #878 第 10 条第 1 项、第 4 节、第 5.1 节、ADR-0073 第 3 条第 4 项（行 58 至 59）：跨法人调岗（部门链上的成本法人由 E1 变 E2）进待会签：会签依据记前后法人，不写目标法人，状态转换 NULL 到待会签，不发事件、不写版本")
    void aCrossEntityTransferWaitsForTheCountersign() throws Exception {
        UUID employee = inheriting(unitE1);
        actIn(hr);

        UUID command = transfer(employee, DAY, unitE2);

        Map<String, Object> row = command(command);
        assertThat(row.get("status")).isEqualTo(AWAITING);
        assertThat(row.get(BASIS_PREVIOUS)).isEqualTo(e1);
        assertThat(row.get(BASIS_NEW)).isEqualTo(e2);
        assertThat(row.get(TARGET_COLUMN)).as("the transfer named no legal entity").isNull();
        assertThat(row.get("applied_at")).isNull();
        assertThat(statusPath(AGGREGATE_PERSONNEL_COMMAND, command)).containsExactly(AWAITING);
        assertThat(allCommandEventsOf(command)).isZero();
        assertThat(versionRowsWrittenBy(command)).as("no version before the countersign (ADR-0073 guard 1)").isZero();
    }

    @Test
    @DisplayName("#894 note #878 第 10 条第 1、12 项、第 4 节：待会签命令在录入事务内得到完整的必需请求集：前后两个法人各一行 CHANGE 请求（序号 1、未决、起草人与请求人同为录入人、有到期日）；审计每个请求一条（requested）")
    void theRequiredSetOfRequestsExistsFromEntry() throws Exception {
        int requested = auditCount(AUDIT_REQUESTED);

        Awaiting waiting = crossEntityTransfer(DAY);

        List<Map<String, Object>> requests = requests(waiting.command(), KIND_CHANGE);
        assertThat(requests).extracting(row -> row.get("legal_entity_id")).containsExactlyInAnyOrder(e1, e2);
        assertThat(requests).allSatisfy(row -> {
            assertThat(row.get("request_sequence_number")).isEqualTo(1);
            assertThat(row.get("decision_kind")).isNull();
            assertThat(row.get("drafted_by")).isEqualTo(hr.userId());
            assertThat(row.get("requested_by")).isEqualTo(hr.userId());
            assertThat(row.get("employee_id")).isEqualTo(stored(waiting.employee()));
            assertThat(row.get("escalation_level")).isEqualTo(0);
            assertThat((LocalDate) row.get("due_on_date")).isAfter(TODAY);
        });
        assertThat(requests).extracting(row -> transitionsOf(row)).allSatisfy(path -> assertThat(path).containsExactly(OPEN));
        assertThat(auditCount(AUDIT_REQUESTED)).isEqualTo(requested + 2);
    }

    private List<String> transitionsOf(Map<String, Object> request) {
        try {
            return statusPath(AGGREGATE_COUNTERSIGN, (UUID) request.get("attribution_countersign_id"));
        } catch (java.sql.SQLException failure) {
            throw new AssertionError(failure);
        }
    }

    @Test
    @DisplayName("#894 note #878 第 10 条第 1 项、ADR-0073 第 3 条第 4 项（行 59）：同一法人内调岗不进待会签：待生效、无会签依据、无请求行")
    void aTransferInsideOneEntityDoesNotWait() throws Exception {
        UUID employee = inheriting(unitE1);
        actIn(hr);

        UUID command = transfer(employee, DAY, unitE1b);

        Map<String, Object> row = command(command);
        assertThat(row.get("status")).isEqualTo(PENDING);
        assertThat(row.get(BASIS_PREVIOUS)).isNull();
        assertThat(row.get(BASIS_NEW)).isNull();
        assertThat(rows("select count(*) as n from " + COUNTERSIGNS + " where personnel_command_id = ?", command).get(0).get("n")).isEqualTo(0L);
    }

    @Test
    @DisplayName("#894 note #878 第 10 条第 1 项、ADR-0073 第 3 条第 4 项（行 58）：成本法人是员工单独指定的法人优先：有单独归属 E1 的员工调到 E2 的部门，成本法人不变，不进待会签")
    void anOwnAttributionWinsOverTheUnitChain() throws Exception {
        UUID employee = employed(REGULAR);
        actIn(hr);

        UUID command = transfer(employee, DAY, unitE2);

        assertThat(command(command).get("status")).isEqualTo(PENDING);
        assertThat(requests(command, KIND_CHANGE)).isEmpty();
    }

    @Test
    @DisplayName("#894 note #878 第 10 条第 1 项、ADR-0073 第 3 条第 2 项（行 54）：判据是成本法人是否变化，不看是否带目标法人：调岗同时带目标法人 E2、而员工单独归属为 E1，进待会签，依据 E1 到 E2，目标法人落库")
    void aTransferThatNamesAnotherEntityWaits() throws Exception {
        UUID employee = employed(REGULAR);
        actIn(hr);
        Map<String, Object> fields = new HashMap<>();
        fields.put(EMPLOYEE_ID, employee);
        fields.put(COMMAND_KIND, TRANSFER);
        fields.put(EFFECTIVE_DATE, DAY);
        fields.put(TARGET_ORG_UNIT_ID, unitE1b);
        fields.put(TARGET_LEGAL_ENTITY_ID, e2);

        UUID command = commands().enter(fields);

        Map<String, Object> row = command(command);
        assertThat(row.get("status")).isEqualTo(AWAITING);
        assertThat(row.get(TARGET_COLUMN)).isEqualTo(e2);
        assertThat(row.get(BASIS_PREVIOUS)).isEqualTo(e1);
        assertThat(row.get(BASIS_NEW)).isEqualTo(e2);
    }

    @Test
    @DisplayName("#894 note #878 第 10 条第 1 项、ADR-0073 第 3 条第 2、4 项：变更法人归属（CHANGE_LEGAL_ENTITY）把成本法人从 E1 改为 E2 进待会签，目标法人落库；改为本来就是的 E1 则成本法人不变，不进待会签")
    void aLegalEntityChangeWaitsOnlyWhenTheCostEntityChanges() throws Exception {
        Awaiting changing = changeTo(e2, DAY);
        UUID same = enterChange(employed(REGULAR), CHANGE_LEGAL_ENTITY, DAY, e1);

        Map<String, Object> row = command(changing.command());
        assertThat(row.get(TARGET_COLUMN)).isEqualTo(e2);
        assertThat(row.get(BASIS_PREVIOUS)).isEqualTo(e1);
        assertThat(row.get(BASIS_NEW)).isEqualTo(e2);
        assertThat(requests(changing.command(), KIND_CHANGE)).extracting(request -> request.get("legal_entity_id")).containsExactlyInAnyOrder(e1, e2);
        assertThat(command(same).get("status")).as("no change of the cost entity, no countersign").isEqualTo(PENDING);
        assertThat(requests(same, KIND_CHANGE)).isEmpty();
    }

    @Test
    @DisplayName("#894 note #878 第 10 条第 1 项、ADR-0073 第 3 条第 4 项（行 58）：调用方无法传入成本法人：录入参数里除目标法人外没有任何成本法人、前后法人、会签依据的字段；决定参数里也没有决定人")
    void theCallerCannotPassTheCostLegalEntity() throws Exception {
        List<String> components = commands().enterComponents();

        assertThat(components).contains(TARGET_LEGAL_ENTITY_ID);
        for (String component : components) {
            assertThat(CALLER_MUST_NOT_SUPPLY_COST).as("component " + component).noneMatch(word -> component.toLowerCase(java.util.Locale.ROOT).contains(word));
        }
        CountersignProbe probe = countersignsAs(headE1);
        for (List<String> deciding : List.of(probe.approveComponents(), probe.rejectComponents())) {
            assertThat(deciding).contains(COUNTERSIGN_ID);
            assertThat(deciding).noneMatch(component -> CALLER_MUST_NOT_SUPPLY.stream().anyMatch(word -> component.toLowerCase(java.util.Locale.ROOT).contains(word)));
        }
    }

    @Test
    @DisplayName("#894 note #878 第 10 条第 10 项、ADR-0073 第 3 条第 2 项（行 54）：目标法人不在法人表内或已停用，被拒：不留命令行、不留请求行")
    void aTargetOutsideTheEntitiesOrInactiveIsRefused() throws Exception {
        UUID inactive = createLegalEntity(orgAdmin.session(), newLegalEntityCode("CD"), "Synthetic inactive entity");
        try (java.sql.Connection workforce = schemaConnection("organization_workforce")) {
            update(workforce, "update legal_entities set status = 'INACTIVE' where id = ?", inactive);
        }
        UUID employee = employed(REGULAR);
        actIn(hr);

        assertThatThrownBy(() -> enterChange(employee, CHANGE_LEGAL_ENTITY, DAY, UUID.randomUUID())).as("unknown entity").isInstanceOf(DomainException.class);
        assertThatThrownBy(() -> enterChange(employee, CHANGE_LEGAL_ENTITY, DAY, inactive)).as("inactive entity").isInstanceOf(DomainException.class);

        assertThat(commandCount(employee)).isZero();
    }

    @Test
    @DisplayName("#894 note #878 第 4 节（用户答复 Q4，2026-10-30）、ADR-0068 第 1 条第 5 项：员工有待会签命令时，生效日更晚的命令被拒（含晚一天）；同一天的被拒（同日一条含待会签）；更早的可以录；起草人撤回后更晚的可以录；另一名员工不受影响")
    void laterDatedCommandsAreRefusedWhileOneAwaits() throws Exception {
        Awaiting waiting = crossEntityTransfer(DAY);
        UUID other = inheriting(unitE1);

        assertThatThrownBy(() -> transfer(waiting.employee(), DAY.plusDays(1), unitE1b)).as("one day later").isInstanceOf(DomainException.class);
        assertThatThrownBy(() -> transfer(waiting.employee(), DAY.plusDays(60), unitE1b)).as("far later").isInstanceOf(DomainException.class);
        assertThatThrownBy(() -> transfer(waiting.employee(), DAY, unitE1b)).as("the same day").isInstanceOf(DomainException.class);
        assertThat(commandCount(waiting.employee())).as("nothing was left behind by the refusals").isEqualTo(1);
        UUID earlier = transfer(waiting.employee(), DAY.minusDays(1), unitE1b);
        UUID unrelated = transfer(other, DAY.plusDays(1), unitE1b);
        commands().withdraw(waiting.command(), "synthetic: replaced");
        UUID later = transfer(waiting.employee(), DAY.plusDays(1), unitE1b);

        assertThat(Set.of(command(earlier).get("status"), command(unrelated).get("status"), command(later).get("status"))).containsExactly(PENDING);
    }

    @Test
    @DisplayName("#894 note #878 第 10 条第 9 项、ADR-0073 第 3 条第 7 项（行 75）：改法人的更正（CORRECTION）不即时生效：进待会签、不写版本；待会签的更正占着同日一条")
    void aCorrectionThatChangesTheEntityWaitsAndHoldsTheDay() throws Exception {
        UUID employee = employed(REGULAR);
        LocalDate past = TODAY.minusDays(5);
        actIn(hr);

        UUID correction = enterChange(employee, CORRECTION, past, e2);

        Map<String, Object> row = command(correction);
        assertThat(row.get("status")).as("not EFFECTIVE at once").isEqualTo(AWAITING);
        assertThat(row.get(BASIS_PREVIOUS)).isEqualTo(e1);
        assertThat(row.get(BASIS_NEW)).isEqualTo(e2);
        assertThat(versionRowsWrittenBy(correction)).isZero();
        assertThat(requests(correction, KIND_CHANGE)).extracting(request -> request.get("legal_entity_id")).containsExactlyInAnyOrder(e1, e2);
        assertThatThrownBy(() -> enterChange(employee, CHANGE_LEGAL_ENTITY, past, e3)).as("one open command per day").isInstanceOf(DomainException.class);
    }

    @Test
    @DisplayName("#894 note #878 第 10 条第 6 项、ADR-0073 第 3 条第 5 项（行 69）、ADR-0027 第 2 项第 2 条：起草人不能改本人的法人归属：人事专员为自己录入的变更法人归属被拒，不留命令行；另一名人事专员为同一人录入则进待会签")
    void aDrafterCannotChangeTheirOwnEntity() throws Exception {
        Staff drafter = specialist();
        actIn(drafter);

        assertThatThrownBy(() -> enterChange(drafter.employeeId(), CHANGE_LEGAL_ENTITY, DAY, e2)).isInstanceOf(DomainException.class);

        assertThat(commandCount(drafter.employeeId())).isZero();
        actIn(hr);
        assertThat(commandStatus(enterChange(drafter.employeeId(), CHANGE_LEGAL_ENTITY, DAY, e2))).as("someone else may draft it").isEqualTo(AWAITING);
    }
}
