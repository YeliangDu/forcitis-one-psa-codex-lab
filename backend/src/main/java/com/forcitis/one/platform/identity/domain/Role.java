package com.forcitis.one.platform.identity.domain;

import com.forcitis.one.kernel.AggregateRoot;
import com.forcitis.one.kernel.StateMachine;
import com.forcitis.one.kernel.event.EventDraft;
import com.forcitis.one.kernel.event.EventSource;
import com.forcitis.one.kernel.event.SourceSystem;
import com.forcitis.one.platform.identity.api.AccessScopeKind;
import com.forcitis.one.platform.identity.api.IdentityError;
import com.forcitis.one.platform.identity.api.Permission;
import com.forcitis.one.platform.identity.api.RoleCode;
import com.forcitis.one.platform.identity.api.RoleId;
import com.forcitis.one.platform.identity.events.RoleCreated;
import com.forcitis.one.platform.identity.events.RoleDeleted;
import com.forcitis.one.platform.identity.events.RoleDisabled;
import com.forcitis.one.platform.identity.events.RoleEnabled;
import com.forcitis.one.platform.identity.events.RoleRevised;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/** Reserved code super_admin; new roles start at the least-privilege scope SELF. A system role is frozen: no change of any kind. */
public final class Role extends AggregateRoot {

    public static final RoleCode SUPER_ADMIN = new RoleCode("super_admin");
    public static final StateMachine<RoleStatus> MACHINE = StateMachine.of(RoleStatus.class)
            .allow(RoleStatus.ENABLED, RoleStatus.DISABLED)
            .allow(RoleStatus.DISABLED, RoleStatus.ENABLED)
            .build();
    private static final Pattern CODE = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._-]{0,63}");
    private static final Set<String> KNOWN_PERMISSIONS = Arrays.stream(Permission.values())
            .map(Permission::code).collect(Collectors.toUnmodifiableSet());

    private RoleId roleId;
    private final RoleCode code;
    private final RoleKind kind;
    private final boolean administrative;
    private final RoleStatus loadedStatus;
    private String name;
    private RoleStatus status;
    private AccessScopeKind accessScope;
    private Set<String> permissionCodes;

    private Role(RoleId roleId, RoleCode code, String name, RoleKind kind, RoleStatus status, AccessScopeKind accessScope,
                 boolean administrative, Set<String> permissionCodes, long version, long eventSequence) {
        super(version, eventSequence);
        this.roleId = roleId;
        this.code = Objects.requireNonNull(code, "code");
        this.name = Objects.requireNonNull(name, "name");
        this.kind = Objects.requireNonNull(kind, "kind");
        this.status = Objects.requireNonNull(status, "status");
        this.loadedStatus = status;
        this.accessScope = Objects.requireNonNull(accessScope, "accessScope");
        this.administrative = administrative;
        this.permissionCodes = Set.copyOf(permissionCodes);
    }

    public static boolean isReserved(RoleCode code) {
        return code.value().equalsIgnoreCase(SUPER_ADMIN.value());
    }

    /** A role about to be inserted; it announces itself once the repository has given it an id (withId). */
    public static Role newCustom(RoleCode code, String name, Set<String> permissionCodes) {
        if (isReserved(code)) {
            throw new RoleRuleException(RoleViolation.RESERVED_CODE);
        }
        if (!CODE.matcher(code.value()).matches()) {
            throw new RoleRuleException(RoleViolation.INVALID_CODE);
        }
        requireName(name);
        requireKnown(permissionCodes);
        Role role = new Role(null, code, name.strip(), RoleKind.CUSTOM, RoleStatus.ENABLED, AccessScopeKind.SELF, false, permissionCodes, 0, 0);
        role.record((envelopes, version) -> new RoleCreated(envelopes.wrap(role.draft("RoleCreated", version)), role.roleId));
        return role;
    }

    public static Role restore(RoleId roleId, RoleCode code, String name, String kind, String status, AccessScopeKind accessScope,
                               boolean administrative, Set<String> permissionCodes, long version, long eventSequence) {
        return new Role(Objects.requireNonNull(roleId, "roleId"), code, name, RoleKind.valueOf(kind), RoleStatus.valueOf(status),
                accessScope, administrative, permissionCodes, version, eventSequence);
    }

    public Role withId(RoleId id) {
        this.roleId = Objects.requireNonNull(id, "id");
        return this;
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

    public RoleStatus loadedStatus() {
        return loadedStatus;
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

    public boolean isEnabled() {
        return status == RoleStatus.ENABLED;
    }

    public void revise(String newName, AccessScopeKind newScope, Set<String> newPermissionCodes) {
        requireModifiable();
        requireName(newName);
        requireKnown(newPermissionCodes);
        this.name = newName.strip();
        this.accessScope = Objects.requireNonNull(newScope, "newScope");
        this.permissionCodes = Set.copyOf(newPermissionCodes);
        record((envelopes, version) -> new RoleRevised(envelopes.wrap(draft("RoleRevised", version)), roleId));
    }

    public void enable() {
        requireModifiable();
        this.status = MACHINE.require(status, RoleStatus.ENABLED, IdentityError.PLATFORM_IDENTITY_ROLE_TRANSITION_ILLEGAL);
        record((envelopes, version) -> new RoleEnabled(envelopes.wrap(draft("RoleEnabled", version)), roleId));
    }

    public void disable() {
        requireModifiable();
        this.status = MACHINE.require(status, RoleStatus.DISABLED, IdentityError.PLATFORM_IDENTITY_ROLE_TRANSITION_ILLEGAL);
        record((envelopes, version) -> new RoleDisabled(envelopes.wrap(draft("RoleDisabled", version)), roleId));
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

    /** Records the deletion; the repository removes the row. */
    public void delete() {
        requireModifiable();
        record((envelopes, version) -> new RoleDeleted(envelopes.wrap(draft("RoleDeleted", version)), roleId));
    }

    public void requireModifiable() {
        if (kind == RoleKind.SYSTEM) {
            throw new RoleRuleException(RoleViolation.SYSTEM_FROZEN);
        }
    }

    private static void requireName(String name) {
        if (name == null || name.isBlank() || name.strip().length() > 100) {
            throw new RoleRuleException(RoleViolation.BLANK_NAME);
        }
    }

    private static void requireKnown(Set<String> permissionCodes) {
        if (!KNOWN_PERMISSIONS.containsAll(permissionCodes)) {
            throw new RoleRuleException(RoleViolation.UNKNOWN_PERMISSION);
        }
    }

    private EventDraft draft(String eventName, long version) {
        return new EventDraft("platform.identity." + eventName, 1, "Role", String.valueOf(roleId.value()), version, null, null,
                new EventSource(SourceSystem.ONE, null), List.of());
    }
}
