package com.forcitis.one.platform.approval.domain;

import com.forcitis.one.kernel.UserId;
import com.forcitis.one.organization.workforce.api.EmployeeProfile;
import com.forcitis.one.organization.workforce.api.EmploymentStatus;
import com.forcitis.one.organization.workforce.api.OrgDirectory;
import com.forcitis.one.organization.workforce.api.OrgUnitId;
import com.forcitis.one.organization.workforce.api.OrgUnitProfile;
import com.forcitis.one.platform.identity.api.RoleCode;
import com.forcitis.one.platform.identity.api.RoleDirectory;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Pure function: assignment rule + organisation snapshot + process variables -> candidate set. The same code decides the
 * candidates of a live task and replays the old TIMESHEET approver sequences (REIMAGINED_ARCHITECTURE §3.4/§3.5).
 * It reads the snapshot only through the read-side contracts {@link OrgDirectory} and {@link RoleDirectory}; it has no
 * clock, no randomness, no side effects (RULE-249: nobody is picked at random any more).
 *
 * <p>Guarantees: the result never contains a null or a separated person (RULE-253); nobody found -> the process
 * administrator (RULE-250/254), reported through {@link ResolutionKind}; the initiator never approves their own request
 * unless the node says so, and only when no other candidate exists is the task escalated to the initiator's direct
 * leader (RULE-307).
 */
public final class AssigneeResolver {

    /** Role of the department director (RULE-256). */
    static final RoleCode DIRECTOR_ROLE = new RoleCode("BU-002");
    /** Default variable naming the delivery unit (RULE-255/256/257). */
    static final String DEFAULT_DEPARTMENT_VARIABLE = "deliveryDeptId";

    private final OrgDirectory org;
    private final RoleDirectory roles;
    private final UserId administrator;

    /** administrator may be null: then an empty candidate set is reported as UNRESOLVED instead of being routed. */
    public AssigneeResolver(OrgDirectory org, RoleDirectory roles, UserId administrator) {
        this.org = org;
        this.roles = roles;
        this.administrator = administrator;
    }

    public Resolution resolve(AssignmentRule rule, boolean selfApprovalAllowed, UserId initiator, ProcessVariables variables) {
        if (rule instanceof AssignmentRule.Initiator) {
            Set<UserId> self = employed(Set.of(initiator));
            return self.isEmpty() ? fallback() : new Resolution(self, ResolutionKind.RESOLVED);
        }
        Collected collected = collect(rule, initiator, variables);
        Set<UserId> candidates = employed(collected.users());
        ResolutionKind kind = collected.escalated() ? ResolutionKind.ESCALATED_REPORTING_LINE : ResolutionKind.RESOLVED;
        if (!selfApprovalAllowed && candidates.contains(initiator)) {
            Set<UserId> others = new LinkedHashSet<>(candidates);
            others.remove(initiator);
            if (!others.isEmpty()) {
                candidates = others;
            } else {
                Set<UserId> leader = org.findDirectLeader(initiator).filter(l -> !l.equals(initiator))
                        .map(l -> employed(Set.of(l))).orElse(Set.of());
                candidates = leader;
                kind = ResolutionKind.ESCALATED_SELF_APPROVAL;
            }
        }
        if (candidates.isEmpty()) {
            return fallback();
        }
        return new Resolution(candidates, kind);
    }

    private Resolution fallback() {
        if (administrator == null) {
            return new Resolution(Set.of(), ResolutionKind.UNRESOLVED);
        }
        return new Resolution(Set.of(administrator), ResolutionKind.ADMINISTRATOR_FALLBACK);
    }

    private record Collected(Set<UserId> users, boolean escalated) {
        static Collected of(Set<UserId> users) {
            return new Collected(users, false);
        }
    }

    private Collected collect(AssignmentRule rule, UserId initiator, ProcessVariables variables) {
        return switch (rule) {
            case AssignmentRule.Role role -> Collected.of(roles.findRoleHolders(role.role()));
            case AssignmentRule.DepartmentHead head -> Collected.of(departmentOf(head.departmentVariable(), initiator, variables)
                    .flatMap(this::nearestHead).map(Set::of).orElse(Set.of()));
            case AssignmentRule.InitiatorLeader leader -> initiatorLeader(initiator, leader.level());
            case AssignmentRule.NamedUsers named -> Collected.of(named.users());
            case AssignmentRule.RegionalReceivablesOwner manager -> Collected.of(
                    departmentOf(blankTo(manager.departmentVariable(), DEFAULT_DEPARTMENT_VARIABLE), initiator, variables)
                            .flatMap(org::findRegionalReceivablesManager).map(Set::of).orElse(Set.of()));
            case AssignmentRule.DepartmentDirector director -> Collected.of(
                    departmentOf(blankTo(director.departmentVariable(), DEFAULT_DEPARTMENT_VARIABLE), initiator, variables)
                            .map(this::directorsOf).orElse(Set.of()));
            case AssignmentRule.FormFieldUsers field -> Collected.of(ProcessVariables.parseUsers(variables.formField(field.field())));
            case AssignmentRule.FormFieldUserLeaders field -> Collected.of(leadersOf(ProcessVariables.parseUsers(variables.formField(field.field()))));
            case AssignmentRule.ProcessVariableUsers variable -> Collected.of(ProcessVariables.parseUsers(variables.variable(variable.variable())));
            case AssignmentRule.Initiator ignored -> Collected.of(Set.of(initiator));
        };
    }

    // ---------- rule helpers ----------

    /** The unit named by a process variable, or the initiator's own unit when no variable is named. */
    private Optional<OrgUnitId> departmentOf(String variable, UserId initiator, ProcessVariables variables) {
        if (variable == null || variable.isBlank()) {
            return org.findEmployee(initiator).map(EmployeeProfile::orgUnitId);
        }
        return variables.firstVariable(variable).or(() -> variables.firstFormField(variable)).flatMap(AssigneeResolver::parseUnit);
    }

    private static Optional<OrgUnitId> parseUnit(String raw) {
        try {
            long id = Long.parseLong(raw.trim());
            return id == 0 ? Optional.empty() : Optional.of(new OrgUnitId(id));
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }

    /** Head of the unit, else of the nearest ancestor that has an employed head (RULE-253/257). */
    private Optional<UserId> nearestHead(OrgUnitId unit) {
        return headFrom(unit, Set.of());
    }

    private Optional<UserId> headFrom(OrgUnitId unit, Set<UserId> excluded) {
        List<OrgUnitId> chain = new ArrayList<>();
        chain.add(unit);
        chain.addAll(org.findAncestors(unit));
        for (OrgUnitId candidate : chain) {
            boolean active = org.findOrgUnit(candidate).map(OrgUnitProfile::active).orElse(false);
            if (!active) {
                continue;
            }
            Optional<UserId> head = org.findOrgUnitHead(candidate).filter(h -> !excluded.contains(h)).filter(this::isEmployed);
            if (head.isPresent()) {
                return head;
            }
        }
        return Optional.empty();
    }

    /** RULE-250: level n leader; an incomplete line pushes up to the nearest department head not already on the line. */
    private Collected initiatorLeader(UserId initiator, int level) {
        Set<UserId> line = new LinkedHashSet<>();
        line.add(initiator);
        UserId current = initiator;
        for (int i = 0; i < level; i++) {
            Optional<UserId> next = org.findDirectLeader(current).filter(l -> !line.contains(l)).filter(this::isEmployed);
            if (next.isEmpty()) {
                Optional<UserId> pushed = org.findEmployee(current).map(EmployeeProfile::orgUnitId)
                        .flatMap(unit -> headFrom(unit, line));
                return new Collected(pushed.map(Set::of).orElse(Set.of()), true);
            }
            current = next.get();
            line.add(current);
        }
        return Collected.of(Set.of(current));
    }

    private Set<UserId> directorsOf(OrgUnitId unit) {
        Set<UserId> found = new LinkedHashSet<>();
        for (UserId holder : roles.findRoleHolders(DIRECTOR_ROLE)) {
            if (org.findEmployee(holder).map(e -> e.orgUnitId().equals(unit)).orElse(false)) {
                found.add(holder);
            }
        }
        return found;
    }

    private Set<UserId> leadersOf(Set<UserId> people) {
        Set<UserId> leaders = new LinkedHashSet<>();
        for (UserId person : people) {
            org.findDirectLeader(person).ifPresent(leaders::add);
        }
        return leaders;
    }

    private Set<UserId> employed(Set<UserId> users) {
        Set<UserId> kept = new LinkedHashSet<>();
        for (UserId user : users) {
            if (user != null && isEmployed(user)) {
                kept.add(user);
            }
        }
        return kept;
    }

    private boolean isEmployed(UserId user) {
        return org.findEmployee(user).map(e -> e.employmentStatus() == EmploymentStatus.EMPLOYED).orElse(false);
    }

    private static String blankTo(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}
