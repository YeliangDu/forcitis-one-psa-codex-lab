package com.forcitis.one.platform.identity.application;

import com.forcitis.one.organization.workforce.api.OrgDirectory;
import com.forcitis.one.organization.workforce.api.OrgUnitId;
import com.forcitis.one.organization.workforce.api.OrgUnitProfile;
import com.forcitis.one.platform.identity.api.RoleId;
import com.forcitis.one.platform.identity.domain.PermissionAdjustment;
import com.forcitis.one.platform.identity.domain.Role;
import com.forcitis.one.platform.identity.domain.RoleRepository;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

/** Turns items into views with role and org unit names. People appear by id only (acceptance contract 13 of #730 S3 forbids people's display names in items). roles is GLOBAL (ADR-0026); the org unit is the item's own subject, which the reader already sees. */
@Component
class PermissionAdjustmentViews {

    private final RoleRepository roles;
    private final OrgDirectory directory;

    PermissionAdjustmentViews(RoleRepository roles, OrgDirectory directory) {
        this.roles = roles;
        this.directory = directory;
    }

    PermissionAdjustmentView of(PermissionAdjustment item) {
        return of(List.of(item)).getFirst();
    }

    List<PermissionAdjustmentView> of(List<PermissionAdjustment> items) {
        Map<RoleId, String> roleNames = new HashMap<>();
        Map<OrgUnitId, String> unitNames = new HashMap<>();
        return items.stream().map(item -> {
            return new PermissionAdjustmentView(item.id().value(), item.kind(), item.origin(), item.status(),
                    item.user() == null ? null : item.user().value(),
                    item.role() == null ? null : item.role().value(), item.role() == null ? null : roleNames.computeIfAbsent(item.role(), this::roleName),
                    item.inactiveOrgUnit() == null ? null : item.inactiveOrgUnit().value(),
                    item.inactiveOrgUnit() == null ? null : unitNames.computeIfAbsent(item.inactiveOrgUnit(), this::unitName),
                    item.businessOwner() == null ? null : item.businessOwner().value(),
                    item.triggerCommand() == null ? null : item.triggerCommand().value(),
                    item.triggerOrgStructureCommand() == null ? null : item.triggerOrgStructureCommand().value(), item.triggerField(),
                    item.upholdExpiresAt(), item.version());
        }).toList();
    }

    private @Nullable String roleName(RoleId role) {
        return roles.findById(role).map(Role::name).orElse(null);
    }

    private @Nullable String unitName(OrgUnitId unit) {
        Optional<OrgUnitProfile> profile = directory.findOrgUnit(unit);
        return profile.map(OrgUnitProfile::name).orElse(null);
    }
}
