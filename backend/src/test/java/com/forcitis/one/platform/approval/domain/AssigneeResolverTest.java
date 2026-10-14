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
        org.role("reviewer", 2, 3, 120);
        Resolution r = resolve(new AssignmentRule.Role(new RoleCode("reviewer")), 1, ProcessVariables.empty());
        assertThat(r.candidates()).isEqualTo(ids(2, 3));
        assertThat(r.kind()).isEqualTo(ResolutionKind.RESOLVED);
    }

    @Test
    @DisplayName("RULE-253/257 department head; a unit without a head pushes up to the nearest ancestor head")
    void departmentHeadPushesUp() {
        assertThat(resolve(new AssignmentRule.DepartmentHead(null), 2, ProcessVariables.empty()).candidates()).isEqualTo(ids(110));
        // initiator 1 sits in unit 11, which has no head: the head of unit 10 takes it
        assertThat(resolve(new AssignmentRule.DepartmentHead(""), 1, ProcessVariables.empty()).candidates()).isEqualTo(ids(110));
        // the unit comes from a process variable; unit 12's only head is separated -> unit 10's head
        assertThat(resolve(new AssignmentRule.DepartmentHead("deliveryUnitId"), 1, variable("deliveryUnitId", "12")).candidates())
                .isEqualTo(ids(110));
        assertThat(resolve(new AssignmentRule.DepartmentHead("deliveryUnitId"), 1, variable("deliveryUnitId", "13")).candidates())
                .isEqualTo(ids(110));
    }

    @Test
    @DisplayName("RULE-253 an unparsable or missing unit never fails with a null: the administrator takes it and the kind says so")
    void unparsableUnitGoesToAdministrator() {
        Resolution r = resolve(new AssignmentRule.DepartmentHead("deliveryUnitId"), 1, variable("deliveryUnitId", "not-a-number"));
        assertThat(r.candidates()).isEqualTo(ids(ADMIN));
        assertThat(r.kind()).isEqualTo(ResolutionKind.ADMINISTRATOR_FALLBACK);
    }

    @Test
    @DisplayName("RULE-250 initiator supervisor level 1 and level 2 follow the reporting line")
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
    @DisplayName("RULE-300 a node that may be skipped is skipped only when its input is complete and valid and nobody is left; a node that may not be skipped goes to the administrator")
    void skipOnlyWhenEmptyAndAllowed() {
        AssignmentRule.FormFieldUsers rule = new AssignmentRule.FormFieldUsers("projectManagerId");
        Resolution skipped = resolver.resolve(rule, false, true, new UserId(1), fields("projectManagerId", "120"));
        assertThat(skipped.kind()).isEqualTo(ResolutionKind.SKIPPED);
        assertThat(skipped.candidates()).isEmpty();
        assertThat(resolver.resolve(rule, false, false, new UserId(1), fields("projectManagerId", "120")).kind())
                .isEqualTo(ResolutionKind.ADMINISTRATOR_FALLBACK);
        assertThat(resolver.resolve(rule, false, false, new UserId(1), ProcessVariables.empty()).kind())
                .isEqualTo(ResolutionKind.ADMINISTRATOR_FALLBACK);
        assertThat(resolver.resolve(rule, false, true, new UserId(1), fields("projectManagerId", "2")).kind())
                .isEqualTo(ResolutionKind.RESOLVED);
    }

    @Test
    @DisplayName("G-1 / RULE-300 a missing field, an empty field and a value that is not an existing person are INVALID_INPUT, never SKIPPED")
    void invalidInputIsNeverSkipped() {
        AssignmentRule.FormFieldUsers rule = new AssignmentRule.FormFieldUsers("projectManagerId");
        for (ProcessVariables input : List.of(ProcessVariables.empty(), fields("projectManagerId"), fields("projectManagerId", " "),
                fields("projectManagerId", "x"), fields("projectManagerId", "0"), fields("projectManagerId", "424242"))) {
            assertThat(resolver.resolve(rule, false, true, new UserId(1), input).kind()).as(input.toString())
                    .isEqualTo(ResolutionKind.INVALID_INPUT);
        }
        assertThat(resolver.resolve(new AssignmentRule.DepartmentHead("unitVar"), false, true, new UserId(1),
                variable("unitVar", "424242")).kind()).isEqualTo(ResolutionKind.INVALID_INPUT);
        assertThat(resolver.resolve(new AssignmentRule.DepartmentHead("unitVar"), false, true, new UserId(1), ProcessVariables.empty())
                .kind()).isEqualTo(ResolutionKind.INVALID_INPUT);
        assertThat(resolver.resolve(new AssignmentRule.InitiatorSupervisor(1), false, true, new UserId(424242),
                ProcessVariables.empty()).kind()).isEqualTo(ResolutionKind.INVALID_INPUT);
    }

    @Test
    @DisplayName("RULE-300 a complete, valid input with really nobody (no supervisor, no head, no role holder) is SKIPPED")
    void reallyNobodyIsSkipped() {
        FakeOrganisation lonely = new FakeOrganisation().unit(1, null, null).person(5, 1, null);
        AssigneeResolver lonelyResolver = new AssigneeResolver(lonely, lonely, new UserId(ADMIN));

        assertThat(lonelyResolver.resolve(new AssignmentRule.InitiatorSupervisor(1), false, true, new UserId(5), ProcessVariables.empty())
                .kind()).isEqualTo(ResolutionKind.SKIPPED);
        assertThat(lonelyResolver.resolve(new AssignmentRule.Role(new RoleCode("nobody-holds-this")), false, true, new UserId(5),
                ProcessVariables.empty()).kind()).isEqualTo(ResolutionKind.SKIPPED);
        assertThat(lonelyResolver.resolve(new AssignmentRule.DepartmentHead(null), false, true, new UserId(5), ProcessVariables.empty())
                .kind()).isEqualTo(ResolutionKind.SKIPPED);
    }

    @Test
    @DisplayName("RULE-258/260 form field users: every distinct value is a candidate; blanks and garbage are ignored")
    void formFieldUsers() {
        org.person(-4000000000000000001L, 10, null);
        Resolution r = resolve(new AssignmentRule.FormFieldUsers("projectManagerId"), 1,
                fields("projectManagerId", "2", "3", "2", " ", "x", "-4000000000000000001"));
        assertThat(r.candidates()).isEqualTo(ids(2, 3, -4000000000000000001L));
    }

    @Test
    @DisplayName("RULE-254 a missing form field goes to the process administrator, never to a hard-coded person")
    void missingFormFieldGoesToAdministrator() {
        Resolution r = resolve(new AssignmentRule.FormFieldUsers("projectManagerId"), 1, ProcessVariables.empty());
        assertThat(r.candidates()).isEqualTo(ids(ADMIN));
        assertThat(r.kind()).isEqualTo(ResolutionKind.ADMINISTRATOR_FALLBACK);
    }

    @Test
    @DisplayName("RULE-307 the initiator is removed when somebody else qualifies (the leader is NOT added)")
    void selfApprovalRemovedWhenOthersExist() {
        Resolution r = resolve(new AssignmentRule.FormFieldUsers("projectManagerId"), 1, fields("projectManagerId", "1", "3"));
        assertThat(r.candidates()).isEqualTo(ids(3));
        assertThat(r.kind()).isEqualTo(ResolutionKind.RESOLVED);
    }

    @Test
    @DisplayName("RULE-307 only when the initiator is the sole candidate does the task escalate to the direct leader")
    void selfApprovalEscalatesToSupervisorOnlyWhenAlone() {
        Resolution r = resolve(new AssignmentRule.FormFieldUsers("projectManagerId"), 1, fields("projectManagerId", "1"));
        assertThat(r.candidates()).isEqualTo(ids(2));
        assertThat(r.kind()).isEqualTo(ResolutionKind.ESCALATED_SELF_APPROVAL);
        // no leader either -> administrator, never the initiator
        Resolution noSupervisor = resolve(new AssignmentRule.FormFieldUsers("projectManagerId"), 3, fields("projectManagerId", "3"));
        assertThat(noSupervisor.candidates()).isEqualTo(ids(ADMIN));
    }

    @Test
    @DisplayName("RULE-307 an employee-activity node may be approved by its initiator")
    void selfApprovalAllowedOnEmployeeActivities() {
        Resolution r = resolver.resolve(new AssignmentRule.FormFieldUsers("projectManagerId"), true, new UserId(1), fields("projectManagerId", "1"));
        assertThat(r.candidates()).isEqualTo(ids(1));
    }

    @Test
    @DisplayName("RULE-249 the function is deterministic: the same input always gives the same candidate set")
    void deterministic() {
        org.role("reviewer", 2, 3, 110, 101);
        Resolution first = resolve(new AssignmentRule.Role(new RoleCode("reviewer")), 1, ProcessVariables.empty());
        for (int i = 0; i < 50; i++) {
            assertThat(resolve(new AssignmentRule.Role(new RoleCode("reviewer")), 1, ProcessVariables.empty())).isEqualTo(first);
        }
        assertThat(first.candidates()).hasSize(4);
    }

    @Test
    @DisplayName("RULE-262 an unknown rule type or a missing attribute is a hard configuration error")
    void ruleCodecIsStrict() {
        assertThatThrownBy(() -> AssignmentRuleCodec.parse("RANDOM_PERSON", n -> null)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> AssignmentRuleCodec.parse("ROLE", n -> null)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> AssignmentRuleCodec.parse("FORM_FIELD_USERS", n -> null)).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("field");
        assertThatThrownBy(() -> AssignmentRuleCodec.parse("INITIATOR_SUPERVISOR", n -> null)).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("level");
        assertThatThrownBy(() -> AssignmentRuleCodec.parse("INITIATOR_SUPERVISOR", n -> n.equals("level") ? "x" : null))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> AssignmentRuleCodec.parse(null, n -> null)).isInstanceOf(IllegalStateException.class);
        assertThat(AssignmentRuleCodec.parse("INITIATOR_SUPERVISOR", n -> n.equals("level") ? "2" : null))
                .isEqualTo(new AssignmentRule.InitiatorSupervisor(2));
        assertThat(AssignmentRuleCodec.parse("FORM_FIELD_USERS", n -> n.equals("field") ? "projectManagerId" : null))
                .isEqualTo(new AssignmentRule.FormFieldUsers("projectManagerId"));
    }

    @Test
    @DisplayName("H1 the department of a DEPARTMENT_HEAD node is optional: not written = the initiator's own unit; written = that variable")
    void departmentIsOptionalForTheDepartmentHead() {
        assertThat(AssignmentRuleCodec.parse("DEPARTMENT_HEAD", n -> null)).isEqualTo(new AssignmentRule.DepartmentHead(null));
        assertThat(AssignmentRuleCodec.parse("DEPARTMENT_HEAD", n -> n.equals("department") ? "deliveryUnitId" : null))
                .isEqualTo(new AssignmentRule.DepartmentHead("deliveryUnitId"));
        assertThat(resolve(new AssignmentRule.DepartmentHead(null), 1, variable("deliveryUnitId", "12")).candidates()).isEqualTo(ids(110));
    }

    @Test
    @DisplayName("1a catalogue: types for later phases are refused with a clear message, types that are not offered are refused too")
    void deferredAndNotBuiltTypesAreRefused() {
        for (String type : AssignmentRuleCodec.DEFERRED_TYPES) {
            assertThatThrownBy(() -> AssignmentRuleCodec.parse(type, n -> "x")).as(type).isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining(type).hasMessageContaining("deferred");
        }
        for (String type : AssignmentRuleCodec.NOT_BUILT_TYPES) {
            assertThatThrownBy(() -> AssignmentRuleCodec.parse(type, n -> "1")).as(type).isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining(type).hasMessageContaining("not offered");
        }
        assertThat(AssignmentRuleCodec.DEFERRED_TYPES).containsExactlyInAnyOrder("REGIONAL_RECEIVABLES_MANAGER", "DEPARTMENT_DIRECTOR",
                "FORM_FIELD_USER_SUPERVISORS");
        assertThat(AssignmentRuleCodec.NOT_BUILT_TYPES).containsExactlyInAnyOrder("NAMED_USERS", "PROCESS_VARIABLE_USERS");
        assertThatThrownBy(() -> AssignmentRuleCodec.parse("INITIATOR", n -> null)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> AssignmentRuleCodec.parse("INITIATOR_LEADER", n -> "1")).isInstanceOf(IllegalStateException.class);
    }
}
