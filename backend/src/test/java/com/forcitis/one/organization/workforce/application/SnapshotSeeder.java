package com.forcitis.one.organization.workforce.application;

import com.forcitis.one.platform.integration.api.OwnObjectId;
import com.forcitis.one.platform.integration.api.SourceOrgSnapshot;
import com.forcitis.one.platform.integration.api.SourceOrgUnit;
import com.forcitis.one.platform.integration.api.SourcePerson;
import com.forcitis.one.platform.integration.api.SourcePosition;
import com.forcitis.one.platform.integration.api.SourceRecordState;
import com.forcitis.one.platform.integration.api.SourceRelationship;
import com.forcitis.one.platform.integration.api.SourceRelationshipKind;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * Puts what a test source holds into the snapshot tables, so tests that need a populated snapshot (login lookups, directory reads, attribution)
 * have one. Test fixture only: production has no writer of these rows (#934, #1222). A person with a login name is employed, one without is
 * separated; a person whose unit or position the source does not list is left out; the first listed head of a unit is its head.
 */
public final class SnapshotSeeder {
    private SnapshotSeeder() {
    }

    public static void seed(JdbcClient jdbc, SourceOrgSnapshot source) {
        List<SourcePosition> positions = source.positions();
        List<SourceOrgUnit> units = source.units();
        Set<UUID> unitIds = new HashSet<>();
        units.forEach(u -> unitIds.add(u.unitId().value()));
        Set<UUID> positionIds = new HashSet<>();
        positions.forEach(q -> positionIds.add(q.positionId().value()));
        Map<UUID, SourcePerson> staff = new LinkedHashMap<>();
        for (SourcePerson s : source.staff()) {
            if (unitIds.contains(s.unitId().value()) && s.positionId() != null && positionIds.contains(s.positionId().value())) {
                staff.putIfAbsent(s.personId().value(), s);
            }
        }
        Map<UUID, UUID> heads = firstPerKind(source, SourceRelationshipKind.UNIT_HEAD, staff);
        Map<UUID, UUID> managers = firstPerKind(source, SourceRelationshipKind.REGIONAL_RECEIVABLES_MANAGER, staff);
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        for (SourcePosition p : positions) {
            jdbc.sql("""
                    insert into organization_workforce.positions (id, code, name, sort_order, status, created_at, updated_at)
                    values (:id, :code, :name, :sortOrder, :status, :now, :now)
                    on conflict (id) do update set code = excluded.code, name = excluded.name, sort_order = excluded.sort_order, status = excluded.status
                    """)
                    .param("id", p.positionId().value()).param("code", p.code()).param("name", p.name())
                    .param("sortOrder", p.sortOrder()).param("status", p.state().name()).param("now", now).update();
        }
        // Units, people and assignments refer to each other: rows first, then the references, so the order of the source does not matter.
        for (SourceOrgUnit u : units) {
            jdbc.sql("""
                    insert into organization_workforce.org_units (id, code, name, parent_org_unit_id, sort_order, status, head_employee_id,
                           regional_receivables_manager_id, region_category, is_delivery_unit, short_name, revoked_at, created_at, updated_at)
                    values (:id, :code, :name, null, :sortOrder, :status, null, null, :category, :delivery, null,
                            null, :now, :now)
                    on conflict (id) do update set code = excluded.code, name = excluded.name,
                           sort_order = excluded.sort_order, status = excluded.status, region_category = excluded.region_category,
                           is_delivery_unit = excluded.is_delivery_unit
                    """)
                    .param("id", u.unitId().value()).param("code", u.code()).param("name", u.name()).param("sortOrder", u.sortOrder())
                    .param("status", u.state() == SourceRecordState.ACTIVE ? "ACTIVE" : "DISABLED")
                    .param("category", u.regionKind() == null ? null : u.regionKind().name()).param("delivery", u.deliveryUnit()).param("now", now).update();
        }
        for (SourcePerson s : staff.values()) {
            boolean employed = s.loginName() != null && !s.loginName().isBlank();
            jdbc.sql("""
                    insert into organization_workforce.employees (id, employee_code, display_name, email, gender, wecom_user_id,
                           employment_status, is_fee_earner, separated_at, created_at, updated_at, event_count)
                    values (:id, :code, :name, :email, :gender, :wecom, :status, :feeEarner,
                            case when cast(:status as text) = 'SEPARATED' then cast(:now as timestamptz) end, :now, :now, 0)
                    on conflict (id) do update set display_name = excluded.display_name, email = excluded.email,
                           gender = excluded.gender, wecom_user_id = excluded.wecom_user_id, employment_status = excluded.employment_status,
                           is_fee_earner = excluded.is_fee_earner, separated_at = excluded.separated_at
                    """)
                    .param("id", s.personId().value()).param("code", s.employeeCode()).param("name", s.displayName())
                    .param("email", s.email()).param("gender", s.gender() == null ? null : s.gender().name())
                    .param("wecom", s.wecomUserId() == null ? null : s.wecomUserId().value())
                    .param("status", employed ? "EMPLOYED" : "SEPARATED").param("feeEarner", s.feeEarner()).param("now", now).update();
        }
        for (SourcePerson s : staff.values()) {
            OwnObjectId supervisor = s.supervisorId();
            UUID reportsTo = supervisor != null && staff.containsKey(supervisor.value()) && !supervisor.equals(s.personId()) ? supervisor.value() : null;
            jdbc.sql("""
                    insert into organization_workforce.assignments (employee_id, org_unit_id, position_id, reports_to_employee_id, created_at, updated_at)
                    values (:employee, :orgUnit, :position, :reportsTo, :now, :now)
                    on conflict (employee_id) do update set org_unit_id = excluded.org_unit_id, position_id = excluded.position_id,
                           reports_to_employee_id = excluded.reports_to_employee_id
                    """)
                    .param("employee", s.personId().value()).param("orgUnit", s.unitId().value()).param("position", s.positionId().value())
                    .param("reportsTo", reportsTo, java.sql.Types.OTHER).param("now", now).update();
        }
        for (SourceOrgUnit u : units) {
            jdbc.sql("""
                    update organization_workforce.org_units set parent_org_unit_id = :parent, head_employee_id = :head, regional_receivables_manager_id = :manager
                     where id = :id
                    """)
                    .param("id", u.unitId().value())
                    .param("parent", u.parentUnitId() != null && unitIds.contains(u.parentUnitId().value()) ? u.parentUnitId().value() : null, java.sql.Types.OTHER)
                    .param("head", heads.get(u.unitId().value()), java.sql.Types.OTHER)
                    .param("manager", managers.get(u.unitId().value()), java.sql.Types.OTHER).update();
        }
    }

    /** The relationships come ordered by person, so the first listed person of a unit is the one that counts (RULE-356). */
    private static Map<UUID, UUID> firstPerKind(SourceOrgSnapshot source, SourceRelationshipKind kind, Map<UUID, SourcePerson> staff) {
        Map<UUID, UUID> first = new LinkedHashMap<>();
        for (SourceRelationship r : source.relationships()) {
            if (r.kind() == kind && !r.personDeleted() && staff.containsKey(r.personId().value())) {
                first.putIfAbsent(r.unitId().value(), r.personId().value());
            }
        }
        return first;
    }
}
