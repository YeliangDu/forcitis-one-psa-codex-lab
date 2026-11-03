package com.forcitis.one.platform.identity.infrastructure.persistence;

import com.forcitis.one.kernel.UserId;
import com.forcitis.one.organization.workforce.api.PersonnelCommandId;
import com.forcitis.one.organization.workforce.api.TriggerFieldKind;
import com.forcitis.one.platform.identity.domain.PermissionAdjustment;
import com.forcitis.one.platform.identity.domain.PermissionAdjustmentKind;
import com.forcitis.one.platform.identity.domain.PermissionAdjustmentRepository;
import java.sql.Types;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class JdbcPermissionAdjustmentRepository implements PermissionAdjustmentRepository {

    private final JdbcClient jdbc;

    JdbcPermissionAdjustmentRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
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
                        escalation_due_at, version, event_count, created_at, updated_at)
                values (:id, :kind, :origin, :status, :user, :command, :field, :beforeUnit, :afterUnit, :beforePosition, :afterPosition,
                        :escalationDueAt, :version, :eventSequence, :at, :at)
                """)
                .param("id", opened.id().value()).param("kind", opened.kind().name()).param("origin", opened.origin().name())
                .param("status", opened.status().name()).param("user", opened.user().value())
                .param("command", opened.triggerCommand().value()).param("field", opened.triggerField().name())
                .param("beforeUnit", opened.beforeOrgUnit() == null ? null : opened.beforeOrgUnit().value(), Types.OTHER)
                .param("afterUnit", opened.afterOrgUnit().value())
                .param("beforePosition", opened.beforePosition() == null ? null : opened.beforePosition().value(), Types.OTHER)
                .param("afterPosition", opened.afterPosition() == null ? null : opened.afterPosition().value(), Types.OTHER)
                .param("escalationDueAt", OffsetDateTime.ofInstant(opened.escalationDueAt(), ZoneOffset.UTC))
                .param("version", opened.versionAfterSave()).param("eventSequence", opened.eventSequenceAfterSave()).param("at", at).update();
    }
}
