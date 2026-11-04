package com.forcitis.one.platform.identity.infrastructure.persistence;

import com.forcitis.one.kernel.EmployeeId;
import com.forcitis.one.kernel.UserId;
import com.forcitis.one.organization.workforce.api.OrgStructureCommandId;
import com.forcitis.one.organization.workforce.api.OrgUnitId;
import com.forcitis.one.organization.workforce.api.PersonnelCommandId;
import com.forcitis.one.organization.workforce.api.PositionId;
import com.forcitis.one.organization.workforce.api.TriggerFieldKind;
import com.forcitis.one.platform.identity.api.IdentityError;
import com.forcitis.one.platform.identity.api.PermissionAdjustmentId;
import com.forcitis.one.platform.identity.api.PermissionAdjustmentKind;
import com.forcitis.one.platform.identity.api.PermissionAdjustmentOrigin;
import com.forcitis.one.platform.identity.api.RoleId;
import com.forcitis.one.platform.identity.domain.AdjustmentReference;
import com.forcitis.one.platform.identity.domain.PermissionAdjustment;
import com.forcitis.one.platform.identity.domain.PermissionAdjustmentReader;
import com.forcitis.one.platform.identity.domain.PermissionAdjustmentRepository;
import com.forcitis.one.platform.identity.api.PermissionAdjustmentStatus;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class JdbcPermissionAdjustmentRepository implements PermissionAdjustmentRepository {

    private final JdbcClient jdbc;
    private final Clock clock;

    JdbcPermissionAdjustmentRepository(JdbcClient jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    @Override
    public boolean existsForTrigger(PermissionAdjustmentKind kind, UserId user, PersonnelCommandId command, TriggerFieldKind field) {
        return jdbc.sql("""
                select exists (select 1 from platform_identity.permission_adjustments
                                where origin_kind = 'ASSIGNMENT_CHANGE' and kind = :kind and user_id = :user
                                  and trigger_personnel_command_id = :command and trigger_field_kind = :field)
                """)
                .param("kind", kind.name()).param("user", user.value()).param("command", command.value()).param("field", field.name())
                .query(Boolean.class).single();
    }

    @Override
    public void insert(PermissionAdjustment opened) {
        OffsetDateTime at = OffsetDateTime.ofInstant(opened.createdAt(), ZoneOffset.UTC);
        jdbc.sql("""
                insert into platform_identity.permission_adjustments (permission_adjustment_id, kind, origin_kind, status, user_id, role_id, org_unit_id,
                        trigger_personnel_command_id, trigger_org_structure_command_id, trigger_field_kind, before_org_unit_id, after_org_unit_id, before_position_id, after_position_id,
                        recipient_employee_id, original_permission_adjustment_id, escalation_due_at, version, event_count, created_at, updated_at)
                values (:id, :kind, :origin, :status, :user, :role, :inactiveUnit, :command, :structureCommand, :field, :beforeUnit, :afterUnit, :beforePosition, :afterPosition,
                        :recipient, :original, :escalationDueAt, :version, :eventSequence, :at, :at)
                """)
                .param("id", opened.id().value()).param("kind", opened.kind().name()).param("origin", opened.origin().name())
                .param("status", opened.status().name()).param("user", opened.user() == null ? null : opened.user().value(), Types.OTHER)
                .param("role", opened.role() == null ? null : opened.role().value(), Types.OTHER)
                .param("inactiveUnit", opened.inactiveOrgUnit() == null ? null : opened.inactiveOrgUnit().value(), Types.OTHER)
                .param("command", opened.triggerCommand() == null ? null : opened.triggerCommand().value(), Types.OTHER)
                .param("structureCommand", opened.triggerOrgStructureCommand() == null ? null : opened.triggerOrgStructureCommand().value(), Types.OTHER)
                .param("field", opened.triggerField() == null ? null : opened.triggerField().name(), Types.VARCHAR)
                .param("beforeUnit", opened.beforeOrgUnit() == null ? null : opened.beforeOrgUnit().value(), Types.OTHER)
                .param("afterUnit", opened.afterOrgUnit() == null ? null : opened.afterOrgUnit().value(), Types.OTHER)
                .param("beforePosition", opened.beforePosition() == null ? null : opened.beforePosition().value(), Types.OTHER)
                .param("afterPosition", opened.afterPosition() == null ? null : opened.afterPosition().value(), Types.OTHER)
                .param("recipient", opened.recipient() == null ? null : opened.recipient().value(), Types.OTHER)
                .param("original", opened.original() == null ? null : opened.original().value(), Types.OTHER)
                .param("escalationDueAt", OffsetDateTime.ofInstant(opened.escalationDueAt(), ZoneOffset.UTC))
                .param("version", opened.versionAfterSave()).param("eventSequence", opened.eventSequenceAfterSave()).param("at", at).update();
    }

    @Override
    public Optional<PermissionAdjustment> findById(PermissionAdjustmentId id) {
        return jdbc.sql("select " + COLUMNS + " from platform_identity.permission_adjustments where permission_adjustment_id = :id")
                .param("id", id.value()).query(JdbcPermissionAdjustmentRepository::item).optional();
    }

    @Override
    public void update(PermissionAdjustment changed) {
        OffsetDateTime at = OffsetDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
        AdjustmentReference reference = changed.adjustedWith();
        int updated = jdbc.sql("""
                update platform_identity.permission_adjustments
                   set status = :status, upheld_at = coalesce(upheld_at, :upheldAt), upheld_by = coalesce(upheld_by, :upheldBy),
                       uphold_reason = coalesce(uphold_reason, :reason), business_owner_employee_id = coalesce(business_owner_employee_id, :owner),
                       uphold_expires_at = coalesce(uphold_expires_at, :expiresAt), adjusted_at = coalesce(adjusted_at, :adjustedAt),
                       adjusted_by = coalesce(adjusted_by, :adjustedBy),
                       adjusted_authorization_request_id = coalesce(adjusted_authorization_request_id, :request),
                       adjusted_audit_entry_id = coalesce(adjusted_audit_entry_id, :entry), escalated_at = coalesce(escalated_at, :escalatedAt),
                       expired_at = coalesce(expired_at, :expiredAt), closed_at = coalesce(closed_at, :closedAt),
                       closure_personnel_command_id = coalesce(closure_personnel_command_id, :closure), is_held_at_expiry = coalesce(is_held_at_expiry, :held),
                       regeneration_decided_at = coalesce(regeneration_decided_at, :decidedAt),
                       version = :next, event_count = :eventSequence, updated_at = :at
                 where permission_adjustment_id = :id and version = :version
                """)
                .param("status", changed.status().name())
                .param("upheldAt", changed.upheldBy() == null ? null : at, Types.TIMESTAMP_WITH_TIMEZONE)
                .param("upheldBy", changed.upheldBy() == null ? null : changed.upheldBy().value(), Types.OTHER)
                .param("reason", changed.upholdReason() == null ? null : changed.upholdReason().value(), Types.VARCHAR)
                .param("owner", changed.businessOwner() == null ? null : changed.businessOwner().value(), Types.OTHER)
                .param("expiresAt", utc(changed.upholdExpiresAt()), Types.TIMESTAMP_WITH_TIMEZONE)
                .param("adjustedAt", changed.status() == PermissionAdjustmentStatus.ADJUSTED ? at : null, Types.TIMESTAMP_WITH_TIMEZONE)
                .param("adjustedBy", changed.adjustedBy() == null ? null : changed.adjustedBy().value(), Types.OTHER)
                .param("request", reference == null || reference.authorizationRequest() == null ? null : reference.authorizationRequest().value(), Types.OTHER)
                .param("entry", reference == null || reference.auditEntry() == null ? null : reference.auditEntry().value(), Types.OTHER)
                .param("escalatedAt", utc(changed.escalatedAt()), Types.TIMESTAMP_WITH_TIMEZONE)
                .param("expiredAt", utc(changed.expiredAt()), Types.TIMESTAMP_WITH_TIMEZONE)
                .param("closedAt", utc(changed.closedAt()), Types.TIMESTAMP_WITH_TIMEZONE)
                .param("closure", changed.closurePersonnelCommand() == null ? null : changed.closurePersonnelCommand().value(), Types.OTHER)
                .param("held", changed.heldAtExpiry(), Types.BOOLEAN)
                .param("decidedAt", utc(changed.regenerationDecidedAt()), Types.TIMESTAMP_WITH_TIMEZONE)
                .param("next", changed.versionAfterSave()).param("eventSequence", changed.eventSequenceAfterSave()).param("at", at)
                .param("id", changed.id().value()).param("version", changed.version()).update();
        if (updated != 1) {
            throw IdentityError.PLATFORM_IDENTITY_CONCURRENT_UPDATE.refusal("the item " + changed.id().value() + " was changed by someone else");
        }
    }

    @Override
    public List<PermissionAdjustmentId> dueToExpire(Instant now, @Nullable PermissionAdjustmentId after, int limit) {
        return ids(jdbc.sql("""
                select permission_adjustment_id from platform_identity.permission_adjustments
                 where status = 'UPHELD' and uphold_expires_at <= :now and (cast(:after as uuid) is null or (created_at, permission_adjustment_id)
                       > (select created_at, permission_adjustment_id from platform_identity.permission_adjustments where permission_adjustment_id = :after))
                 order by created_at, permission_adjustment_id limit :limit
                """).param("now", utc(now)).param("after", after == null ? null : after.value(), Types.OTHER).param("limit", limit));
    }

    @Override
    public List<PermissionAdjustmentId> pendingRegeneration(@Nullable PermissionAdjustmentId after, int limit) {
        return ids(jdbc.sql("""
                select permission_adjustment_id from platform_identity.permission_adjustments
                 where status = 'EXPIRED' and regeneration_decided_at is null and (cast(:after as uuid) is null or (created_at, permission_adjustment_id)
                       > (select created_at, permission_adjustment_id from platform_identity.permission_adjustments where permission_adjustment_id = :after))
                 order by created_at, permission_adjustment_id limit :limit
                """).param("after", after == null ? null : after.value(), Types.OTHER).param("limit", limit));
    }

    @Override
    public List<PermissionAdjustmentId> overdueOpen(Instant now, @Nullable PermissionAdjustmentId after, int limit) {
        return ids(jdbc.sql("""
                select permission_adjustment_id from platform_identity.permission_adjustments
                 where status = 'OPEN' and escalated_at is null and escalation_due_at <= :now and (cast(:after as uuid) is null or (created_at, permission_adjustment_id)
                       > (select created_at, permission_adjustment_id from platform_identity.permission_adjustments where permission_adjustment_id = :after))
                 order by created_at, permission_adjustment_id limit :limit
                """).param("now", utc(now)).param("after", after == null ? null : after.value(), Types.OTHER).param("limit", limit));
    }

    @Override
    public List<PermissionAdjustmentId> openOf(UserId user) {
        return ids(jdbc.sql("""
                select permission_adjustment_id from platform_identity.permission_adjustments
                 where user_id = :user and status = 'OPEN' order by created_at, permission_adjustment_id
                """).param("user", user.value()));
    }

    @Override
    public List<PermissionAdjustmentId> openForInactiveUnitsOf(RoleId role) {
        return ids(jdbc.sql("""
                select permission_adjustment_id from platform_identity.permission_adjustments
                 where role_id = :role and origin_kind = 'ORG_UNIT_INACTIVE' and status = 'OPEN' order by created_at, permission_adjustment_id
                """).param("role", role.value()));
    }

    @Override
    public boolean existsOpenOrUpheldForInactiveUnit(RoleId role, OrgUnitId orgUnit) {
        return jdbc.sql("""
                select exists (select 1 from platform_identity.permission_adjustments
                                where origin_kind = 'ORG_UNIT_INACTIVE' and role_id = :role and org_unit_id = :unit and status in ('OPEN', 'UPHELD'))
                """).param("role", role.value()).param("unit", orgUnit.value()).query(Boolean.class).single();
    }

    private static List<PermissionAdjustmentId> ids(JdbcClient.StatementSpec query) {
        return query.query((row, ignored) -> new PermissionAdjustmentId(row.getObject(1, UUID.class))).list();
    }

    private static @Nullable OffsetDateTime utc(@Nullable Instant instant) {
        return instant == null ? null : OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
    }

    @Override
    public List<PermissionAdjustment> findFor(PermissionAdjustmentReader reader, @Nullable PermissionAdjustmentStatus status,
            @Nullable PermissionAdjustmentKind kind, @Nullable PermissionAdjustmentId after, int limit) {
        return jdbc.sql("select " + COLUMNS + """
                  from platform_identity.permission_adjustments
                 where (recipient_employee_id = :employee or (:holdsRead and user_id is distinct from :user))
                   and (cast(:status as text) is null or status = :status)
                   and (cast(:kind as text) is null or kind = :kind)
                   and (cast(:after as uuid) is null or (created_at, permission_adjustment_id)
                        < (select created_at, permission_adjustment_id from platform_identity.permission_adjustments where permission_adjustment_id = :after))
                 order by created_at desc, permission_adjustment_id desc
                 limit :limit
                """)
                .param("employee", reader.employee().value()).param("holdsRead", reader.holdsRead()).param("user", reader.user().value())
                .param("status", status == null ? null : status.name(), Types.VARCHAR).param("kind", kind == null ? null : kind.name(), Types.VARCHAR)
                .param("after", after == null ? null : after.value(), Types.OTHER).param("limit", limit)
                .query(JdbcPermissionAdjustmentRepository::item).list();
    }

    private static final String COLUMNS = "permission_adjustment_id, kind, origin_kind, status, user_id, role_id, org_unit_id, trigger_personnel_command_id, "
            + "trigger_org_structure_command_id, trigger_field_kind, before_org_unit_id, after_org_unit_id, before_position_id, after_position_id, recipient_employee_id, escalation_due_at, "
            + "created_at, upheld_by, business_owner_employee_id, uphold_expires_at, original_permission_adjustment_id, escalated_at, expired_at, is_held_at_expiry, "
            + "regeneration_decided_at, version, event_count";

    private static PermissionAdjustment item(ResultSet row, int ignored) throws SQLException {
        return PermissionAdjustment.restore(new PermissionAdjustment.Stored(new PermissionAdjustmentId(row.getObject("permission_adjustment_id", UUID.class)),
                PermissionAdjustmentKind.valueOf(row.getString("kind")), PermissionAdjustmentOrigin.valueOf(row.getString("origin_kind")),
                PermissionAdjustmentStatus.valueOf(row.getString("status")), mapped(row, "user_id", UserId::new), mapped(row, "role_id", RoleId::new),
                mapped(row, "org_unit_id", OrgUnitId::new), mapped(row, "trigger_personnel_command_id", PersonnelCommandId::new),
                mapped(row, "trigger_org_structure_command_id", OrgStructureCommandId::new),
                row.getString("trigger_field_kind") == null ? null : TriggerFieldKind.valueOf(row.getString("trigger_field_kind")),
                mapped(row, "before_org_unit_id", OrgUnitId::new), mapped(row, "after_org_unit_id", OrgUnitId::new),
                mapped(row, "before_position_id", PositionId::new), mapped(row, "after_position_id", PositionId::new),
                mapped(row, "recipient_employee_id", EmployeeId::new), row.getObject("escalation_due_at", OffsetDateTime.class).toInstant(),
                row.getObject("created_at", OffsetDateTime.class).toInstant(), mapped(row, "upheld_by", UserId::new),
                mapped(row, "business_owner_employee_id", EmployeeId::new),
                instant(row, "uphold_expires_at"), mapped(row, "original_permission_adjustment_id", PermissionAdjustmentId::new), instant(row, "escalated_at"),
                instant(row, "expired_at"), (Boolean) row.getObject("is_held_at_expiry"), instant(row, "regeneration_decided_at"), row.getLong("version"), row.getLong("event_count")));
    }

    private static @Nullable Instant instant(ResultSet row, String column) throws SQLException {
        OffsetDateTime value = row.getObject(column, OffsetDateTime.class);
        return value == null ? null : value.toInstant();
    }

    private static <T> @Nullable T mapped(ResultSet row, String column, Function<UUID, T> wrap) throws SQLException {
        UUID value = row.getObject(column, UUID.class);
        return value == null ? null : wrap.apply(value);
    }
}
