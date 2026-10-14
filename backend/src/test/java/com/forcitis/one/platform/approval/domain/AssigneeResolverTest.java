package com.forcitis.one.platform.approval.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.forcitis.one.kernel.UserId;
import com.forcitis.one.platform.approval.FakeOrganisation;
import com.forcitis.one.platform.identity.api.RoleCode;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** The pure assignment function on a synthetic organisation. Nothing here touches Spring, a database or the engine. */
class AssigneeResolverTest {

    static final long ADMIN = 900;
    // units: 1 root (head 101) > 10 (head 110) > 11 (no head) ; 10 > 12 (head 120, separated)
    // people: initiator 1 (unit 11, leader 2), 2 (unit 10, leader 3), 3 (unit 1, no leader)
    FakeOrganisation org;
    AssigneeResolver resolver;

    @BeforeEach
    void organisation() {
        org = new FakeOrganisation()
                .unit(1, null, 101L).unit(10, 1L, 110L).unit(11, 10L, null).unit(12, 10L, 120L)
                .unit(13, 10L, 130L, 131L, false)
                .person(101, 1, null).person(110, 10, null).separated(120, 12, null).person(130, 13, null).person(131, 10, null)
                .person(1, 11, 2L).person(2, 10, 3L).person(3, 1, null)
                .person(ADMIN, 1, null);
        resolver = new AssigneeResolver(org, org, new UserId(ADMIN));
    }

    Resolution resolve(AssignmentRule rule, long initiator, ProcessVariables variables) {
        return resolver.resolve(rule, false, new UserId(initiator), variables);
    }

    static Set<UserId> ids(long... values) {
        return java.util.Arrays.stream(values).mapToObj(UserId::new).collect(java.util.stream.Collectors.toSet());
    }

    static ProcessVariables fields(String name, String... values) {
        return new ProcessVariables(Map.of(name, List.of(values)), Map.of());
    }

    static ProcessVariables variable(String name, String... values) {
        return new ProcessVariables(Map.of(), Map.of(name, List.of(values)));
    }

    @Test
    @DisplayName("RULE-253 a role gives its enabled, employed holders; separated holders are excluded")
    void roleHoldersWithoutSeparatedPeople() {
        org.role("TEST-ROLE", 2, 3, 120);
        Resolution r = resolve(new AssignmentRule.Role(new RoleCode("TEST-ROLE")), 1, ProcessVariables.empty());
        assertThat(r.candidates()).isEqualTo(ids(2, 3));
        assertThat(r.kind()).isEqualTo(ResolutionKind.RESOLVED);
    }

    @Test
    @DisplayName("RULE-253/257 department head; a unit without a head pushes up to the nearest ancestor head")
    void departmentHeadPushesUp() {
        assertThat(resolve(new AssignmentRule.DepartmentHead(null), 2, ProcessVariables.empty()).candidates()).isEqualTo(ids(110));
        // initiator 1 sits in unit 11, which has no head: the head of unit 10 takes it
        assertThat(resolve(new AssignmentRule.DepartmentHead(""), 1, ProcessVariables.empty()).candidates()).isEqualTo(ids(110));
        // the unit comes from a process variable (RULE-257); unit 12's only head is separated -> unit 10's head
        assertThat(resolve(new AssignmentRule.DepartmentHead("deliveryDeptId"), 1, variable("deliveryDeptId", "12")).candidates())
                .isEqualTo(ids(110));
        // an inactive (revoked) unit is skipped
        assertThat(resolve(new AssignmentRule.DepartmentHead("deliveryDeptId"), 1, variable("deliveryDeptId", "13")).candidates())
                .isEqualTo(ids(110));
    }

    @Test
    @DisplayName("RULE-253 an unparsable or missing unit never fails with a null: the administrator takes it and the kind says so")
    void unparsableUnitGoesToAdministrator() {
        Resolution r = resolve(new AssignmentRule.DepartmentHead("deliveryDeptId"), 1, variable("deliveryDeptId", "not-a-number"));
        assertThat(r.candidates()).isEqualTo(ids(ADMIN));
        assertThat(r.kind()).isEqualTo(ResolutionKind.ADMINISTRATOR_FALLBACK);
    }

    @Test
    @DisplayName("RULE-250 initiator leader level 1 and level 2 follow the reporting line")
    void initiatorSupervisors() {
        assertThat(resolve(new AssignmentRule.InitiatorSupervisor(1), 1, ProcessVariables.empty()).candidates()).isEqualTo(ids(2));
        assertThat(resolve(new AssignmentRule.InitiatorSupervisor(2), 1, ProcessVariables.empty()).candidates()).isEqualTo(ids(3));
    }

    @Test
    @DisplayName("RULE-250 an incomplete reporting line pushes up to a department head (no NPE, no silent skip)")
    void incompleteReportingLinePushesUp() {
        // initiator 2's leader is 3, whose line ends: level 2 has nobody -> head of person 3's unit (root, head 101)
        Resolution r = resolve(new AssignmentRule.InitiatorSupervisor(2), 2, ProcessVariables.empty());
        assertThat(r.candidates()).isEqualTo(ids(101));
        assertThat(r.kind()).isEqualTo(ResolutionKind.ESCALATED_REPORTING_LINE);
        // person 3 has no leader at all: level 1 pushes up from person 3's unit (root head 101)
        assertThat(resolve(new AssignmentRule.InitiatorSupervisor(1), 3, ProcessVariables.empty()).candidates()).isEqualTo(ids(101));
    }

    @Test
    @DisplayName("RULE-250 nothing found anywhere goes to the process administrator; without one the result is UNRESOLVED")
    void nothingFoundGoesToAdministrator() {
        FakeOrganisation lonely = new FakeOrganisation().unit(1, null, null).person(5, 1, null);
        Resolution r = new AssigneeResolver(lonely, lonely, new UserId(ADMIN))
                .resolve(new AssignmentRule.InitiatorSupervisor(1), false, new UserId(5), ProcessVariables.empty());
        assertThat(r.candidates()).isEqualTo(ids(ADMIN));
        assertThat(r.kind()).isEqualTo(ResolutionKind.ADMINISTRATOR_FALLBACK);

        Resolution none = new AssigneeResolver(lonely, lonely, null)
                .resolve(new AssignmentRule.InitiatorSupervisor(1), false, new UserId(5), ProcessVariables.empty());
        assertThat(none.candidates()).isEmpty();
        assertThat(none.kind()).isEqualTo(ResolutionKind.UNRESOLVED);
    }

    @Test
    @DisplayName("RULE-253 named users: separated and unknown people are dropped, the set never contains a null")
    void namedUsers() {
        Resolution r = resolve(new AssignmentRule.NamedUsers(ids(2, 120, 777)), 1, ProcessVariables.empty());
        assertThat(r.candidates()).isEqualTo(ids(2));
    }

    @Test
    @DisplayName("RULE-255 regional receivables manager of the unit named by the variable; none -> administrator")
    void regionalReceivablesManager() {
        org.unit(14, 10L, null, 131L, true);
        assertThat(resolve(new AssignmentRule.RegionalReceivablesOwner("deliveryDeptId"), 1, variable("deliveryDeptId", "14")).candidates())
                .isEqualTo(ids(131));
        Resolution none = resolve(new AssignmentRule.RegionalReceivablesOwner("deliveryDeptId"), 1, variable("deliveryDeptId", "11"));
        assertThat(none.kind()).isEqualTo(ResolutionKind.ADMINISTRATOR_FALLBACK);
    }

    @Test
    @DisplayName("RULE-256 department director = holders of BU-002 who sit directly in the unit, not in a parent")
    void departmentDirector() {
        org.role("BU-002", 2, 3, 110);
        Resolution r = resolve(new AssignmentRule.DepartmentDirector("deliveryDeptId"), 1, variable("deliveryDeptId", "10"));
        assertThat(r.candidates()).isEqualTo(ids(2, 110));
        assertThat(resolve(new AssignmentRule.DepartmentDirector("deliveryDeptId"), 1, variable("deliveryDeptId", "1")).candidates())
                .isEqualTo(ids(3));
    }

    @Test
    @DisplayName("RULE-258/260 form field users: every distinct value is a candidate; blanks and garbage are ignored")
    void formFieldUsers() {
        org.person(-4000000000000000001L, 10, null);
        Resolution r = resolve(new AssignmentRule.FormFieldUsers("manager_id"), 1,
                fields("manager_id", "2", "3", "2", " ", "x", "-4000000000000000001"));
        assertThat(r.candidates()).isEqualTo(ids(2, 3, -4000000000000000001L));
    }

    @Test
    @DisplayName("RULE-254 a missing form field goes to the process administrator, never to a hard-coded person")
    void missingFormFieldGoesToAdministrator() {
        Resolution r = resolve(new AssignmentRule.FormFieldUsers("manager_id"), 1, ProcessVariables.empty());
        assertThat(r.candidates()).isEqualTo(ids(ADMIN));
        assertThat(r.kind()).isEqualTo(ResolutionKind.ADMINISTRATOR_FALLBACK);
    }

    @Test
    @DisplayName("RULE-259/260 the direct leaders of the people in a form field, resolved per value and unioned")
    void formFieldUserSupervisors() {
        org.person(20, 10, 110L).person(21, 10, 101L);
        Resolution r = resolve(new AssignmentRule.FormFieldUserSupervisors("manager_id"), 1, fields("manager_id", "20", "21", "999"));
        assertThat(r.candidates()).isEqualTo(ids(110, 101));
    }

    @Test
    @DisplayName("process variable users come from engine variables, not from business fields")
    void processVariableUsers() {
        assertThat(resolve(new AssignmentRule.ProcessVariableUsers("reviewerId"), 1, variable("reviewerId", "2")).candidates())
                .isEqualTo(ids(2));
        // a business field of the same name must not be read as an engine variable
        assertThat(resolve(new AssignmentRule.ProcessVariableUsers("reviewerId"), 1, fields("reviewerId", "2")).kind())
                .isEqualTo(ResolutionKind.ADMINISTRATOR_FALLBACK);
    }

    @Test
    @DisplayName("RULE-307 the initiator is removed when somebody else qualifies (the leader is NOT added)")
    void selfApprovalRemovedWhenOthersExist() {
        Resolution r = resolve(new AssignmentRule.FormFieldUsers("manager_id"), 1, fields("manager_id", "1", "3"));
        assertThat(r.candidates()).isEqualTo(ids(3));
        assertThat(r.kind()).isEqualTo(ResolutionKind.RESOLVED);
    }

    @Test
    @DisplayName("RULE-307 only when the initiator is the sole candidate does the task escalate to the direct leader")
    void selfApprovalEscalatesToSupervisorOnlyWhenAlone() {
        Resolution r = resolve(new AssignmentRule.FormFieldUsers("manager_id"), 1, fields("manager_id", "1"));
        assertThat(r.candidates()).isEqualTo(ids(2));
        assertThat(r.kind()).isEqualTo(ResolutionKind.ESCALATED_SELF_APPROVAL);
        // no leader either -> administrator, never the initiator
        Resolution noSupervisor = resolve(new AssignmentRule.FormFieldUsers("manager_id"), 3, fields("manager_id", "3"));
        assertThat(noSupervisor.candidates()).isEqualTo(ids(ADMIN));
    }

    @Test
    @DisplayName("RULE-307 an employee-activity node may be approved by its initiator")
    void selfApprovalAllowedOnEmployeeActivities() {
        Resolution r = resolver.resolve(new AssignmentRule.FormFieldUsers("manager_id"), true, new UserId(1), fields("manager_id", "1"));
        assertThat(r.candidates()).isEqualTo(ids(1));
    }

    @Test
    @DisplayName("the initiator rule gives the initiator and is exempt from the self-approval guard")
    void initiatorRule() {
        assertThat(resolve(new AssignmentRule.Initiator(), 1, ProcessVariables.empty()).candidates()).isEqualTo(ids(1));
    }

    @Test
    @DisplayName("RULE-249 the function is deterministic: the same input always gives the same candidate set")
    void deterministic() {
        org.role("TEST-ROLE", 2, 3, 110, 101);
        Resolution first = resolve(new AssignmentRule.Role(new RoleCode("TEST-ROLE")), 1, ProcessVariables.empty());
        for (int i = 0; i < 50; i++) {
            assertThat(resolve(new AssignmentRule.Role(new RoleCode("TEST-ROLE")), 1, ProcessVariables.empty())).isEqualTo(first);
        }
        assertThat(first.candidates()).hasSize(4);
    }

    @Test
    @DisplayName("RULE-262 an unknown rule type or a missing attribute is a hard configuration error")
    void ruleCodecIsStrict() {
        assertThatThrownBy(() -> AssignmentRuleCodec.parse("RANDOM_PERSON", n -> null)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> AssignmentRuleCodec.parse("ROLE", n -> null)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> AssignmentRuleCodec.parse(null, n -> null)).isInstanceOf(IllegalStateException.class);
        assertThat(AssignmentRuleCodec.parse("INITIATOR_SUPERVISOR", n -> n.equals("level") ? "2" : null))
                .isEqualTo(new AssignmentRule.InitiatorSupervisor(2));
    }
}
