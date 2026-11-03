package com.forcitis.one.platform.identity.infrastructure.persistence;

import com.forcitis.one.kernel.EmployeeId;
import com.forcitis.one.kernel.UserId;
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
                insert into platform_identity.permission_adjustments (permission_adjustment_id, kind, origin_kind, status, user_id,
                        trigger_personnel_command_id, trigger_field_kind, before_org_unit_id, after_org_unit_id, before_position_id, after_position_id,
                        recipient_employee_id, escalation_due_at, version, event_count, created_at, updated_at)
                values (:id, :kind, :origin, :status, :user, :command, :field, :beforeUnit, :afterUnit, :beforePosition, :afterPosition,
                        :recipient, :escalationDueAt, :version, :eventSequence, :at, :at)
                """)
                .param("id", opened.id().value()).param("kind", opened.kind().name()).param("origin", opened.origin().name())
                .param("status", opened.status().name()).param("user", opened.user() == null ? null : opened.user().value(), Types.OTHER)
                .param("command", opened.triggerCommand() == null ? null : opened.triggerCommand().value(), Types.OTHER)
                .param("field", opened.triggerField() == null ? null : opened.triggerField().name(), Types.VARCHAR)
                .param("beforeUnit", opened.beforeOrgUnit() == null ? null : opened.beforeOrgUnit().value(), Types.OTHER)
                .param("afterUnit", opened.afterOrgUnit() == null ? null : opened.afterOrgUnit().value(), Types.OTHER)
                .param("beforePosition", opened.beforePosition() == null ? null : opened.beforePosition().value(), Types.OTHER)
                .param("afterPosition", opened.afterPosition() == null ? null : opened.afterPosition().value(), Types.OTHER)
                .param("recipient", opened.recipient() == null ? null : opened.recipient().value(), Types.OTHER)
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
                   set status = :status, upheld_at = :upheldAt, upheld_by = :upheldBy, uphold_reason = :reason,
                       business_owner_employee_id = :owner, uphold_expires_at = :expiresAt, adjusted_at = :adjustedAt, adjusted_by = :adjustedBy,
                       adjusted_authorization_request_id = :request, adjusted_audit_entry_id = :entry,
                       version = :next, event_count = :eventSequence, updated_at = :at
                 where permission_adjustment_id = :id and version = :version
                """)
                .param("status", changed.status().name())
                .param("upheldAt", changed.upheldBy() == null ? null : at, Types.TIMESTAMP_WITH_TIMEZONE)
                .param("upheldBy", changed.upheldBy() == null ? null : changed.upheldBy().value(), Types.OTHER)
                .param("reason", changed.upholdReason() == null ? null : changed.upholdReason().value(), Types.VARCHAR)
                .param("owner", changed.businessOwner() == null ? null : changed.businessOwner().value(), Types.OTHER)
                .param("expiresAt", changed.upholdExpiresAt() == null ? null : OffsetDateTime.ofInstant(changed.upholdExpiresAt(), ZoneOffset.UTC),
                        Types.TIMESTAMP_WITH_TIMEZONE)
                .param("adjustedAt", changed.adjustedBy() == null ? null : at, Types.TIMESTAMP_WITH_TIMEZONE)
                .param("adjustedBy", changed.adjustedBy() == null ? null : changed.adjustedBy().value(), Types.OTHER)
                .param("request", reference == null || reference.authorizationRequest() == null ? null : reference.authorizationRequest().value(), Types.OTHER)
                .param("entry", reference == null || reference.auditEntry() == null ? null : reference.auditEntry().value(), Types.OTHER)
                .param("next", changed.versionAfterSave()).param("eventSequence", changed.eventSequenceAfterSave()).param("at", at)
                .param("id", changed.id().value()).param("version", changed.version()).update();
        if (updated != 1) {
            throw IdentityError.PLATFORM_IDENTITY_CONCURRENT_UPDATE.refusal("the item " + changed.id().value() + " was changed by someone else");
        }
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
            + "trigger_field_kind, before_org_unit_id, after_org_unit_id, before_position_id, after_position_id, recipient_employee_id, escalation_due_at, "
            + "created_at, upheld_by, business_owner_employee_id, uphold_expires_at, version, event_count";

    private static PermissionAdjustment item(ResultSet row, int ignored) throws SQLException {
        return PermissionAdjustment.restore(new PermissionAdjustment.Stored(new PermissionAdjustmentId(row.getObject("permission_adjustment_id", UUID.class)),
                PermissionAdjustmentKind.valueOf(row.getString("kind")), PermissionAdjustmentOrigin.valueOf(row.getString("origin_kind")),
                PermissionAdjustmentStatus.valueOf(row.getString("status")), mapped(row, "user_id", UserId::new), mapped(row, "role_id", RoleId::new),
                mapped(row, "org_unit_id", OrgUnitId::new), mapped(row, "trigger_personnel_command_id", PersonnelCommandId::new),
                row.getString("trigger_field_kind") == null ? null : TriggerFieldKind.valueOf(row.getString("trigger_field_kind")),
                mapped(row, "before_org_unit_id", OrgUnitId::new), mapped(row, "after_org_unit_id", OrgUnitId::new),
                mapped(row, "before_position_id", PositionId::new), mapped(row, "after_position_id", PositionId::new),
                mapped(row, "recipient_employee_id", EmployeeId::new), row.getObject("escalation_due_at", OffsetDateTime.class).toInstant(),
                row.getObject("created_at", OffsetDateTime.class).toInstant(), mapped(row, "upheld_by", UserId::new),
                mapped(row, "business_owner_employee_id", EmployeeId::new),
                row.getObject("uphold_expires_at", OffsetDateTime.class) == null ? null : row.getObject("uphold_expires_at", OffsetDateTime.class).toInstant(),
                row.getLong("version"), row.getLong("event_count")));
    }

    private static <T> @Nullable T mapped(ResultSet row, String column, Function<UUID, T> wrap) throws SQLException {
        UUID value = row.getObject(column, UUID.class);
        return value == null ? null : wrap.apply(value);
    }
}
