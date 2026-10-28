package com.forcitis.one.platform.identity.domain;

import com.forcitis.one.kernel.AggregateRoot;
import com.forcitis.one.kernel.StateMachine;
import com.forcitis.one.kernel.event.EventDraft;
import com.forcitis.one.platform.identity.api.AccessScopeKind;
import com.forcitis.one.platform.identity.api.CustomOrgUnit;
import com.forcitis.one.platform.identity.api.IdentityError;
import com.forcitis.one.platform.identity.api.Permission;
import com.forcitis.one.platform.identity.api.RoleCode;
import com.forcitis.one.platform.identity.api.RoleId;
import com.forcitis.one.platform.identity.api.RoleVersionId;
import com.forcitis.one.platform.identity.events.RoleCreated;
import com.forcitis.one.platform.identity.events.RoleDeleted;
import com.forcitis.one.platform.identity.events.RoleDisabled;
import com.forcitis.one.platform.identity.events.RoleEnabled;
import com.forcitis.one.platform.identity.events.RoleRevised;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;

/**
 * Reserved code super_admin; new roles start at the least-privilege scope SELF. A system role is frozen: no change of any kind. The
 * identity (code, kind, administrative marker) never changes; the definition (name, status, scope, permission set) is the role's current
 * version, and a change of the definition becomes a new version when the repository saves it (ADR-0034 decision 1).
 */
public final class Role extends AggregateRoot {

    public static final RoleCode SUPER_ADMIN = new RoleCode("super_admin");
    public static final RoleCode SECURITY_AUDITOR = new RoleCode("security_auditor");
    public static final StateMachine<RoleStatus> MACHINE = StateMachine.of(RoleStatus.class)
            .allow(RoleStatus.ENABLED, RoleStatus.DISABLED)
            .allow(RoleStatus.DISABLED, RoleStatus.ENABLED)
            .build();
    private static final Pattern CODE = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._-]{0,63}");

    private final RoleId roleId;
    private final RoleCode code;
    private final RoleKind kind;
    private final boolean administrative;
    private final @Nullable RoleDefinition loadedDefinition;
    private @Nullable RoleVersionId roleVersionId;
    private String name;
    private RoleStatus status;
    private AccessScopeKind accessScope;
    private Set<String> permissionCodes;
    private Set<CustomOrgUnit> customOrgUnits;

    private Role(RoleId roleId, RoleCode code, RoleKind kind, boolean administrative, @Nullable RoleVersionId roleVersionId, String name, RoleStatus status,
                 AccessScopeKind accessScope, Set<String> permissionCodes, Set<CustomOrgUnit> customOrgUnits, boolean stored, long version, long eventSequence) {
        super(version, eventSequence);
        this.roleId = roleId;
        this.code = Objects.requireNonNull(code, "code");
        this.name = Objects.requireNonNull(name, "name");
        this.kind = Objects.requireNonNull(kind, "kind");
        this.status = Objects.requireNonNull(status, "status");
        this.accessScope = Objects.requireNonNull(accessScope, "accessScope");
        this.administrative = administrative;
        this.permissionCodes = Set.copyOf(permissionCodes);
        this.customOrgUnits = Set.copyOf(customOrgUnits);
        this.roleVersionId = roleVersionId;
        this.loadedDefinition = stored ? definition() : null;
    }

    public static boolean isReserved(RoleCode code) {
        return code.value().equalsIgnoreCase(SUPER_ADMIN.value());
    }

    /** A role about to be inserted; its id is made by the caller, never by the repository. */
    public static Role newCustom(RoleId roleId, RoleCode code, String name, Set<String> permissionCodes) {
        if (isReserved(code)) {
            throw new RoleRuleException(RoleViolation.RESERVED_CODE);
        }
        if (!CODE.matcher(code.value()).matches()) {
            throw new RoleRuleException(RoleViolation.INVALID_CODE);
        }
        requireName(name);
        requireKnown(permissionCodes);
        Role role = new Role(Objects.requireNonNull(roleId, "roleId"), code, RoleKind.CUSTOM, false, null, name.strip(), RoleStatus.ENABLED, AccessScopeKind.SELF,
                permissionCodes, Set.of(), false, 0, 0);
        role.record((envelopes, version) -> new RoleCreated(envelopes.wrap(role.draft("RoleCreated", version)), role.roleId, role.requireVersionId()));
        return role;
    }

    /**
     * A stored role with its current version; permissionCodes are the codes of that version the catalogue still grants, customOrgUnits the
     * units that version lists (empty unless its scope kind is CUSTOM_ORG_UNITS).
     */
    public static Role restore(RoleId roleId, RoleCode code, String kind, boolean administrative, RoleVersionId roleVersionId, String name, String status,
                               AccessScopeKind accessScope, Set<String> permissionCodes, Set<CustomOrgUnit> customOrgUnits, long version, long eventSequence) {
        return new Role(Objects.requireNonNull(roleId, "roleId"), code, RoleKind.valueOf(kind), administrative, Objects.requireNonNull(roleVersionId, "roleVersionId"),
                name, RoleStatus.valueOf(status), accessScope, permissionCodes, customOrgUnits, true, version, eventSequence);
    }

    public RoleId roleId() {
        return roleId;
    }

    public RoleCode code() {
        return code;
    }

    public String name() {
        return name;
    }

    public RoleKind kind() {
        return kind;
    }

    public RoleStatus status() {
        return status;
    }

    public AccessScopeKind accessScope() {
        return accessScope;
    }

    public boolean administrative() {
        return administrative;
    }

    public Set<String> permissionCodes() {
        return permissionCodes;
    }

    /** The org units the current definition lists; empty unless the scope kind is CUSTOM_ORG_UNITS. */
    public Set<CustomOrgUnit> customOrgUnits() {
        return customOrgUnits;
    }

    public boolean isEnabled() {
        return status == RoleStatus.ENABLED;
    }

    /** The current version: the one the role was loaded with, or the one the repository wrote when it saved the role. */
    public @Nullable RoleVersionId roleVersionId() {
        return roleVersionId;
    }

    /** The definition the role holds now, changes included. */
    public RoleDefinition definition() {
        return new RoleDefinition(name, status, accessScope, permissionCodes, customOrgUnits);
    }

    /** Whether the definition differs from the stored version: only then does saving the role add a version (ADR-0034 decision 1 item 4). */
    public boolean definitionChanged() {
        return !definition().equals(loadedDefinition);
    }

    /** Called by the repository once it wrote the version the role's definition now lives in; the role's events name it. */
    public void versionWritten(RoleVersionId written) {
        this.roleVersionId = Objects.requireNonNull(written, "written");
    }

    /** A revision that keeps the custom org units where the new scope kind still lists units. */
    public void revise(String newName, AccessScopeKind newScope, Set<String> newPermissionCodes) {
        revise(newName, newScope, newPermissionCodes, null);
    }

    /**
     * newCustomOrgUnits null keeps the units the role lists now when the new scope kind is CUSTOM_ORG_UNITS, and lists none otherwise
     * (one-coordinator ruling for #728). A CUSTOM_ORG_UNITS role lists at least one unit, a unit at most once, and a role of another scope
     * kind lists none (F-1 card, constraint 7).
     */
    public void revise(String newName, AccessScopeKind newScope, Set<String> newPermissionCodes, @Nullable Collection<CustomOrgUnit> newCustomOrgUnits) {
        requireModifiable();
        requireName(newName);
        requireKnown(newPermissionCodes);
        Objects.requireNonNull(newScope, "newScope");
        Set<CustomOrgUnit> units = customOrgUnitsFor(newScope, newCustomOrgUnits);
        this.name = newName.strip();
        this.accessScope = newScope;
        this.permissionCodes = Set.copyOf(newPermissionCodes);
        this.customOrgUnits = units;
        record((envelopes, version) -> new RoleRevised(envelopes.wrap(draft("RoleRevised", version)), roleId, requireVersionId()));
    }

    private Set<CustomOrgUnit> customOrgUnitsFor(AccessScopeKind newScope, @Nullable Collection<CustomOrgUnit> wanted) {
        boolean custom = newScope == AccessScopeKind.CUSTOM_ORG_UNITS;
        if (wanted == null) {
            Set<CustomOrgUnit> kept = custom ? customOrgUnits : Set.of();
            if (custom && kept.isEmpty()) {
                throw new RoleRuleException(RoleViolation.CUSTOM_ORG_UNITS_INVALID);
            }
            return kept;
        }
        long distinctUnits = wanted.stream().map(CustomOrgUnit::orgUnitId).distinct().count();
        if (custom ? wanted.isEmpty() || distinctUnits != wanted.size() : !wanted.isEmpty()) {
            throw new RoleRuleException(RoleViolation.CUSTOM_ORG_UNITS_INVALID);
        }
        return Set.copyOf(wanted);
    }

    public void enable() {
        requireModifiable();
        this.status = MACHINE.require(status, RoleStatus.ENABLED, IdentityError.PLATFORM_IDENTITY_ROLE_TRANSITION_ILLEGAL);
        record((envelopes, version) -> new RoleEnabled(envelopes.wrap(draft("RoleEnabled", version)), roleId, requireVersionId()));
    }

    public void disable() {
        requireModifiable();
        this.status = MACHINE.require(status, RoleStatus.DISABLED, IdentityError.PLATFORM_IDENTITY_ROLE_TRANSITION_ILLEGAL);
        record((envelopes, version) -> new RoleDisabled(envelopes.wrap(draft("RoleDisabled", version)), roleId, requireVersionId()));
    }

    /** Sets the wanted state: asking for the state it already has changes nothing. */
    public void declareEnabled(boolean enabled) {
        requireModifiable();
        if (enabled == isEnabled()) {
            return;
        }
        if (enabled) {
            enable();
        } else {
            disable();
        }
    }

    /** Records the deletion; the repository closes the last version and keeps the row, so the code is never used again (0034-Q3). */
    public void delete() {
        requireModifiable();
        record((envelopes, version) -> new RoleDeleted(envelopes.wrap(draft("RoleDeleted", version)), roleId, requireVersionId()));
    }

    public void requireModifiable() {
        if (kind == RoleKind.SYSTEM) {
            throw new RoleRuleException(RoleViolation.SYSTEM_FROZEN);
        }
    }

    private RoleVersionId requireVersionId() {
        if (roleVersionId == null) {
            throw new IllegalStateException("role " + roleId.value() + " announces an event before its version was written");
        }
        return roleVersionId;
    }

    private static void requireName(String name) {
        if (name == null || name.isBlank() || name.strip().length() > 100) {
            throw new RoleRuleException(RoleViolation.BLANK_NAME);
        }
    }

    /** Written codes must be in the catalogue and not deprecated (ADR-0034 decision 1 item 3). */
    private static void requireKnown(Set<String> permissionCodes) {
        if (!permissionCodes.stream().allMatch(Permission::isLive)) {
            throw new RoleRuleException(RoleViolation.UNKNOWN_PERMISSION);
        }
        if (permissionCodes.stream().anyMatch(AdministratorMode.PLATFORM_ONLY::contains)) {
            throw new RoleRuleException(RoleViolation.ADMINISTRATOR_MODE_ONLY);
        }
    }

    private EventDraft draft(String eventName, long version) {
        return new EventDraft("platform.identity." + eventName, 1, "Role", roleId.value(), version, null, null, List.of());
    }
}
