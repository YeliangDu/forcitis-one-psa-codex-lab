package com.forcitis.one.platform.approval.domain;

import com.forcitis.one.kernel.AccountEmployees;
import com.forcitis.one.kernel.EmployeeId;
import com.forcitis.one.kernel.UserId;
import com.forcitis.one.organization.workforce.api.EmployeeProfile;
import com.forcitis.one.platform.approval.api.ApprovalError;
import com.forcitis.one.organization.workforce.api.EmploymentStatus;
import com.forcitis.one.organization.workforce.api.OrgDirectory;
import com.forcitis.one.organization.workforce.api.OrgUnitId;
import com.forcitis.one.organization.workforce.api.OrgUnitProfile;
import com.forcitis.one.platform.identity.api.RoleDirectory;
import java.time.LocalDate;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/** Pure function: no clock, no randomness, no side effects; the same code decides live candidates and replays old sequences. */
public final class AssigneeResolver {

    private final OrgDirectory org;
    private final RoleDirectory roles;
    private final Set<UserId> administrators;
    private final AccountEmployees accountEmployees;
    private final LocalDate today;

    /**
     * administrators may be empty: then an empty candidate set is UNRESOLVED instead of routed. today is the business day the candidates are
     * resolved for: a person on unpaid leave that day is skipped, like a separated one (ADR-0068 section 3); the resolver itself reads no clock.
     */
    public AssigneeResolver(OrgDirectory org, RoleDirectory roles, Set<UserId> administrators, AccountEmployees accountEmployees,
            LocalDate today) {
        this.today = today;
        this.org = org;
        this.roles = roles;
        this.accountEmployees = accountEmployees;
        this.administrators = Set.copyOf(administrators);
    }

    public Resolution resolve(AssignmentRule rule, boolean selfApprovalAllowed, EmployeeId initiator, ProcessVariables variables) {
        return resolve(rule, selfApprovalAllowed, false, initiator, variables);
    }

    public Resolution resolve(AssignmentRule rule, boolean selfApprovalAllowed, boolean skipWhenEmpty, EmployeeId initiator,
            ProcessVariables variables) {
        Collected collected = collect(rule, initiator, variables);
        Set<EmployeeId> candidates = employed(collected.users());
        if (skipWhenEmpty && candidates.isEmpty()) {
            boolean namedButInvalid = rule instanceof AssignmentRule.FormFieldUsers && !collected.users().isEmpty();
            return new Resolution(Set.of(), collected.invalidInput() ? ResolutionKind.INVALID_INPUT : ResolutionKind.SKIPPED,
                    !collected.invalidInput() && namedButInvalid);
        }
        ResolutionKind kind = collected.escalated() ? ResolutionKind.ESCALATED_REPORTING_LINE : ResolutionKind.RESOLVED;
        if (!selfApprovalAllowed && candidates.contains(initiator)) {
            Set<EmployeeId> others = new LinkedHashSet<>(candidates);
            others.remove(initiator);
            if (!others.isEmpty()) {
                candidates = others;
            } else {
                Set<EmployeeId> leader = org.findSupervisor(initiator).filter(l -> !l.equals(initiator))
                        .map(l -> employed(Set.of(l))).orElse(Set.of());
                candidates = leader;
                kind = ResolutionKind.ESCALATED_SELF_APPROVAL;
            }
        }
        if (candidates.isEmpty()) {
            return fallback();
        }
        return new Resolution(accountsOf(candidates), kind);
    }

    private Set<UserId> accountsOf(Set<EmployeeId> employees) {
        Set<UserId> accounts = new LinkedHashSet<>();
        for (EmployeeId employee : employees) {
            accounts.add(accountEmployees.accountOf(employee).orElseThrow(() -> ApprovalError.PLATFORM_APPROVAL_NO_ASSIGNEE_FOUND.refusal(
                    "employee " + employee.value() + " is an approver but has no account to hand the task to")));
        }
        return accounts;
    }

    private EmployeeId employeeOf(UserId holder) {
        return accountEmployees.employeeOf(holder).orElseThrow(() -> new IllegalStateException("role holder " + holder.value() + " has no account"));
    }

    private Resolution fallback() {
        if (administrators.isEmpty()) {
            return new Resolution(Set.of(), ResolutionKind.UNRESOLVED);
        }
        return new Resolution(administrators, ResolutionKind.ADMINISTRATOR_FALLBACK);
    }

    /** invalidInput: what the rule reads is missing or points at nobody who exists, so "no candidates" would not mean "nobody is responsible". */
    private record Collected(Set<EmployeeId> users, boolean escalated, boolean invalidInput) {
        static Collected of(Set<EmployeeId> users) {
            return new Collected(users, false, false);
        }

        static Collected invalid() {
            return new Collected(Set.of(), false, true);
        }
    }

    private Collected collect(AssignmentRule rule, EmployeeId initiator, ProcessVariables variables) {
        return switch (rule) {
            case AssignmentRule.Role role -> Collected.of(roles.findRoleHolders(role.role()).stream().map(this::employeeOf)
                    .collect(Collectors.toCollection(LinkedHashSet::new)));
            case AssignmentRule.DepartmentHead head -> departmentHead(head, initiator, variables);
            case AssignmentRule.InitiatorSupervisor supervisor -> org.findEmployee(initiator).isEmpty() ? Collected.invalid()
                    : initiatorSupervisor(initiator, supervisor.level());
            case AssignmentRule.FormFieldUsers field -> formFieldUsers(field.field(), variables);
        };
    }

    private Collected departmentHead(AssignmentRule.DepartmentHead head, EmployeeId initiator, ProcessVariables variables) {
        Optional<OrgUnitId> unit = departmentOf(head.departmentVariable(), initiator, variables);
        if (unit.isEmpty() || org.findOrgUnit(unit.get()).isEmpty()) {
            return Collected.invalid();
        }
        return Collected.of(nearestHead(unit.get()).map(Set::of).orElse(Set.of()));
    }

    private Collected formFieldUsers(String field, ProcessVariables variables) {
        List<String> raw = variables.formField(field);
        boolean valid = !raw.isEmpty() && raw.stream().allMatch(this::isExistingPerson);
        return new Collected(ProcessVariables.parseEmployees(raw), false, !valid);
    }

    private boolean isExistingPerson(String raw) {
        return raw != null && ProcessVariables.parseEmployees(List.of(raw)).stream().anyMatch(id -> org.findEmployee(id).isPresent());
    }

    private Optional<OrgUnitId> departmentOf(String variable, EmployeeId initiator, ProcessVariables variables) {
        if (variable == null || variable.isBlank()) {
            return org.findEmployee(initiator).map(EmployeeProfile::orgUnitId);
        }
        return variables.firstVariable(variable).or(() -> variables.firstFormField(variable)).flatMap(AssigneeResolver::parseUnit);
    }

    /** Blank is no department; any other value that is not an org unit id in its canonical UUID form refuses the assignment. */
    private static Optional<OrgUnitId> parseUnit(String raw) {
        if (raw.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(OrgUnitId.parse(raw.trim()));
        } catch (IllegalArgumentException notAnOrgUnitId) {
            throw ApprovalError.PLATFORM_APPROVAL_ASSIGNEE_INPUT_INVALID.refusal("a department is named by a value that is not an org unit id");
        }
    }

    /** Head of the unit, else of the nearest ancestor that has an employed head. */
    private Optional<EmployeeId> nearestHead(OrgUnitId unit) {
        return headFrom(unit, Set.of());
    }

    private Optional<EmployeeId> headFrom(OrgUnitId unit, Set<EmployeeId> excluded) {
        Set<OrgUnitId> seen = new LinkedHashSet<>();
        OrgUnitId candidate = unit;
        while (candidate != null && seen.add(candidate)) {
            Optional<OrgUnitProfile> profile = org.findOrgUnit(candidate);
            OrgUnitId parent = profile.map(OrgUnitProfile::parentOrgUnitId).orElse(null);
            if (!profile.map(OrgUnitProfile::active).orElse(false)) {
                candidate = parent;
                continue;
            }
            Optional<EmployeeId> head = org.findOrgUnitHead(candidate).filter(h -> !excluded.contains(h)).filter(this::isEmployed);
            if (head.isPresent()) {
                return head;
            }
            candidate = parent;
        }
        return Optional.empty();
    }

    /** Level n supervisor; an incomplete line pushes up to the nearest department head not already on the line. */
    private Collected initiatorSupervisor(EmployeeId initiator, int level) {
        Set<EmployeeId> line = new LinkedHashSet<>();
        line.add(initiator);
        EmployeeId current = initiator;
        for (int i = 0; i < level; i++) {
            Optional<EmployeeId> next = org.findSupervisor(current).filter(l -> !line.contains(l)).filter(this::isEmployed);
            if (next.isEmpty()) {
                Optional<EmployeeId> pushed = org.findEmployee(current).map(EmployeeProfile::orgUnitId)
                        .flatMap(unit -> headFrom(unit, line));
                return new Collected(pushed.map(Set::of).orElse(Set.of()), true, false);
            }
            current = next.get();
            line.add(current);
        }
        return Collected.of(Set.of(current));
    }

    private Set<EmployeeId> employed(Set<EmployeeId> users) {
        Set<EmployeeId> kept = new LinkedHashSet<>();
        for (EmployeeId user : users) {
            if (user != null && isEmployed(user)) {
                kept.add(user);
            }
        }
        return kept;
    }

    private boolean isEmployed(EmployeeId user) {
        return org.findEmployee(user).map(e -> e.employmentStatus() == EmploymentStatus.EMPLOYED).orElse(false) && !org.availabilityOn(user, today).onUnpaidLeave();
    }
}
