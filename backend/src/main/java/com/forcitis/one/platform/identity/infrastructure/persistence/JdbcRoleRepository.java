package com.forcitis.one.platform.identity.infrastructure.persistence;

import com.forcitis.one.kernel.DomainException;
import com.forcitis.one.kernel.ErrorKind;
import com.forcitis.one.kernel.IdGenerator;
import com.forcitis.one.kernel.UserId;
import com.forcitis.one.platform.audit.api.AuditEntryId;
import com.forcitis.one.platform.identity.api.AccessScopeKind;
import com.forcitis.one.platform.identity.api.CustomOrgUnit;
import com.forcitis.one.platform.identity.api.IdentityError;
import com.forcitis.one.platform.identity.api.RoleCode;
import com.forcitis.one.platform.identity.api.RoleId;
import com.forcitis.one.platform.identity.api.RoleVersionId;
import com.forcitis.one.platform.identity.domain.ChangeChannel;
import com.forcitis.one.platform.identity.domain.DeclarationOutcome;
import com.forcitis.one.platform.identity.domain.DeclarationResult;
import com.forcitis.one.platform.identity.domain.DeclaredRole;
import com.forcitis.one.platform.identity.domain.Role;
import com.forcitis.one.platform.identity.domain.RoleChange;
import com.forcitis.one.platform.identity.domain.RoleDefinition;
import com.forcitis.one.platform.identity.domain.RoleKind;
import com.forcitis.one.platform.identity.domain.RoleRepository;
import com.forcitis.one.platform.identity.domain.RoleRuleException;
import com.forcitis.one.platform.identity.domain.RoleStatus;
import com.forcitis.one.platform.identity.domain.RoleViolation;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * Roles and their versions (ADR-0034 decision 1). The roles row keeps the identity, the optimistic version and the event count; the
 * definition lives in role_versions, one row per change, appended only. Every write first locks the roles row (FOR UPDATE), so changes of
 * one role are serial; a new version starts at greatest(:now, current start, latest end of the role's versions), the current one is
 * closed at the same moment, and the database's exclusion constraints back the one-current-version and one-name-at-a-time rules.
 */
@Repository
class JdbcRoleRepository implements RoleRepository {

    private final JdbcClient jdbc;
    private final Clock clock;
    private final IdGenerator ids;
    private final RoleVersionAudit audit;

    JdbcRoleRepository(JdbcClient jdbc, Clock clock, IdGenerator ids, RoleVersionAudit audit) {
        this.ids = ids;
        this.clock = clock;
        this.jdbc = jdbc;
        this.audit = audit;
    }

    /** The version that is current now, as the writes below need it. */
    private record CurrentVersion(RoleVersionId id, int number, RoleDefinition definition, ChangeChannel channel, @Nullable Integer declarationRevisionNumber) {
    }

    @Override
    public Optional<Role> findById(RoleId roleId) {
        return RoleRows.select(jdbc, """
                where r.role_id = :id
                """, Map.of("id", roleId.value())).stream().findFirst();
    }

    @Override
    public Optional<Role> findByCode(RoleCode code) {
        return RoleRows.select(jdbc, """
                where lower(r.code) = lower(:code)
                """, Map.of("code", code.value())).stream().findFirst();
    }

    @Override
    public List<Role> findAll() {
        return RoleRows.select(jdbc, """
                order by r.code
                """, Map.of());
    }

    @Override
    public Role insert(Role role, RoleChange change) {
        Instant now = clock.instant();
        if (codeTaken(role.code())) {
            throw new RoleRuleException(RoleViolation.DUPLICATE_CODE);
        }
        if (nameTaken(role.name(), role.roleId(), now)) {
            throw new RoleRuleException(RoleViolation.DUPLICATE_NAME);
        }
        Optional<UUID> id = jdbc.sql("""
                        insert into platform_identity.roles (role_id, code, kind, is_administrative, created_at, updated_at, version, event_count)
                        values (:id, :code, :kind, :administrative, :now, :now, :version, :eventSequence)
                        on conflict do nothing
                        returning role_id
                        """)
                .param("id", role.roleId().value()).param("code", role.code().value()).param("kind", role.kind().name())
                .param("administrative", role.administrative()).param("now", at(now)).param("version", role.versionAfterSave())
                .param("eventSequence", role.eventSequenceAfterSave())
                .query(UUID.class).optional();
        if (id.isEmpty()) {
            throw new RoleRuleException(RoleViolation.DUPLICATE_CODE);
        }
        role.versionWritten(insertVersion(role.roleId(), 1, now, now, role.definition(), change, Set.of()));
        return role;
    }

    @Override
    public void update(Role role, RoleChange change) {
        Instant now = clock.instant();
        CurrentVersion current = lockCurrent(role);
        if (role.definitionChanged()) {
            Instant from = nextStart(role.roleId(), now);
            if (nameTaken(role.name(), role.roleId(), from)) {
                throw new RoleRuleException(RoleViolation.DUPLICATE_NAME);
            }
            updateToReplace(current.id(), from);
            role.versionWritten(insertVersion(role.roleId(), current.number() + 1, from, now, role.definition(), change, current.definition().customOrgUnits()));
        }
        int updated = jdbc.sql("""
                        update platform_identity.roles set updated_at = :now, version = :next, event_count = :eventSequence
                         where role_id = :id and version = :version
                        """)
                .param("now", at(now)).param("next", role.versionAfterSave()).param("eventSequence", role.eventSequenceAfterSave())
                .param("id", role.roleId().value()).param("version", role.version())
                .update();
        if (updated != 1) {
            throw staleRole(role);
        }
    }

    @Override
    public void delete(Role role, UserId deletedBy, String reason) {
        Instant now = clock.instant();
        CurrentVersion current = lockCurrent(role);
        boolean held = jdbc.sql("""
                select exists (select 1 from platform_identity.account_roles where role_id = :id)
                """)
                .param("id", role.roleId().value()).query(Boolean.class).single();
        if (held) {
            throw IdentityError.PLATFORM_IDENTITY_ROLE_IN_USE.refusal("role " + role.roleId().value() + " is still held by accounts");
        }
        Instant to = jdbc.sql("""
                select greatest(:now, effective_from_at) from platform_identity.role_versions where role_version_id = :version
                """)
                .param("now", at(now)).param("version", current.id().value())
                .query((rs, n) -> rs.getObject(1, OffsetDateTime.class).toInstant()).single();
        AuditEntryId entry = audit.closed(role.roleId(), current.id(), current.number(), deletedBy, to, now);
        int closed = jdbc.sql("""
                        update platform_identity.role_versions
                           set effective_to_at = :to, updated_at = :to, closed_by = :closedBy, closure_reason = :reason, closure_audit_entry_id = :entry
                         where role_version_id = :version and effective_to_at is null
                        """)
                .param("to", at(to)).param("closedBy", deletedBy.value()).param("reason", reason).param("entry", entry.value())
                .param("version", current.id().value())
                .update();
        if (closed != 1) {
            throw staleRole(role);
        }
        int updated = jdbc.sql("""
                        update platform_identity.roles set updated_at = :now, version = :next, event_count = :eventSequence
                         where role_id = :id and version = :version
                        """)
                .param("now", at(now)).param("next", role.versionAfterSave()).param("eventSequence", role.eventSequenceAfterSave())
                .param("id", role.roleId().value()).param("version", role.version())
                .update();
        if (updated != 1) {
            throw staleRole(role);
        }
    }

    /** The identity columns of a roles row and its optimistic version, as the reconciliation compares them. */
    private record Identity(String code, String kind, boolean administrative, long version) {
    }

    /**
     * Decides from plain reads first: a role that is already at its declaration needs no write and no lock, so a start on a read-only
     * account succeeds (SingleOperatorModeFailClosedIT P1-17). Only when something is to be written is the row created if absent, locked
     * and read again, and the decision taken again under the lock, so two instances starting together write once.
     */
    @Override
    public DeclarationResult saveDeclared(DeclaredRole declared) {
        DeclarationResult unlocked = decide(declared, false);
        if (unlocked.outcome() != DeclarationOutcome.CREATED && unlocked.outcome() != DeclarationOutcome.REVISED) {
            return unlocked;
        }
        Instant now = clock.instant();
        jdbc.sql("""
                        insert into platform_identity.roles (role_id, code, kind, is_administrative, created_at, updated_at, version, event_count)
                        values (:id, :code, :kind, :administrative, :now, :now, 0, 0)
                        on conflict do nothing
                        """)
                .param("id", declared.roleId().value()).param("code", declared.code().value()).param("kind", RoleKind.SYSTEM.name())
                .param("administrative", declared.administrative()).param("now", at(now))
                .update();
        DeclarationResult locked = decide(declared, true);
        if (locked.outcome() != DeclarationOutcome.CREATED && locked.outcome() != DeclarationOutcome.REVISED) {
            return locked;
        }
        Identity identity = identity(declared.roleId(), true).orElseThrow();
        Optional<CurrentVersion> current = currentVersion(declared.roleId());
        Instant from = nextStart(declared.roleId(), now);
        current.ifPresent(version -> updateToReplace(version.id(), from));
        int number = jdbc.sql("""
                select coalesce(max(version_number), 0) + 1 from platform_identity.role_versions where role_id = :id
                """)
                .param("id", declared.roleId().value()).query(Integer.class).single();
        insertVersion(declared.roleId(), number, from, now, declared.definition(), RoleChange.seed(declared.revisionNumber()),
                current.map(version -> version.definition().customOrgUnits()).orElse(Set.of()));
        int updated = jdbc.sql("""
                        update platform_identity.roles set updated_at = :now, version = :next
                         where role_id = :id and version = :version
                        """)
                .param("now", at(now)).param("next", identity.version() + 1).param("id", declared.roleId().value())
                .param("version", identity.version())
                .update();
        if (updated != 1) {
            throw new IllegalStateException("system role " + declared.roleId().value() + " changed under its row lock");
        }
        return locked;
    }

    /** What the reconciliation would do with the role as the database holds it now; locked reads the roles row FOR UPDATE first. */
    private DeclarationResult decide(DeclaredRole declared, boolean locked) {
        Optional<Identity> identity = identity(declared.roleId(), locked);
        if (identity.isEmpty()) {
            // Unlocked, another instance may be writing the row right now: only the locked read (after the insert) can call it a conflict,
            // which then means another role holds the code.
            return new DeclarationResult(locked ? DeclarationOutcome.CONFLICTING : DeclarationOutcome.CREATED, null);
        }
        if (!identity.get().code().equals(declared.code().value()) || !identity.get().kind().equals(RoleKind.SYSTEM.name())
                || identity.get().administrative() != declared.administrative()) {
            return new DeclarationResult(DeclarationOutcome.CONFLICTING, null);
        }
        Optional<CurrentVersion> current = currentVersion(declared.roleId());
        int stored = jdbc.sql("""
                select coalesce(max(declaration_revision_number), 0) from platform_identity.role_versions where role_id = :id
                """)
                .param("id", declared.roleId().value()).query(Integer.class).single();
        Integer highest = stored == 0 ? null : stored;
        DeclarationOutcome outcome = declared.outcome(current.map(CurrentVersion::definition).orElse(null),
                current.filter(v -> v.channel() == ChangeChannel.SEED).map(CurrentVersion::declarationRevisionNumber).orElse(null), highest);
        return new DeclarationResult(outcome, highest);
    }

    private Optional<Identity> identity(RoleId roleId, boolean locked) {
        return jdbc.sql("""
                select code, kind, is_administrative, version from platform_identity.roles where role_id = :id
                """ + (locked ? " for update" : ""))
                .param("id", roleId.value())
                .query((rs, n) -> new Identity(rs.getString("code"), rs.getString("kind"), rs.getBoolean("is_administrative"), rs.getLong("version")))
                .optional();
    }

    /** Locks the roles row and returns the current version; a role changed or deleted since it was loaded is a conflict. */
    private CurrentVersion lockCurrent(Role role) {
        Optional<Long> locked = jdbc.sql("""
                select version from platform_identity.roles where role_id = :id for update
                """)
                .param("id", role.roleId().value()).query(Long.class).optional();
        if (locked.isEmpty() || locked.get() != role.version()) {
            throw staleRole(role);
        }
        return currentVersion(role.roleId()).orElseThrow(() -> staleRole(role));
    }

    /** The current version with its whole definition, the custom org units included. */
    private Optional<CurrentVersion> currentVersion(RoleId roleId) {
        record Stored(RoleVersionId id, int number, String name, RoleStatus status, AccessScopeKind scope, Set<String> codes, ChangeChannel channel,
                      @Nullable Integer revision) {
        }
        return jdbc.sql("""
                        select role_version_id, version_number, role_name, role_status, access_scope_kind, permission_codes, change_channel_kind,
                               declaration_revision_number
                          from platform_identity.role_versions where role_id = :id and effective_to_at is null
                        """)
                .param("id", roleId.value())
                .query((rs, n) -> new Stored(new RoleVersionId(rs.getObject("role_version_id", UUID.class)), rs.getInt("version_number"),
                        rs.getString("role_name"), RoleStatus.valueOf(rs.getString("role_status")), AccessScopeKind.valueOf(rs.getString("access_scope_kind")),
                        RoleRows.codes(rs, "permission_codes"), ChangeChannel.valueOf(rs.getString("change_channel_kind")),
                        (Integer) rs.getObject("declaration_revision_number")))
                .optional()
                .map(stored -> new CurrentVersion(stored.id(), stored.number(), new RoleDefinition(stored.name(), stored.status(), stored.scope(), stored.codes(),
                        RoleRows.customOrgUnits(jdbc, List.of(stored.id())).getOrDefault(stored.id(), Set.of())), stored.channel(), stored.revision()));
    }

    /** ADR-0034 decision 1 item 6: never before the current version's start, nor before the latest end of the role's versions. */
    private Instant nextStart(RoleId roleId, Instant now) {
        return jdbc.sql("""
                        select greatest(:now, max(effective_from_at), max(effective_to_at)) from platform_identity.role_versions where role_id = :id
                        """)
                .param("now", at(now)).param("id", roleId.value())
                .query((rs, n) -> rs.getObject(1, OffsetDateTime.class).toInstant()).single();
    }

    /** Closes the current version where the next one starts; a replaced version keeps its closure columns empty (the next one names who). */
    private void updateToReplace(RoleVersionId current, Instant at) {
        int closed = jdbc.sql("""
                        update platform_identity.role_versions set effective_to_at = :at, updated_at = :at
                         where role_version_id = :version and effective_to_at is null
                        """)
                .param("at", at(at)).param("version", current.value())
                .update();
        if (closed != 1) {
            throw new IllegalStateException("role version " + current.value() + " was closed by someone else under the role's row lock");
        }
    }

    /**
     * Writes the version and, in the same transaction, its whole custom org unit detail (F-1 card constraint 4: details only with the new
     * version, never added to an existing one). previousUnits are the units of the version this one replaces, for the audit entry.
     */
    private RoleVersionId insertVersion(RoleId roleId, int number, Instant from, Instant now, RoleDefinition definition, RoleChange change,
                                        Set<CustomOrgUnit> previousUnits) {
        RoleVersionId versionId = new RoleVersionId(ids.next());
        AuditEntryId entry = audit.opened(roleId, versionId, number, change, from, now, previousUnits, definition.customOrgUnits());
        jdbc.sql("""
                        insert into platform_identity.role_versions (role_version_id, role_id, version_number, effective_from_at, effective_to_at, role_name,
                               role_status, access_scope_kind, permission_codes, change_channel_kind, recorded_by, change_reason, authorization_request_id,
                               declaration_revision_number, version_audit_entry_id, closed_by, closure_reason, closure_audit_entry_id, created_at, updated_at)
                        values (:versionId, :roleId, :number, :from, null, :name, :status, :scope, :codes, :channel, :recordedBy, :reason, :request,
                                :revision, :entry, null, null, null, :now, :now)
                        """)
                .param("versionId", versionId.value()).param("roleId", roleId.value()).param("number", number).param("from", at(from))
                .param("name", definition.name()).param("status", definition.status().name()).param("scope", definition.accessScope().name())
                .param("codes", definition.permissionCodes().stream().sorted().toArray(String[]::new)).param("channel", change.channel().name())
                .param("recordedBy", change.recordedBy() == null ? null : change.recordedBy().value()).param("reason", change.reason())
                .param("request", change.authorizationRequestId() == null ? null : change.authorizationRequestId().value())
                .param("revision", change.declarationRevisionNumber()).param("entry", entry.value()).param("now", at(now))
                .update();
        for (CustomOrgUnit unit : definition.customOrgUnits()) {
            jdbc.sql("""
                            insert into platform_identity.role_version_custom_org_units (role_version_custom_org_unit_id, role_version_id, org_unit_id,
                                   is_descendants_included, created_at, updated_at)
                            values (:id, :versionId, :orgUnitId, :descendants, :now, :now)
                            """)
                    .param("id", ids.next()).param("versionId", versionId.value()).param("orgUnitId", unit.orgUnitId().value())
                    .param("descendants", unit.includeDescendants()).param("now", at(now))
                    .update();
        }
        return versionId;
    }

    private boolean codeTaken(RoleCode code) {
        return jdbc.sql("""
                select exists (select 1 from platform_identity.roles where lower(code) = lower(:code))
                """)
                .param("code", code.value()).query(Boolean.class).single();
    }

    /**
     * Whether another role's version holds the name (any case) at or after the moment: the check ex_role_versions_role_name_period would
     * otherwise fail with (an empty interval holds nothing). The constraint stays the backstop for two writers at once.
     */
    private boolean nameTaken(String name, RoleId roleId, Instant from) {
        return jdbc.sql("""
                        select exists (select 1 from platform_identity.role_versions
                                        where lower(role_name) = lower(:name) and role_id <> :id
                                          and (effective_to_at is null or (effective_to_at > :from and effective_to_at > effective_from_at)))
                        """)
                .param("name", name).param("id", roleId.value()).param("from", at(from)).query(Boolean.class).single();
    }

    private static OffsetDateTime at(Instant instant) {
        return OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
    }

    private static DomainException staleRole(Role role) {
        return new DomainException(IdentityError.PLATFORM_IDENTITY_CONCURRENT_UPDATE, ErrorKind.CONFLICT,
                "role " + role.roleId().value() + " was changed by someone else");
    }
}
