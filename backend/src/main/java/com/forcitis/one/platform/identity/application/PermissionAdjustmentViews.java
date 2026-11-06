package com.forcitis.one.platform.identity.application;

import com.forcitis.one.kernel.EmployeeId;
import com.forcitis.one.kernel.UserId;
import com.forcitis.one.organization.workforce.api.EmployeeLabel;
import com.forcitis.one.organization.workforce.api.EmployeeLabels;
import com.forcitis.one.organization.workforce.api.OrgDirectory;
import com.forcitis.one.organization.workforce.api.OrgUnitId;
import com.forcitis.one.organization.workforce.api.OrgUnitProfile;
import com.forcitis.one.platform.identity.api.RoleId;
import com.forcitis.one.platform.identity.domain.AccountRepository;
import com.forcitis.one.platform.identity.domain.PermissionAdjustment;
import com.forcitis.one.platform.identity.domain.Role;
import com.forcitis.one.platform.identity.domain.RoleRepository;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

/** Turns items into views with display names. user_accounts and roles are GLOBAL (ADR-0026); the org unit is the item's own subject, which the reader already sees. */
@Component
class PermissionAdjustmentViews {

    private final AccountRepository accounts;
    private final EmployeeLabels labels;
    private final RoleRepository roles;
    private final OrgDirectory directory;

    PermissionAdjustmentViews(AccountRepository accounts, EmployeeLabels labels, RoleRepository roles, OrgDirectory directory) {
        this.accounts = accounts;
        this.labels = labels;
        this.roles = roles;
        this.directory = directory;
    }

    PermissionAdjustmentView of(PermissionAdjustment item) {
        return of(List.of(item)).getFirst();
    }

    List<PermissionAdjustmentView> of(List<PermissionAdjustment> items) {
        Map<UserId, EmployeeId> employeeOf = new HashMap<>();
        Set<EmployeeId> employees = new HashSet<>();
        for (PermissionAdjustment item : items) {
            if (item.user() != null && !employeeOf.containsKey(item.user())) {
                accounts.findEmployee(item.user()).ifPresent(employee -> employeeOf.put(item.user(), employee));
            }
            if (item.businessOwner() != null) {
                employees.add(item.businessOwner());
            }
        }
        employees.addAll(employeeOf.values());
        Map<EmployeeId, EmployeeLabel> named = labels.labelsOf(employees);
        Map<RoleId, String> roleNames = new HashMap<>();
        Map<OrgUnitId, String> unitNames = new HashMap<>();
        return items.stream().map(item -> {
            @Nullable EmployeeId subject = item.user() == null ? null : employeeOf.get(item.user());
            return new PermissionAdjustmentView(item.id().value(), item.kind(), item.origin(), item.status(),
                    item.user() == null ? null : item.user().value(), nameOf(subject, named),
                    item.role() == null ? null : item.role().value(), item.role() == null ? null : roleNames.computeIfAbsent(item.role(), this::roleName),
                    item.inactiveOrgUnit() == null ? null : item.inactiveOrgUnit().value(),
                    item.inactiveOrgUnit() == null ? null : unitNames.computeIfAbsent(item.inactiveOrgUnit(), this::unitName),
                    item.businessOwner() == null ? null : item.businessOwner().value(), nameOf(item.businessOwner(), named),
                    item.triggerCommand() == null ? null : item.triggerCommand().value(),
                    item.triggerOrgStructureCommand() == null ? null : item.triggerOrgStructureCommand().value(), item.triggerField(),
                    item.upholdExpiresAt(), item.version());
        }).toList();
    }

    private static @Nullable String nameOf(@Nullable EmployeeId employee, Map<EmployeeId, EmployeeLabel> named) {
        EmployeeLabel label = employee == null ? null : named.get(employee);
        return label == null ? null : label.displayName();
    }

    private @Nullable String roleName(RoleId role) {
        return roles.findById(role).map(Role::name).orElse(null);
    }

    private @Nullable String unitName(OrgUnitId unit) {
        Optional<OrgUnitProfile> profile = directory.findOrgUnit(unit);
        return profile.map(OrgUnitProfile::name).orElse(null);
    }
}
