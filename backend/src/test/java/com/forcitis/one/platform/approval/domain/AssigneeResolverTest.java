package com.forcitis.one.platform.approval.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.forcitis.one.fixture.doubles.TestAccounts;
import com.forcitis.one.fixture.doubles.TestOrgIds;
import com.forcitis.one.kernel.DomainException;
import com.forcitis.one.platform.approval.api.ApprovalError;
import com.forcitis.one.kernel.UserId;
import com.forcitis.one.platform.approval.FakeOrganisation;
import com.forcitis.one.platform.identity.api.RoleCode;
import java.util.List;
import java.util.Map;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
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
        resolver = new AssigneeResolver(org, org, java.util.Set.of(TestAccounts.account(ADMIN)), TestAccounts.everyoneHasOne(), java.time.LocalDate.of(2026, 11, 2));
    }

    Resolution resolve(AssignmentRule rule, long initiator, ProcessVariables variables) {
        return resolver.resolve(rule, false, TestAccounts.employee(initiator), variables);
    }

    static Set<UserId> ids(long... values) {
        return java.util.Arrays.stream(values).mapToObj(v -> TestAccounts.account(TestAccounts.employee(v))).collect(java.util.stream.Collectors.toSet());
    }

    static String person(long n) {
        return TestAccounts.employee(n).toString();
    }

    static String unit(long n) {
        return TestOrgIds.orgUnit(n).toString();
    }

    static void refused(Runnable resolution) {
        assertThatThrownBy(resolution::run).isInstanceOfSatisfying(DomainException.class,
                e -> assertThat(e.errorCode()).isEqualTo(ApprovalError.PLATFORM_APPROVAL_ASSIGNEE_INPUT_INVALID));
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
    @DisplayName("RULE-253/257 a parent chain that loops back on itself ends at the loop: no endless climb, the head found before the loop still counts")
    void departmentHeadStopsAtAParentLoop() {
        org.unit(20, 21L, null).unit(21, 20L, null).unit(22, 21L, 122L).person(122, 22, null);
        java.time.Duration limit = java.time.Duration.ofSeconds(5);
        assertTimeoutPreemptively(limit, () -> {
            assertThat(resolve(new AssignmentRule.DepartmentHead("loopUnitId"), 1, variable("loopUnitId", unit(20))).candidates())
                    .doesNotContain(TestAccounts.account(TestAccounts.employee(110)));
            assertThat(resolve(new AssignmentRule.DepartmentHead("loopUnitId"), 1, variable("loopUnitId", unit(22))).candidates())
                    .isEqualTo(ids(122));
        });
    }

    @Test
    @DisplayName("RULE-253/257 department head; a unit without a head pushes up to the nearest ancestor head")
    void departmentHeadPushesUp() {
        assertThat(resolve(new AssignmentRule.DepartmentHead(null), 2, ProcessVariables.empty()).candidates()).isEqualTo(ids(110));
        // initiator 1 sits in unit 11, which has no head: the head of unit 10 takes it
        assertThat(resolve(new AssignmentRule.DepartmentHead(""), 1, ProcessVariables.empty()).candidates()).isEqualTo(ids(110));
        // the unit comes from a process variable; unit 12's only head is separated -> unit 10's head
        assertThat(resolve(new AssignmentRule.DepartmentHead("deliveryUnitId"), 1, variable("deliveryUnitId", unit(12))).candidates())
                .isEqualTo(ids(110));
        assertThat(resolve(new AssignmentRule.DepartmentHead("deliveryUnitId"), 1, variable("deliveryUnitId", unit(13))).candidates())
                .isEqualTo(ids(110));
    }

    @Test
    @DisplayName("RULE-253 a blank unit never fails with a null: the administrator takes it and the kind says so")
    void blankUnitGoesToAdministrator() {
        Resolution r = resolve(new AssignmentRule.DepartmentHead("deliveryUnitId"), 1, variable("deliveryUnitId", " "));
        assertThat(r.candidates()).isEqualTo(ids(ADMIN));
        assertThat(r.kind()).isEqualTo(ResolutionKind.ADMINISTRATOR_FALLBACK);
        assertThat(resolver.resolve(new AssignmentRule.DepartmentHead("deliveryUnitId"), false, true, TestAccounts.employee(1),
                variable("deliveryUnitId", " ")).kind()).as("a blank unit is no unit, not the initiator's own").isEqualTo(ResolutionKind.INVALID_INPUT);
    }

    @Test
    @DisplayName("ADR-0033 a unit variable that is not an org unit id refuses the assignment: never skipped, never the initiator's unit")
    void unitThatIsNoOrgUnitIdIsRefused() {
        for (String value : List.of("not-a-number", "12", "-12", "0", unit(12).toUpperCase(java.util.Locale.ROOT))) {
            refused(() -> resolve(new AssignmentRule.DepartmentHead("deliveryUnitId"), 1, variable("deliveryUnitId", value)));
            refused(() -> resolver.resolve(new AssignmentRule.DepartmentHead("deliveryUnitId"), false, true, TestAccounts.employee(1),
                    variable("deliveryUnitId", value)));
        }
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
        Resolution r = new AssigneeResolver(lonely, lonely, java.util.Set.of(TestAccounts.account(ADMIN)), TestAccounts.everyoneHasOne(), java.time.LocalDate.of(2026, 11, 2))
                .resolve(new AssignmentRule.InitiatorSupervisor(1), false, TestAccounts.employee(5), ProcessVariables.empty());
        assertThat(r.candidates()).isEqualTo(ids(ADMIN));
        assertThat(r.kind()).isEqualTo(ResolutionKind.ADMINISTRATOR_FALLBACK);

        Resolution none = new AssigneeResolver(lonely, lonely, java.util.Set.of(), TestAccounts.everyoneHasOne(), java.time.LocalDate.of(2026, 11, 2))
                .resolve(new AssignmentRule.InitiatorSupervisor(1), false, TestAccounts.employee(5), ProcessVariables.empty());
        assertThat(none.candidates()).isEmpty();
        assertThat(none.kind()).isEqualTo(ResolutionKind.UNRESOLVED);
    }

    @Test
    @DisplayName("RULE-300 a node that may be skipped is skipped only when its input is complete and valid and nobody is left; a node that may not be skipped goes to the administrator; a node that may not be skipped goes to the administrator")
    void skipOnlyWhenEmptyAndAllowed() {
        AssignmentRule.FormFieldUsers rule = new AssignmentRule.FormFieldUsers("projectManagerId");
        Resolution skipped = resolver.resolve(rule, false, true, TestAccounts.employee(1), fields("projectManagerId", person(120)));
        assertThat(skipped.kind()).isEqualTo(ResolutionKind.SKIPPED);
        assertThat(skipped.namedApproverInvalid()).as("the document names an approver who cannot act: remembered, so that an approval whose every node was skipped like this goes back to the initiator").isTrue();
        assertThat(skipped.candidates()).isEmpty();
        Resolution nobody = resolver.resolve(new AssignmentRule.Role(new com.forcitis.one.platform.identity.api.RoleCode("nobody-holds-this")), false, true,
                TestAccounts.employee(1), ProcessVariables.empty());
        assertThat(nobody.kind()).isEqualTo(ResolutionKind.SKIPPED);
        assertThat(nobody.namedApproverInvalid()).as("a rule that finds nobody by itself is a set-up matter").isFalse();
        assertThat(resolver.resolve(rule, false, false, TestAccounts.employee(1), fields("projectManagerId", person(120))).kind())
                .isEqualTo(ResolutionKind.ADMINISTRATOR_FALLBACK);
        assertThat(resolver.resolve(rule, false, false, TestAccounts.employee(1), ProcessVariables.empty()).kind())
                .isEqualTo(ResolutionKind.ADMINISTRATOR_FALLBACK);
        assertThat(resolver.resolve(rule, false, true, TestAccounts.employee(1), fields("projectManagerId", person(2))).kind())
                .isEqualTo(ResolutionKind.RESOLVED);
    }

    @Test
    @DisplayName("G-1 / RULE-300 a missing field, an empty field and a value that is not an existing person are INVALID_INPUT, never SKIPPED")
    void invalidInputIsNeverSkipped() {
        AssignmentRule.FormFieldUsers rule = new AssignmentRule.FormFieldUsers("projectManagerId");
        for (ProcessVariables input : List.of(ProcessVariables.empty(), fields("projectManagerId"), fields("projectManagerId", " "),
                fields("projectManagerId", person(424242)))) {
            assertThat(resolver.resolve(rule, false, true, TestAccounts.employee(1), input).kind()).as(input.toString())
                    .isEqualTo(ResolutionKind.INVALID_INPUT);
        }
        for (String notAnEmployeeId : List.of("x", "0", "424242")) {
            refused(() -> resolver.resolve(rule, false, true, TestAccounts.employee(1), fields("projectManagerId", notAnEmployeeId)));
        }
        assertThat(resolver.resolve(new AssignmentRule.DepartmentHead("unitVar"), false, true, TestAccounts.employee(1),
                variable("unitVar", unit(424242))).kind()).isEqualTo(ResolutionKind.INVALID_INPUT);
        assertThat(resolver.resolve(new AssignmentRule.DepartmentHead("unitVar"), false, true, TestAccounts.employee(1), ProcessVariables.empty())
                .kind()).isEqualTo(ResolutionKind.INVALID_INPUT);
        assertThat(resolver.resolve(new AssignmentRule.InitiatorSupervisor(1), false, true, TestAccounts.employee(424242),
                ProcessVariables.empty()).kind()).isEqualTo(ResolutionKind.INVALID_INPUT);
    }

    @Test
    @DisplayName("RULE-300 a complete, valid input with really nobody (no supervisor, no head, no role holder) is SKIPPED")
    void reallyNobodyIsSkipped() {
        FakeOrganisation lonely = new FakeOrganisation().unit(1, null, null).person(5, 1, null);
        AssigneeResolver lonelyResolver = new AssigneeResolver(lonely, lonely, java.util.Set.of(TestAccounts.account(ADMIN)), TestAccounts.everyoneHasOne(), java.time.LocalDate.of(2026, 11, 2));

        assertThat(lonelyResolver.resolve(new AssignmentRule.InitiatorSupervisor(1), false, true, TestAccounts.employee(5), ProcessVariables.empty())
                .kind()).isEqualTo(ResolutionKind.SKIPPED);
        assertThat(lonelyResolver.resolve(new AssignmentRule.Role(new RoleCode("nobody-holds-this")), false, true, TestAccounts.employee(5),
                ProcessVariables.empty()).kind()).isEqualTo(ResolutionKind.SKIPPED);
        assertThat(lonelyResolver.resolve(new AssignmentRule.DepartmentHead(null), false, true, TestAccounts.employee(5), ProcessVariables.empty())
                .kind()).isEqualTo(ResolutionKind.SKIPPED);
    }

    @Test
    @DisplayName("RULE-258/260 form field users: every distinct value is a candidate; blanks are ignored")
    void formFieldUsers() {
        long negativeBigId = Long.MIN_VALUE / 2;
        org.person(negativeBigId, 10, null);
        Resolution r = resolve(new AssignmentRule.FormFieldUsers("projectManagerId"), 1,
                fields("projectManagerId", person(2), person(3), person(2), " ", person(negativeBigId)));
        assertThat(r.candidates()).isEqualTo(ids(2, 3, negativeBigId));
    }

    @Test
    @DisplayName("IMP-1030-PAR-B5 (ADR-0068 section 3, the third in-service check): a person on unpaid leave is skipped as an approver, like a separated one; the others stay")
    void aPersonOnUnpaidLeaveIsSkipped() {
        org.onLeave.add(TestAccounts.employee(2));

        Resolution r = resolve(new AssignmentRule.FormFieldUsers("projectManagerId"), 1, fields("projectManagerId", person(2), person(3)));

        assertThat(r.candidates()).as("2 is on leave: only 3 remains").isEqualTo(ids(3));
    }

    @Test
    @DisplayName("ADR-0033 one value that is not an employee id among valid ones refuses the whole field, it is not skipped")
    void garbageAmongPeopleIsRefused() {
        for (String garbage : List.of("x", "3", "-3", person(3).toUpperCase(java.util.Locale.ROOT))) {
            refused(() -> resolve(new AssignmentRule.FormFieldUsers("projectManagerId"), 1, fields("projectManagerId", person(2), " ", garbage)));
        }
    }

    @Test
    @DisplayName("ADR-0033 parseEmployees: blanks are ignored, a value that is not an employee id refuses the list as a whole")
    void parseEmployeesRefusesTheWholeList() {
        assertThat(ProcessVariables.parseEmployees(java.util.Arrays.asList(person(2), " ", null, person(2), person(3))))
                .containsExactly(TestAccounts.employee(2), TestAccounts.employee(3));
        for (String garbage : List.of("x", "3", "0", "-3", person(3).toUpperCase(java.util.Locale.ROOT))) {
            assertThatThrownBy(() -> ProcessVariables.parseEmployees(List.of(person(2), garbage))).as(garbage)
                    .isInstanceOfSatisfying(DomainException.class,
                            e -> assertThat(e.errorCode()).isEqualTo(ApprovalError.PLATFORM_APPROVAL_ASSIGNEE_INPUT_INVALID));
        }
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
        Resolution r = resolve(new AssignmentRule.FormFieldUsers("projectManagerId"), 1, fields("projectManagerId", person(1), person(3)));
        assertThat(r.candidates()).isEqualTo(ids(3));
        assertThat(r.kind()).isEqualTo(ResolutionKind.RESOLVED);
    }

    @Test
    @DisplayName("RULE-307 only when the initiator is the sole candidate does the task escalate to the direct leader")
    void selfApprovalEscalatesToSupervisorOnlyWhenAlone() {
        Resolution r = resolve(new AssignmentRule.FormFieldUsers("projectManagerId"), 1, fields("projectManagerId", person(1)));
        assertThat(r.candidates()).isEqualTo(ids(2));
        assertThat(r.kind()).isEqualTo(ResolutionKind.ESCALATED_SELF_APPROVAL);
        // no leader either -> administrator, never the initiator
        Resolution noSupervisor = resolve(new AssignmentRule.FormFieldUsers("projectManagerId"), 3, fields("projectManagerId", person(3)));
        assertThat(noSupervisor.candidates()).isEqualTo(ids(ADMIN));
    }

    @Test
    @DisplayName("#1436 a noFallback node never escalates to the supervisor or the administrators: nobody left is UNRESOLVED")
    void noFallbackLeavesTheNodeUnresolved() {
        AssignmentRule rule = new AssignmentRule.FormFieldUsers("projectManagerId");
        // the initiator alone: the supervisor (2) would get it without the option, nobody gets it with it
        Resolution alone = resolver.resolve(rule, false, false, true, TestAccounts.employee(1), fields("projectManagerId", person(1)));
        assertThat(alone.kind()).isEqualTo(ResolutionKind.UNRESOLVED);
        assertThat(alone.candidates()).isEmpty();
        // the named person is separated: the administrators would get it without the option
        Resolution separated = resolver.resolve(rule, false, false, true, TestAccounts.employee(1), fields("projectManagerId", person(120)));
        assertThat(separated.kind()).isEqualTo(ResolutionKind.UNRESOLVED);
        assertThat(separated.candidates()).isEmpty();
        // somebody else qualifies: unchanged, the initiator is removed
        Resolution others = resolver.resolve(rule, false, false, true, TestAccounts.employee(1), fields("projectManagerId", person(1), person(3)));
        assertThat(others.kind()).isEqualTo(ResolutionKind.RESOLVED);
        assertThat(others.candidates()).isEqualTo(ids(3));
    }

    @Test
    @DisplayName("RULE-307 an employee-activity node may be approved by its initiator")
    void selfApprovalAllowedOnEmployeeActivities() {
        Resolution r = resolver.resolve(new AssignmentRule.FormFieldUsers("projectManagerId"), true, TestAccounts.employee(1), fields("projectManagerId", person(1)));
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
        assertThat(resolve(new AssignmentRule.DepartmentHead(null), 1, variable("deliveryUnitId", unit(12))).candidates()).isEqualTo(ids(110));
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
        assertThatThrownBy(() -> AssignmentRuleCodec.parse("SUPERVISOR_OF_INITIATOR", n -> "1")).isInstanceOf(IllegalStateException.class);
    }
}
